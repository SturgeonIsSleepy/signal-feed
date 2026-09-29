package cc.ccwu.signalfeed

import android.content.Context
import android.net.Uri
import android.util.Xml
import androidx.core.text.HtmlCompat
import cc.ccwu.signalfeed.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

internal data class Subscription(val url: String, val name: String, val topic: String, val enabled: Boolean = true) {
    val id: String get() = "sub:" + MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
}
internal class Subscriptions private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("subscriptions", Context.MODE_PRIVATE)
    val items = MutableStateFlow(runCatching { parse(prefs.getString("items", "{\"subscriptions\":[]}")!!) }.getOrDefault(emptyList()))
    val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    fun save(list: List<Subscription>) {
        val unique = list.distinctBy { it.url }; require(unique.size <= 100) { "最多 100 个订阅" }
        prefs.edit().putString("items", encode(unique)).apply(); items.value = unique
    }
    fun merge(list: List<Subscription>) { save(items.value + list.filter { fresh -> items.value.none { it.url == fresh.url } }) }
    fun encode(list: List<Subscription>) = JSONObject().put("subscriptions", JSONArray(list.map {
        JSONObject().put("url", it.url).put("name", it.name).put("topic", it.topic).put("enabled", it.enabled)
    })).toString(2)
    fun read(uri: Uri): List<Subscription> = context.contentResolver.openInputStream(uri)!!.use { input ->
        val bytes = input.readBytesLimited(1024 * 1024)
        parse(bytes.toString(Charsets.UTF_8))
    }
    private fun parse(text: String): List<Subscription> {
        val result = mutableListOf<Subscription>()
        fun add(url: String, name: String, topic: String, enabled: Boolean = true) {
            val parsed = url.toHttpUrlOrNull() ?: error("订阅地址无效")
            require(parsed.isHttps && parsed.username.isEmpty() && parsed.password.isEmpty()) { "订阅需要 HTTPS 地址" }
            require(name.isNotBlank() && name.length <= 120)
            require(topic.isNotBlank() && topic.length <= 32) { "主题长度应为 1 至 32 个字符" }
            result += Subscription(parsed.toString(), name, topic, enabled)
        }
        if (text.trimStart('\uFEFF', ' ', '\r', '\n').startsWith('<')) {
            val xml = parser(text)
            while (xml.eventType != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG && xml.name == "outline") {
                    xml.getAttributeValue(null, "xmlUrl")?.let { url -> add(url, xml.getAttributeValue(null, "title") ?: xml.getAttributeValue(null, "text") ?: url, xml.getAttributeValue(null, "category") ?: "其他") }
                }
                xml.next()
            }
        } else {
            val array = JSONObject(text).getJSONArray("subscriptions")
            require(array.length() <= 100)
            for (i in 0 until array.length()) { val row = array.getJSONObject(i); add(row.getString("url"), row.getString("name"), row.optString("topic", "其他"), row.optBoolean("enabled", true)) }
        }
        require(result.size <= 100); return result.distinctBy { it.url }
    }
    suspend fun refresh(): Int = withContext(Dispatchers.IO) {
        if (!context.getSharedPreferences("reading", Context.MODE_PRIVATE).getBoolean("subscriptions_enabled", true)) return@withContext 0
        val dao = FeedDatabase.get(context).feedDao(); var failures = 0
        for (subscription in items.value.filter { it.enabled }) {
            val id = subscription.id
            if (dao.account(id) == null) dao.upsertAccounts(listOf(AccountEntity(id, subscription.name, "@subscription")))
            dao.insertTopics(listOf(TopicEntity(subscription.topic, subscription.topic)))
            try {
                client.newCall(Request.Builder().url(RuntimeModules.url(subscription.url)).header("User-Agent", "SignalFeed/0.7 RSS reader").build()).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val xml = response.body!!.byteStream().use { it.readBytesLimited(4 * 1024 * 1024).toString(Charsets.UTF_8) }
                    val posts = parseFeed(xml, subscription)
                    dao.upsertSources(listOf(SourceEntity(id, id, subscription.name, subscription.url)))
                    dao.upsertPosts(posts)
                    dao.upsertPostSources(posts.map { PostSourceEntity(it.id, id, it.originalUrl) })
                    dao.upsertPostTopics(posts.map { PostTopicEntity(it.id, subscription.topic) })
                }
            } catch (_: Exception) {
                failures++
                dao.upsertSources(listOf(SourceEntity(id, id, subscription.name, subscription.url, "ERROR")))
            }
        }
        failures
    }
    private fun parseFeed(text: String, subscription: Subscription): List<PostEntity> {
        val xml = parser(text); val result = mutableListOf<PostEntity>()
        var fields: MutableMap<String, String>? = null; var depth = 0; var field = ""; var content = StringBuilder()
        while (xml.eventType != XmlPullParser.END_DOCUMENT && result.size < 100) {
            when (xml.eventType) {
                XmlPullParser.START_TAG -> {
                    val name = xml.name.substringAfter(':')
                    if (name == "item" || name == "entry") { fields = mutableMapOf(); depth = xml.depth }
                    else if (fields != null && xml.depth == depth + 1) {
                        field = name; content = StringBuilder()
                        if (name == "link" && xml.getAttributeValue(null, "rel") in listOf(null, "alternate")) xml.getAttributeValue(null, "href")?.let { fields!!["link"] = it }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (fields != null) content.append(xml.text)
                XmlPullParser.END_TAG -> if (fields != null) {
                    if (xml.depth == depth + 1 && content.isNotBlank()) fields!![field] = content.toString()
                    if (xml.depth == depth) {
                        val data = fields!!; fields = null
                        val title = plain(data["title"].orEmpty())
                        val rawLink = data["link"].orEmpty().trim()
                        val link = runCatching { java.net.URI(subscription.url).resolve(rawLink).toString() }.getOrDefault("")
                        val date = listOf("pubDate", "published", "date", "updated").firstNotNullOfOrNull { key -> data[key]?.let { date(it.trim()) } }
                        if (title.isNotBlank() && rawLink.isNotBlank() && link.startsWith("https://") && date != null) {
                            val body = plain(data["encoded"] ?: data["content"] ?: data["description"] ?: data["summary"].orEmpty())
                            val hash = MessageDigest.getInstance("SHA-256").digest(link.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
                            result += PostEntity(subscription.id + ":" + hash, subscription.id, if (body.isBlank() || body == title) title else "$title\n\n$body", date, System.currentTimeMillis(), 55, "UNCONFIRMED", false, link)
                        }
                    }
                }
            }
            xml.next()
        }
        check(result.isNotEmpty()) { "订阅没有带有效链接和日期的 RSS/Atom 条目" }; return result
    }
    private fun plain(text: String) = HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
    private fun date(text: String): Long? = runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
        ?: runCatching { ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { ZonedDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
    private fun parser(text: String) = Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true); setInput(text.reader()) }
    companion object {
        @Volatile private var instance: Subscriptions? = null
        fun get(context: Context): Subscriptions = instance ?: synchronized(this) { instance ?: Subscriptions(context.applicationContext).also { instance = it } }
    }
}
internal fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) { val n = read(buffer); if (n < 0) break; require(out.size() + n <= limit) { "文件超过大小限制" }; out.write(buffer, 0, n) }
    return out.toByteArray()
}

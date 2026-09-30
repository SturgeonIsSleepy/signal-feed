package cc.ccwu.signalfeed

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest

/** Imported configuration only. Empty storage means an empty application. */
internal class ShellPacks private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("shell-packs", Context.MODE_PRIVATE)
    val sourcePacks = MutableStateFlow(loadSources())
    val sourceApi = MutableStateFlow(sourcePacks.value.firstOrNull()?.optString("apiBaseUrl"))
    val sourceAccounts = MutableStateFlow(sourcePacks.value.flatMap { accounts(it) }.toSet())
    val filterPacks = MutableStateFlow(rows("filters"))
    val translatorPacks = MutableStateFlow(rows("translators"))
    val dataPacks = MutableStateFlow(rows("data"))
    val breakingPacks = MutableStateFlow(rows("breaking"))
    val theme = MutableStateFlow(runCatching { JSONObject(prefs.getString("theme", "{}")!!) }.getOrDefault(JSONObject()))
    private fun rows(kind: String) = runCatching { val a = JSONArray(prefs.getString(kind, "[]")); List(a.length()) { a.getJSONObject(it) } }.getOrDefault(emptyList())
    private fun loadSources(): List<JSONObject> {
        if (prefs.contains("sources")) return rows("sources")
        val old = prefs.getString("source_api", null) ?: return emptyList()
        val ids = prefs.getString("source_accounts", "")!!.split(',').filter { it.isNotBlank() }
        return if (ids.isEmpty()) emptyList() else listOf(JSONObject().put("id", "legacy-source")
            .put("name", "已有聚合服务").put("apiBaseUrl", old).put("accounts", JSONArray(ids)))
    }
    private fun accounts(json: JSONObject): List<String> = json.optJSONArray("accounts")?.let { array ->
        (0 until array.length()).map { array.getString(it) }
    }.orEmpty()
    private fun writeSources(list: List<JSONObject>) {
        prefs.edit().putString("sources", JSONArray(list).toString()).remove("source_api").remove("source_accounts").apply()
        sourcePacks.value = list
        sourceApi.value = list.firstOrNull()?.optString("apiBaseUrl")
        sourceAccounts.value = list.flatMap(::accounts).toSet()
    }
    fun breakingRules(): List<JSONObject> = breakingPacks.value + filterPacks.value.flatMap { pack ->
        val array = pack.optJSONArray("breaking")
        (0 until (array?.length() ?: 0)).map { array!!.getJSONObject(it) }
    }
    private fun normalizedSource(json: JSONObject, current: List<JSONObject>): JSONObject {
        require(json.optInt("formatVersion") == 1) { "配置格式版本应为 1" }
        val url = https(json.getString("apiBaseUrl")).trimEnd('/') + "/"
        val ids = accounts(json)
        require(ids.size in 1..100) { "聚合服务文件须列出订阅账号" }
        require(ids.distinct().size == ids.size) { "同一文件中的账号不能重复" }
        require(ids.all { it.matches(Regex("[a-zA-Z0-9_-]{1,64}")) }) { "账号标识无效" }
        val fingerprint = MessageDigest.getInstance("SHA-256").digest((url + ids.sorted().joinToString(",")).toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
        val id = json.optString("id").ifBlank { "source-$fingerprint" }
        require(id.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) { "来源标识无效" }
        val remaining = current.filterNot { it.optString("id") == id }
        require(remaining.size < 100) { "最多导入 100 份来源文件" }
        require(remaining.none { it.optString("apiBaseUrl") != url && accounts(it).any(ids::contains) }) {
            "不同服务使用了相同账号标识，请调整来源文件"
        }
        return JSONObject(json.toString()).put("id", id).put("name", json.optString("name", "聚合服务"))
            .put("apiBaseUrl", url).put("accounts", JSONArray(ids))
    }
    fun importSubscriptionBundle(json: JSONObject, install: Boolean = true): String {
        require(json.optInt("formatVersion") == 1 && json.optString("type") == "subscription-pack")
        val sourceRows = json.optJSONArray("sources") ?: JSONArray()
        val panelRows = json.optJSONArray("panels") ?: JSONArray()
        require(sourceRows.length() <= 100 && panelRows.length() <= 100)
        val subscriptions = Subscriptions.get(context)
        val feeds = if (json.has("subscriptions")) subscriptions.parse(json.toString()) else emptyList()
        require(sourceRows.length() + panelRows.length() + feeds.size > 0) { "订阅包没有内容" }
        var nextSources = sourcePacks.value
        for (i in 0 until sourceRows.length()) {
            val source = normalizedSource(sourceRows.getJSONObject(i), nextSources)
            nextSources = nextSources.filterNot { it.optString("id") == source.getString("id") } + source
        }
        var nextPanels = dataPacks.value
        for (i in 0 until panelRows.length()) {
            val panel = panelRows.getJSONObject(i)
            validatePack("data", panel)
            nextPanels = nextPanels.filterNot { it.getString("id") == panel.getString("id") } + panel
        }
        require(nextPanels.size <= 100 && (subscriptions.items.value + feeds).distinctBy { it.url }.size <= 100)
        if (install) {
            if (sourceRows.length() > 0) writeSources(nextSources)
            if (panelRows.length() > 0) write("data", nextPanels)
            if (feeds.isNotEmpty()) subscriptions.merge(feeds)
        }
        return "${sourceRows.length()} 个服务，${feeds.size} 个 RSS，${panelRows.length()} 个数据栏目"
    }
    fun validatePack(kind: String, json: JSONObject) {
        require(json.optInt("formatVersion") == 1) { "配置格式版本应为 1" }
        when (kind) {
            "source" -> normalizedSource(json, sourcePacks.value)
            "theme" -> {
                val palettes = listOf(json) + listOfNotNull(json.optJSONObject("light"), json.optJSONObject("dark"))
                palettes.forEach { palette ->
                    listOf("primary", "background", "surface", "text", "secondary", "line", "muted").forEach { key ->
                        if (palette.has(key)) require(palette.getString(key).matches(Regex("#[0-9a-fA-F]{6}"))) { "$key 须为 #RRGGBB" }
                    }
                }
                val languages = json.optJSONArray("languages") ?: JSONArray()
                require(languages.length() <= 20)
                val codes = mutableSetOf<String>()
                for (i in 0 until languages.length()) {
                    val language = languages.getJSONObject(i)
                    val code = language.getString("code")
                    require(code.matches(Regex("[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*")) && code !in listOf("zh", "en") && codes.add(code)) { "语言标识无效或重复" }
                    require(language.getString("name").length in 1..80)
                    val strings = language.getJSONObject("strings")
                    require(strings.length() in 1..1000)
                    strings.keys().forEach { key ->
                        require(key.length in 1..2000 && strings.get(key) is String && strings.getString(key).length in 1..4000)
                        val slots = Regex("\\{[a-zA-Z]+}")
                        require(key.replace(slots, "").isNotBlank()) { "语言字典键需要固定文案" }
                        require(slots.findAll(key).map { it.value }.toSet() == slots.findAll(strings.getString(key)).map { it.value }.toSet()) { "语言字典占位符不一致" }
                    }
                }
            }
            else -> {
                require(json.getString("id").matches(Regex("[a-zA-Z0-9_-]{1,64}"))) { "配置 id 无效" }
                require(json.getString("name").length in 1..80)
                validate(kind, json)
            }
        }
    }
    fun import(kind: String, json: JSONObject): String {
        validatePack(kind, json)
        when (kind) {
            "source" -> {
                val normalized = normalizedSource(json, sourcePacks.value)
                val id = normalized.getString("id")
                val current = sourcePacks.value.filterNot { it.optString("id") == id }
                writeSources(current + normalized)
                return "已添加 ${normalized.getString("name")}"
            }
            "theme" -> {
                theme.value = json
                prefs.edit().putString("theme", json.toString()).apply()
                return "${json.optString("name", "主题包")} 已应用"
            }
            "filter", "translator", "data", "breaking" -> {
                val id = json.getString("id")
                require(id.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) { "配置 id 无效" }
                require(json.getString("name").length in 1..80)
                validate(kind, json)
                val key = key(kind)
                val updated = (rows(key).filterNot { it.getString("id") == id } + json).takeLast(100)
                write(key, updated)
                return "已导入 ${json.getString("name")}"
            }
            else -> error("不支持的配置类型")
        }
    }
    fun remove(kind: String, id: String) {
        if (kind == "source") writeSources(sourcePacks.value.filterNot { it.optString("id") == id })
        else if (kind == "theme") { theme.value = JSONObject(); prefs.edit().remove("theme").apply() }
        else write(key(kind), rows(key(kind)).filterNot { it.optString("id") == id })
    }
    private fun write(key: String, list: List<JSONObject>) {
        prefs.edit().putString(key, JSONArray(list).toString()).apply()
        when (key) {
            "filters" -> filterPacks.value = list
            "translators" -> translatorPacks.value = list
            "data" -> dataPacks.value = list
            "breaking" -> breakingPacks.value = list
        }
    }
    private fun key(kind: String) = when (kind) { "filter" -> "filters"; "translator" -> "translators"; "data" -> "data"; "breaking" -> "breaking"; else -> error("invalid kind") }
    private fun validate(kind: String, json: JSONObject) {
        when (kind) {
            "filter" -> {
                val array = json.optJSONArray("rules") ?: JSONArray()
                val breaking = json.optJSONArray("breaking") ?: JSONArray()
                require(array.length() <= 100 && breaking.length() <= 100 && array.length() + breaking.length() > 0)
                for (i in 0 until array.length()) {
                    val rule = array.getJSONObject(i)
                    require(rule.getString("action") in setOf("hide", "allow"))
                    require(listOf("accounts", "topics", "keywords").any { (rule.optJSONArray(it)?.length() ?: 0) > 0 } || rule.optString("bodyRegex").isNotEmpty()) { "筛选规则至少需要一个条件" }
                    rule.optString("bodyRegex").takeIf { it.isNotEmpty() }?.let { require(it.length <= 240); Regex(it) }
                }
                for (i in 0 until breaking.length()) validate("breaking", breaking.getJSONObject(i))
            }
            "translator" -> {
                require(json.getString("mode") in setOf("device", "worker"))
                if (json.getString("mode") == "worker") https(json.getString("url"))
            }
            "data" -> {
                require(json.getString("kind") in setOf("cards", "calendar", "leaderboard", "f1-calendar", "f1-points", "ai-models"))
                val rows = json.optJSONArray("rows")
                require(rows == null || rows.length() <= 500)
                if (json.has("url")) https(json.getString("url"))
            }
            "breaking" -> {
                json.optString("bodyRegex").takeIf { it.isNotEmpty() }?.let { require(it.length <= 240); Regex(it) }
                require(json.optInt("minimumImportance", 0) in 0..100)
                require(json.optInt("minimumImportance", 0) > 0 ||
                    listOf("accounts", "topics", "keywords").any { (json.optJSONArray(it)?.length() ?: 0) > 0 } ||
                    json.optString("bodyRegex").isNotEmpty()) { "Breaking 规则至少需要一个条件" }
            }
        }
    }
    private fun https(input: String): String {
        val url = input.toHttpUrlOrNull() ?: error("请输入有效 HTTPS 地址")
        require(url.isHttps && url.username.isEmpty() && url.password.isEmpty())
        return url.toString()
    }
    companion object {
        @Volatile private var instance: ShellPacks? = null
        fun get(context: Context): ShellPacks = instance ?: synchronized(this) { instance ?: ShellPacks(context.applicationContext).also { instance = it } }
    }
}

internal object ImportedRules {
    private fun values(json: JSONObject, key: String) = json.optJSONArray(key)?.let { array -> (0 until array.length()).map { array.getString(it) } }.orEmpty()
    fun hide(packs: List<JSONObject>, body: String, account: String, topics: Set<String>): Boolean {
        for (pack in packs) {
            val rules = pack.optJSONArray("rules") ?: continue
            for (i in 0 until rules.length()) {
                val rule = rules.getJSONObject(i)
                if (values(rule, "accounts").takeIf { it.isNotEmpty() }?.let { account !in it } == true) continue
                if (values(rule, "topics").takeIf { it.isNotEmpty() }?.let { topics.intersect(it.toSet()).isEmpty() } == true) continue
                if (values(rule, "keywords").takeIf { it.isNotEmpty() }?.let { words -> words.none { body.contains(it, true) } } == true) continue
                val expression = rule.optString("bodyRegex")
                if (expression.isNotEmpty() && !runCatching { Regex(expression).containsMatchIn(body.take(5000)) }.getOrDefault(false)) continue
                return rule.optString("action") == "hide"
            }
        }
        return false
    }
    fun breaking(packs: List<JSONObject>, body: String, account: String, topics: Set<String>, importance: Int): Boolean = packs.any { rule ->
        importance >= rule.optInt("minimumImportance", 0) &&
            values(rule, "accounts").let { it.isEmpty() || account in it } &&
            values(rule, "topics").let { it.isEmpty() || topics.any(it::contains) } &&
            values(rule, "keywords").let { words -> words.isEmpty() || words.any { body.contains(it, true) } } &&
            rule.optString("bodyRegex").let { expression -> expression.isEmpty() || runCatching { Regex(expression).containsMatchIn(body.take(5000)) }.getOrDefault(false) }
    }
}

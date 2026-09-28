package cc.ccwu.signalfeed

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.ZipInputStream

internal data class FeedMod(val id: String, val name: String, val version: String, val words: List<String>, val accounts: List<String>, val enabled: Boolean = true)
internal class ModStore(context: Context) {
    private val prefs = context.getSharedPreferences("mods", Context.MODE_PRIVATE)
    var packs by mutableStateOf(runCatching {
        val list = JSONArray(prefs.getString("packs", "[]"))
        List(list.length()) { decode(list.getJSONObject(it), false) }
    }.getOrDefault(emptyList()))
        private set
    fun save(pack: FeedMod) { packs = packs.filterNot { it.id == pack.id } + pack; persist() }
    fun remove(id: String) { packs = packs.filterNot { it.id == id }; persist() }
    fun toggle(id: String, enabled: Boolean) { packs = packs.map { if (it.id == id) it.copy(enabled = enabled) else it }; persist() }
    fun hides(body: String, account: String) = packs.any { it.enabled && (account in it.accounts || it.words.any { word -> body.contains(word, ignoreCase = true) }) }
    private fun persist() {
        val list = JSONArray()
        packs.forEach { list.put(JSONObject().put("formatVersion", 1).put("id", it.id).put("name", it.name).put("version", it.version)
            .put("hideKeywords", JSONArray(it.words)).put("hideAccounts", JSONArray(it.accounts)).put("enabled", it.enabled)) }
        prefs.edit().putString("packs", list.toString()).apply()
    }
    companion object {
        private fun decode(json: JSONObject, importing: Boolean): FeedMod {
            require(json.getInt("formatVersion") == 1) { "不支持此 Mod 格式版本" }
            val id = json.getString("id")
            require(id.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) { "Mod 标识格式不正确" }
            fun values(key: String): List<String> {
                val array = json.optJSONArray(key) ?: return emptyList()
                require(array.length() <= 200) { "每类规则最多 200 条" }
                return List(array.length()) { array.getString(it).trim().also { value -> require(value.length in 1..120) { "规则需为 1 至 120 个字符" } } }.distinct()
            }
            val name = json.getString("name").trim()
            val version = json.getString("version").trim()
            require(name.length in 1..60 && version.length in 1..30) { "名称或版本长度不正确" }
            return FeedMod(id, name, version, values("hideKeywords"), values("hideAccounts"), importing || json.optBoolean("enabled", true))
        }
        fun read(context: Context, uri: Uri): FeedMod {
            val stream = context.contentResolver.openInputStream(uri) ?: error("无法读取文件")
            var manifest: ByteArray? = null
            ZipInputStream(stream.buffered()).use { zip ->
                var total = 0
                var count = 0
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(++count <= 32) { "压缩包文件过多" }
                    require(entry.name == "mod.json" && !entry.isDirectory && manifest == null) { "规则包只允许包含根目录 mod.json" }
                    val bytes = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= 256 * 1024) { "规则包解压内容不得超过 256 KB" }
                        bytes.write(buffer, 0, read)
                    }
                    manifest = bytes.toByteArray()
                }
            }
            return decode(JSONObject(manifest?.toString(Charsets.UTF_8) ?: error("缺少 mod.json")), true)
        }
    }
}
internal val LocalMods = staticCompositionLocalOf<ModStore?> { null }

package cc.ccwu.signalfeed

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

internal const val CODE_BASE = "signalfeed-0.7.0"
internal class CodeMods(private val context: Context) {
    private val prefs = context.getSharedPreferences("code_mods", Context.MODE_PRIVATE)
    private val directory = File(context.filesDir, "code-mods").apply { mkdirs() }
    private val installed = runCatching { context.assets.open("code-mods.json").bufferedReader().use { JSONObject(it.readText()) } }
        .getOrElse { JSONObject().put("baseId", CODE_BASE).put("buildId", "base").put("mods", JSONArray()) }
    val baseId = installed.getString("baseId")
    val active: List<JSONObject> = rows(installed.getJSONArray("mods"))
    var desired by mutableStateOf(if (prefs.getString("installed", "") == installed.getString("buildId")) {
        runCatching { rows(JSONArray(prefs.getString("desired", "[]"))) }.getOrDefault(active)
    } else active)
        private set
    private fun rows(array: JSONArray) = List(array.length()) { array.getJSONObject(it) }
    fun set(list: List<JSONObject>) {
        desired = list
        prefs.edit().putString("installed", installed.getString("buildId")).putString("desired", JSONArray(list).toString()).apply()
    }
    fun add(pack: JSONObject) { set(desired.filterNot { it.getString("id") == pack.getString("id") } + pack) }
    fun read(uri: Uri): JSONObject {
        val temp = File.createTempFile("incoming-", ".zip", directory)
        try {
            context.contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { out ->
                val buffer = ByteArray(8192); var total = 0
                while (true) { val n = input.read(buffer); if (n < 0) break; total += n; require(total <= 16 * 1024 * 1024) { "代码包不得超过 16 MB" }; out.write(buffer, 0, n) }
            } }
            var manifest: JSONObject? = null
            var total = 0; var entries = 0
            val names = mutableSetOf<String>()
            ZipInputStream(temp.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(++entries <= 512 && names.add(entry.name)) { "文件过多或文件名重复" }
                    require(!entry.name.contains('\\') && !entry.name.startsWith('/') && entry.name.split('/').none { it == ".." || it.contains(':') }) { "压缩包路径无效" }
                    val bytes = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while (true) { val n = zip.read(buffer); if (n < 0) break; total += n; require(total <= 32 * 1024 * 1024) { "解压内容超过 32 MB" }; if (entry.name == "mod.json") { require(bytes.size() + n <= 256 * 1024); bytes.write(buffer, 0, n) } }
                    if (entry.name == "mod.json") manifest = JSONObject(bytes.toString("UTF-8"))
                }
            }
            val pack = manifest ?: error("缺少 mod.json")
            require(pack.getInt("formatVersion") == 2 && pack.getString("type") == "source-patch") { "请选择格式 2 的 source-patch 代码包" }
            require(pack.getString("baseId") == baseId) { "代码包不适用于当前基线 $baseId" }
            require(pack.getString("id").matches(Regex("[a-zA-Z0-9_-]{1,64}"))) { "包标识无效" }
            require(pack.getString("name").length in 1..60 && pack.getString("version").length in 1..30)
            require(pack.getJSONArray("files").length() in 1..256) { "缺少补丁文件清单" }
            val digest = MessageDigest.getInstance("SHA-256").digest(temp.readBytes()).joinToString("") { "%02x".format(it) }
            val file = File(directory, "$digest.zip")
            temp.copyTo(file, overwrite = true)
            return pack.put("sha256", digest)
        } finally { temp.delete() }
    }
    fun export(uri: Uri) {
        context.contentResolver.openOutputStream(uri, "wt")!!.use { stream -> ZipOutputStream(stream).use { zip ->
            zip.putNextEntry(ZipEntry("request.json"))
            zip.write(JSONObject().put("formatVersion", 1).put("baseId", baseId).put("mods", JSONArray(desired)).toString(2).toByteArray())
            zip.closeEntry()
            desired.forEach { pack ->
                val name = pack.getString("sha256") + ".zip"
                require(name.matches(Regex("[a-f0-9]{64}\\.zip")))
                zip.putNextEntry(ZipEntry("packages/$name"))
                val local = File(directory, name)
                (if (local.exists()) local.inputStream() else context.assets.open("code-mod-packages/$name")).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        } }
    }
}

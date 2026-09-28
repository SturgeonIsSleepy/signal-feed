package cc.ccwu.signalfeed

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.view.View
import cc.ccwu.signalfeed.modapi.SignalMod
import dalvik.system.DexClassLoader
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

internal class ModuleStore(val context: Context) {
    val root = File(context.filesDir, "modules").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("runtime-mods", Context.MODE_PRIVATE)
    fun packages(): List<JSONObject> = root.listFiles().orEmpty().filter { File(it, "installed").isFile && File(it, "mod.json").isFile }.mapNotNull { dir ->
        runCatching { JSONObject(File(dir, "mod.json").readText()).put("directory", dir.name).put("enabled", prefs.getBoolean(dir.name, true)) }.getOrNull()
    }
    fun enable(directory: String, value: Boolean) { prefs.edit().putBoolean(directory, value).commit() }
    fun remove(directory: String) { enable(directory, false); child(directory).deleteRecursively() }
    private fun child(name: String): File { require(name.matches(Regex("[a-f0-9-]{36}"))); return File(root, name) }
    fun stage(uri: Uri): File {
        val dir = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            var size = 0; var count = 0; val seen = mutableSetOf<String>()
            ZipInputStream(context.contentResolver.openInputStream(uri)!!.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    require(++count <= 512 && seen.add(name)) { "重复文件或文件过多" }
                    require(name == "mod.json" || name == "classes.dex" || name.startsWith("assets/") || name.startsWith("lib/")) { "只支持 mod.json、classes.dex、assets 和 lib" }
                    require(!name.contains('\\') && name.split('/').none { it == ".." || it.contains(':') }) { "路径无效" }
                    val target = File(dir, name)
                    if (entry.isDirectory) { target.mkdirs(); continue }
                    target.parentFile!!.mkdirs()
                    target.outputStream().use { out ->
                        if (name == "classes.dex") check(target.setReadOnly())
                        val buffer = ByteArray(8192)
                        while (true) { val n = zip.read(buffer); if (n < 0) break; size += n; require(size <= 32 * 1024 * 1024) { "模块超过 32 MB" }; out.write(buffer, 0, n) }
                    }
                }
            }
            val manifest = File(dir, "mod.json"); require(manifest.length() <= 65536)
            val json = JSONObject(manifest.readText())
            require(json.getInt("formatVersion") == 3 && json.getInt("apiVersion") == 1) { "需要格式 3、API 1 手机模块" }
            require(json.getString("id").matches(Regex("[a-zA-Z0-9_-]{1,64}")))
            require(json.getString("entryClass").matches(Regex("[a-zA-Z_][a-zA-Z0-9_.$]+")))
            require(json.getString("name").length in 1..80 && json.getString("version").length in 1..30)
            require(File(dir, "classes.dex").isFile) { "缺少 classes.dex" }
            json.put("dexSha256", sha(File(dir, "classes.dex")))
            File(dir, "pending.json").writeText(json.toString())
            manifest.delete() // A staged package is never loaded before confirmation.
            return dir
        } catch (error: Throwable) { dir.deleteRecursively(); throw error }
    }
    fun accept(dir: File) {
        val json = JSONObject(File(dir, "pending.json").readText())
        packages().filter { it.getString("id") == json.getString("id") }.forEach { remove(it.getString("directory")) }
        check(File(dir, "pending.json").renameTo(File(dir, "mod.json"))) { "安装未完成" }
        File(dir, "installed").writeText("1")
        enable(dir.name, true)
    }
    companion object { fun sha(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) } }
}
internal object RuntimeModules {
    private var store: ModuleStore? = null
    private var loaded = listOf<Pair<String, SignalMod>>()
    var notice: String? = null
        private set
    fun attach(activity: Activity, safe: Boolean) {
        detach()
        val current = ModuleStore(activity.applicationContext); store = current
        val prefs = activity.getSharedPreferences("runtime-mods", Context.MODE_PRIVATE)
        // A prior module exception/native crash leaves this marker behind.
        val unfinished = prefs.getString("executing", null)
        if (unfinished != null) { current.enable(unfinished, false); notice = "已停用上次异常的模块" }
        prefs.edit().remove("executing").commit()
        if (safe || !activity.getSharedPreferences("reading", Context.MODE_PRIVATE).getBoolean("runtime_modules", true)) { notice = "恢复模式：本次未加载模块"; return }
        loaded = current.packages().filter { it.getBoolean("enabled") }.sortedBy { it.getString("id") }.mapNotNull { pack ->
            val id = pack.getString("directory")
            guarded(id, null as Pair<String, SignalMod>?) {
                val dir = File(current.root, id); val dex = File(dir, "classes.dex")
                check(ModuleStore.sha(dex) == pack.getString("dexSha256"))
                check(dex.setReadOnly())
                val loader = DexClassLoader(dex.path, activity.codeCacheDir.path, File(dir, "lib/${android.os.Build.SUPPORTED_ABIS.first()}").path, activity.classLoader)
                val mod = loader.loadClass(pack.getString("entryClass")).getDeclaredConstructor().newInstance() as SignalMod
                mod.onAttach(activity, dir)
                id to mod
            }
        }
    }
    @Synchronized private fun <T> guarded(id: String, fallback: T, block: () -> T): T {
        val prefs = store?.context?.getSharedPreferences("runtime-mods", Context.MODE_PRIVATE) ?: return fallback
        if (!prefs.getBoolean(id, true)) return fallback
        prefs.edit().putString("executing", id).commit()
        return try { block() } catch (error: Throwable) { store?.enable(id, false); notice = "模块已停用：${error.javaClass.simpleName}"; fallback }
        finally { prefs.edit().remove("executing").commit() }
    }
    fun transform(json: String): String = loaded.fold(json) { value, (id, mod) -> guarded(id, value) { mod.transformFeed(value).also { org.json.JSONArray(it) } } }
    fun url(url: String): String = loaded.fold(url) { value, (id, mod) -> guarded(id, value) { mod.rewriteUrl(value).also { require(it.startsWith("https://")) } } }
    fun home(activity: Activity): View? = loaded.firstNotNullOfOrNull { (id, mod) -> guarded(id, null as View?) { mod.createHome(activity) } }
    fun detach() { loaded.forEach { (id, mod) -> guarded(id, Unit) { mod.onDetach() } }; loaded = emptyList() }
}

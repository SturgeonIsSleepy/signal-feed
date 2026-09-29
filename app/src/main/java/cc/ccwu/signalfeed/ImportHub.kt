package cc.ccwu.signalfeed

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.util.zip.ZipInputStream

private data class IdentifiedFile(
    val kind: String, val type: String, val name: String, val detail: String,
    val json: JSONObject? = null, val subscriptions: List<Subscription>? = null,
    val ruleMod: FeedMod? = null, val stagedModule: File? = null
) {
    fun discard() { stagedModule?.deleteRecursively() }
}

private fun identifyFile(context: Context, uri: Uri): IdentifiedFile {
    val header = context.contentResolver.openInputStream(uri)!!.use { stream ->
        ByteArray(4).also { DataInputStream(stream).readFully(it) }
    }
    if (header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()) {
        var manifest: JSONObject? = null
        ZipInputStream(context.contentResolver.openInputStream(uri)!!.buffered()).use { zip ->
            var count = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++count <= 512) { "ZIP 文件过多" }
                if (entry.name == "mod.json" && !entry.isDirectory) {
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        require(output.size() + n <= 65536) { "Mod 清单过大" }
                        output.write(buffer, 0, n)
                    }
                    manifest = JSONObject(output.toString(Charsets.UTF_8.name()))
                    break
                }
            }
        }
        val json = manifest ?: error("ZIP 中没有根目录 mod.json")
        return when (json.optInt("formatVersion")) {
            1 -> ModStore.read(context, uri).let { IdentifiedFile("rule-mod", "ZIP 规则 Mod", it.name,
                "版本 ${it.version} · 屏蔽关键词 ${it.words.size} 条、账号 ${it.accounts.size} 个", ruleMod = it) }
            3 -> {
                val staged = ModuleStore(context).stage(uri)
                try {
                    val checked = JSONObject(File(staged, "pending.json").readText())
                    IdentifiedFile("runtime-mod", "手机运行时 Mod", checked.getString("name"),
                        "版本 ${checked.getString("version")} · 入口 ${checked.getString("entryClass")}。安装后重启应用生效", stagedModule = staged)
                } catch (error: Throwable) { staged.deleteRecursively(); throw error }
            }
            else -> error("此 ZIP 类型不能在手机中直接安装。请使用格式 1 或格式 3 的 Mod")
        }
    }
    val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytesLimited(1024 * 1024).toString(Charsets.UTF_8) }
    if (text.trimStart('\uFEFF', ' ', '\r', '\n').startsWith('<')) {
        val rows = Subscriptions.get(context).read(uri)
        require(rows.isNotEmpty()) { "OPML 中没有 RSS 订阅" }
        return IdentifiedFile("rss", "RSS / OPML 信息源", "${rows.size} 个订阅", rows.take(12).joinToString("、") { it.name }, subscriptions = rows)
    }
    val json = JSONObject(text.trimStart('\uFEFF'))
    if (json.has("subscriptions")) {
        val rows = Subscriptions.get(context).read(uri)
        require(rows.isNotEmpty()) { "文件中没有 RSS 订阅" }
        return IdentifiedFile("rss", "RSS 信息源", "${rows.size} 个订阅", rows.take(12).joinToString("、") { it.name }, subscriptions = rows)
    }
    require(json.optInt("formatVersion") == 1) { "配置格式版本应为 1" }
    val kind = when {
        json.has("apiBaseUrl") -> "source"
        json.has("rules") -> "filter"
        json.has("mode") -> "translator"
        json.has("kind") -> "data"
        listOf("primary", "background", "surface", "text").any(json::has) -> "theme"
        listOf("minimumImportance", "accounts", "topics", "keywords", "bodyRegex").any(json::has) -> "breaking"
        else -> error("无法识别文件类型，请检查配置字段")
    }
    val type = mapOf("source" to "聚合服务信息源", "filter" to "筛选规则", "translator" to "翻译器",
        "data" to "数据栏目", "theme" to "主题美化", "breaking" to "Breaking 筛选规则").getValue(kind)
    val detail = if (kind == "source") "${json.optJSONArray("accounts")?.length() ?: 0} 个账号 · ${json.optString("apiBaseUrl")}" else json.optString("description")
    return IdentifiedFile(kind, type, json.optString("name", type), detail, json = json)
}

@Composable internal fun ImportHub(model: FeedViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mods = LocalMods.current
    var pending by remember { mutableStateOf<IdentifiedFile?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) { identifyFile(context, uri) } }
                .onSuccess { pending = it; message = "" }
                .onFailure { message = it.message ?: "无法识别文件" }
            busy = false
        }
    }
    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy) {
            Text(if (busy) "正在识别文件…" else "导入文件")
        }
        Text("自动识别订阅、规则、翻译器、数据、主题和 ZIP Mod，确认后安装。", style = MaterialTheme.typography.bodySmall)
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
    }
    pending?.let { file -> AlertDialog(onDismissRequest = { file.discard(); pending = null },
        title = { Text("识别为：${file.type}") },
        text = { Text("${file.name}\n${file.detail}\n\n确认安装到对应栏目？") },
        confirmButton = { TextButton(onClick = {
            runCatching {
                when (file.kind) {
                    "rss" -> { Subscriptions.get(context).merge(file.subscriptions!!); Notifications.configure(context); model.refresh(); "已添加 ${file.name}" }
                    "rule-mod" -> { requireNotNull(mods).save(file.ruleMod!!); "已安装 ${file.name}" }
                    "runtime-mod" -> { ModuleStore(context).accept(file.stagedModule!!); "已安装 ${file.name}，重启应用后生效" }
                    else -> ShellPacks.get(context).import(file.kind, file.json!!).also {
                        if (file.kind == "source") { Notifications.configure(context); model.refresh() }
                        if (file.kind == "breaking") {
                            context.getSharedPreferences("notifications", Context.MODE_PRIVATE).edit().putBoolean("initialized", false).apply()
                            Notifications.configure(context)
                            if (Build.VERSION.SDK_INT >= 33 && context is Activity &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                                context.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                        }
                    }
                }
            }.onSuccess { message = it }.onFailure { message = it.message ?: "安装失败"; file.discard() }
            pending = null
        }) { Text("确认安装") } },
        dismissButton = { TextButton(onClick = { file.discard(); pending = null }) { Text("取消") } }) }
}

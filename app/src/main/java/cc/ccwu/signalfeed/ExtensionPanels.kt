package cc.ccwu.signalfeed

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

@Composable internal fun RuntimeModulePanel() {
    val context = LocalContext.current
    val store = remember { ModuleStore(context) }
    var revision by remember { mutableIntStateOf(0) }
    val packages = remember(revision) { store.packages() }
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) { store.stage(uri) } }.onSuccess { pending = it }.onFailure { message = it.message.orEmpty() }
            busy = false
        }
    }
    Column(Modifier.padding(18.dp)) {
        Text("手机模块", fontWeight = FontWeight.Bold)
        Text("导入预编译 DEX 的 ZIP 模块，可扩展页面、信息流、网络和应用逻辑。安装、停用、卸载后退出并重新打开生效，无需电脑重新打包 App。")
        RuntimeModules.notice?.let { Text(it) }
        Button(onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }, enabled = !busy) { Text("安装手机模块 ZIP") }
        packages.forEach { pack ->
            Row {
                Column(Modifier.weight(1f)) { Text(pack.getString("name")); Text("版本 ${pack.getString("version")}") }
                Switch(pack.getBoolean("enabled"), { store.enable(pack.getString("directory"), it); revision++; message = "已保存，退出后重新打开生效" })
            }
            TextButton(onClick = { store.remove(pack.getString("directory")); revision++; message = "模块文件已移除，退出后清除内存中的模块代码" }) { Text("卸载模块") }
        }
        if (packages.isEmpty()) Text("尚未安装手机模块")
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        TextButton(onClick = { (context as? Activity)?.finishAffinity(); android.os.Process.killProcess(android.os.Process.myPid()) }) { Text("退出应用，重新打开使变更生效") }
        Text("无法正常启动时，使用桌面的 SignalFeed 恢复入口。旧源码补丁仍需电脑构建，手机模块不能替换系统权限声明。", style = MaterialTheme.typography.bodySmall)
    }
    pending?.let { dir ->
        val pack = remember(dir) { JSONObject(File(dir, "pending.json").readText()) }
        AlertDialog(onDismissRequest = { dir.deleteRecursively(); pending = null }, title = { Text("安装 ${pack.getString("name")}？") },
            text = { Text("模块能读取和修改应用数据、访问网络并改变界面。仅安装可信作者提供的模块。版本 ${pack.getString("version")}，入口 ${pack.getString("entryClass")}") },
            confirmButton = { TextButton(onClick = { runCatching { store.accept(dir) }.onSuccess { revision++; message = "已安装，退出后重新打开生效" }.onFailure { message = it.message.orEmpty() }; pending = null }) { Text("安装") } },
            dismissButton = { TextButton(onClick = { dir.deleteRecursively(); pending = null }) { Text("取消") } })
    }
}

@Composable internal fun SubscriptionPanel(model: FeedViewModel) {
    val context = LocalContext.current
    val store = remember { Subscriptions.get(context) }
    val subscriptions by store.items.collectAsState()
    val accounts by model.accounts.collectAsState()
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<List<Subscription>?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) { store.read(uri) } }.onSuccess { pending = it }.onFailure { message = it.message.orEmpty() }
            busy = false
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(store.encode(subscriptions).toByteArray()) } } }.onSuccess { message = "已导出订阅" }.onFailure { message = it.message.orEmpty() }
        }
    }
    LazyColumn {
        item { Column(Modifier.padding(18.dp)) {
            Text("信息源订阅", fontWeight = FontWeight.Bold)
            Text("导入 JSON 或 OPML 文件添加 RSS / Atom 信息源。重复地址自动合并，停用或删除后隐藏缓存内容。内置账号仍可在账号与关注中管理。")
            Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy) { Text("导入订阅文件") }
            Row { TextButton(onClick = { exporter.launch("signalfeed-subscriptions.json") }) { Text("导出") }; TextButton(onClick = { model.refresh() }) { Text("刷新订阅") } }
            Text(message)
            if (subscriptions.isEmpty()) Text("尚未添加文件订阅")
        } }
        item { Text("内置订阅", Modifier.padding(18.dp), fontWeight = FontWeight.Bold) }
        items(accounts.filterNot { it.id.startsWith("sub:") }, key = { "builtin:" + it.id }) { account ->
            Row(Modifier.padding(horizontal = 18.dp)) { Text(account.name, Modifier.weight(1f)); Switch(!account.muted, { model.mute(account, !it) }) }
        }
        item { Text("文件订阅", Modifier.padding(18.dp), fontWeight = FontWeight.Bold) }
        items(subscriptions, key = { it.id }) { sub -> Column(Modifier.padding(18.dp)) {
            Row { Text(sub.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Switch(sub.enabled, { on -> store.save(subscriptions.map { if (it.id == sub.id) it.copy(enabled = on) else it }) }) }
            Text("${sub.topic}\n${sub.url}", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { store.save(subscriptions.filterNot { it.id == sub.id }) }) { Text("删除订阅") }
            HorizontalDivider()
        } }
    }
    pending?.let { list -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("添加 ${list.size} 个订阅？") }, text = { Text(list.take(15).joinToString("\n") { "${it.name}：${it.topic}" } + if (list.size > 15) "\n…" else "") }, confirmButton = { TextButton(onClick = { runCatching { store.merge(list) }.onSuccess { message = "已添加，正在刷新"; model.refresh() }.onFailure { message = it.message.orEmpty() }; pending = null }) { Text("添加") } }, dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } }) }
}

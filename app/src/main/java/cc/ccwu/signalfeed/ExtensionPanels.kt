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

@Composable internal fun RuntimeModulePanel() {
    val context = LocalContext.current
    val store = remember { ModuleStore(context) }
    var revision by remember { mutableIntStateOf(0) }
    val packages = remember(revision) { store.packages() }
    var message by remember { mutableStateOf("") }
    Column(Modifier.padding(18.dp)) {
        UiText("手机模块", fontWeight = FontWeight.Bold)
        UiText("导入预编译 DEX 的 ZIP 模块，可扩展页面、信息流、网络和应用逻辑。安装、停用、卸载后退出并重新打开生效，无需电脑重新打包 App。")
        RuntimeModules.notice?.let { UiText(it) }
        UiText("安装入口位于设置首页的「导入文件」。", style = MaterialTheme.typography.bodySmall)
        packages.forEach { pack ->
            Row {
                Column(Modifier.weight(1f)) { UiText(pack.getString("name")); UiText("版本 ${pack.getString("version")}") }
                Switch(pack.getBoolean("enabled"), { store.enable(pack.getString("directory"), it); revision++; message = "已保存，退出后重新打开生效" })
            }
            TextButton(onClick = { store.remove(pack.getString("directory")); revision++; message = "模块文件已移除，退出后清除内存中的模块代码" }) { UiText("卸载模块") }
        }
        if (packages.isEmpty()) UiText("尚未安装手机模块")
        if (message.isNotBlank()) UiText(message, color = MaterialTheme.colorScheme.primary)
        TextButton(onClick = { (context as? Activity)?.finishAffinity(); android.os.Process.killProcess(android.os.Process.myPid()) }) { UiText("退出应用，重新打开使变更生效") }
        UiText("无法正常启动时，使用桌面的 SignalFeed 恢复入口。旧源码补丁仍需电脑构建，手机模块不能替换系统权限声明。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable internal fun SubscriptionPanel(model: FeedViewModel) {
    val context = LocalContext.current
    val store = remember { Subscriptions.get(context) }
    val subscriptions by store.items.collectAsState()
    val backend by ShellPacks.get(context).sourceApi.collectAsState()
    val selectedAccounts by ShellPacks.get(context).sourceAccounts.collectAsState()
    val accounts by model.accounts.collectAsState()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(store.encode(subscriptions).toByteArray()) } } }.onSuccess { message = "已导出订阅" }.onFailure { message = it.message.orEmpty() }
        }
    }
    LazyColumn {
        item { Column(Modifier.padding(18.dp)) {
            UiText("信息源订阅", fontWeight = FontWeight.Bold)
            UiText("RSS / Atom 订阅可通过设置首页导入 JSON 或 OPML。重复地址自动合并，停用或删除后隐藏缓存内容。")
            Row { TextButton(onClick = { exporter.launch("signalfeed-subscriptions.json") }) { UiText("导出") }; TextButton(onClick = { model.refresh() }) { UiText("刷新订阅") } }
            UiText(message)
            if (subscriptions.isEmpty()) UiText("尚未添加文件订阅")
        } }
        item { if (backend != null) UiText("聚合服务账号", Modifier.padding(18.dp), fontWeight = FontWeight.Bold) }
        items(accounts.filter { backend != null && it.id in selectedAccounts }, key = { "backend:" + it.id }) { account ->
            Row(Modifier.padding(horizontal = 18.dp)) { UiText(account.name, Modifier.weight(1f)); Switch(!account.muted, { model.mute(account, !it) }) }
        }
        item { UiText("文件订阅", Modifier.padding(18.dp), fontWeight = FontWeight.Bold) }
        items(subscriptions, key = { it.id }) { sub -> Column(Modifier.padding(18.dp)) {
            Row { UiText(sub.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Switch(sub.enabled, { on -> store.save(subscriptions.map { if (it.id == sub.id) it.copy(enabled = on) else it }); Notifications.configure(context) }) }
            UiText("${sub.topic}\n${sub.url}", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { store.save(subscriptions.filterNot { it.id == sub.id }); Notifications.configure(context) }) { UiText("删除订阅") }
            HorizontalDivider()
        } }
    }
}

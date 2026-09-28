package cc.ccwu.signalfeed

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject

@Composable internal fun CodeModPanel() {
    val context = LocalContext.current
    val store = remember { CodeMods(context) }
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<JSONObject?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) { store.read(uri) } }.onSuccess { pending = it }.onFailure { message = it.message.orEmpty() }
            busy = false
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) { store.export(uri) } }.onSuccess { message = "已导出，请交给电脑构建工具生成 APK 并覆盖安装" }.onFailure { message = "导出失败：${it.message}" }
            busy = false
        }
    }
    Column(Modifier.padding(18.dp)) {
        Text("代码与资源 Mod", fontWeight = FontWeight.Bold)
        Text("可修改应用源码和资源。手机选择补丁，电脑从干净基线重新构建，再覆盖安装。应用数据保留。")
        Text("当前基线：${store.baseId}")
        Text("当前安装：${store.active.joinToString("、") { it.getString("name") }.ifEmpty { "原版" }}")
        Button(onClick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }, enabled = !busy) { Text("导入代码 ZIP") }
        Text("下次构建保留的 Mod", fontWeight = FontWeight.SemiBold)
        if (store.desired.isEmpty()) Text("无，将生成干净原版")
        store.desired.forEach { pack ->
            Row {
                Text("${pack.getString("name")}  ${pack.getString("version")}", Modifier.weight(1f))
                TextButton(onClick = { store.set(store.desired.filterNot { it.getString("id") == pack.getString("id") }); message = "已排除，重新构建并覆盖安装后才会移除代码" }, enabled = !busy) { Text("移除") }
            }
        }
        Row {
            TextButton(onClick = { store.set(emptyList()); message = "已选择恢复原版，请导出重建请求" }, enabled = !busy) { Text("恢复原版代码") }
            TextButton(onClick = { store.set(store.active); message = "已取消待应用变更" }, enabled = !busy) { Text("取消变更") }
        }
        Button(onClick = { exporter.launch("signalfeed-rebuild.zip") }, enabled = !busy) { Text("导出重建请求") }
        Text("安装或卸载均需重新构建 APK。未覆盖安装前，当前代码保持不变。Mod 修改的数据不会回滚。")
        if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.primary)
    }
    pending?.let { pack -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("加入 ${pack.getString("name")}？") },
        text = { Text("此代码包可改变应用行为，拥有应用本身的权限。它将修改 ${pack.getJSONArray("files").length()} 个文件。加入后仍需电脑构建和覆盖安装。") },
        confirmButton = { TextButton(onClick = { store.add(pack); pending = null; message = "已加入下次构建" }) { Text("加入") } },
        dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } }) }
}

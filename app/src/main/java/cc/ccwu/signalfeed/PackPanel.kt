package cc.ccwu.signalfeed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable internal fun PackPanel(kind: String, onApplied: () -> Unit = {}) {
    val store = ShellPacks.get(LocalContext.current)
    val packs = when (kind) {
        "source" -> store.sourcePacks.collectAsState().value
        "filter" -> store.filterPacks.collectAsState().value
        "translator" -> store.translatorPacks.collectAsState().value
        "data" -> store.dataPacks.collectAsState().value
        "breaking" -> store.breakingPacks.collectAsState().value
        else -> listOf(store.theme.collectAsState().value).filter { it.length() > 0 }
    }
    val title = when (kind) { "source" -> "聚合服务"; "filter" -> "筛选规则"; "translator" -> "翻译器";
        "data" -> "数据栏目"; "breaking" -> "Breaking 规则"; else -> "主题美化" }
    LazyColumn {
        item { Column(Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text("已安装 ${packs.size} 项。请在设置首页使用统一导入入口。", style = MaterialTheme.typography.bodySmall)
            if (packs.isEmpty()) Text("尚未导入", Modifier.padding(vertical = 20.dp))
        } }
        items(packs, key = { if (kind == "theme") "theme" else it.optString("id") }) { item ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(item.optString("name", title), fontWeight = FontWeight.SemiBold)
                    Text(description(kind, item), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { store.remove(kind, item.optString("id")); onApplied() }) { Text("移除") }
            }
            HorizontalDivider()
        }
        item { Text("移除后立即停用，原有帖子与缓存保留。", Modifier.padding(18.dp), style = MaterialTheme.typography.bodySmall) }
    }
}

private fun description(kind: String, item: JSONObject): String = if (kind == "source")
    "${item.optJSONArray("accounts")?.length() ?: 0} 个账号 · ${item.optString("apiBaseUrl")}" else item.optString("description")

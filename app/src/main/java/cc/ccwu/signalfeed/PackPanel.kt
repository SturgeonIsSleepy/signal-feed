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
            UiText(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            UiText("已安装 ${packs.size} 项。请在设置首页使用统一导入入口。", style = MaterialTheme.typography.bodySmall)
            if (packs.isEmpty()) UiText("尚未导入", Modifier.padding(vertical = 20.dp))
        } }
        items(packs, key = { if (kind == "theme") "theme" else it.optString("id") }) { item ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                Column(Modifier.weight(1f)) {
                    UiText(item.optString("name", title), fontWeight = FontWeight.SemiBold)
                    UiText(description(kind, item), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { store.remove(kind, item.optString("id")); onApplied() }) { UiText("移除") }
            }
            HorizontalDivider()
        }
        item { UiText("移除后立即停用，原有帖子与缓存保留。", Modifier.padding(18.dp), style = MaterialTheme.typography.bodySmall) }
    }
}

private fun description(kind: String, item: JSONObject): String = if (kind == "source")
    "${item.optJSONArray("accounts")?.length() ?: 0} 个账号 · ${item.optString("apiBaseUrl")}" else item.optString("description")

@Composable internal fun ContentFilterPanel(onApplied: () -> Unit) {
    val store = ShellPacks.get(LocalContext.current)
    val filters by store.filterPacks.collectAsState()
    val breaking by store.breakingPacks.collectAsState()
    val ruleStore = LocalMods.current
    val rulePacks = if (LocalFeatures.current.enabled(Feature.CODE_MOD_WINDOW)) ruleStore?.packs.orEmpty() else emptyList()
    val packs = filters.map { "filter" to it } + breaking.map { "breaking" to it }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UiText("内容筛选包同时管理屏蔽与 Breaking，可导入多份", style = MaterialTheme.typography.bodyMedium)
            UiText("没有配置时显示原始信息流，通知保持关闭", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (packs.isEmpty() && rulePacks.isEmpty()) UiText("尚未导入", Modifier.padding(vertical = 16.dp))
        } }
        items(packs, key = { it.first + ":" + it.second.getString("id") }) { (kind, pack) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    UiText(pack.getString("name"), fontWeight = FontWeight.SemiBold)
                    val count = pack.optJSONArray("rules")?.length() ?: 0
                    val alerts = if (kind == "breaking") 1 else pack.optJSONArray("breaking")?.length() ?: 0
                    UiText("$count 条屏蔽或保留规则，$alerts 条 Breaking 规则", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    pack.optString("description").takeIf { it.isNotBlank() }?.let { UiText(it, style = MaterialTheme.typography.bodySmall) }
                }
                TextButton(onClick = { store.remove(kind, pack.getString("id")); onApplied() }) { UiText("移除") }
            }
            HorizontalDivider()
        }
        items(rulePacks, key = { "rule-zip:" + it.id }) { pack ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    UiText(pack.name, fontWeight = FontWeight.SemiBold)
                    UiText("版本 ${pack.version} · 屏蔽关键词 ${pack.words.size} 条、账号 ${pack.accounts.size} 个",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Switch(pack.enabled, { ruleStore?.toggle(pack.id, it); onApplied() })
                }
                TextButton(onClick = { ruleStore?.remove(pack.id); onApplied() }) { UiText("移除") }
            }
            HorizontalDivider()
        }
    }
}

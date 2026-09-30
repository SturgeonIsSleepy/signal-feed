package cc.ccwu.signalfeed

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.ccwu.signalfeed.data.AiSnapshot
import cc.ccwu.signalfeed.data.F1Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

@Composable internal fun ImportedDataPage(f1: F1Snapshot?, ai: AiSnapshot?, f1Message: String?, aiMessage: String?, now: Long,
    onF1Refresh: () -> Unit, onAiRefresh: () -> Unit) {
    val context = LocalContext.current
    val panels by ShellPacks.get(context).dataPacks.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    val panel = panels.firstOrNull { it.optString("id") == selected } ?: panels.firstOrNull()
    Column {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            UiText("数据栏目", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (panel != null) UiText("${panels.size} 个栏目", style = MaterialTheme.typography.bodySmall)
        }
        if (panels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    UiText("还没有数据栏目", fontWeight = FontWeight.Bold)
                    UiText("在设置中导入订阅包，可包含赛历、榜单或自定义内容", style = MaterialTheme.typography.bodySmall)
                }
            }
            return@Column
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            panels.forEach { item -> FilterChip(selected = panel?.optString("id") == item.optString("id"), onClick = { selected = item.optString("id") }, label = { UiText(rememberContentTranslation(item.optString("name")).body) }) }
        }
        when (panel?.optString("kind")) {
            "f1-calendar" -> DataPage(f1, ai, f1Message, aiMessage, now, onF1Refresh, onAiRefresh, tabs = listOf(0))
            "f1-points" -> DataPage(f1, ai, f1Message, aiMessage, now, onF1Refresh, onAiRefresh, tabs = listOf(1))
            "ai-models" -> DataPage(f1, ai, f1Message, aiMessage, now, onF1Refresh, onAiRefresh, tabs = listOf(2))
            else -> CustomDataPanel(panel ?: return@Column)
        }
    }
}

@Composable private fun CustomDataPanel(config: JSONObject) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember(config.toString()) { mutableStateOf(config.optJSONArray("rows") ?: JSONArray()) }
    var error by remember(config.toString()) { mutableStateOf<String?>(null) }
    var updated by remember(config.toString()) { mutableStateOf<Long?>(null) }
    val url = config.optString("url")
    fun refresh() { if (url.isNotEmpty()) scope.launch {
        runCatching { withContext(Dispatchers.IO) {
            val req = Request.Builder().url(RuntimeModules.url(url)).build()
            OkHttpClient().newCall(req).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                val text = response.body!!.byteStream().use { it.readBytesLimited(2 * 1024 * 1024).toString(Charsets.UTF_8) }
                val root = JSONObject(text); root.optJSONArray(config.optString("rowsKey", "rows")) ?: error("未找到数据数组")
            }
        } }.onSuccess { rows = it; error = null; updated = System.currentTimeMillis() }.onFailure { error = "刷新失败，显示已导入内容：${it.message}" }
    } }
    LaunchedEffect(config.toString()) { refresh() }
    val list = remember(rows.toString(), config.toString()) { (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i) } }
    val maximum = list.maxOfOrNull { it.optDouble(config.optString("valueKey", "value"), 0.0).takeIf(Double::isFinite) ?: 0.0 }?.coerceAtLeast(0.0) ?: 0.0
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(rememberContentTranslation(config.optString("description")).body, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                if (url.isNotEmpty()) TextButton(onClick = ::refresh) { UiText("刷新") }
            }
            error?.let { UiText(it, Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.error) }
            updated?.let { UiText("更新：${fullDateTime(it)}", Modifier.padding(horizontal = 18.dp), style = MaterialTheme.typography.bodySmall) }
        }
        items(list, key = { list.indexOf(it) }) { row ->
            val title = row.optString(config.optString("titleKey", "title"))
            val detail = row.optString(config.optString("detailKey", "detail"))
            val content = rememberContentTranslation(if (detail.isBlank()) title else "$title\n\n$detail").body
            val value = row.optDouble(config.optString("valueKey", "value"), 0.0).takeIf(Double::isFinite) ?: 0.0
            val link = row.optString(config.optString("linkKey", "url"))
            Column(Modifier.fillMaxWidth().clickable(enabled = link.startsWith("https://")) { openOriginal(context, link) }.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(content.substringBefore("\n\n"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 25.sp)
                if (config.optString("kind") == "calendar") {
                    val time = row.opt(config.optString("timeKey", "startAt"))
                    val millis = when (time) { is Number -> time.toLong(); is String -> runCatching { Instant.parse(time).toEpochMilli() }.getOrNull(); else -> null }
                    if (millis != null) UiText(fullDateTime(millis), color = MaterialTheme.colorScheme.primary)
                }
                if (config.optString("kind") == "leaderboard") {
                    UiText(row.optString(config.optString("valueKey", "value")))
                    LinearProgressIndicator(progress = { if (maximum > 0) (value / maximum).coerceIn(0.0, 1.0).toFloat() else 0f }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                }
                if (detail.isNotEmpty()) Text(content.substringAfter("\n\n", detail), style = MaterialTheme.typography.bodyMedium, lineHeight = 24.sp)
                if (link.startsWith("https://")) UiText("查看来源 ↗", color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider()
        }
    }
}

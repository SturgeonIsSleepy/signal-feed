package cc.ccwu.signalfeed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.ccwu.signalfeed.data.AccountEntity
import cc.ccwu.signalfeed.data.TopicEntity

@Composable internal fun OrganizedSettings(accounts: List<AccountEntity>, topics: List<TopicEntity>, model: FeedViewModel, directX: Boolean, onDirectX: (Boolean) -> Unit, features: FeatureSettings, onFeature: (Feature, Boolean) -> Unit) {
    var section by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val backend by ShellPacks.get(context).sourceApi.collectAsState()
    val selectedAccounts by ShellPacks.get(context).sourceAccounts.collectAsState()
    BackHandler(section.isNotEmpty()) { section = "" }
    Column {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (section.isNotEmpty()) TextButton(onClick = { section = "" }) { Text("‹ 返回") }
            Text(section.ifEmpty { "设置" }, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider()
        if (section == "信息源订阅") { SourceSetupPanel(model); return@Column }
        if (section in listOf("筛选规则", "翻译器", "数据栏目", "Breaking 规则", "主题美化")) {
            val kind = when(section) { "筛选规则" -> "filter"; "翻译器" -> "translator"; "数据栏目" -> "data"; "Breaking 规则" -> "breaking"; else -> "theme" }
            PackPanel(kind) { if (kind == "breaking") Notifications.configure(context) }
            return@Column
        }
        if (section == "Mod 管理") { ModManager(); return@Column }
        if (section.isEmpty()) ImportHub(model)
        LazyColumn {
            if (section.isEmpty()) {
                val sections = listOf("信息源订阅" to "管理 RSS、OPML 和聚合服务", "筛选规则" to "查看和移除过滤规则", "翻译器" to "管理已安装的翻译器", "Breaking 规则" to "管理通知筛选条件", "数据栏目" to "管理赛历、榜单和自定义内容", "主题美化" to "管理配色主题", "Mod 管理" to "管理手机模块与 ZIP 规则包", "账号与关注" to "订阅后管理关注和屏蔽", "主题偏好" to "调整已订阅主题权重", "阅读与打开方式" to "原文和浏览器设置", "功能与回退" to "恢复之前的交互行为")
                items(sections) { (title, subtitle) ->
                    ListItem(headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text(subtitle) }, trailingContent = { Text("›") }, modifier = Modifier.clickable { section = title })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
                item { Text("SignalFeed 0.9\n所有文件从上方入口导入，可在对应栏目移除", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
            } else if (section == "账号与关注") {
                items(accounts.filter { it.id.startsWith("sub:") || (backend != null && it.id in selectedAccounts) }, key = { it.id }) { account ->
                    Column(Modifier.padding(16.dp)) {
                        Text(account.name, fontWeight = FontWeight.Bold)
                        Text(account.handle, fontSize = 12.sp)
                        SettingsToggle("关注", "加入 Following", account.followed) { model.follow(account, it) }
                        SettingsToggle("屏蔽", "从信息流隐藏", account.muted) { model.mute(account, it) }
                        Text("推荐权重 ${"%.2f".format(account.weight)}")
                        Slider(account.weight.toFloat(), { model.weight(account, it.toDouble()) }, valueRange = .5f..2f, steps = 5)
                    }
                    HorizontalDivider()
                }
            } else if (section == "主题偏好") {
                items(topics, key = { it.id }) { topic ->
                    Column(Modifier.padding(18.dp)) {
                        Text("${topic.name}  ${"%.2f".format(topic.weight)}", fontWeight = FontWeight.SemiBold)
                        Slider(topic.weight.toFloat(), { model.topicWeight(topic, it.toDouble()) }, valueRange = .5f..2f, steps = 5)
                    }
                }
            } else {
                if (section == "阅读与打开方式" || section == "功能与回退") item {
                    SettingsToggle("X 消息直接查看原文", "跳过详情，使用原来的来源打开方式", directX, onDirectX)
                }
                val selected = when (section) {
                    "阅读与打开方式" -> listOf(Feature.CLEAN_TEXT, Feature.EXTERNAL_BROWSER)
                    else -> Feature.entries.filterNot { it == Feature.TRANSLATION }
                }
                items(selected, key = { it.key }) { feature -> SettingsToggle(feature.title, feature.description, features.enabled(feature)) { onFeature(feature, it) } }
            }
        }
    }
}

@Composable private fun SettingsToggle(title: String, description: String, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { change(!enabled) }.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(enabled, change)
    }
}

@Composable internal fun ModManager() {
    val store = LocalMods.current ?: return
    var message by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxWidth()) {
        item { RuntimeModulePanel(); HorizontalDivider() }
        item { if (LocalFeatures.current.enabled(Feature.CODE_MOD_WINDOW)) { Text("以下为关键词规则包（格式 1），可立即启停；代码和页面扩展请使用上方手机模块。", Modifier.padding(18.dp)); HorizontalDivider() } }
        item {
            Column(Modifier.padding(18.dp)) {
                Text("ZIP 规则包", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("支持补充屏蔽关键词和账号。请从设置首页的统一入口导入 ZIP。", fontSize = 13.sp)
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                if (store.packs.isEmpty()) Text("尚未安装规则包", Modifier.padding(vertical = 16.dp))
            }
        }
        items(store.packs, key = { it.id }) { pack ->
            SettingsToggle(pack.name, "版本 ${pack.version}  关键词 ${pack.words.size} 条  账号 ${pack.accounts.size} 个", pack.enabled) { store.toggle(pack.id, it) }
            TextButton(onClick = { store.remove(pack.id); message = "已卸载 ${pack.name}，消息恢复显示" }, modifier = Modifier.padding(start = 12.dp)) { Text("卸载") }
            HorizontalDivider()
        }
        item { Text("格式：ZIP 根目录只放 mod.json\n必填：formatVersion（1）、id、name、version\n可选：hideKeywords、hideAccounts（字符串数组）\n同一 id 再次导入会替换旧规则", Modifier.padding(18.dp), fontSize = 12.sp) }
    }
}


package cc.ccwu.signalfeed

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun OrganizedSettings(accounts: List<AccountEntity>, topics: List<TopicEntity>, model: FeedViewModel, directX: Boolean, onDirectX: (Boolean) -> Unit, features: FeatureSettings, onFeature: (Feature, Boolean) -> Unit) {
    var section by rememberSaveable { mutableStateOf("") }
    BackHandler(section.isNotEmpty()) { section = "" }
    Column {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (section.isNotEmpty()) TextButton(onClick = { section = "" }) { Text("‹ 返回") }
            Text(section.ifEmpty { "设置" }, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider()
        if (section == "信息源订阅") { SubscriptionPanel(model); return@Column }
        if (section == "Mod 管理") { ModManager(); return@Column }
        LazyColumn {
            if (section.isEmpty()) {
                val sections = listOf("信息源订阅" to "导入 JSON / OPML，管理 RSS 与 Atom", "阅读与打开方式" to "翻译、正文整理、X 与浏览器", "内容过滤" to "去除废话、宣传评论、新增媒体", "账号与关注" to "关注、屏蔽与账号权重", "主题偏好" to "调整各类消息的推荐权重", "Mod 管理" to "导入 ZIP 规则包、启停与卸载", "功能与回退" to "逐项恢复旧行为，保留数据")
                items(sections) { (title, subtitle) ->
                    ListItem(headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text(subtitle) }, trailingContent = { Text("›") }, modifier = Modifier.clickable { section = title })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
                item { Text("SignalFeed 0.7\n所有更新均可在功能与回退中单独关闭", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
            } else if (section == "账号与关注") {
                items(accounts, key = { it.id }) { account ->
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
                    "阅读与打开方式" -> listOf(Feature.TRANSLATION, Feature.CLOUD_TRANSLATION, Feature.CLEAN_TEXT, Feature.EXTERNAL_BROWSER)
                    "内容过滤" -> listOf(Feature.LOW_INFORMATION, Feature.EDITORIAL_FILTER, Feature.NEW_SOURCES)
                    else -> Feature.entries
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<FeedMod?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            loading = true
            runCatching { withContext(Dispatchers.IO) { ModStore.read(context, uri) } }
                .onSuccess { pending = it }.onFailure { message = it.message ?: "无法导入此文件" }
            loading = false
        }
    }
    LazyColumn(Modifier.fillMaxWidth()) {
        item { RuntimeModulePanel(); HorizontalDivider() }
        item { if (LocalFeatures.current.enabled(Feature.CODE_MOD_WINDOW)) { Text("以下为关键词规则包（格式 1），可立即启停；代码和页面扩展请使用上方手机模块。", Modifier.padding(18.dp)); HorizontalDivider() } }
        item {
            Column(Modifier.padding(18.dp)) {
                Text("ZIP 规则包", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("支持补充屏蔽关键词和账号。规则仅在本机生效，不删除原帖。代码和资源修改请使用上方代码 Mod 入口。", fontSize = 13.sp)
                Button(onClick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }, enabled = !loading) { Text(if (loading) "正在读取…" else "导入 .zip") }
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
    pending?.let { pack ->
        AlertDialog(onDismissRequest = { pending = null }, title = { Text("安装 ${pack.name}？") },
            text = { Text("版本 ${pack.version}\n屏蔽关键词：${pack.words.joinToString("、").take(600).ifEmpty { "无" }}\n屏蔽账号：${pack.accounts.joinToString("、").take(300).ifEmpty { "无" }}\n${if (store.packs.any { it.id == pack.id }) "将替换同名标识的现有规则包" else "可随时停用或卸载"}") },
            confirmButton = { TextButton(onClick = { store.save(pack); pending = null; message = "已安装，规则立即生效" }) { Text("安装") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } })
    }
}


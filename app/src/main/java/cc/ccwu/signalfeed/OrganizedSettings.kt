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
            if (section.isNotEmpty()) TextButton(onClick = { section = "" }) { UiText("‹ 返回") }
            UiText(section.ifEmpty { "设置" }, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider()
        if (section == "订阅") { SourceSetupPanel(model); return@Column }
        if (section == "内容筛选") { ContentFilterPanel { Notifications.configure(context) }; return@Column }
        if (section == "主题包") { PackPanel("theme"); return@Column }
        if (section == "手机 Mod") { ModManager(); return@Column }
        if (section == "语言与外观") { ReaderOptionsPanel(); return@Column }
        if (section.isEmpty()) ImportHub(model)
        LazyColumn {
            if (section.isEmpty()) {
                val sections = listOf("语言与外观" to "中英文、深色模式和阅读效果", "主题包" to "配色、深浅色和语言字典",
                    "订阅" to "管理来源、账号与数据栏目", "内容筛选" to "屏蔽、保留和 Breaking 通知规则", "手机 Mod" to "直接在手机安装、停用与卸载",
                    "账号与关注" to "订阅后管理关注和屏蔽", "主题偏好" to "调整已订阅主题权重", "阅读与打开方式" to "原文和浏览器设置", "功能与回退" to "恢复之前的交互行为")
                items(sections) { (title, subtitle) ->
                    ListItem(headlineContent = { UiText(title, fontWeight = FontWeight.SemiBold) }, supportingContent = { UiText(subtitle) }, trailingContent = { UiText("›") }, modifier = Modifier.clickable { section = title })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
                item { UiText("SignalFeed\n导入内容与设置均保存在本机", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
            } else if (section == "账号与关注") {
                items(accounts.filter { it.id.startsWith("sub:") || (backend != null && it.id in selectedAccounts) }, key = { it.id }) { account ->
                    Column(Modifier.padding(16.dp)) {
                        UiText(account.name, fontWeight = FontWeight.Bold)
                        UiText(account.handle, fontSize = 12.sp)
                        SettingsToggle("关注", "加入 Following", account.followed) { model.follow(account, it) }
                        SettingsToggle("屏蔽", "从信息流隐藏", account.muted) { model.mute(account, it) }
                        UiText("推荐权重 ${"%.2f".format(account.weight)}")
                        Slider(account.weight.toFloat(), { model.weight(account, it.toDouble()) }, valueRange = .5f..2f, steps = 5)
                    }
                    HorizontalDivider()
                }
            } else if (section == "主题偏好") {
                items(topics, key = { it.id }) { topic ->
                    Column(Modifier.padding(18.dp)) {
                        UiText("${topic.name}  ${"%.2f".format(topic.weight)}", fontWeight = FontWeight.SemiBold)
                        Slider(topic.weight.toFloat(), { model.topicWeight(topic, it.toDouble()) }, valueRange = .5f..2f, steps = 5)
                    }
                }
            } else {
                if (section == "阅读与打开方式" || section == "功能与回退") item {
                    SettingsToggle("X 消息直接查看原文", "跳过详情，使用原来的来源打开方式", directX, onDirectX)
                }
                val selected = when (section) {
                    "阅读与打开方式" -> listOf(Feature.CLEAN_TEXT, Feature.EXTERNAL_BROWSER)
                    else -> Feature.entries
                }
                items(selected, key = { it.key }) { feature -> SettingsToggle(feature.title, feature.description, features.enabled(feature)) { onFeature(feature, it) } }
            }
        }
    }
}

@Composable internal fun ReaderOptionsPanel() {
    val context = LocalContext.current
    val store = remember { ReaderOptionsStore.get(context) }
    val options by store.options.collectAsState()
    val theme by ShellPacks.get(context).theme.collectAsState()
    val languages = remember(theme.toString()) { availableLanguages(theme) }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            UiText("应用语言", Modifier.padding(start = 18.dp, top = 18.dp), fontWeight = FontWeight.Bold)
            UiText("翻译目标跟随应用语言", Modifier.padding(horizontal = 18.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        items(languages, key = { it.code }) { language ->
            ListItem(headlineContent = { UiText(language.name) }, trailingContent = { RadioButton(LocalUiLanguage.current.code == language.code, { store.save(options.copy(language = language.code)) }) },
                modifier = Modifier.clickable { store.save(options.copy(language = language.code)) })
        }
        item {
            UiText("通过主题包导入更多语言", Modifier.padding(18.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            SettingsToggle("自动翻译", "自动将消息和内容翻译为界面语言，随时可查看原文", options.autoTranslate) { store.save(options.copy(autoTranslate = it)) }
            UiText("首次翻译会下载语言模型，完成后可离线使用", Modifier.padding(horizontal = 18.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            UiText("语言不支持自动翻译时保留原文", Modifier.padding(horizontal = 18.dp, vertical = 6.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.padding(top = 16.dp))
            UiText("外观", Modifier.padding(18.dp), fontWeight = FontWeight.Bold)
        }
        items(listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")) { (id, label) ->
            ListItem(headlineContent = { UiText(label) }, trailingContent = { RadioButton(options.appearance == id, { store.save(options.copy(appearance = id)) }) },
                modifier = Modifier.clickable { store.save(options.copy(appearance = id)) })
        }
        item {
            HorizontalDivider()
            SettingsToggle("顶栏背景模糊", "保持文字清晰，Android 12 及以上生效", options.blur) { store.save(options.copy(blur = it)) }
            SettingsToggle("滚动隐藏顶栏", "上滑收起，下滑恢复", options.hideHeader) { store.save(options.copy(hideHeader = it)) }
        }
    }
}

@Composable private fun SettingsToggle(title: String, description: String, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { change(!enabled) }.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            UiText(title, fontWeight = FontWeight.SemiBold)
            UiText(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(enabled, change)
    }
}

@Composable internal fun ModManager() {
    LazyColumn(Modifier.fillMaxWidth()) {
        item { RuntimeModulePanel() }
        item {
            UiText("关键词规则包在内容筛选中管理", Modifier.padding(18.dp),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


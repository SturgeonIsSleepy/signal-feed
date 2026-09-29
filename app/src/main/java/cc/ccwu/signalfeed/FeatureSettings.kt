package cc.ccwu.signalfeed

import androidx.compose.runtime.staticCompositionLocalOf

internal enum class Feature(val key: String, val title: String, val description: String) {
    TRANSLATION("translation", "消息翻译", "关闭后只显示原文"),
    CLEAN_TEXT("clean_text", "正文整理", "关闭后恢复来源原始文本与标签"),
    SETTINGS_LAYOUT("settings_layout", "分类设置页", "关闭后恢复旧版长列表设置页"),
    MODS("mods_enabled", "启用规则 Mod", "关闭后暂停过滤规则包，不影响已编译的代码 Mod"),
    SUBSCRIPTIONS("subscriptions_enabled", "文件订阅", "关闭后暂停文件订阅并隐藏其帖子，保留订阅及缓存"),
    RUNTIME_MODULES("runtime_modules", "加载手机模块", "关闭后退出并重开恢复原版运行逻辑，保留模块包"),
    CODE_MOD_WINDOW("code_mod_window", "过滤规则包入口", "关闭后隐藏关键词过滤规则包管理"),
    MODEL_CHARTS("model_charts", "新版模型榜", "关闭后恢复配置列表，显示全部思考强度"),
    LABEL_ACTIONS("label_actions", "标签与头像交互", "关闭后恢复旧版静态标签"),
    EXTERNAL_BROWSER("external_browser", "网页使用默认浏览器", "关闭后恢复浏览器内嵌页；X 始终使用旧版来源打开方式")
}
internal data class FeatureSettings(val values: Map<String, Boolean> = emptyMap()) {
    fun enabled(feature: Feature) = values[feature.key] ?: true
}
internal val LocalFeatures = staticCompositionLocalOf { FeatureSettings() }
internal data class FeedActions(val topic: (String) -> Unit = {}, val account: (String) -> Unit = {}, val breaking: () -> Unit = {}, val settings: () -> Unit = {})
internal val LocalFeedActions = staticCompositionLocalOf { FeedActions() }


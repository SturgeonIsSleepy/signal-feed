package cc.ccwu.signalfeed

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent

internal data class ReadablePost(val body: String, val tags: List<String>, val metadata: String?)
internal fun readablePost(body: String, account: String, clean: Boolean = true): ReadablePost {
    if (!clean || account != "wuxing") return ReadablePost(body, emptyList(), null)
    var text = body.trim()
    val header = Regex("^五星体育\\s+(?:\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}\\s+)?\\d{1,2}:\\d{2}\\s+来自\\s*[^\\s#＃]+\\s*").find(text)
    if (header != null) text = text.removePrefix(header.value)
    val tags = Regex("[#＃]([^#＃\\n]+)[#＃]").findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.distinct().toList()
    text = text.replace(Regex("[#＃][^#＃\\n]+[#＃]"), " ").replace(Regex("[ \\t]{2,}"), " ").trim()
    text = text.replace(Regex("[\\u200b\\u200c\\u200d]\\s*(?:\\d+\\s+){2}\\d+\\s*$"), "").trim()
    return ReadablePost(text.ifBlank { body }, tags, header?.value?.trim())
}
internal fun isXUrl(url: String): Boolean = runCatching {
    java.net.URI(url).host?.lowercase() in setOf("x.com", "www.x.com", "twitter.com", "www.twitter.com", "mobile.twitter.com")
}.getOrDefault(false)
internal fun openOriginal(context: Context, url: String) {
    val uri = Uri.parse(url)
    if (uri.scheme != "https" && uri.scheme != "http") return
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (isXUrl(url) || !context.getSharedPreferences("reading", Context.MODE_PRIVATE).getBoolean("external_browser", true)) {
        try { CustomTabsIntent.Builder().build().launchUrl(context, uri); return }
        catch (_: ActivityNotFoundException) { /* Fall back to the system browser. */ }
    }
    val browser = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
        .resolveActivity(context.packageManager)?.packageName
    if (browser != null && browser != "android") intent.setPackage(browser)
    try { context.startActivity(intent) }
    catch (_: ActivityNotFoundException) { Toast.makeText(context, "未找到可用浏览器，请安装或设置默认浏览器", Toast.LENGTH_LONG).show() }
}

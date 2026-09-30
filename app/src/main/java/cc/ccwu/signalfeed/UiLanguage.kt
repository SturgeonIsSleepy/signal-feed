package cc.ccwu.signalfeed

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import org.json.JSONObject

internal class UiLanguage(val code: String, val name: String, val strings: Map<String, String> = emptyMap()) {
    private val templates = strings.filterKeys { it.contains(Regex("\\{[a-zA-Z]+}")) }.map { (source, target) ->
        val slots = Regex("\\{([a-zA-Z]+)}").findAll(source).toList()
        var position = 0
        val pattern = buildString {
            append("^")
            slots.forEach { slot -> append(Regex.escape(source.substring(position, slot.range.first))); append("(.*?)"); position = slot.range.last + 1 }
            append(Regex.escape(source.substring(position))); append("$")
        }
        Triple(Regex(pattern, RegexOption.DOT_MATCHES_ALL), slots.map { it.groupValues[1] }, target)
    }
    fun text(source: String): String {
        strings[source]?.let { return it }
        if (source.length > 2000) return source
        for ((pattern, slots, target) in templates) {
            val match = pattern.matchEntire(source) ?: continue
            val values = slots.mapIndexed { index, key ->
                val value = match.groupValues[index + 1]
                key to (strings[value] ?: value)
            }.toMap()
            return Regex("\\{([a-zA-Z]+)}").replace(target) { values[it.groupValues[1]] ?: it.value }
        }
        return source
    }
}
internal val Chinese = UiLanguage("zh", "中文")
internal val English = UiLanguage("en", "English", englishStrings)
internal val LocalUiLanguage = staticCompositionLocalOf { Chinese }

internal fun availableLanguages(theme: JSONObject): List<UiLanguage> {
    val array = theme.optJSONArray("languages")
    return listOf(Chinese, English) + (0 until (array?.length() ?: 0)).mapNotNull { index ->
        val language = array?.optJSONObject(index) ?: return@mapNotNull null
        val strings = language.optJSONObject("strings") ?: return@mapNotNull null
        val code = language.optString("code")
        if (code in listOf("zh", "en")) return@mapNotNull null
        UiLanguage(code, language.optString("name"), englishStrings + strings.keys().asSequence().associateWith { strings.getString(it) })
    }
}

@Composable internal fun uiText(text: String) = LocalUiLanguage.current.text(text)

@Composable internal fun contentLabel(text: String): String {
    val localized = uiText(text)
    return if (localized != text) localized else rememberContentTranslation(text).body
}

@Composable internal fun UiText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current) {
    androidx.compose.material3.Text(uiText(text), modifier, color = color, fontSize = fontSize,
        fontWeight = fontWeight, textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
        softWrap = softWrap, maxLines = maxLines, style = style)
}

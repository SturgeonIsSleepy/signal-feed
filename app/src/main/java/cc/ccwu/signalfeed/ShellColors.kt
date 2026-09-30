package cc.ccwu.signalfeed

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.staticCompositionLocalOf
import org.json.JSONObject

internal data class ShellColors(val accent: Color = Color(0xFF1D9BF0), val background: Color = Color.White,
    val surface: Color = Color.White, val text: Color = Color(0xFF0F1419), val secondary: Color = Color(0xFF536471),
    val line: Color = Color(0xFFEFF3F4), val muted: Color = Color(0xFFF5F7F8), val dark: Boolean = false) {
    val accentSurface get() = accent.copy(alpha = if (dark) .16f else .09f)
}
internal val LocalShellColors = staticCompositionLocalOf { ShellColors() }
internal fun colors(json: JSONObject, dark: Boolean = false): ShellColors {
    val palette = if (dark) json.optJSONObject("dark") ?: JSONObject() else json.optJSONObject("light") ?: json
    fun value(key: String, fallback: Color) = runCatching { Color(AndroidColor.parseColor(palette.optString(key))) }.getOrDefault(fallback)
    return ShellColors(value("primary", Color(0xFF1D9BF0)), value("background", if (dark) Color(0xFF090D12) else Color.White),
        value("surface", if (dark) Color(0xFF10171F) else Color.White), value("text", if (dark) Color(0xFFE7E9EA) else Color(0xFF0F1419)),
        value("secondary", if (dark) Color(0xFF9BA9B5) else Color(0xFF536471)),
        value("line", if (dark) Color(0xFF25313C) else Color(0xFFEFF3F4)),
        value("muted", if (dark) Color(0xFF18222D) else Color(0xFFF5F7F8)), dark)
}

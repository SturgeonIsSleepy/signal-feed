package cc.ccwu.signalfeed

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.staticCompositionLocalOf
import org.json.JSONObject

internal data class ShellColors(val accent: Color = Color(0xFF1D9BF0), val background: Color = Color.White,
    val surface: Color = Color.White, val text: Color = Color(0xFF0F1419))
internal val LocalShellColors = staticCompositionLocalOf { ShellColors() }
internal fun colors(json: JSONObject): ShellColors {
    fun value(key: String, fallback: Color) = runCatching { Color(AndroidColor.parseColor(json.optString(key))) }.getOrDefault(fallback)
    return ShellColors(value("primary", Color(0xFF1D9BF0)), value("background", Color.White),
        value("surface", Color.White), value("text", Color(0xFF0F1419)))
}

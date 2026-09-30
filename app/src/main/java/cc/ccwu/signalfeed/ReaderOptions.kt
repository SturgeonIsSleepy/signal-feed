package cc.ccwu.signalfeed

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow

internal data class ReaderOptions(val language: String = "zh", val appearance: String = "system",
    val autoTranslate: Boolean = true, val blur: Boolean = true, val hideHeader: Boolean = true)
internal val LocalReaderOptions = staticCompositionLocalOf { ReaderOptions() }

internal class ReaderOptionsStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("reader-options", Context.MODE_PRIVATE)
    private fun read() = ReaderOptions(prefs.getString("language", "zh")!!, prefs.getString("appearance", "system")!!,
        prefs.getBoolean("auto_translate", true), prefs.getBoolean("blur", true), prefs.getBoolean("hide_header", true))
    val options = MutableStateFlow(read())
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> options.value = read() }
    init { prefs.registerOnSharedPreferenceChangeListener(listener) }
    fun save(value: ReaderOptions) {
        prefs.edit().putString("language", value.language).putString("appearance", value.appearance)
            .putBoolean("auto_translate", value.autoTranslate).putBoolean("blur", value.blur)
            .putBoolean("hide_header", value.hideHeader).apply()
    }
    companion object {
        @Volatile private var instance: ReaderOptionsStore? = null
        fun get(context: Context): ReaderOptionsStore = instance ?: synchronized(this) {
            instance ?: ReaderOptionsStore(context.applicationContext).also { instance = it }
        }
    }
}

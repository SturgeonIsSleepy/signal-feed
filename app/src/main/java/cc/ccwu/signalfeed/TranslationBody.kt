package cc.ccwu.signalfeed

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private suspend fun <T> Task<T>.result(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

internal object ContentTranslation {
    private val requests = Semaphore(2)
    suspend fun translate(context: Context, original: String, language: String): String = withContext(Dispatchers.IO) {
        if (original.isBlank()) return@withContext original
        val target = TranslateLanguage.fromLanguageTag(language) ?: return@withContext original
        val hash = MessageDigest.getInstance("SHA-256").digest((target + "\n" + original).toByteArray()).joinToString("") { "%02x".format(it) }
        val cache = File(context.cacheDir, "translations/device-v2-$hash.txt")
        if (cache.exists()) return@withContext cache.readText()
        requests.withPermit {
            if (cache.exists()) return@withPermit cache.readText()
            withTimeout(180_000) {
                val identifier = LanguageIdentification.getClient()
                val source = try { TranslateLanguage.fromLanguageTag(identifier.identifyLanguage(original.take(4000)).result()) }
                    finally { identifier.close() }
                if (source == null || source == target) return@withTimeout original
                val translator = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
                try {
                    translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).result()
                    val output = original.split("\n\n").map { paragraph ->
                        paragraph.chunked(3000).map { if (it.isBlank()) it else translator.translate(it).result() }.joinToString("")
                    }.joinToString("\n\n")
                    cache.parentFile?.mkdirs()
                    val temp = File.createTempFile("translation-", ".txt", cache.parentFile)
                    try { temp.writeText(output); temp.renameTo(cache) } finally { temp.delete() }
                    output
                } finally { translator.close() }
            }
        }
    }
}

internal data class TranslatedContent(val body: String, val busy: Boolean = false, val translated: Boolean = false, val failed: Boolean = false)
@Composable internal fun rememberContentTranslation(original: String, enabled: Boolean = LocalReaderOptions.current.autoTranslate): TranslatedContent {
    val context = LocalContext.current
    val language = LocalUiLanguage.current.code
    val allowed = enabled && LocalFeatures.current.enabled(Feature.TRANSLATION)
    var result by remember(original, language, allowed) { mutableStateOf(TranslatedContent(original, busy = allowed && original.isNotBlank())) }
    LaunchedEffect(original, language, allowed) {
        if (!allowed) return@LaunchedEffect
        try {
            val body = ContentTranslation.translate(context, original, language)
            result = TranslatedContent(body, translated = body != original)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { result = TranslatedContent(original, failed = true) }
    }
    return result
}

@Composable internal fun TranslatableBody(original: String, postId: String, hasTitle: Boolean = true) {
    val auto = LocalReaderOptions.current.autoTranslate
    var manual by remember(original, postId, auto, LocalUiLanguage.current.code) { mutableStateOf<Boolean?>(null) }
    val showingTranslation = manual ?: auto
    val result = rememberContentTranslation(original, showingTranslation)
    val palette = LocalShellColors.current
    Column {
        Row {
            TextButton(onClick = { manual = !showingTranslation }) {
                UiText(if (showingTranslation) "查看原文" else "翻译")
            }
        }
        when {
            result.busy -> UiText("正在准备语言包…", color = palette.secondary, fontSize = 12.sp)
            result.failed -> UiText("翻译暂不可用，显示原文", color = palette.secondary, fontSize = 12.sp)
            result.translated -> UiText("译文仅供参考，可查看原文核对", color = palette.secondary, fontSize = 12.sp)
        }
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                result.body.split("\n\n").filter { it.isNotBlank() }.forEachIndexed { index, paragraph ->
                    Text(paragraph, color = palette.text, fontSize = if (index == 0 && hasTitle) 21.sp else 16.sp,
                        fontWeight = if (index == 0 && hasTitle) FontWeight.Bold else FontWeight.Normal,
                        lineHeight = if (index == 0 && hasTitle) 30.sp else 27.sp)
                }
            }
        }
    }
}

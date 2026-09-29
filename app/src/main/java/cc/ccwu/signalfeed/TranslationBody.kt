package cc.ccwu.signalfeed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private suspend fun <T> Task<T>.result(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

@Composable internal fun TranslatableBody(original: String, postId: String, hasTitle: Boolean = true) {
    val context = LocalContext.current
    val providers by ShellPacks.get(context).translatorPacks.collectAsState()
    val provider = providers.firstOrNull()
    val cloudEnabled = provider?.optString("mode") == "worker"
    val deviceEnabled = provider?.optString("mode") == "device" || provider?.optString("fallback") == "device"
    val scope = rememberCoroutineScope()
    var translated by remember(original) { mutableStateOf<String?>(null) }
    var showTranslation by remember(original) { mutableStateOf(false) }
    var busy by remember(original) { mutableStateOf(false) }
    var engine by remember(original) { mutableStateOf("Qwen 3 云端翻译") }
    var status by remember(original) { mutableStateOf<String?>(null) }
    Column {
        Row {
            TextButton(enabled = !busy, onClick = {
                if (translated != null) { showTranslation = !showTranslation }
                else scope.launch {
                    busy = true
                    try {
                        val cache = withContext(Dispatchers.IO) {
                            val key = MessageDigest.getInstance("SHA-256").digest(original.toByteArray()).joinToString("") { "%02x".format(it) }
                            File(context.cacheDir, "translations/cloud-v1-$key.txt").also { it.parentFile?.mkdirs() }
                        }
                        val cached = withContext(Dispatchers.IO) { if (cloudEnabled && cache.exists()) cache.readText() else null }
                        if (cached != null) { translated = cached; showTranslation = true; status = null }
                        else withTimeout(360_000) {
                            status = "正在识别语言…"
                            val identifier = LanguageIdentification.getClient()
                            val code = try { identifier.identifyLanguage(original).result() } finally { identifier.close() }
                            val source = TranslateLanguage.fromLanguageTag(code)
                            if (source == null) { status = "无法可靠识别语言或暂不支持此语言。"; return@withTimeout }
                            if (source == TranslateLanguage.CHINESE) { status = "正文已是中文。"; return@withTimeout }
                            status = "正在云端翻译，保留专业术语和完整正文…"
                            val hash = cache.name.removePrefix("cloud-v1-").removeSuffix(".txt")
                            val cloud = try { if (cloudEnabled) CloudTranslation.translate(provider!!.getString("url"), postId, hash) else null }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { null }
                            if (cloud != null) {
                                withContext(Dispatchers.IO) { cache.writeText(cloud) }
                                translated = cloud; showTranslation = true; status = null
                                return@withTimeout
                            }
                            if (!deviceEnabled) { status = "此翻译器不可用，请检查导入的服务和网络。"; return@withTimeout }
                            engine = if (cloudEnabled) "Google 离线翻译（云端暂不可用，专业术语可能有误）" else "Google 离线翻译"
                            val translator = Translation.getClient(TranslatorOptions.Builder()
                                .setSourceLanguage(source).setTargetLanguage(TranslateLanguage.CHINESE).build())
                            try {
                                status = "准备语言包，首次需联网下载，可能需要几分钟…"
                                translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).result()
                                val paragraphs = original.split("\n\n")
                                val output = ArrayList<String>()
                                paragraphs.forEachIndexed { index, text ->
                                    status = "正在翻译 ${index + 1}/${paragraphs.size} 段…"
                                    output.add(if (text.isBlank()) text else translator.translate(text).result())
                                }
                                val result = output.joinToString("\n\n")
                                withContext(Dispatchers.IO) { File(cache.parentFile, "offline-" + cache.name).writeText(result) }
                                translated = result; showTranslation = true; status = null
                            } finally { translator.close() }
                        }
                    } catch (_: TimeoutCancellationException) { status = "语言包下载或翻译超时，请检查网络后重试。" }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { status = "翻译失败，请检查网络后重试。原文已保留。" }
                    finally { busy = false }
                }
            }) { Text(if (busy) "翻译中…" else if (showTranslation) "查看原文" else if (translated != null) "查看译文" else "翻译为中文") }
        }
        status?.let { Text(it, color = Color(0xFF536471), fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp)) }
        if (showTranslation) Text("机器翻译 · $engine · 可切回原文核对", color = Color(0xFF536471), fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 10.dp))
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                (if (showTranslation) translated ?: original else original).split("\n\n").filter { it.isNotBlank() }.forEachIndexed { index, paragraph ->
                    Text(paragraph, color = Color(0xFF0F1419), fontSize = if (index == 0 && hasTitle) 20.sp else 16.sp,
                        fontWeight = if (index == 0 && hasTitle) FontWeight.Bold else FontWeight.Normal,
                        lineHeight = if (index == 0 && hasTitle) 29.sp else 27.sp)
                }
            }
        }
    }
}

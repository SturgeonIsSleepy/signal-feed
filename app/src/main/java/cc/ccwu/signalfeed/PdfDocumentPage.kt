package cc.ccwu.signalfeed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private val documentClient = OkHttpClient.Builder().callTimeout(40, TimeUnit.SECONDS).build()

private class Document(private val renderer: PdfRenderer) : AutoCloseable {
    val pages = renderer.pageCount
    private var closed = false
    @Synchronized fun render(index: Int): Bitmap {
        check(!closed)
        return renderer.openPage(index).use { page ->
            val width = 1440
            val height = (width.toLong() * page.height / page.width).toInt().coerceIn(1, 5000)
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                it.eraseColor(android.graphics.Color.WHITE)
                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }
    @Synchronized override fun close() { if (!closed) { closed = true; renderer.close() } }
}

private fun loadDocument(context: Context, url: String): Document {
    require(url.startsWith("https://"))
    val name = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
    val directory = File(context.cacheDir, "documents").apply { mkdirs() }
    val cached = File(directory, "$name.pdf")
    if (!cached.exists()) {
        val temporary = File.createTempFile("download-", ".pdf", directory)
        try {
            documentClient.newCall(Request.Builder().url(url).header("User-Agent", "SignalFeed/0.2 (Android document reader)").build()).execute().use { response ->
                check(response.isSuccessful) { "Document HTTP ${response.code}" }
                val body = checkNotNull(response.body)
                body.byteStream().use { input -> temporary.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= 20 * 1024 * 1024) { "Document too large" }
                        output.write(buffer, 0, count)
                    }
                } }
            }
            PdfRenderer(ParcelFileDescriptor.open(temporary, ParcelFileDescriptor.MODE_READ_ONLY)).use { check(it.pageCount > 0) }
            check(temporary.renameTo(cached))
        } finally { temporary.delete() }
    }
    return Document(PdfRenderer(ParcelFileDescriptor.open(cached, ParcelFileDescriptor.MODE_READ_ONLY)))
}

@Composable
internal fun PdfDocumentPage(url: String, onBack: () -> Unit, onOriginal: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var document by remember(url) { mutableStateOf<Document?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    var retry by remember(url) { mutableIntStateOf(0) }
    var zoom by remember(url) { mutableFloatStateOf(1f) }
    DisposableEffect(url, retry) {
        val job = scope.launch {
            var opened: Document? = null
            try {
                failed = false
                withContext(Dispatchers.IO) { opened = loadDocument(context, url) }
                document = opened
                awaitCancellation()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                android.util.Log.w("SignalFeedPdf", "Document load failed", error)
                failed = true
            }
            finally { withContext(NonCancellable + Dispatchers.IO) { opened?.close() } }
        }
        onDispose { job.cancel() }
    }
    Column(Modifier.fillMaxSize().background(LocalShellColors.current.muted)) {
        Row(Modifier.fillMaxWidth().background(Color.White), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(FeedIcons.Back, "返回消息详情") }
            UiText("原始文件", Modifier.weight(1f))
            TextButton(onClick = { zoom = (zoom - 0.5f).coerceAtLeast(1f) }, enabled = zoom > 1f) { UiText("−") }
            TextButton(onClick = { zoom = (zoom + 0.5f).coerceAtMost(3f) }, enabled = zoom < 3f) { UiText("放大") }
        }
        val loaded = document
        if (loaded != null) {
            BoxWithConstraints(Modifier.weight(1f)) {
                val pageWidth = maxWidth * zoom
                Column(Modifier.horizontalScroll(rememberScrollState())) {
                    LazyColumn(Modifier.width(pageWidth), contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(loaded.pages) { index ->
                            var renderFailed by remember(loaded, index) { mutableStateOf(false) }
                            val bitmap by produceState<Bitmap?>(null, loaded, index) {
                                try { value = withContext(Dispatchers.IO) { loaded.render(index) } }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { renderFailed = true }
                            }
                            Column {
                                UiText("第 ${index + 1} / ${loaded.pages} 页", style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(bottom = 6.dp))
                                bitmap?.let { Image(it.asImageBitmap(), "文档第 ${index + 1} 页", Modifier.fillMaxWidth()
                                    .aspectRatio(it.width.toFloat() / it.height)) }
                                    ?: Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                                        if (renderFailed) TextButton(onClick = onOriginal) { UiText("此页无法显示，打开原文") }
                                        else CircularProgressIndicator()
                                    }
                            }
                        }
                    }
                }
            }
        } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (failed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                UiText("文档暂时无法加载，请检查网络后重试")
                TextButton(onClick = { retry++ }) { UiText("重新加载") }
                TextButton(onClick = onOriginal) { UiText("打开原文") }
            } else CircularProgressIndicator()
        }
    }
}

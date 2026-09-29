package cc.ccwu.signalfeed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal object CloudTranslation {
    private val client = OkHttpClient.Builder().callTimeout(180, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).build()
    suspend fun translate(baseUrl: String, postId: String, hash: String): String = withContext(Dispatchers.IO) {
        val url = (baseUrl.trimEnd('/') + "/v1/translate").toHttpUrl().newBuilder()
            .addQueryParameter("postId", postId).addQueryParameter("sourceHash", hash).build()
        client.newCall(Request.Builder().url(url).post(ByteArray(0).toRequestBody()).build()).execute().use { response ->
            check(response.isSuccessful) { "Cloud translation ${response.code}" }
            val result = JSONObject(response.body!!.string())
            check(result.getString("sourceHash") == hash)
            result.getString("body").also { check(it.isNotBlank()) }
        }
    }
}

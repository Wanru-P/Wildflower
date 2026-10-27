package com.floralens.data.chat

import android.content.Context
import com.floralens.BuildConfig
import com.floralens.data.culture.NatureServiceConfig
import com.floralens.data.culture.NatureServiceException
import com.floralens.data.culture.NatureServiceEndpoints
import com.floralens.domain.chat.PlantChatContext
import com.floralens.domain.chat.PlantChatPolicy
import com.floralens.domain.chat.PlantChatRole
import com.floralens.domain.chat.PlantChatTurn
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import androidx.core.content.edit

@Singleton
class PlantChatConsentStore @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val preferences get() = context.getSharedPreferences("plant-chat-consent", Context.MODE_PRIVATE)
    fun accepted(): Boolean = preferences.getBoolean("accepted-v1", false)
    fun grant() = preferences.edit { putBoolean("accepted-v1", true) }
    fun revoke() = preferences.edit { remove("accepted-v1") }
}

@Singleton
class PlantChatRepository @Inject constructor(private val serviceConfig: NatureServiceConfig) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val busy = AtomicBoolean(false)

    // OkHttp's callback delivers headers, not necessarily the whole body. A ViewModel
    // caller must never resume on Main and then block while reading/closing that body.
    suspend fun reply(context: PlantChatContext, history: List<PlantChatTurn>, question: String): String = withContext(Dispatchers.IO) {
        val cleanQuestion = question.trim()
        if (!PlantChatPolicy.validQuestion(cleanQuestion)) throw PlantChatException("input")
        if (!busy.compareAndSet(false, true)) throw PlantChatException("busy")
        try {
            val configuration = try { serviceConfig.resolve(client) }
            catch (error: NatureServiceException) { throw PlantChatException(error.reason, error.diagnosticType) }
            if (!NatureServiceConfig.validUrl(configuration.url)) throw PlantChatException("configuration")
            val endpoint = chatEndpoint(configuration.url)
            val safeHistory = PlantChatPolicy.trimmedHistory(history)
            val body = JSONObject()
                .put("scientificName", context.identity.scientific.orEmpty())
                .put("chineseName", context.identity.chinese.orEmpty())
                .put("certain", context.identity.certain)
                .put("question", cleanQuestion)
                .put("history", JSONArray().apply {
                    safeHistory.forEach { turn -> put(JSONObject()
                        .put("role", if (turn.role == PlantChatRole.USER) "user" else "assistant")
                        .put("content", turn.text)) }
                }).toString()
            val request = Request.Builder().url(endpoint)
                .header("Authorization", "Bearer ${configuration.token}")
                .post(body.toRequestBody("application/json".toMediaType())).build()
            val uri = URI(endpoint)
            val transport = if (BuildConfig.DEBUG && uri.scheme == "http" && uri.host in setOf("127.0.0.1", "10.0.2.2", "localhost"))
                client.newBuilder().proxy(Proxy.NO_PROXY).build() else client
            val response = try { transport.newCall(request).await() }
                catch (e: PlantChatException) { throw e }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { throw PlantChatException("network", e.javaClass.simpleName) }
            return@withContext response.use {
                if (!it.isSuccessful) {
                    if (it.code in setOf(401, 403)) serviceConfig.clearSession()
                    throw PlantChatException(when (it.code) {
                    401, 403 -> "authorization"
                    429 -> "rate"
                    else -> "network"
                    }, "HTTP_${it.code}")
                }
                val bytes = it.body?.byteStream()?.use { stream ->
                    val buffer = ByteArray(32769); var size = 0
                    while (size < buffer.size) { val count = stream.read(buffer, size, buffer.size - size); if (count < 0) break; size += count }
                    buffer.copyOf(size)
                } ?: throw PlantChatException("response")
                if (bytes.size > 32768) throw PlantChatException("response")
                val json = JSONObject(String(bytes, Charsets.UTF_8))
                if (json.optString("source") != "ai_answer") throw PlantChatException("response")
                json.getString("answer").trim().also { answer ->
                    if (!PlantChatPolicy.validAnswer(answer)) throw PlantChatException("response")
                }
            }
        } finally {
            busy.set(false)
        }
    }

    private fun chatEndpoint(configured: String): String {
        return NatureServiceEndpoints.endpoint(configured, "chat")
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }
        })
    }
}

class PlantChatException(val reason: String, val diagnosticType: String? = null) : Exception(reason)

fun plantChatMessage(reason: String): String = when (reason) {
    "configuration" -> if (BuildConfig.DEBUG) "尚未配置安全问答服务。你仍可使用下方的本地观察建议。" else "花间问答服务暂未开放。你仍可使用下方的本地观察建议。"
    "authorization" -> "问答服务授权已更新，请稍后重试。"
    "rate", "busy" -> "提问有些频繁，请稍后再试。"
    "input" -> "请将问题控制在 300 字以内。"
    "response" -> "这次回答不适合展示，请换一种问法再试。"
    "cancelled" -> "已停止本次回答。"
    else -> "当前网络或问答服务不可用，请稍后重试。"
}

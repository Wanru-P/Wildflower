package com.floralens.data.culture

import android.content.Context
import android.util.AtomicFile
import com.floralens.domain.culture.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import com.floralens.BuildConfig
import java.net.Proxy
import java.net.URI
import androidx.core.content.edit

class NatureServiceException(val reason: String, val diagnosticType: String? = null) : Exception(reason)

@Singleton
class NatureExpressionRepository @Inject constructor(@param:ApplicationContext private val context: Context, val config: NatureServiceConfig) {
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    private val ioLock = Mutex()
    private val jobs = ConcurrentHashMap<String, Mutex>()
    private val directory get() = File(context.filesDir, "nature-expressions-v1").apply { mkdirs() }
    private val options get() = context.getSharedPreferences("nature-expression-options", Context.MODE_PRIVATE)
    fun selectedStyle(identity: CultureIdentity): String = options.getString(identity.key, "quiet").takeIf { it in setOf("quiet", "journal") } ?: "quiet"
    fun selectStyle(identity: CultureIdentity, style: String) {
        require(style in setOf("quiet", "journal"))
        options.edit { putString(identity.key, style) }
    }
    suspend fun cached(identity: CultureIdentity, style: String = selectedStyle(identity)): NatureExpression? = withContext(Dispatchers.IO) {
        ioLock.withLock { runCatching {
            val file = AtomicFile(File(directory, NatureTextPolicy.cacheKey(identity, style)))
            val bytes = file.openRead().use { stream ->
                val buffer = ByteArray(4097); var size = 0
                while (size < buffer.size) { val n = stream.read(buffer, size, buffer.size - size); if (n < 0) break; size += n }
                require(size <= 4096); buffer.copyOf(size)
            }
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            val age = System.currentTimeMillis() - json.getLong("created")
            require(age in 0..TTL && json.getInt("version") == NatureTextPolicy.VERSION && json.getString("key") == identity.key && json.getString("style") == style)
            require(json.optString("mode", "species") == identity.natureMode)
            NatureExpression(json.getString("text"), ExpressionSource.AI, identity.key).takeIf(NatureTextPolicy::valid)
        }.getOrNull() }
    }
    suspend fun current(identity: CultureIdentity, style: String = selectedStyle(identity)) = cached(identity, style) ?: NatureTextPolicy.local(identity)
    suspend fun generate(identity: CultureIdentity, style: String = selectedStyle(identity), force: Boolean = false): NatureExpression {
        require(style in setOf("quiet", "journal"))
        val lock = jobs.getOrPut(NatureTextPolicy.cacheKey(identity, style)) { Mutex() }
        if (!lock.tryLock()) throw NatureServiceException("busy")
        try {
            if (!force) cached(identity, style)?.let { return it }
            val configuration = config.resolve(client)
            if (!NatureServiceConfig.validUrl(configuration.url)) throw NatureServiceException("configuration")
            val expression = withContext(Dispatchers.IO) {
                // Unknown identities never transmit a nickname, candidate name, photo, note or location.
                val payload = JSONObject().put("scientificName", if (identity.canGenerate) identity.scientific else "")
                    .put("chineseName", if (identity.canGenerate) identity.chinese.orEmpty() else "").put("style", style)
                if (!identity.canGenerate) payload.put("mode", "general")
                val body = payload.toString()
                val request = Request.Builder().url(natureEndpoint(configuration.url)).header("Authorization", "Bearer ${configuration.token}")
                    .post(body.toRequestBody("application/json".toMediaType())).build()
                // The AVD's external HTTP proxy must never intercept its loopback test service.
                // Production HTTPS continues to respect the user's network/proxy configuration.
                val uri = URI(configuration.url)
                val transport = if (BuildConfig.DEBUG && uri.scheme == "http" && uri.host in setOf("127.0.0.1", "10.0.2.2", "localhost"))
                    client.newBuilder().proxy(Proxy.NO_PROXY).build() else client
                val text = try { transport.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code in setOf(401, 403)) config.clearSession()
                        val reason = when (response.code) {
                            401, 403 -> "authorization"
                            429 -> "rate"
                            504 -> "timeout"
                            400 -> "request"
                            503 -> "unavailable"
                            else -> if (runCatching { JSONObject(response.peekBody(1024).string()).optString("error") }.getOrNull() == "invalid_output") "response" else "unavailable"
                        }
                        throw NatureServiceException(reason, "HTTP_${response.code}")
                    }
                    val bytes = response.body?.byteStream()?.use { stream ->
                        val buffer = ByteArray(8193); var size = 0
                        while (size < buffer.size) { val n = stream.read(buffer, size, buffer.size - size); if (n < 0) break; size += n }
                        buffer.copyOf(size)
                    } ?: throw NatureServiceException("response")
                    if (bytes.size > 8192) throw NatureServiceException("response")
                    val json = JSONObject(String(bytes, Charsets.UTF_8))
                    if (json.optString("source") != "ai_original" ||
                        json.optString("scientificName") != (if (identity.canGenerate) identity.scientific else "") ||
                        json.optString("mode", "species") != identity.natureMode) throw NatureServiceException("response")
                    json.getString("text")
                } } catch (e: NatureServiceException) { throw e } catch (e: Exception) {
                    // Class name only for diagnostics: never retain an upstream cause/message/URL/key.
                    throw NatureServiceException("network", e.javaClass.simpleName)
                }
                ensureActive()
                NatureExpression(text, ExpressionSource.AI, identity.key).also { if (!NatureTextPolicy.valid(it)) throw NatureServiceException("response") }
            }
            currentCoroutineContext().ensureActive()
            withContext(Dispatchers.IO) { ioLock.withLock {
                val files = directory.listFiles().orEmpty().filter { it.name.matches(Regex("[0-9a-f]{64}")) }.sortedBy { it.lastModified() }
                files.take((files.size - 127).coerceAtLeast(0)).forEach { AtomicFile(it).delete() } // cache only, never photos or Room
                val file = AtomicFile(File(directory, NatureTextPolicy.cacheKey(identity, style)))
                val output = file.startWrite()
                try { output.write(JSONObject().put("version", NatureTextPolicy.VERSION).put("key", identity.key).put("style", style)
                    .put("mode", identity.natureMode).put("created", System.currentTimeMillis()).put("text", expression.text).toString().toByteArray()); file.finishWrite(output) }
                catch (e: Exception) { file.failWrite(output); throw e }
            } }
            return expression
        } finally { lock.unlock() }
    }
    companion object {
        const val TTL = 30L * 24 * 60 * 60 * 1000
        fun message(reason: String) = when (reason) {
            "configuration" -> if (BuildConfig.DEBUG) "尚未配置安全创作服务，仍可阅读本地自然文字。" else "自然创作服务暂未开放，仍可阅读本地自然文字。"
            "identity" -> "植物身份尚不明确，先用观察引导记录这次相遇。"
            "rate", "busy" -> "请稍后再试，当前自然文字会为你保留。"
            "authorization" -> "创作服务授权已更新，请稍后重试。"
            "response" -> "这次文字暂不适合展示，已保留原有内容。"
            "timeout" -> "创作等待超时，请重试。已有文字会为你保留。"
            "request" -> "创作请求暂未被服务接受，请重试或更新应用。"
            "unavailable" -> "创作服务暂时不可用，请稍后重试。已有文字会为你保留。"
            else -> "网络连接失败，请检查网络后重试。已有文字会为你保留。"
        }
    }

    private fun natureEndpoint(configured: String): String {
        return NatureServiceEndpoints.endpoint(configured, "nature")
    }
}

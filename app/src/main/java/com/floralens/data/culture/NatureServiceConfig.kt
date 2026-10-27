package com.floralens.data.culture

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.floralens.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NatureServiceConfiguration(val url: String, val token: String, val expiresAt: Long = Long.MAX_VALUE)

/** Only a scoped proxy credential, encrypted at rest. Never accepts a provider API key. */
@Singleton
class NatureServiceConfig @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val preferences get() = context.getSharedPreferences("nature-service-private", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(): NatureServiceConfiguration? = runCatching {
        val data = preferences.getString("encrypted", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12))) }
        val json = JSONObject(String(cipher.doFinal(data.copyOfRange(12, data.size)), Charsets.UTF_8))
        NatureServiceConfiguration(json.getString("url"), json.getString("token"), json.optLong("expiresAt", Long.MAX_VALUE))
    }.getOrNull()
    // KTX edit(commit=true) returns Unit; retain commit's Boolean to detect storage failures.
    @android.annotation.SuppressLint("UseKtx")
    fun save(url: String, token: String) {
        require(validUrl(url)) { "请填写 HTTPS 代理地址，不要填写 DeepSeek 官方接口" }
        require(token.length in 32..256 && !token.startsWith("sk-") && !token.any(Char::isWhitespace)) { "请填写代理访问凭证，而不是 DeepSeek API Key" }
        persist(NatureServiceConfiguration(url, token))
    }
    fun clear() { preferences.edit { remove("encrypted") } }
    fun available(): Boolean = read()?.let { validUrl(it.url) } == true || productionOrigin() != null

    /**
     * Debug can retain a manually configured loopback proxy. A release build
     * always uses the configured production service and obtains an expiring
     * credential; no provider credential is ever shipped in the APK.
     */
    suspend fun resolve(client: OkHttpClient): NatureServiceConfiguration = withContext(Dispatchers.IO) {
        val manual = read()
        if (BuildConfig.DEBUG) {
            if (manual != null && validUrl(manual.url)) return@withContext manual
            throw NatureServiceException("configuration")
        }
        val origin = productionOrigin() ?: throw NatureServiceException("configuration")
        val now = System.currentTimeMillis()
        if (manual != null && manual.url == origin && manual.expiresAt in (now + 60_000L)..(now + 86_400_000L)) return@withContext manual
        issueSession(client, origin)
    }

    fun clearSession() {
        val current = read()
        if (current?.url == productionOrigin()) clear()
    }

    private fun issueSession(client: OkHttpClient, origin: String): NatureServiceConfiguration {
        val installationId = preferences.getString("installation-id", null) ?: UUID.randomUUID().toString().also {
            check(preferences.edit().putString("installation-id", it).commit())
        }
        val endpoint = NatureServiceEndpoints.endpoint(origin, "session")
        val request = Request.Builder().url(endpoint)
            .post(JSONObject().put("installId", installationId).toString().toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw NatureServiceException(if (response.code == 429) "rate" else "network", "HTTP_${response.code}")
                val bytes = response.body?.byteStream()?.use { stream ->
                    val buffer = ByteArray(2049); var count = 0
                    while (count < buffer.size) { val read = stream.read(buffer, count, buffer.size - count); if (read < 0) break; count += read }
                    buffer.copyOf(count)
                } ?: throw NatureServiceException("response")
                if (bytes.size > 2048) throw NatureServiceException("response")
                val text = String(bytes, Charsets.UTF_8)
                val json = JSONObject(text)
                val token = json.getString("token")
                val expiresAt = json.getLong("expiresAt")
                val now = System.currentTimeMillis()
                if (token.length !in 50..256 || token.startsWith("sk-") || token.any(Char::isWhitespace) ||
                    expiresAt !in (now + 30_000L)..(now + 86_400_000L)) throw NatureServiceException("response")
                NatureServiceConfiguration(origin, token, expiresAt).also(::persist)
            }
        } catch (error: NatureServiceException) { throw error
        } catch (error: Exception) { throw NatureServiceException("network", error.javaClass.simpleName) }
    }

    private fun persist(configuration: NatureServiceConfiguration) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val plain = JSONObject().put("url", configuration.url).put("token", configuration.token).put("expiresAt", configuration.expiresAt).toString().toByteArray()
        val bytes = cipher.iv + cipher.doFinal(plain)
        check(preferences.edit().putString("encrypted", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit())
    }
    companion object {
        private const val ALIAS = "wildflower-nature-proxy-aes"
        private fun productionOrigin(): String? = BuildConfig.NATURE_PROXY_ORIGIN.trim().takeIf(::validUrl)?.let(NatureServiceEndpoints::base)
        fun validUrl(value: String): Boolean = runCatching {
            val uri = URI(value)
            NatureServiceEndpoints.base(value)
            uri.host != null && uri.userInfo == null && uri.rawQuery == null && uri.fragment == null &&
                !uri.host.lowercase().endsWith("deepseek.com") && (uri.scheme == "https" || BuildConfig.DEBUG && uri.scheme == "http" && uri.host in setOf("127.0.0.1", "10.0.2.2", "localhost"))
        }.getOrDefault(false)
    }
}

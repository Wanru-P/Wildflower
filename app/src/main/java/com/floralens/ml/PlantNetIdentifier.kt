package com.floralens.ml

import android.util.Log
import com.floralens.BuildConfig
import com.floralens.domain.model.IdCandidate
import com.floralens.domain.model.IdentificationSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Online identification via the Pl@ntNet API. See ml/HybridIdentifier.kt
 * for the routing logic and PRIVACY.md for what's actually transmitted.
 * Logs under tag "PlantNetIdentifier".
 *
 * Requests related images (include-related-images=true) and collects all of
 * them per candidate so the details screen can show a small gallery.
 */
@Singleton
class PlantNetIdentifier @Inject constructor() : PlantIdentifier {

    companion object {
        private const val TAG = "PlantNetIdentifier"
        private const val BASE_URL = "https://my-api.plantnet.org/v2/identify"
        private const val PROJECT = "all"
        private const val NB_RESULTS = 5
        private const val MAX_IMAGES_PER_CANDIDATE = 4
        private val JPEG = "image/jpeg".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun classify(imageBytes: ByteArray): List<IdCandidate> =
        withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.PLANTNET_API_KEY
            if (apiKey.isBlank()) {
                Log.e(TAG, "PLANTNET_API_KEY is blank at runtime — check local.properties, then Clean + Rebuild")
                throw IOException("Pl@ntNet API key not configured — see local.properties")
            }

            val request = buildRequest(apiKey, imageBytes)

            Log.d(TAG, "Requesting identification (${imageBytes.size} bytes)")

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    Log.e(TAG, "Pl@ntNet HTTP ${response.code}: $errorBody")
                    throw IOException("Pl@ntNet request failed: HTTP ${response.code} — $errorBody")
                }
                val payload = response.body?.string()
                    ?: throw IOException("Pl@ntNet returned an empty response")
                val candidates = parseResults(payload)
                Log.d(TAG, "Pl@ntNet returned ${candidates.size} candidate(s)")
                candidates
            }
        }

    internal fun buildRequest(apiKey: String, imageBytes: ByteArray): Request {
        val url = "$BASE_URL/$PROJECT".toHttpUrl().newBuilder()
            .addQueryParameter("api-key", apiKey)
            // /v2/languages advertises zh. Omitting lang defaults commonNames to English.
            .addQueryParameter("lang", "zh")
            .addQueryParameter("nb-results", NB_RESULTS.toString())
            .addQueryParameter("include-related-images", "true")
            .build()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("images", "capture.jpg", imageBytes.toRequestBody(JPEG))
            .addFormDataPart("organs", "auto")
            .build()
        return Request.Builder().url(url).post(body).build()
    }

    internal fun parseResults(payload: String): List<IdCandidate> {
        val results = JSONObject(payload).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            runCatching {
                val entry = results.getJSONObject(i)
                val species = entry.getJSONObject("species")
                val scientificName = species.optString(
                    "scientificNameWithoutAuthor",
                    species.optString("scientificName", "Unknown"),
                )
                val commonNames = species.optJSONArray("commonNames")
                val names = if (commonNames == null) emptyList() else
                    (0 until commonNames.length()).mapNotNull { commonNames.opt(it) as? String }
                val commonName = PlantNetCommonNames.preferred(names, scientificName)
                IdCandidate(
                    scientificName = scientificName,
                    commonName = commonName,
                    confidence = entry.optDouble("score", 0.0).toFloat().coerceIn(0f, 1f),
                    source = IdentificationSource.PLANTNET,
                    imageUrls = extractImageUrls(entry),
                )
            }.getOrNull()
        }
    }

    /**
     * Collects up to MAX_IMAGES_PER_CANDIDATE image URLs from a result's
     * "images" array, preferring medium ("m") for gallery display, falling
     * back to small/original per entry.
     */
    private fun extractImageUrls(entry: JSONObject): List<String> = runCatching {
        val images = entry.optJSONArray("images") ?: return@runCatching emptyList()
        (0 until images.length())
            .mapNotNull { i ->
                val urls = images.getJSONObject(i).optJSONObject("url")
                urls?.optString("m")?.takeIf { it.isNotBlank() }
                    ?: urls?.optString("s")?.takeIf { it.isNotBlank() }
                    ?: urls?.optString("o")?.takeIf { it.isNotBlank() }
            }
            .take(MAX_IMAGES_PER_CANDIDATE)
    }.getOrDefault(emptyList())
}

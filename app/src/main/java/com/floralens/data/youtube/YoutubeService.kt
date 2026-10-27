package com.floralens.data.youtube

import com.floralens.BuildConfig
import com.floralens.domain.model.PlantVideo
import com.floralens.domain.model.VideoSection
import com.floralens.domain.model.VideoAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube Data API v3 search for plant videos (online mode only). Runs three
 * themed searches per plant — care, general, propagation — each a section in
 * the Videos tab. Tapping a result launches YouTube; we don't embed.
 *
 * Free-tier quota is 10,000 units/day and each search costs 100, so three
 * per plant = ~33 plant-views/day. A small in-memory cache avoids re-spending
 * quota when the same plant is reopened within a session. Logs under
 * "YouTubeService".
 */
interface YouTubeService {
    suspend fun sectionsFor(commonName: String, scientificName: String): List<VideoSection>
    fun configured(): Boolean = true
    suspend fun lookup(commonName: String, scientificName: String): VideoLookup {
        val sections = sectionsFor(commonName, scientificName)
        return VideoLookup(sections, if (sections.isEmpty()) VideoAvailability.EMPTY else VideoAvailability.READY)
    }
}

data class VideoLookup(val sections: List<VideoSection>, val availability: VideoAvailability)

@Singleton
class YouTubeDataService @Inject constructor() : YouTubeService {

    companion object {
        private const val SEARCH_URL = "https://www.googleapis.com/youtube/v3/search"
        private const val RESULTS_PER_SECTION = 6
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    // Session cache: plant name → sections. Cleared when the process dies.
    private val cache = mutableMapOf<String, List<VideoSection>>()

    override fun configured(): Boolean = BuildConfig.YOUTUBE_API_KEY.isNotBlank()
    override suspend fun sectionsFor(commonName: String, scientificName: String): List<VideoSection> = lookup(commonName, scientificName).sections

    override suspend fun lookup(commonName: String, scientificName: String): VideoLookup =
        withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.YOUTUBE_API_KEY
            if (apiKey.isBlank()) {
                return@withContext VideoLookup(emptyList(), VideoAvailability.UNAVAILABLE)
            }

            // Prefer the common name for search relevance; fall back to scientific.
            val subject = commonName.takeIf { it.isNotBlank() && it != scientificName } ?: scientificName
            val cacheKey = subject.lowercase()
            cache[cacheKey]?.let { return@withContext VideoLookup(it, VideoAvailability.READY) }

            val queries = listOf(
                "Care & watering" to "$subject plant care watering",
                "About this plant" to "$subject plant guide",
                "Propagation" to "$subject plant propagation",
            )

            var failures = 0
            val sections = queries.mapNotNull { (title, query) ->
                val videos = try { search(query, apiKey) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { failures++; emptyList() }
                if (videos.isEmpty()) null else VideoSection(title, videos)
            }

            if (sections.isNotEmpty()) cache[cacheKey] = sections
            VideoLookup(sections, when {
                sections.isNotEmpty() -> VideoAvailability.READY
                failures > 0 -> VideoAvailability.UNAVAILABLE
                else -> VideoAvailability.EMPTY
            })
        }

    private fun search(query: String, apiKey: String): List<PlantVideo> {
        val url = "$SEARCH_URL?part=snippet&type=video&maxResults=$RESULTS_PER_SECTION" +
                "&q=${URLEncoder.encode(query, "UTF-8")}&key=$apiKey"

        client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw java.io.IOException("Video service unavailable")
            }
            val json = JSONObject(resp.body?.string() ?: return emptyList())
            val items = json.optJSONArray("items") ?: return emptyList()
            return (0 until items.length()).mapNotNull { i ->
                runCatching {
                    val item = items.getJSONObject(i)
                    val videoId = item.getJSONObject("id").optString("videoId").takeIf { it.isNotBlank() }
                        ?: return@runCatching null
                    val snippet = item.getJSONObject("snippet")
                    val thumb = snippet.getJSONObject("thumbnails")
                        .optJSONObject("medium")?.optString("url")
                        ?: snippet.getJSONObject("thumbnails").optJSONObject("default")?.optString("url")
                        ?: return@runCatching null
                    PlantVideo(
                        videoId = videoId,
                        title = snippet.optString("title", "Untitled"),
                        channelTitle = snippet.optString("channelTitle", ""),
                        thumbnailUrl = thumb,
                    )
                }.getOrNull()
            }
        }
    }
}

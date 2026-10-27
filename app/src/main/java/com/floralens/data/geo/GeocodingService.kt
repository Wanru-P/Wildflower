package com.floralens.data.geo

import android.util.Log
import com.floralens.domain.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns place names (from Gemini's nativeRegions) into coordinates via the
 * free Nominatim/OpenStreetMap geocoder — no API key, consistent with the
 * app's no-Google-Play-Services stance. Only used to plot pins; no map tiles
 * are ever fetched (the world map is drawn on Canvas).
 *
 * Nominatim's usage policy caps requests at 1/second and requires a
 * descriptive User-Agent. [geocodeAll] serialises lookups through a mutex +
 * delay so a burst of regions can't breach that limit and get the app
 * blocked. Names that don't resolve are simply dropped.
 */
interface GeocodingService {
    /** Geocodes each name in order, throttled. Unresolved names are omitted. */
    suspend fun geocodeAll(names: List<String>): List<GeoPoint>
}

@Singleton
class NominatimGeocodingService @Inject constructor() : GeocodingService {

    companion object {
        private const val TAG = "Geocoding"
        private const val BASE_URL = "https://nominatim.openstreetmap.org/search"
        private const val USER_AGENT = "FloraLens/1.0 (Android plant app; portfolio project)"
        private const val MIN_INTERVAL_MS = 1100L // > 1s to stay under Nominatim's cap
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Serialises all geocode calls app-wide so parallel screens can't burst.
    private val throttle = Mutex()
    private var lastCallAt = 0L

    override suspend fun geocodeAll(names: List<String>): List<GeoPoint> =
        withContext(Dispatchers.IO) {
            names.mapNotNull { name -> geocodeOne(name) }
        }

    private suspend fun geocodeOne(name: String): GeoPoint? {
        throttle.withLock {
            val since = System.currentTimeMillis() - lastCallAt
            if (since < MIN_INTERVAL_MS) delay(MIN_INTERVAL_MS - since)
            lastCallAt = System.currentTimeMillis()
        }

        return runCatching {
            val url = "$BASE_URL?format=json&limit=1&q=${URLEncoder.encode(name, "UTF-8")}"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Nominatim HTTP ${response.code} for '$name'")
                    return@use null
                }
                val body = response.body?.string() ?: return@use null
                val arr = JSONArray(body)
                if (arr.length() == 0) {
                    Log.d(TAG, "No geocode result for '$name'")
                    return@use null
                }
                val first = arr.getJSONObject(0)
                val lat = first.optString("lat").toDoubleOrNull() ?: return@use null
                val lon = first.optString("lon").toDoubleOrNull() ?: return@use null
                GeoPoint(label = name, latitude = lat, longitude = lon)
            }
        }.getOrNull()
    }
}
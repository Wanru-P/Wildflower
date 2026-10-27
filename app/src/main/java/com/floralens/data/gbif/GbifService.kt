package com.floralens.data.gbif

import android.util.Log
import com.floralens.domain.model.CountrySlice
import com.floralens.domain.model.OccurrenceData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real species-occurrence distribution from GBIF — no API key needed.
 * Two calls: species/match (name → taxon key), then occurrence/search with
 * a country facet (taxon key → per-country counts). Returns null when GBIF
 * has no usable data, which the UI treats as "no distribution data".
 * Logs under tag "GbifService".
 */
interface GbifService {
    suspend fun occurrencesByCountry(scientificName: String, topN: Int = 6): OccurrenceData?
}

@Singleton
class GbifOccurrenceService @Inject constructor() : GbifService {

    companion object {
        private const val TAG = "GbifService"
        private const val MATCH_URL = "https://api.gbif.org/v1/species/match"
        private const val SEARCH_URL = "https://api.gbif.org/v1/occurrence/search"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    override suspend fun occurrencesByCountry(scientificName: String, topN: Int): OccurrenceData? =
        withContext(Dispatchers.IO) {
            val taxonKey = matchTaxonKey(scientificName) ?: run {
                Log.d(TAG, "No GBIF taxon match for '$scientificName'")
                return@withContext null
            }
            fetchFacets(taxonKey, topN)
        }

    private fun matchTaxonKey(scientificName: String): Int? = runCatching {
        val url = "$MATCH_URL?name=${URLEncoder.encode(scientificName, "UTF-8")}"
        client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            val json = JSONObject(resp.body?.string() ?: return@runCatching null)
            json.optInt("usageKey").takeIf { it > 0 }
        }
    }.getOrNull()

    private fun fetchFacets(taxonKey: Int, topN: Int): OccurrenceData? = runCatching {
        val url = "$SEARCH_URL?taxonKey=$taxonKey&facet=country&facetLimit=20&limit=0"
        client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "GBIF occurrence HTTP ${resp.code}")
                return@runCatching null
            }
            val json = JSONObject(resp.body?.string() ?: return@runCatching null)
            val total = json.optLong("count", 0)
            val facets = json.optJSONArray("facets") ?: return@runCatching null
            if (facets.length() == 0) return@runCatching null

            val counts = facets.getJSONObject(0).optJSONArray("counts") ?: return@runCatching null
            val all = (0 until counts.length()).mapNotNull { i ->
                runCatching {
                    val c = counts.getJSONObject(i)
                    val code = c.optString("name").takeIf { it.isNotBlank() } ?: return@runCatching null
                    val cnt = c.optLong("count", 0)
                    CountrySlice(code, countryName(code), cnt)
                }.getOrNull()
            }.sortedByDescending { it.count }

            if (all.isEmpty()) return@runCatching null

            val top = all.take(topN)
            val restCount = all.drop(topN).sumOf { it.count }
            val slices = if (restCount > 0) {
                top + CountrySlice("OTHER", "Other", restCount)
            } else {
                top
            }

            Log.d(TAG, "GBIF: total=$total, ${slices.size} slices")
            OccurrenceData(totalCount = total, slices = slices)
        }
    }.getOrNull()

    /** ISO-2 code → display name via the JVM's locale data; falls back to the code. */
    private fun countryName(code: String): String =
        runCatching {
            Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH)
                .takeIf { it.isNotBlank() && it != code } ?: code
        }.getOrDefault(code)
}
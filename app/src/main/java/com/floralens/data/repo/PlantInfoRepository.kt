package com.floralens.data.repo

import com.floralens.data.db.PlantInfoDao
import com.floralens.data.db.entity.PlantInfoEntity
import com.floralens.domain.model.CareLevel
import com.floralens.domain.model.CareLevels
import com.floralens.domain.model.CountrySlice
import com.floralens.domain.model.GeoPoint
import com.floralens.domain.model.OccurrenceData
import com.floralens.domain.model.PlantInsights
import com.floralens.domain.model.RegionalName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One saved copy of a species' details — everything the details screen
 * renders except videos.
 */
data class SavedPlantInfo(
    val insights: PlantInsights,
    val geoPoints: List<GeoPoint>,
    val occurrence: OccurrenceData?,
    val savedAt: Instant,
)

/**
 * Offline store for "Learn more" details. Serialization lives here — the
 * entity is dumb strings, the domain models stay Room-free. org.json is used
 * (same as the network services) rather than adding a serialization library
 * for one table.
 */
@Singleton
class PlantInfoRepository @Inject constructor(
    private val dao: PlantInfoDao,
) {

    /** Auto-save after an online load, and the manual refresh — both land here. */
    suspend fun save(
        insights: PlantInsights,
        geoPoints: List<GeoPoint>,
        occurrence: OccurrenceData?,
        commonName: String?,
    ) {
        dao.upsert(
            PlantInfoEntity(
                scientificName = insights.scientificName,
                commonName = commonName,
                insightsJson = insights.toJson().toString(),
                geoPointsJson = geoPoints.toGeoJson().toString(),
                occurrenceJson = occurrence?.toJson()?.toString(),
                savedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun getSaved(scientificName: String): SavedPlantInfo? {
        val entity = dao.get(scientificName) ?: return null
        val insights = runCatching { parseInsights(JSONObject(entity.insightsJson)) }.getOrNull()
            ?: return null // corrupt row — treat as not saved rather than crash
        val geoPoints = runCatching { parseGeoPoints(JSONArray(entity.geoPointsJson)) }
            .getOrDefault(emptyList())
        val occurrence = entity.occurrenceJson?.let {
            runCatching { parseOccurrence(JSONObject(it)) }.getOrNull()
        }
        return SavedPlantInfo(
            insights = insights,
            geoPoints = geoPoints,
            occurrence = occurrence,
            savedAt = Instant.ofEpochMilli(entity.savedAtEpochMs),
        )
    }

    fun observeSavedSpecies(): Flow<Set<String>> =
        dao.observeSavedSpecies().map { it.toSet() }

    fun observeIsSaved(scientificName: String): Flow<Boolean> =
        dao.observeIsSaved(scientificName)

    suspend fun delete(scientificName: String) = dao.delete(scientificName)

    // ── JSON codec ──────────────────────────────────────────────────

    private fun PlantInsights.toJson() = JSONObject().apply {
        put("scientificName", scientificName)
        putOpt("description", description)
        put("regionalNames", JSONArray().apply {
            regionalNames.forEach {
                put(JSONObject().put("language", it.language).put("name", it.name))
            }
        })
        putOpt("origin", origin)
        put("nativeRegions", JSONArray(nativeRegions))
        putOpt("growthHabit", growthHabit)
        putOpt("bloomSeason", bloomSeason)
        put("careLevels", JSONObject().apply {
            put("water", careLevels.water.name)
            put("light", careLevels.light.name)
            put("humidity", careLevels.humidity.name)
            put("difficulty", careLevels.difficulty.name)
        })
        putOpt("careNotes", careNotes)
        put("propagation", JSONArray(propagation))
        put("commonProblems", JSONArray(commonProblems))
        put("funFacts", JSONArray(funFacts))
        putOpt("toxicityNote", toxicityNote)
    }

    private fun parseInsights(o: JSONObject) = PlantInsights(
        scientificName = o.getString("scientificName"),
        description = o.optStringOrNull("description"),
        regionalNames = o.optJSONArray("regionalNames").mapObjects {
            RegionalName(language = it.getString("language"), name = it.getString("name"))
        },
        origin = o.optStringOrNull("origin"),
        nativeRegions = o.optJSONArray("nativeRegions").mapStrings(),
        growthHabit = o.optStringOrNull("growthHabit"),
        bloomSeason = o.optStringOrNull("bloomSeason"),
        careLevels = o.optJSONObject("careLevels")?.let {
            CareLevels(
                water = CareLevel.from(it.optString("water")),
                light = CareLevel.from(it.optString("light")),
                humidity = CareLevel.from(it.optString("humidity")),
                difficulty = CareLevel.from(it.optString("difficulty")),
            )
        } ?: CareLevels(),
        careNotes = o.optStringOrNull("careNotes"),
        propagation = o.optJSONArray("propagation").mapStrings(),
        commonProblems = o.optJSONArray("commonProblems").mapStrings(),
        funFacts = o.optJSONArray("funFacts").mapStrings(),
        toxicityNote = o.optStringOrNull("toxicityNote"),
    )

    private fun List<GeoPoint>.toGeoJson() = JSONArray().apply {
        this@toGeoJson.forEach {
            put(
                JSONObject()
                    .put("label", it.label)
                    .put("latitude", it.latitude)
                    .put("longitude", it.longitude),
            )
        }
    }

    private fun parseGeoPoints(arr: JSONArray): List<GeoPoint> = arr.mapObjects {
        GeoPoint(
            label = it.getString("label"),
            latitude = it.getDouble("latitude"),
            longitude = it.getDouble("longitude"),
        )
    }

    private fun OccurrenceData.toJson() = JSONObject().apply {
        put("totalCount", totalCount)
        put("slices", JSONArray().apply {
            slices.forEach {
                put(
                    JSONObject()
                        .put("countryCode", it.countryCode)
                        .put("displayName", it.displayName)
                        .put("count", it.count),
                )
            }
        })
    }

    private fun parseOccurrence(o: JSONObject) = OccurrenceData(
        totalCount = o.getLong("totalCount"),
        slices = o.optJSONArray("slices").mapObjects {
            CountrySlice(
                countryCode = it.getString("countryCode"),
                displayName = it.getString("displayName"),
                count = it.getLong("count"),
            )
        },
    )

    // ── org.json helpers ────────────────────────────────────────────

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private inline fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until this@mapObjects.length()) {
                runCatching { transform(this@mapObjects.getJSONObject(i)) }
                    .getOrNull()?.let(::add)
            }
        }
    }

    private fun JSONArray?.mapStrings(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until this@mapStrings.length()) {
                this@mapStrings.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
}
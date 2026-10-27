package com.floralens.data.seed

import android.content.Context
import com.floralens.data.db.ObservationDao
import com.floralens.data.db.PlantDao
import com.floralens.data.db.entity.ObservationEntity
import com.floralens.data.db.entity.PlantEntity
import com.floralens.data.photos.PhotoStore
import com.floralens.domain.model.ObservationType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Marlowe & friends — debug builds only. Seeds the collection so screenshots
 * and the demo GIF look alive. Call from FloraLensApp when BuildConfig.DEBUG.
 *
 * Demo photos live in the debug source set (src/debug/assets/demo) and are
 * copied through PhotoStore at seed time — release builds contain neither
 * the images nor this call, and start empty.
 *
 * Three of the six get IDENTIFIED observations with coordinates (map pins);
 * the rest get located-less ones — mirroring the "location is optional" story.
 * Coordinates are a fictional neighborhood spread (~2 km), not anyone's home.
 */
@Singleton
class DemoSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val plantDao: PlantDao,
    private val observationDao: ObservationDao,
    private val photoStore: PhotoStore,
) {
    suspend fun seedIfEmpty() {
        if (plantDao.count() > 0) return
        val now = Instant.now()

        seed("Marlowe", "Monstera deliciosa", "Swiss cheese plant", 7,
            lastWatered = now.minus(7, ChronoUnit.DAYS),
            identifiedDaysAgo = 40, asset = "marlowe.jpg",
            lat = 18.9582, lng = 72.8321)                                  // water today, pinned
        seed("Percy", "Epipremnum aureum", "Pothos", 8,
            lastWatered = now.minus(4, ChronoUnit.DAYS),
            identifiedDaysAgo = 32, asset = "percy.jpg",
            lat = 18.9640, lng = 72.8258)                                  // healthy, pinned
        seed("Finn", "Sansevieria trifasciata", "Snake plant", 14,
            lastWatered = now.minus(17, ChronoUnit.DAYS),
            identifiedDaysAgo = 25, asset = "finn.jpg",
            lat = 18.9515, lng = 72.8410)                                  // overdue 3 days, pinned
        seed("Delphine", "Ficus lyrata", "Fiddle-leaf fig", 7,
            lastWatered = now.minus(1, ChronoUnit.DAYS),
            identifiedDaysAgo = 18, asset = "delphine.jpg")                // healthy, no pin
        seed("Iris", "Spathiphyllum", "Peace lily", 5,
            lastWatered = now.minus(5, ChronoUnit.DAYS),
            identifiedDaysAgo = 12, asset = "iris.jpg")                    // water today, no pin
        seed("Bijou", "Senecio rowleyanus", "String of pearls", 12,
            lastWatered = now.minus(6, ChronoUnit.DAYS),
            identifiedDaysAgo = 6, asset = "bijou.jpg")                    // healthy, no pin
    }

    private suspend fun seed(
        nickname: String,
        scientific: String,
        common: String,
        interval: Int,
        lastWatered: Instant,
        identifiedDaysAgo: Long,
        asset: String,
        lat: Double? = null,
        lng: Double? = null,
    ) {
        val photoUri = copyDemoAsset(asset)

        val plantId = plantDao.upsert(
            PlantEntity(
                nickname = nickname,
                scientificName = scientific,
                commonName = common,
                photoUri = photoUri,
                waterIntervalDays = interval,
                lastWateredAtEpochMs = lastWatered.toEpochMilli(),
                remindersEnabled = true,
                createdAtEpochMs = Instant.now().toEpochMilli(),
            ),
        )
        observationDao.insert(
            ObservationEntity(
                plantId = plantId,
                type = ObservationType.IDENTIFIED.name,
                note = null,
                photoUri = photoUri,
                latitude = lat,
                longitude = lng,
                timestampEpochMs = Instant.now().minus(identifiedDaysAgo, ChronoUnit.DAYS).toEpochMilli(),
            ),
        )
    }

    /**
     * Copies a bundled demo image through PhotoStore so seeded plants carry
     * real photo URIs — exactly the path user captures take. Returns null
     * (placeholder tile) if the asset is missing rather than failing the seed.
     */
    private suspend fun copyDemoAsset(fileName: String): String? = runCatching {
        context.assets.open("demo/$fileName").use { it.readBytes() }
    }.getOrNull()?.let { bytes -> photoStore.save(bytes) }
}
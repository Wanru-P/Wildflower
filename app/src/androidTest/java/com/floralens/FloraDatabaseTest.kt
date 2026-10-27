package com.floralens

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.floralens.data.db.FloraDatabase
import com.floralens.data.db.entity.ObservationEntity
import com.floralens.data.db.entity.PlantEntity
import com.floralens.domain.model.ObservationType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * DAO tests against a real in-memory Room database — the SQL, entity
 * mappings, and Flow queries, not fakes. Runs on device/emulator.
 */
@RunWith(AndroidJUnit4::class)
class FloraDatabaseTest {

    private lateinit var db: FloraDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FloraDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun plant(nickname: String) = PlantEntity(
        nickname = nickname,
        scientificName = "Testus plantus",
        commonName = nickname,
        photoUri = null,
        waterIntervalDays = 7,
        lastWateredAtEpochMs = null,
        remindersEnabled = true,
        createdAtEpochMs = Instant.now().toEpochMilli(),
    )

    private fun observation(
        plantId: Long,
        type: ObservationType,
        lat: Double? = null,
        lng: Double? = null,
    ) = ObservationEntity(
        plantId = plantId,
        type = type.name,
        note = null,
        photoUri = null,
        latitude = lat,
        longitude = lng,
        timestampEpochMs = Instant.now().toEpochMilli(),
    )

    @Test
    fun upsertAndObserveAll_roundTripsPlants() = runBlocking {
        db.plantDao().upsert(plant("Marlowe"))
        db.plantDao().upsert(plant("Percy"))

        val all = db.plantDao().observeAll().first()
        assertEquals(2, all.size)
        assertEquals(setOf("Marlowe", "Percy"), all.map { it.nickname }.toSet())
    }

    @Test
    fun setLastWatered_updatesOnlyTargetPlant() = runBlocking {
        val marloweId = db.plantDao().upsert(plant("Marlowe"))
        val percyId = db.plantDao().upsert(plant("Percy"))
        val wateredAt = Instant.parse("2026-07-10T08:00:00Z").toEpochMilli()

        db.plantDao().setLastWatered(marloweId, wateredAt)

        val all = db.plantDao().observeAll().first().associateBy { it.id }
        assertEquals(wateredAt, all.getValue(marloweId).lastWateredAtEpochMs)
        assertNull(all.getValue(percyId).lastWateredAtEpochMs)
    }

    @Test
    fun observeWithLocation_returnsOnlyLocatedObservations() = runBlocking {
        val id = db.plantDao().upsert(plant("Marlowe"))
        db.observationDao().insert(observation(id, ObservationType.IDENTIFIED, lat = 18.95, lng = 72.83))
        db.observationDao().insert(observation(id, ObservationType.IDENTIFIED)) // no coords
        db.observationDao().insert(observation(id, ObservationType.WATERED, lat = 18.96, lng = 72.82))

        val located = db.observationDao().observeWithLocation().first()
        assertEquals(2, located.size)
        assertTrue(located.all { it.latitude != null && it.longitude != null })
    }

    @Test
    fun observeLeafChecks_filtersByType() = runBlocking {
        val id = db.plantDao().upsert(plant("Marlowe"))
        db.observationDao().insert(observation(id, ObservationType.LEAF_CHECK))
        db.observationDao().insert(observation(id, ObservationType.WATERED))
        db.observationDao().insert(observation(id, ObservationType.LEAF_CHECK))

        val checks = db.observationDao().observeLeafChecks().first()
        assertEquals(2, checks.size)
        assertTrue(checks.all { it.type == ObservationType.LEAF_CHECK.name })
    }

    @Test
    fun observeForPlant_isolatesPlants() = runBlocking {
        val marloweId = db.plantDao().upsert(plant("Marlowe"))
        val percyId = db.plantDao().upsert(plant("Percy"))
        db.observationDao().insert(observation(marloweId, ObservationType.IDENTIFIED))
        db.observationDao().insert(observation(marloweId, ObservationType.WATERED))
        db.observationDao().insert(observation(percyId, ObservationType.IDENTIFIED))

        assertEquals(2, db.observationDao().observeForPlant(marloweId).first().size)
        assertEquals(1, db.observationDao().observeForPlant(percyId).first().size)
    }
}
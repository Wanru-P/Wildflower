package com.floralens

import app.cash.turbine.test
import com.floralens.data.db.PlantDao
import com.floralens.data.db.PlantInfoDao
import com.floralens.data.db.entity.PlantEntity
import com.floralens.data.db.entity.PlantInfoEntity
import com.floralens.data.repo.PlantInfoRepository
import com.floralens.data.repo.PlantRepository
import com.floralens.data.settings.SettingsProvider
import com.floralens.domain.model.CareStatus
import com.floralens.domain.model.IdentificationMode
import com.floralens.domain.usecase.ComputeCareStatus
import com.floralens.ui.screens.plants.MyPlantsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * My Plants grid state: Room flow -> PlantWithStatus list, statuses derived
 * through ComputeCareStatus with a pinned clock, reactive updates on insert,
 * and the offline info-availability chip (infoSaved) joined from the
 * plant_info store.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MyPlantsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val zone: ZoneId = ZoneId.of("UTC")

    /** "Today" is pinned to 2026-07-07 for every status assertion below. */
    private val clock: Clock = Clock.fixed(Instant.parse("2026-07-07T10:00:00Z"), zone)

    private class FakePlantDao : PlantDao {
        private val state = MutableStateFlow<List<PlantEntity>>(emptyList())
        private var nextId = 1L

        override fun observeAll(): Flow<List<PlantEntity>> = state
        override fun observeById(id: Long): Flow<PlantEntity?> =
            state.map { list -> list.find { it.id == id } }

        override suspend fun upsert(plant: PlantEntity): Long {
            val id = if (plant.id != 0L) plant.id else nextId++
            state.value = state.value.filterNot { it.id == id } + plant.copy(id = id)
            return id
        }

        override suspend fun setLastWatered(id: Long, epochMs: Long) {
            state.value = state.value.map { if (it.id == id) it.copy(lastWateredAtEpochMs = epochMs) else it }
        }

        override suspend fun setRemindersEnabled(id: Long, enabled: Boolean) {
            state.value = state.value.map { if (it.id == id) it.copy(remindersEnabled = enabled) else it }
        }

        override suspend fun delete(id: Long) {
            state.value = state.value.filterNot { it.id == id }
        }

        override suspend fun count(): Int = state.value.size
    }

    /** In-memory plant_info store — same Flow-backed pattern as FakePlantDao. */
    private class FakePlantInfoDao : PlantInfoDao {
        private val state = MutableStateFlow<List<PlantInfoEntity>>(emptyList())

        override suspend fun upsert(info: PlantInfoEntity) {
            state.value = state.value.filterNot { it.scientificName == info.scientificName } + info
        }

        override suspend fun get(scientificName: String): PlantInfoEntity? =
            state.value.find { it.scientificName == scientificName }

        override fun observeSavedSpecies(): Flow<List<String>> =
            state.map { list -> list.map { it.scientificName } }

        override fun observeIsSaved(scientificName: String): Flow<Boolean> =
            state.map { list -> list.any { it.scientificName == scientificName } }

        override suspend fun delete(scientificName: String) {
            state.value = state.value.filterNot { it.scientificName == scientificName }
        }
    }

    private class FakeSettings(
        mode: IdentificationMode = IdentificationMode.OFFLINE,
    ) : SettingsProvider {
        override val identificationMode = MutableStateFlow(mode)
    }

    private lateinit var dao: FakePlantDao
    private lateinit var infoDao: FakePlantInfoDao

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        dao = FakePlantDao()
        infoDao = FakePlantInfoDao()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(mode: IdentificationMode = IdentificationMode.OFFLINE,
        observations: List<com.floralens.domain.model.Observation> = emptyList(),
        saved: androidx.lifecycle.SavedStateHandle = androidx.lifecycle.SavedStateHandle()) = MyPlantsViewModel(
        plantRepository = PlantRepository(dao),
        plantInfoRepository = PlantInfoRepository(infoDao),
        settings = FakeSettings(mode),
        computeCareStatus = ComputeCareStatus(clock),
        seasons = object : com.floralens.data.season.SeasonSource {
            override fun observe() = PlantRepository(dao).observeAll().map { com.floralens.domain.season.SeasonArchive.build(it, observations, zone) }
        },
        saved = saved,
    )

    private fun entity(nickname: String, intervalDays: Int, lastWatered: String?) = PlantEntity(
        nickname = nickname,
        scientificName = "Testus plantus",
        commonName = nickname,
        photoUri = null,
        waterIntervalDays = intervalDays,
        lastWateredAtEpochMs = lastWatered?.let { Instant.parse(it).toEpochMilli() },
        remindersEnabled = true,
        createdAtEpochMs = Instant.parse("2026-07-01T00:00:00Z").toEpochMilli(),
    )

    @Test
    fun `empty collection emits loaded empty state`() = runTest(dispatcher) {
        viewModel().uiState.test {
            assertTrue(awaitItem().isLoading) // initial

            val loaded = awaitItem()
            assertFalse(loaded.isLoading)
            assertTrue(loaded.plants.isEmpty())
        }
    }

    @Test fun `grid date follows natural observations and selected season not collection or watering`() = runTest(dispatcher) {
        val id = dao.upsert(entity("参考档案", 7, null))
        dao.upsert(entity("无观察档案", 7, null))
        val spring = Instant.parse("2026-03-12T09:00:00Z")
        val autumn = Instant.parse("2026-10-02T09:00:00Z")
        val records = listOf(
            com.floralens.domain.model.Observation(plantId = id, type = com.floralens.domain.model.ObservationType.PHOTO_OBSERVATION, timestamp = spring),
            com.floralens.domain.model.Observation(plantId = id, type = com.floralens.domain.model.ObservationType.IDENTIFIED, timestamp = autumn),
            com.floralens.domain.model.Observation(plantId = id, type = com.floralens.domain.model.ObservationType.WATERED, timestamp = autumn.plusSeconds(100)),
        )
        val vm = viewModel(observations = records)
        vm.uiState.test {
            awaitItem()
            val all = awaitItem()
            assertEquals(autumn, all.plants.first { it.plant.id == id }.latestObservationAt)
            assertEquals(null, all.plants.first { it.plant.id != id }.latestObservationAt)
            vm.selectSeason(com.floralens.domain.season.Season.SPRING)
            val filtered = awaitItem()
            assertEquals(1, filtered.plants.size)
            assertEquals(spring, filtered.plants.single().latestObservationAt)
            assertEquals(2, filtered.totalPlants)
        }
    }

    @Test
    fun `statuses are derived from lastWateredAt and interval with pinned clock`() = runTest(dispatcher) {
        // clock "today" = 2026-07-07
        dao.upsert(entity("Marlowe", 7, "2026-06-30T09:00:00Z"))  // due 07-07 -> DUE_TODAY
        dao.upsert(entity("Finn", 14, "2026-06-20T09:00:00Z"))    // due 07-04 -> OVERDUE
        dao.upsert(entity("Percy", 7, "2026-07-06T09:00:00Z"))    // due 07-13 -> THIS_WEEK
        dao.upsert(entity("Bijou", 30, "2026-07-06T09:00:00Z"))   // due 08-05 -> HEALTHY
        dao.upsert(entity("Iris", 7, null))                        // never watered -> DUE_TODAY

        viewModel().uiState.test {
            awaitItem() // initial loading
            val loaded = awaitItem()

            fun statusOf(name: String) = loaded.plants.first { it.plant.nickname == name }.status
            assertEquals(CareStatus.DUE_TODAY, statusOf("Marlowe"))
            assertEquals(CareStatus.OVERDUE, statusOf("Finn"))
            assertEquals(CareStatus.THIS_WEEK, statusOf("Percy"))
            assertEquals(CareStatus.HEALTHY, statusOf("Bijou"))
            assertEquals(CareStatus.DUE_TODAY, statusOf("Iris"))
        }
    }

    @Test
    fun `grid updates reactively when a plant is added`() = runTest(dispatcher) {
        viewModel().uiState.test {
            awaitItem() // initial loading
            assertTrue(awaitItem().plants.isEmpty()) // loaded, empty

            dao.upsert(entity("Marlowe", 7, "2026-06-30T09:00:00Z"))

            val updated = awaitItem()
            assertEquals(1, updated.plants.size)
            assertEquals("Marlowe", updated.plants.first().plant.nickname)
        }
    }

    @Test
    fun `infoSaved reflects the plant_info store and offlineMode follows the setting`() = runTest(dispatcher) {
        dao.upsert(entity("Marlowe", 7, "2026-06-30T09:00:00Z")) // Testus plantus

        viewModel(mode = IdentificationMode.OFFLINE).uiState.test {
            awaitItem() // initial loading
            val loaded = awaitItem()
            assertTrue(loaded.offlineMode)
            assertFalse(loaded.plants.first().infoSaved) // nothing saved yet

            // Saving details for the species flips the chip reactively.
            infoDao.upsert(
                PlantInfoEntity(
                    scientificName = "Testus plantus",
                    commonName = "Marlowe",
                    insightsJson = "{}",
                    geoPointsJson = "[]",
                    occurrenceJson = null,
                    savedAtEpochMs = 0L,
                ),
            )

            val updated = awaitItem()
            assertTrue(updated.plants.first().infoSaved)
        }
    }
}

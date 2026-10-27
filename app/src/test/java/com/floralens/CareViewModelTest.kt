package com.floralens

import app.cash.turbine.test
import com.floralens.data.db.ObservationDao
import com.floralens.data.db.PlantDao
import com.floralens.data.db.entity.ObservationEntity
import com.floralens.data.db.entity.PlantEntity
import com.floralens.data.repo.ObservationRepository
import com.floralens.data.repo.PlantRepository
import com.floralens.domain.model.ObservationType
import com.floralens.domain.usecase.ComputeCareStatus
import com.floralens.domain.usecase.MarkWatered
import com.floralens.ui.screens.care.CareViewModel
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
 * Care dashboard state: sectioning into overdue/today/thisWeek, human due-text
 * ("Overdue by 3 days" / "Due tomorrow"), the all-caught-up flag, and the full
 * mark-watered round trip (row leaves the dashboard + WATERED observation appended).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CareViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val zone: ZoneId = ZoneId.of("UTC")

    /** "Today" is pinned to 2026-07-07 for every assertion below. */
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

    private class FakeObservationDao : ObservationDao {
        override fun observeNatural(): Flow<List<ObservationEntity>> = state.map { list -> list.filter { it.type in setOf("IDENTIFIED", "PHOTO_OBSERVATION") } }
        val inserted = mutableListOf<ObservationEntity>()
        private val state = MutableStateFlow<List<ObservationEntity>>(emptyList())
        private var nextId = 1L

        override suspend fun insert(observation: ObservationEntity): Long {
            val id = nextId++
            val row = observation.copy(id = id)
            inserted += row
            state.value = state.value + row
            return id
        }

        override fun observeForPlant(plantId: Long): Flow<List<ObservationEntity>> =
            state.map { list -> list.filter { it.plantId == plantId } }

        override fun observeWithLocation(): Flow<List<ObservationEntity>> =
            state.map { list -> list.filter { it.latitude != null && it.longitude != null } }

        override fun observeLeafChecks(): Flow<List<ObservationEntity>> =
            state.map { list -> list.filter { it.type == ObservationType.LEAF_CHECK.name } }
    }

    private lateinit var plantDao: FakePlantDao
    private lateinit var observationDao: FakeObservationDao

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        plantDao = FakePlantDao()
        observationDao = FakeObservationDao()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): CareViewModel {
        val plants = PlantRepository(plantDao)
        val observations = ObservationRepository(observationDao)
        return CareViewModel(
            plantRepository = plants,
            computeCareStatus = ComputeCareStatus(clock),
            markWatered = MarkWatered(plants, observations),
        )
    }

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
    fun `plants are sectioned by status and healthy plants are excluded`() = runTest(dispatcher) {
        // clock "today" = 2026-07-07
        plantDao.upsert(entity("Finn", 14, "2026-06-20T09:00:00Z"))    // due 07-04 -> overdue
        plantDao.upsert(entity("Marlowe", 7, "2026-06-30T09:00:00Z"))  // due 07-07 -> today
        plantDao.upsert(entity("Percy", 7, "2026-07-06T09:00:00Z"))    // due 07-13 -> this week
        plantDao.upsert(entity("Bijou", 30, "2026-07-06T09:00:00Z"))   // due 08-05 -> healthy, hidden

        viewModel().uiState.test {
            awaitItem() // initial, not loaded
            val loaded = awaitItem()

            assertEquals(listOf("Finn"), loaded.overdue.map { it.plant.nickname })
            assertEquals(listOf("Marlowe"), loaded.today.map { it.plant.nickname })
            assertEquals(listOf("Percy"), loaded.thisWeek.map { it.plant.nickname })
            assertFalse(loaded.allCaughtUp)
        }
    }

    @Test
    fun `due text covers overdue plural, today, tomorrow, and in-n-days`() = runTest(dispatcher) {
        plantDao.upsert(entity("Finn", 14, "2026-06-20T09:00:00Z"))     // due 07-04 -> overdue by 3 days
        plantDao.upsert(entity("Ivy", 7, "2026-06-29T09:00:00Z"))       // due 07-06 -> overdue by 1 day
        plantDao.upsert(entity("Marlowe", 7, "2026-06-30T09:00:00Z"))   // due 07-07 -> water today
        plantDao.upsert(entity("Sage", 8, "2026-06-30T09:00:00Z"))      // due 07-08 -> due tomorrow
        plantDao.upsert(entity("Percy", 7, "2026-07-04T09:00:00Z"))     // due 07-11 -> due in 4 days

        viewModel().uiState.test {
            awaitItem()
            val loaded = awaitItem()

            fun dueTextOf(name: String) =
                (loaded.overdue + loaded.today + loaded.thisWeek).first { it.plant.nickname == name }.dueText

            assertEquals("已到养护时间 3 天", dueTextOf("Finn"))
            assertEquals("已到养护时间 1 天", dueTextOf("Ivy"))
            assertEquals("今天留意浇水", dueTextOf("Marlowe"))
            assertEquals("明天留意浇水", dueTextOf("Sage"))
            assertEquals("4 天后留意浇水", dueTextOf("Percy"))
            assertTrue(loaded.overdue.all { it.urgent })
            assertFalse(loaded.today.any { it.urgent })
        }
    }

    @Test
    fun `all caught up only when loaded and nothing is due`() = runTest(dispatcher) {
        plantDao.upsert(entity("Bijou", 30, "2026-07-06T09:00:00Z")) // healthy

        viewModel().uiState.test {
            val initial = awaitItem()
            assertFalse(initial.allCaughtUp) // not loaded yet — must not flash empty state

            val loaded = awaitItem()
            assertTrue(loaded.allCaughtUp)
        }
    }

    @Test
    fun `mark watered removes plant from dashboard and appends WATERED observation`() = runTest(dispatcher) {
        val finnId = plantDao.upsert(entity("Finn", 14, "2026-06-20T09:00:00Z")) // overdue

        val vm = viewModel()
        vm.uiState.test {
            awaitItem() // initial
            assertEquals(1, awaitItem().overdue.size)

            vm.onMarkWatered(finnId)

            val updated = awaitItem()
            assertTrue(updated.overdue.isEmpty())
            assertTrue(updated.allCaughtUp) // watered now -> due in 14 days -> healthy, hidden

            assertEquals(1, observationDao.inserted.size)
            val obs = observationDao.inserted.single()
            assertEquals(finnId, obs.plantId)
            assertEquals(ObservationType.WATERED.name, obs.type)
        }
    }
}

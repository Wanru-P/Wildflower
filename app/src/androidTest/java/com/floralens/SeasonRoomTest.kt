package com.floralens

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.data.db.FloraDatabase
import com.floralens.data.db.entity.toEntity
import com.floralens.data.repo.PlantRepository
import com.floralens.data.season.SeasonRepository
import com.floralens.domain.model.*
import com.floralens.domain.season.Season
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SeasonRoomTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun time(date: String) = LocalDateTime.parse(date).atZone(ZoneId.systemDefault()).toInstant()
    @Test fun naturalQueryPreservesCareAndCountsMissingPhotos() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context,FloraDatabase::class.java).build()
        try {
            val repo = PlantRepository(db.plantDao())
            val id = repo.upsert(Plant(nickname="Debug · 无照片测试",scientificName="Ginkgo biloba",commonName="银杏",waterIntervalDays=7))
            ObservationType.entries.forEach { type -> db.observationDao().insert(Observation(plantId=id,type=type,timestamp=time("2026-04-15T12:00:00")).toEntity()) }
            val archive = withTimeout(5000) { SeasonRepository(repo,db.observationDao(),context).observe().first() }
            assertEquals(2,archive.records.size); assertEquals(2,archive.observations(2026,Season.SPRING).size)
            assertEquals(1,archive.garden(Season.SPRING).size); assertNull(archive.garden(Season.SPRING).single().photoUri)
            assertEquals(5,db.archiveDao().history(id).size); assertEquals(3,db.openHelper.readableDatabase.version)
        } finally { db.close() }
    }
    @Test fun flowChangesAfterDateEditDeletionAndSameSeasonDedup() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context,FloraDatabase::class.java).build()
        try {
            val repo = PlantRepository(db.plantDao()); val source = SeasonRepository(repo,db.observationDao(),context)
            val id = repo.upsert(Plant(nickname="Debug · 跨季节",scientificName="Ginkgo biloba",commonName="银杏",waterIntervalDays=7))
            val a = db.observationDao().insert(Observation(plantId=id,type=ObservationType.PHOTO_OBSERVATION,note="原备注",timestamp=time("2026-04-15T12:00:00")).toEntity())
            val b = db.observationDao().insert(Observation(plantId=id,type=ObservationType.IDENTIFIED,timestamp=time("2026-05-15T12:00:00")).toEntity())
            val current = MutableStateFlow<com.floralens.domain.season.SeasonArchive?>(null)
            val job = launch(Dispatchers.IO) { source.observe().collect { current.value = it } }
            try {
                val initial = withTimeout(5000) { current.filterNotNull().first { it.records.size == 2 } }
                assertEquals(1,initial.garden(Season.SPRING).size)
                db.archiveDao().edit(a,time("2026-10-15T12:00:00").toEpochMilli(),"原备注")
                val edited = withTimeout(5000) { current.filterNotNull().first { it.garden(Season.AUTUMN).size == 1 } }
                assertEquals(1,edited.observations(2026,Season.SPRING).size); assertEquals("原备注",edited.observations(2026,Season.AUTUMN).single().observation.note)
                db.archiveDao().deleteObservation(b)
                val deleted = withTimeout(5000) { current.filterNotNull().first { it.records.size == 1 } }
                assertTrue(deleted.garden(Season.SPRING).isEmpty()); assertEquals(1,deleted.garden(null).size)
            } finally { job.cancelAndJoin() }
        } finally { db.close() }
    }
}

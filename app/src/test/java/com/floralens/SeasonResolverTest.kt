package com.floralens

import com.floralens.domain.model.*
import com.floralens.domain.season.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.io.File

class SeasonResolverTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val plant = Plant(id = 1, nickname = "测试银杏", scientificName = "Ginkgo biloba", commonName = "银杏", waterIntervalDays = 7)
    private fun observation(id: Long, month: Int, type: ObservationType = ObservationType.PHOTO_OBSERVATION, photo: String? = null) =
        Observation(id, 1, type, photoUri = photo, timestamp = Instant.parse("2026-${month.toString().padStart(2,'0')}-15T12:00:00Z"))
    @Test fun allMonthBoundaries() {
        assertEquals(listOf(Season.WINTER, Season.WINTER, Season.SPRING, Season.SPRING, Season.SPRING, Season.SUMMER,
            Season.SUMMER, Season.SUMMER, Season.AUTUMN, Season.AUTUMN, Season.AUTUMN, Season.WINTER), (1..12).map(SeasonResolver::month))
    }
    @Test fun invalidMonthRejected() { assertThrows(IllegalArgumentException::class.java) { SeasonResolver.month(0) }; assertThrows(IllegalArgumentException::class.java) { SeasonResolver.month(13) } }
    @Test fun winterIsCalendarYearNotCombinedWinter() {
        assertEquals(SeasonPeriod(2026, Season.WINTER), SeasonResolver.period(Instant.parse("2026-12-15T00:00:00Z"), zone))
        assertEquals(SeasonPeriod(2027, Season.WINTER), SeasonResolver.period(Instant.parse("2027-01-15T00:00:00Z"), zone))
    }
    @Test fun leapDayIsWinter() { assertEquals(SeasonPeriod(2024, Season.WINTER), SeasonResolver.period(Instant.parse("2024-02-29T12:00:00Z"), zone)) }
    @Test fun zoneChangesCanChangeSeasonAndYear() {
        val time = Instant.parse("2027-01-01T00:30:00Z")
        assertEquals(2027, SeasonResolver.period(time, zone).year)
        assertEquals(2026, SeasonResolver.period(time, ZoneId.of("America/Los_Angeles")).year)
        val boundary = Instant.parse("2026-03-01T00:30:00Z")
        assertEquals(Season.SPRING, SeasonResolver.period(boundary, zone).season)
        assertEquals(Season.WINTER, SeasonResolver.period(boundary, ZoneId.of("America/Los_Angeles")).season)
    }
    @Test fun samePlantAcrossSeasonsAndDeduplicatedWithinSeason() {
        val archive = SeasonArchive.build(listOf(plant), listOf(observation(1,3), observation(2,4), observation(3,10)), zone)
        assertEquals(2, archive.observations(2026,Season.SPRING).size)
        assertEquals(1, archive.garden(Season.SPRING).size); assertEquals(1, archive.garden(Season.AUTUMN).size)
    }
    @Test fun emptyPlantStaysInAllOnly() {
        val archive = SeasonArchive.build(listOf(plant), emptyList(), zone)
        assertEquals(listOf(plant), archive.garden(null)); assertTrue(archive.garden(Season.SPRING).isEmpty()); assertTrue(archive.years.isEmpty())
    }
    @Test fun careAndNotesExcludedMissingPhotoStillCounted() {
        val archive = SeasonArchive.build(listOf(plant), ObservationType.entries.mapIndexed { i, type -> observation(i.toLong(),3,type) }, zone)
        assertEquals(2, archive.records.size); assertEquals(1, archive.garden(Season.SPRING).size); assertNull(archive.garden(Season.SPRING).single().photoUri)
    }
    @Test fun editAndDeleteRecalculateWithoutStoredSeason() {
        val a = observation(1,3); val b = observation(2,10)
        val moved = a.copy(timestamp = observation(3,6).timestamp)
        assertTrue(SeasonArchive.build(listOf(plant), listOf(moved,b),zone).garden(Season.SPRING).isEmpty())
        assertEquals(1, SeasonArchive.build(listOf(plant), listOf(moved,b),zone).garden(Season.SUMMER).size)
        assertTrue(SeasonArchive.build(listOf(plant),listOf(moved),zone).garden(Season.AUTUMN).isEmpty())
    }
    @Test fun seasonalCoverUsesLatestExistingPhotoNotGlobalOrMissing() {
        val file = File.createTempFile("season-test-", ".jpg")
        try {
            val old = observation(1,3,photo=file.toURI().toString()); val missing = observation(2,4,photo="file:///missing.jpg")
            val autumn = observation(3,10,photo="file:///global.jpg")
            val archive = SeasonArchive.build(listOf(plant.copy(photoUri = autumn.photoUri)),listOf(old,missing,autumn),zone)
            assertEquals(old.photoUri, archive.garden(Season.SPRING).single().photoUri)
            assertNull(archive.garden(Season.AUTUMN).single().photoUri)
            assertEquals(autumn.photoUri, archive.garden(null).single().photoUri)
        } finally { file.delete() }
    }
    @Test fun unknownYearAndOrphanRecordsStayEmpty() {
        val archive = SeasonArchive.build(listOf(plant),listOf(observation(1,3),observation(2,10).copy(plantId=99)),zone)
        assertTrue(archive.observations(1999,Season.SPRING).isEmpty()); assertEquals(1, archive.records.size)
    }
}

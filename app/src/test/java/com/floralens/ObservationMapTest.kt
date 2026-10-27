package com.floralens

import com.floralens.domain.model.*
import com.floralens.ui.screens.map.ObservationMapProjection
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ObservationMapTest {
    private fun plant(id: Long, name: String = "同名档案") = Plant(id, name, "Ginkgo biloba", "银杏", "cover-$id", 7)
    private fun observation(id: Long, owner: Long = 1, lat: Double? = 31.2, lon: Double? = 121.4) =
        Observation(id, owner, ObservationType.PHOTO_OBSERVATION, "原始备注-$id", "observation-$id", lat, lon, Instant.ofEpochSecond(id))

    @Test fun nullPairsAreNotLocations() {
        assertFalse(ObservationLocation.valid(null, null))
        assertFalse(ObservationLocation.valid(1.0, null))
        assertFalse(ObservationLocation.valid(null, 1.0))
    }
    @Test fun finiteBoundsInclusiveAndZeroIsNotInvented() {
        listOf(-90.0 to -180.0, 90.0 to 180.0, 0.0 to 0.0).forEach { assertTrue(ObservationLocation.valid(it.first, it.second)) }
        listOf(-90.01 to 0.0, 90.01 to 0.0, 0.0 to -180.01, 0.0 to 180.01).forEach { assertFalse(ObservationLocation.valid(it.first, it.second)) }
    }
    @Test fun nonFiniteValuesAreAlwaysRejected() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
            assertFalse(ObservationLocation.valid(it, 1.0)); assertFalse(ObservationLocation.valid(1.0, it))
        }
    }
    @Test fun swappedCoordinatesAreNeverGuessedOrReplaced() {
        assertFalse(ObservationLocation.valid(121.4, 31.2))
        val input = observation(1, lat = 40.0, lon = 30.0)
        val pin = ObservationMapProjection.join(listOf(input), listOf(plant(1))).pins.single()
        assertEquals(40.0, pin.latitude, 0.0); assertEquals(30.0, pin.longitude, 0.0)
    }
    @Test fun sameSpeciesAndSameNicknameStillJoinById() {
        val result = ObservationMapProjection.join(listOf(observation(1), observation(2, 2)), listOf(plant(1), plant(2)))
        assertEquals(listOf(2L, 1L), result.pins.map { it.plant.id })
    }
    @Test fun eachObservationUsesItsOwnPhotoNoteAndDateNotCover() {
        val original = observation(2)
        val pin = ObservationMapProjection.join(listOf(original), listOf(plant(1))).pins.single()
        assertEquals(original.photoUri, pin.displayPhotoUri); assertEquals(original.note, pin.note)
        assertEquals(original.timestamp, pin.timestamp)
    }
    @Test fun renameAndCoverChangeDoNotChangeObservationIdentity() {
        val events = listOf(observation(1), observation(2))
        val a = ObservationMapProjection.join(events, listOf(plant(1)))
        val b = ObservationMapProjection.join(events, listOf(plant(1).copy(nickname = "复访", photoUri = "new-cover")))
        assertEquals(a.pins.map { it.observationId }, b.pins.map { it.observationId })
        assertEquals(a.pins.map { it.displayPhotoUri }, b.pins.map { it.displayPhotoUri })
        assertTrue(b.pins.all { it.plant.nickname == "复访" })
    }
    @Test fun orphanAndNoLocationAreIgnoredWhileInvalidCountIsHonest() {
        val events = listOf(observation(1, 999), observation(2, lat = null), observation(3, lat = Double.NaN), observation(4, lat = 91.0), observation(5))
        val copy = events.toList()
        val result = ObservationMapProjection.join(events, listOf(plant(1)))
        assertEquals(listOf(5L), result.pins.map { it.observationId }); assertEquals(2, result.invalidLocations)
        assertEquals(copy, events)
    }
    @Test fun identicalAndNearbyCoordinatesRemainIndividuallySelectable() {
        val result = ObservationMapProjection.join(listOf(observation(1), observation(2, 2), observation(3, lat = 31.2001), observation(4, lat = 32.0)), listOf(plant(1), plant(2)))
        assertEquals(setOf(1L, 2L, 3L), ObservationMapProjection.nearby(result.pins.single { it.observationId == 1L }, result.pins).map { it.observationId }.toSet())
        assertEquals(4, result.pins.size)
    }
    @Test fun editDeleteAndMissingPlantUpdateWithoutStalePins() {
        val first = observation(1)
        val moved = first.copy(latitude = 30.0, longitude = 120.0, note = "复访", timestamp = Instant.ofEpochSecond(99))
        val after = ObservationMapProjection.join(listOf(moved), listOf(plant(1))).pins.single()
        assertEquals(30.0, after.latitude, 0.0); assertEquals("复访", after.note)
        assertTrue(ObservationMapProjection.join(emptyList(), listOf(plant(1))).pins.isEmpty())
        assertTrue(ObservationMapProjection.join(listOf(first), emptyList()).pins.isEmpty())
    }
    @Test fun orderIsDeterministicForSameTimestamp() {
        val result = ObservationMapProjection.join(listOf(observation(1), observation(2).copy(timestamp = Instant.ofEpochSecond(1))), listOf(plant(1)))
        assertEquals(listOf(2L, 1L), result.pins.map { it.observationId })
    }
    @Test fun nullObservationPhotoFallsBackWithoutRewritingHistory() {
        val input = observation(1).copy(photoUri = null)
        assertEquals("cover-1", ObservationMapProjection.join(listOf(input), listOf(plant(1))).pins.single().displayPhotoUri)
        assertNull(input.photoUri)
    }
    @Test fun mixedCoordinateSystemsUseDisplayPositionsForNearbySelection() {
        val pins = ObservationMapProjection.join(listOf(observation(1), observation(2, lat = 31.8)), listOf(plant(1))).pins
        val projected = pins.associate { it.observationId to com.floralens.domain.model.DisplayCoordinate(31.2, 121.4) }
        assertEquals(2, ObservationMapProjection.nearby(pins.first(), pins, projected).size)
        assertEquals(1, ObservationMapProjection.nearby(pins.first(), pins, emptyMap()).size)
        assertEquals(31.8, pins.first { it.observationId == 2L }.latitude, 0.0)
    }
}

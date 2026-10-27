package com.floralens

import com.floralens.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class MapCoordinatePolicyTest {
    private val converterCalls = mutableListOf<CoordinateSystem>()
    private fun project(lat: Double? = 31.2, lon: Double? = 121.4,
        source: CoordinateSystem = CoordinateSystem.WGS84, region: CoordinateRegion = CoordinateRegion.DOMESTIC,
        fail: Boolean = false) = MapCoordinatePolicy.project(lat, lon, source, region) { a, b, s ->
        converterCalls += s; if (fail) throw IllegalStateException("conversion unavailable")
        DisplayCoordinate(a + .001, b + .002)
    }
    @Test fun domesticWgsUsesGpsConversion() { val result = project()!!; assertEquals(31.201, result.latitude, 1e-9); assertEquals(121.402, result.longitude, 1e-9); assertEquals(listOf(CoordinateSystem.WGS84), converterCalls) }
    @Test fun domesticGcjUsesCommonConversion() { project(source = CoordinateSystem.GCJ02); assertEquals(listOf(CoordinateSystem.GCJ02), converterCalls) }
    @Test fun bd09IsNotConvertedAgain() { assertEquals(DisplayCoordinate(31.2, 121.4), project(source = CoordinateSystem.BD09LL)); assertTrue(converterCalls.isEmpty()) }
    @Test fun overseasWgsIsUnchanged() { assertEquals(DisplayCoordinate(37.5, 127.0), project(37.5, 127.0, region = CoordinateRegion.OVERSEAS)); assertTrue(converterCalls.isEmpty()) }
    @Test fun unknownHistoryNeverAssumedWgs() { assertNull(project(source = CoordinateSystem.UNKNOWN)); assertTrue(converterCalls.isEmpty()) }
    @Test fun uncertainRegionIsWithheld() { assertNull(project(region = CoordinateRegion.UNCERTAIN)); assertTrue(converterCalls.isEmpty()) }
    @Test fun missingAndInvalidAndSwappedValuesAreRejected() {
        for ((a,b) in listOf(null to null, 121.4 to 31.2, Double.NaN to 121.4, 31.2 to Double.POSITIVE_INFINITY, 31.2 to 181.0)) assertNull(project(a,b))
        assertTrue(converterCalls.isEmpty())
    }
    @Test fun failureNeverFallsBackToWrongRawDomesticPosition() { assertNull(project(fail = true)) }
    @Test fun gcjClaimOutsideCoverageIsNotAssumedWgs() { assertNull(project(source = CoordinateSystem.GCJ02, region = CoordinateRegion.OVERSEAS)) }
    @Test fun projectionDoesNotModifyRawObservation() {
        val input = Observation(plantId = 3, type = ObservationType.PHOTO_OBSERVATION, latitude = 31.2, longitude = 121.4)
        val copy = input.copy(); project(input.latitude, input.longitude); assertEquals(copy, input)
    }
}

package com.floralens

import com.floralens.data.explore.seasonKey
import com.floralens.data.explore.seasonLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class SeasonPresentationTest {
    @Test fun seasons_followCalendarMonths_notSpeciesBloom() {
        assertEquals("winter", seasonKey(1))
        assertEquals("spring", seasonKey(3))
        assertEquals("summer", seasonKey(6))
        assertEquals("autumn", seasonKey(9))
        assertEquals("winter", seasonKey(12))
    }
    @Test fun chineseLabels_coverAllTwelveMonths() {
        assertEquals(listOf("冬日", "冬日", "春日", "春日", "春日", "夏日", "夏日", "夏日", "秋日", "秋日", "秋日", "冬日"),
            (1..12).map(::seasonLabel))
    }
}

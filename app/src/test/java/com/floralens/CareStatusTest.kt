package com.floralens

import com.floralens.domain.model.CareStatus
import com.floralens.domain.model.Plant
import com.floralens.domain.usecase.ComputeCareStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The unit-test showcase — pure derivation, pinned clock.
 * Edge cases per README: month boundaries, never-watered plants, interval changes.
 */
class CareStatusTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private fun clockAt(iso: String) = Clock.fixed(Instant.parse(iso), zone)
    private fun plant(lastWatered: String?, intervalDays: Int) = Plant(
        nickname = "Marlowe",
        scientificName = "Monstera deliciosa",
        commonName = "Swiss cheese plant",
        waterIntervalDays = intervalDays,
        lastWateredAt = lastWatered?.let(Instant::parse),
    )

    @Test
    fun `never watered is due today`() {
        val compute = ComputeCareStatus(clockAt("2026-07-05T10:00:00Z"))
        assertEquals(CareStatus.DUE_TODAY, compute(plant(null, 7), zone))
    }

    @Test
    fun `due date today is due today`() {
        val compute = ComputeCareStatus(clockAt("2026-07-08T10:00:00Z"))
        assertEquals(CareStatus.DUE_TODAY, compute(plant("2026-07-01T09:00:00Z", 7), zone))
    }

    @Test
    fun `past due date is overdue`() {
        val compute = ComputeCareStatus(clockAt("2026-07-11T10:00:00Z"))
        assertEquals(CareStatus.OVERDUE, compute(plant("2026-07-01T09:00:00Z", 7), zone))
    }

    @Test
    fun `due within seven days is this week`() {
        val compute = ComputeCareStatus(clockAt("2026-07-02T10:00:00Z"))
        assertEquals(CareStatus.THIS_WEEK, compute(plant("2026-07-01T09:00:00Z", 5), zone))
    }

    @Test
    fun `due beyond seven days is healthy`() {
        val compute = ComputeCareStatus(clockAt("2026-07-02T10:00:00Z"))
        assertEquals(CareStatus.HEALTHY, compute(plant("2026-07-01T09:00:00Z", 14), zone))
    }

    @Test
    fun `month boundary - watered end of June due in July`() {
        val compute = ComputeCareStatus(clockAt("2026-07-01T10:00:00Z"))
        // Watered Jun 24, interval 7 → due Jul 1 (crosses the month boundary)
        assertEquals(CareStatus.DUE_TODAY, compute(plant("2026-06-24T09:00:00Z", 7), zone))
    }

    @Test
    fun `interval change re-derives status from same lastWateredAt`() {
        val compute = ComputeCareStatus(clockAt("2026-07-06T10:00:00Z"))
        val watered = "2026-07-01T09:00:00Z"
        // interval 3 → due Jul 4 → overdue; interval 14 → due Jul 15 → healthy
        assertEquals(CareStatus.OVERDUE, compute(plant(watered, 3), zone))
        assertEquals(CareStatus.HEALTHY, compute(plant(watered, 14), zone))
    }
}

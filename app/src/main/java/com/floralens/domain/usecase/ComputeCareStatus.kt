package com.floralens.domain.usecase

import com.floralens.domain.model.CareStatus
import com.floralens.domain.model.Plant
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * The unit-test showcase. Pure derivation from lastWateredAt + intervalDays —
 * never persisted. Clock is injected so tests can pin "today"
 * (month boundaries, never-watered plants, interval changes).
 */
class ComputeCareStatus @Inject constructor(
    private val clock: Clock,
) {
    /**
     * The date watering is next due. A never-watered plant is due "now" —
     * i.e. today's date — which is what drives it into DUE_TODAY below.
     */
    fun dueDate(plant: Plant, zone: ZoneId = clock.zone): LocalDate {
        val lastWatered = plant.lastWateredAt ?: return LocalDate.now(clock)
        return lastWatered.atZone(zone).toLocalDate().plusDays(plant.waterIntervalDays.toLong())
    }

    /**
     * Signed day count to the due date: negative = overdue by N days,
     * zero = due today, positive = due in N days. Drives Care screen copy
     * like "Overdue by 3 days" / "Due in 4 days" without re-deriving dates there.
     */
    fun daysUntilDue(plant: Plant, zone: ZoneId = clock.zone): Long {
        val today = LocalDate.now(clock)
        return ChronoUnit.DAYS.between(today, dueDate(plant, zone))
    }

    operator fun invoke(plant: Plant, zone: ZoneId = clock.zone): CareStatus {
        val today = LocalDate.now(clock)
        val due = dueDate(plant, zone)
        return when {
            due.isBefore(today) -> CareStatus.OVERDUE
            due.isEqual(today) -> CareStatus.DUE_TODAY
            !due.isAfter(today.plusDays(7)) -> CareStatus.THIS_WEEK
            else -> CareStatus.HEALTHY
        }
    }
}

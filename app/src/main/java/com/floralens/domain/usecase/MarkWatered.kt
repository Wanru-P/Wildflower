package com.floralens.domain.usecase

import com.floralens.data.repo.ObservationRepository
import com.floralens.data.repo.PlantRepository
import com.floralens.domain.model.Observation
import com.floralens.domain.model.ObservationType
import java.time.Instant
import javax.inject.Inject

/** Updates lastWateredAt + appends a WATERED row to the Observation event table. */
class MarkWatered @Inject constructor(
    private val plants: PlantRepository,
    private val observations: ObservationRepository,
) {
    suspend operator fun invoke(plantId: Long, at: Instant = Instant.now()) {
        plants.setLastWatered(plantId, at)
        observations.add(Observation(plantId = plantId, type = ObservationType.WATERED, timestamp = at))
    }
}

package com.floralens.domain.usecase

import com.floralens.data.photos.ArchiveService
import com.floralens.domain.model.Species
import javax.inject.Inject

class CollectPlantUseCase @Inject constructor(private val archive: ArchiveService) {
    suspend operator fun invoke(operationId: String, species: Species, nickname: String, captureId: String?, existingPlantId: Long?) =
        archive.collect(operationId, species, nickname, captureId, existingPlantId)
}
class AddObservationUseCase @Inject constructor(private val archive: ArchiveService) {
    suspend operator fun invoke(plantId: Long, captureId: String, observedAt: Long, note: String?) = archive.add(plantId, captureId, observedAt, note)
}
class SetPlantCoverUseCase @Inject constructor(private val archive: ArchiveService) {
    suspend operator fun invoke(plantId: Long, observationId: Long) = archive.cover(plantId, observationId)
}
class DeleteObservationUseCase @Inject constructor(private val archive: ArchiveService) {
    suspend operator fun invoke(plantId: Long, observationId: Long) = archive.delete(plantId, observationId)
}

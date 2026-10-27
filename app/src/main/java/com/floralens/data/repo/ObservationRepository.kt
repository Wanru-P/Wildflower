package com.floralens.data.repo

import com.floralens.data.db.ObservationDao
import com.floralens.data.db.entity.toDomain
import com.floralens.data.db.entity.toEntity
import com.floralens.domain.model.Observation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObservationRepository @Inject constructor(
    private val dao: ObservationDao,
) {
    suspend fun add(observation: Observation): Long = dao.insert(observation.toEntity())

    fun observeForPlant(plantId: Long): Flow<List<Observation>> =
        dao.observeForPlant(plantId).map { list -> list.map { it.toDomain() } }

    fun observeMapPins(): Flow<List<Observation>> =
        dao.observeWithLocation().map { list -> list.map { it.toDomain() } }

    fun observeLeafChecks(): Flow<List<Observation>> =
        dao.observeLeafChecks().map { list -> list.map { it.toDomain() } }
}

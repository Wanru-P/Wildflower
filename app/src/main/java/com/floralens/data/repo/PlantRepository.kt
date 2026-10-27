package com.floralens.data.repo

import com.floralens.data.db.PlantDao
import com.floralens.data.db.entity.toDomain
import com.floralens.data.db.entity.toEntity
import com.floralens.domain.model.Plant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlantRepository @Inject constructor(
    private val dao: PlantDao,
) {
    fun observeAll(): Flow<List<Plant>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeById(id: Long): Flow<Plant?> =
        dao.observeById(id).map { it?.toDomain() }

    suspend fun upsert(plant: Plant): Long = dao.upsert(plant.toEntity())

    suspend fun setLastWatered(id: Long, at: Instant) =
        dao.setLastWatered(id, at.toEpochMilli())

    suspend fun setRemindersEnabled(id: Long, enabled: Boolean) =
        dao.setRemindersEnabled(id, enabled)

    suspend fun delete(id: Long) = dao.delete(id)
}

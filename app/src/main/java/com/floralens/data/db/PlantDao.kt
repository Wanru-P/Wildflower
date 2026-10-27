package com.floralens.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.floralens.data.db.entity.PlantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlantDao {
    @Query("SELECT * FROM plants ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<PlantEntity>>

    @Query("SELECT * FROM plants WHERE id = :id")
    fun observeById(id: Long): Flow<PlantEntity?>

    @androidx.room.Upsert
    suspend fun upsert(plant: PlantEntity): Long

    @Query("UPDATE plants SET lastWateredAtEpochMs = :epochMs WHERE id = :id")
    suspend fun setLastWatered(id: Long, epochMs: Long)

    @Query("UPDATE plants SET remindersEnabled = :enabled WHERE id = :id")
    suspend fun setRemindersEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM plants WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM plants")
    suspend fun count(): Int
}

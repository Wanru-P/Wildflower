package com.floralens.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.floralens.data.db.entity.ObservationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Three views over ONE event table (data-flow rule #3):
 * history per plant, map pins, leaf-health log.
 */
@Dao
interface ObservationDao {
    /** Nature journal deliberately excludes watering, notes and leaf-health events. */
    @Query("SELECT * FROM observations WHERE type IN ('IDENTIFIED', 'PHOTO_OBSERVATION') ORDER BY timestampEpochMs DESC, id DESC")
    fun observeNatural(): Flow<List<ObservationEntity>>

    @Insert
    suspend fun insert(observation: ObservationEntity): Long

    /** Plant history. */
    @Query("SELECT * FROM observations WHERE plantId = :plantId ORDER BY timestampEpochMs DESC")
    fun observeForPlant(plantId: Long): Flow<List<ObservationEntity>>

    /** Map pins — any observation carrying a location. */
    @Query("SELECT * FROM observations WHERE latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY timestampEpochMs DESC")
    fun observeWithLocation(): Flow<List<ObservationEntity>>

    /** Leaf-health log. */
    @Query("SELECT * FROM observations WHERE type = 'LEAF_CHECK' ORDER BY timestampEpochMs DESC")
    fun observeLeafChecks(): Flow<List<ObservationEntity>>
}

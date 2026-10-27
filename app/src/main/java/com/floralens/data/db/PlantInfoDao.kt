package com.floralens.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.floralens.data.db.entity.PlantInfoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlantInfoDao {

    /** Auto-save on online view and manual refresh both land here. */
    @Upsert
    suspend fun upsert(info: PlantInfoEntity)

    /** One-shot read for the details screen's offline path. */
    @Query("SELECT * FROM plant_info WHERE scientificName = :scientificName")
    suspend fun get(scientificName: String): PlantInfoEntity?

    /**
     * Every saved species — the My Plants grid maps each plant's
     * scientificName into this set to decide its availability chip,
     * updating live the moment a save lands.
     */
    @Query("SELECT scientificName FROM plant_info")
    fun observeSavedSpecies(): Flow<List<String>>

    /** Reactive single-species check, used by the details screen's "saved" indicator. */
    @Query("SELECT COUNT(*) > 0 FROM plant_info WHERE scientificName = :scientificName")
    fun observeIsSaved(scientificName: String): Flow<Boolean>

    @Query("DELETE FROM plant_info WHERE scientificName = :scientificName")
    suspend fun delete(scientificName: String)
}
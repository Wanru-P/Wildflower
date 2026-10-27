package com.floralens.data.db

import androidx.room.*
import com.floralens.data.db.entity.*
import kotlinx.coroutines.flow.Flow

/** Durable commit receipt: closes the crash gap between filesystem and Room. */
@Entity(tableName = "photo_operations")
data class PhotoOperation(@PrimaryKey val operationId: String, val plantId: Long, val observationId: Long?, val photoUri: String?)

@Dao
interface ArchiveDao {
    @Query("SELECT * FROM plants WHERE id = :id") suspend fun plant(id: Long): PlantEntity?
    @Query("SELECT * FROM plants WHERE speciesKey = :key ORDER BY createdAtEpochMs DESC") suspend fun sameSpecies(key: String): List<PlantEntity>
    @Insert suspend fun insertPlant(plant: PlantEntity): Long
    @Insert suspend fun receipt(operation: PhotoOperation)
    @Query("SELECT * FROM photo_operations WHERE operationId = :id") suspend fun operation(id: String): PhotoOperation?
    @Query("SELECT * FROM observations WHERE id = :id") suspend fun observation(id: Long): ObservationEntity?
    @Query("SELECT * FROM observations WHERE plantId = :id ORDER BY timestampEpochMs DESC, id DESC") suspend fun history(id: Long): List<ObservationEntity>
    @Query("SELECT * FROM observations WHERE plantId = :id AND photoUri IS NOT NULL ORDER BY timestampEpochMs DESC, id DESC") fun gallery(id: Long): Flow<List<ObservationEntity>>
    @Query("UPDATE plants SET coverObservationId = :observationId, photoUri = :uri WHERE id = :id") suspend fun cover(id: Long, observationId: Long?, uri: String?)
    @Query("UPDATE plants SET nickname = :name WHERE id = :id") suspend fun nickname(id: Long, name: String)
    @Query("UPDATE observations SET timestampEpochMs = :time, note = :note WHERE id = :id") suspend fun edit(id: Long, time: Long, note: String?)
    @Query("DELETE FROM observations WHERE id = :id") suspend fun deleteObservation(id: Long)
    @Query("SELECT (SELECT COUNT(*) FROM plants WHERE photoUri = :uri) + (SELECT COUNT(*) FROM observations WHERE photoUri = :uri)") suspend fun references(uri: String): Int
}

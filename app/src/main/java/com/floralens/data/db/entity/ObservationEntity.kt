package com.floralens.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.floralens.domain.model.Observation
import com.floralens.domain.model.ObservationType
import java.time.Instant

@Entity(
    tableName = "observations",
    foreignKeys = [ForeignKey(
        entity = PlantEntity::class,
        parentColumns = ["id"],
        childColumns = ["plantId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("plantId"), Index("type")],
)
data class ObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantId: Long,
    val type: String, // ObservationType.name
    val note: String?,
    val photoUri: String?,
    val latitude: Double?,
    val longitude: Double?,
    val timestampEpochMs: Long,
    val identificationJson: String? = null,
)

fun ObservationEntity.toDomain() = Observation(
    id = id,
    plantId = plantId,
    type = ObservationType.valueOf(type),
    note = note,
    photoUri = photoUri,
    latitude = latitude,
    longitude = longitude,
    timestamp = Instant.ofEpochMilli(timestampEpochMs),
    identificationJson = identificationJson,
)

fun Observation.toEntity() = ObservationEntity(
    id = id,
    plantId = plantId,
    type = type.name,
    note = note,
    photoUri = photoUri,
    latitude = latitude,
    longitude = longitude,
    timestampEpochMs = timestamp.toEpochMilli(),
    identificationJson = identificationJson,
)

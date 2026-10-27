package com.floralens.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.floralens.domain.model.Plant
import java.time.Instant

@Entity(tableName = "plants")
data class PlantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nickname: String,
    val scientificName: String,
    val commonName: String,
    val photoUri: String?,
    val waterIntervalDays: Int,
    val lastWateredAtEpochMs: Long?,
    val remindersEnabled: Boolean,
    val createdAtEpochMs: Long,
    @ColumnInfo(defaultValue = "''") val speciesKey: String = "",
    val coverObservationId: Long? = null,
)

fun PlantEntity.toDomain() = Plant(
    id = id,
    nickname = nickname,
    scientificName = scientificName,
    commonName = commonName,
    photoUri = photoUri,
    waterIntervalDays = waterIntervalDays,
    lastWateredAt = lastWateredAtEpochMs?.let(Instant::ofEpochMilli),
    remindersEnabled = remindersEnabled,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    speciesKey = speciesKey.ifBlank { com.floralens.domain.model.SpeciesKey.fromLegacy(scientificName, commonName) },
    coverObservationId = coverObservationId,
)

fun Plant.toEntity() = PlantEntity(
    id = id,
    nickname = nickname,
    scientificName = scientificName,
    commonName = commonName,
    photoUri = photoUri,
    waterIntervalDays = waterIntervalDays,
    lastWateredAtEpochMs = lastWateredAt?.toEpochMilli(),
    remindersEnabled = remindersEnabled,
    createdAtEpochMs = createdAt.toEpochMilli(),
    speciesKey = speciesKey,
    coverObservationId = coverObservationId,
)

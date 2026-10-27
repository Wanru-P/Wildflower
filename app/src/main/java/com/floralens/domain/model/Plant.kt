package com.floralens.domain.model

import java.time.Instant

/** A plant in the user's collection. */
data class Plant(
    val id: Long = 0,
    val nickname: String,
    val scientificName: String,
    val commonName: String,
    val photoUri: String? = null,
    val waterIntervalDays: Int,
    val lastWateredAt: Instant? = null,
    val remindersEnabled: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val speciesKey: String = SpeciesKey.fromLegacy(scientificName, commonName),
    val coverObservationId: Long? = null,
)

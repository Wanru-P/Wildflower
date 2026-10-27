package com.floralens.domain.model

import java.time.Instant

/**
 * THE one event table (data-flow rule #3): identified / watered / note /
 * leaf-check rows all live here. History list, map pins, and the health log
 * are just three queries over it.
 */
data class Observation(
    val id: Long = 0,
    val plantId: Long,
    val type: ObservationType,
    val note: String? = null,
    val photoUri: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Instant = Instant.now(),
    /** JSON snapshot of THIS identification only; null for ordinary added photos. */
    val identificationJson: String? = null,
)

enum class ObservationType { IDENTIFIED, WATERED, NOTE, LEAF_CHECK, PHOTO_OBSERVATION }

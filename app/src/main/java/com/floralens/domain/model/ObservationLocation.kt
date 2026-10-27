package com.floralens.domain.model

/** Validation only. Never guesses, swaps, geocodes or overwrites recorded coordinates. */
object ObservationLocation {
    fun valid(latitude: Double?, longitude: Double?): Boolean =
        latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}

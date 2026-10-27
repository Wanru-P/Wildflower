package com.floralens.domain.model

/** A geocoded native-region point, ready to plot on the world map. */
data class GeoPoint(
    val label: String,
    val latitude: Double,
    val longitude: Double,
)
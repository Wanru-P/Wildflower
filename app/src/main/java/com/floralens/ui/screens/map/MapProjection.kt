package com.floralens.ui.screens.map

import com.floralens.domain.model.Observation
import com.floralens.domain.model.ObservationLocation
import com.floralens.domain.model.Plant
import java.time.Instant
import kotlin.math.*

/** Stable observation identity; a cover update must not replace this observation's photo. */
data class MapPin(
    val observationId: Long,
    val plant: Plant,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Instant,
    val photoUri: String? = null,
    val note: String? = null,
    val coordinateSystem: com.floralens.domain.model.CoordinateSystem = com.floralens.domain.model.CoordinateSystem.UNKNOWN,
) {
    val displayPhotoUri: String? get() = photoUri ?: plant.photoUri
}

data class MapProjection(val pins: List<MapPin>, val invalidLocations: Int)

object ObservationMapProjection {
    fun join(observations: List<Observation>, plants: List<Plant>): MapProjection {
        val byId = plants.associateBy(Plant::id)
        var invalid = 0
        val pins = observations.sortedWith(compareByDescending<Observation> { it.timestamp }.thenByDescending { it.id })
            .mapNotNull { observation ->
                val plant = byId[observation.plantId] ?: return@mapNotNull null
                if (observation.latitude == null || observation.longitude == null) return@mapNotNull null
                if (!ObservationLocation.valid(observation.latitude, observation.longitude)) {
                    invalid++
                    return@mapNotNull null
                }
                MapPin(observation.id, plant, observation.latitude, observation.longitude, observation.timestamp,
                    observation.photoUri, observation.note)
            }
        return MapProjection(pins, invalid)
    }

    /** Nearby observations remain individually selectable, including identical coordinates. */
    fun nearby(selected: MapPin, pins: List<MapPin>, displayed: Map<Long, com.floralens.domain.model.DisplayCoordinate>? = null): List<MapPin> = pins.filter {
        if (displayed == null) distanceMeters(selected, it) <= 50.0
        else {
            val a = displayed[selected.observationId]; val b = displayed[it.observationId]
            if (a == null || b == null) it.observationId == selected.observationId
            else distanceMeters(selected.copy(latitude = a.latitude, longitude = a.longitude), it.copy(latitude = b.latitude, longitude = b.longitude)) <= 50.0
        }
    }

    private fun distanceMeters(a: MapPin, b: MapPin): Double {
        val lat = Math.toRadians(b.latitude - a.latitude)
        val lon = Math.toRadians(b.longitude - a.longitude)
        val x = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(lon / 2).pow(2)
        return 6_371_000 * 2 * asin(sqrt(x.coerceIn(0.0, 1.0)))
    }
}

package com.floralens.domain.model

enum class CoordinateSystem { UNKNOWN, WGS84, GCJ02, BD09LL }
enum class CoordinateRegion { DOMESTIC, OVERSEAS, UNCERTAIN }
data class DisplayCoordinate(val latitude: Double, val longitude: Double)

/** Conversion policy never mutates source coordinates; ambiguous history/borders stay local. */
object MapCoordinatePolicy {
    fun project(latitude: Double?, longitude: Double?, source: CoordinateSystem, region: CoordinateRegion,
                convert: (Double, Double, CoordinateSystem) -> DisplayCoordinate?): DisplayCoordinate? {
        if (!ObservationLocation.valid(latitude, longitude) || source == CoordinateSystem.UNKNOWN) return null
        val lat = latitude!!; val lon = longitude!!
        if (source == CoordinateSystem.BD09LL) return DisplayCoordinate(lat, lon)
        if (region == CoordinateRegion.UNCERTAIN) return null
        if (region == CoordinateRegion.OVERSEAS) return if (source == CoordinateSystem.WGS84) DisplayCoordinate(lat, lon) else null
        return runCatching { convert(lat, lon, source) }.getOrNull()
            ?.takeIf { ObservationLocation.valid(it.latitude, it.longitude) }
    }
}

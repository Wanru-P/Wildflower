package com.floralens.ui.screens.map

import android.content.Context
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.utils.CoordinateConverter
import com.floralens.domain.model.*
import org.json.JSONObject
import kotlin.math.*

/** Public-domain Natural Earth mask only routes conversion and is never a displayed boundary.
 * A 5km border/coast buffer is withheld rather than guessing near a generalized edge. */
class MapRegionMask(context: Context) {
    private val rings = JSONObject(context.assets.open("map_coordinate_regions.json").bufferedReader().use { it.readText() })
        .getJSONArray("rings").let { data -> (0 until data.length()).map { i ->
            data.getJSONArray(i).let { ring -> (0 until ring.length()).map { j ->
                ring.getJSONArray(j).let { DisplayCoordinate(it.getDouble(1), it.getDouble(0)) }
            } }
        } }
    fun classify(lat: Double, lon: Double): CoordinateRegion {
        var inside = false
        var nearest = Double.POSITIVE_INFINITY
        rings.forEach { ring ->
            var hit = false
            ring.zipWithNext().forEach { (a, b) ->
                if ((a.latitude > lat) != (b.latitude > lat) &&
                    lon < (b.longitude - a.longitude) * (lat - a.latitude) / (b.latitude - a.latitude) + a.longitude) hit = !hit
                val cos = cos(Math.toRadians(lat))
                val ax = (a.longitude - lon) * cos * 111_195; val ay = (a.latitude - lat) * 111_195
                val bx = (b.longitude - lon) * cos * 111_195; val by = (b.latitude - lat) * 111_195
                val dx = bx - ax; val dy = by - ay
                val t = (-(ax * dx + ay * dy) / (dx * dx + dy * dy).coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
                nearest = min(nearest, hypot(ax + t * dx, ay + t * dy))
            }
            inside = inside || hit
        }
        return when { nearest < 5_000 -> CoordinateRegion.UNCERTAIN; inside -> CoordinateRegion.DOMESTIC; else -> CoordinateRegion.OVERSEAS }
    }
}

fun baiduCoordinate(pin: MapPin, mask: MapRegionMask): LatLng? =
    baiduCoordinate(pin.latitude, pin.longitude, pin.coordinateSystem, mask)

fun baiduCoordinate(latitude: Double, longitude: Double, system: CoordinateSystem, mask: MapRegionMask): LatLng? =
    MapCoordinatePolicy.project(latitude, longitude, system, mask.classify(latitude, longitude)) { lat, lon, source ->
        CoordinateConverter().from(if (source == CoordinateSystem.WGS84) CoordinateConverter.CoordType.GPS else CoordinateConverter.CoordType.COMMON)
            .coord(LatLng(lat, lon)).convert()?.let { DisplayCoordinate(it.latitude, it.longitude) }
    }?.let { LatLng(it.latitude, it.longitude) }

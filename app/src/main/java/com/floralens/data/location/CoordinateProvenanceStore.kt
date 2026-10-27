package com.floralens.data.location

import android.content.Context
import android.content.SharedPreferences
import com.floralens.domain.model.CoordinateSystem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import javax.inject.Inject
import javax.inject.Singleton

/** Versioned sidecar, not a Room migration. An edited coordinate invalidates its old provenance. */
@Singleton
class CoordinateProvenanceStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("observation_coordinate_sources_v1", Context.MODE_PRIVATE)
    val changes = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener); trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()
    private fun signature(lat: Double, lon: Double) = "${lat.toRawBits()}:${lon.toRawBits()}"
    fun record(id: Long, lat: Double, lon: Double, source: CoordinateSystem) {
        check(prefs.edit().putString(id.toString(), "${source.name}:${signature(lat, lon)}").commit())
    }
    fun source(id: Long, lat: Double, lon: Double): CoordinateSystem {
        val stored = prefs.getString(id.toString(), null) ?: return CoordinateSystem.UNKNOWN
        val name = stored.substringBefore(':')
        if (stored.substringAfter(':') != signature(lat, lon)) return CoordinateSystem.UNKNOWN
        return runCatching { CoordinateSystem.valueOf(name) }.getOrDefault(CoordinateSystem.UNKNOWN)
    }
    fun remove(id: Long) { prefs.edit().remove(id.toString()).apply() }
}

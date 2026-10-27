package com.floralens.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** A captured coordinate — kept as a plain pair of doubles, no Android types leak out. */
data class Coordinates(val latitude: Double, val longitude: Double)

/**
 * Passive, optional location for observation pins. Extracted as an interface
 * (CareDefaults / PlantIdentifier pattern) so unit tests swap in a fake
 * without a real Android Context.
 */
interface LocationProvider {
    /** Best cached fix, or null (no permission / no fix yet). Never activates GPS. */
    fun lastKnownLocation(): Coordinates?
}

/**
 * Privacy by construction: framework LocationManager only (no Play Services
 * dependency), and only the *last known* fix is read — this never activates
 * GPS, never requests updates, and returns null without complaint when the
 * user hasn't granted location. The app is fully functional without it.
 */
@Singleton
class SystemLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationProvider {

    private val locationManager: LocationManager?
        get() = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // guarded by hasPermission()
    override fun lastKnownLocation(): Coordinates? {
        if (!hasPermission()) return null
        val manager = locationManager ?: return null
        // The passive provider can still expose an old cached fix after the
        // system location switch is turned off. Respect that privacy choice.
        if (!LocationManagerCompat.isLocationEnabled(manager)) return null
        return runCatching { manager.getProviders(true)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .filter { com.floralens.domain.model.ObservationLocation.valid(it.latitude, it.longitude) }
            .maxByOrNull { it.time }
            ?.let { Coordinates(it.latitude, it.longitude) }
        }.getOrNull() // permission can be revoked between the check and read
    }
}

package com.floralens.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connectivity check for the online identification path. Extracted as an
 * interface (LocationProvider / PlantIdentifier pattern) so HybridIdentifier
 * is unit-testable without a real Android Context.
 */
interface NetworkStatus {
    /** True if there's an active network that claims internet capability. */
    fun isConnected(): Boolean
}

/**
 * Framework ConnectivityManager only — no extra dependency. Requires
 * ACCESS_NETWORK_STATE, already in the manifest.
 *
 * Deliberately checks NET_CAPABILITY_INTERNET only, not
 * NET_CAPABILITY_VALIDATED. VALIDATED is Android's own probe confirming a
 * network actually reaches the internet, and it's known to be slow or
 * unreliable to set on plenty of legitimate connections — requiring it
 * caused real false negatives (reporting "offline" while genuinely
 * connected), which meant online mode never even attempted a Pl@ntNet call.
 * HybridIdentifier already wraps the real network call in its own
 * try/catch with a fallback, so a wrong "connected" here just costs one
 * fast-failing request rather than silently blocking online mode outright.
 */
@Singleton
class SystemNetworkStatus @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkStatus {

    override fun isConnected(): Boolean = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching false
        val network = manager.activeNetwork ?: return@runCatching false
        val capabilities = manager.getNetworkCapabilities(network) ?: return@runCatching false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)
}
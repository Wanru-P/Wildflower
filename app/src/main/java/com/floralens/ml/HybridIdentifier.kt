package com.floralens.ml

import android.util.Log
import com.floralens.data.network.NetworkStatus
import com.floralens.data.settings.SettingsRepository
import com.floralens.di.RealIdentifier
import com.floralens.domain.model.IdCandidate
import com.floralens.domain.model.IdentificationMode
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes classification to on-device or Pl@ntNet based on the user's stored
 * preference. This is what the unqualified [PlantIdentifier] binding
 * resolves to — so IdentifyPlant and every existing caller need zero
 * changes.
 *
 * Every fallback path now logs *why* — this was previously silent, which
 * made "online mode isn't actually calling Pl@ntNet" undiagnosable from the
 * outside. Check Logcat for tag "HybridIdentifier" if online mode still
 * seems to be using on-device results.
 */
@Singleton
class HybridIdentifier @Inject constructor(
    @RealIdentifier private val onDevice: PlantIdentifier,
    private val online: PlantNetIdentifier,
    private val settings: SettingsRepository,
    private val networkStatus: NetworkStatus,
) : PlantIdentifier {

    companion object {
        private const val TAG = "HybridIdentifier"
    }

    override suspend fun classify(imageBytes: ByteArray): List<IdCandidate> {
        val mode = settings.identificationMode.first()

        if (mode == IdentificationMode.OFFLINE) {
            return onDevice.classify(imageBytes)
        }

        if (!networkStatus.isConnected()) {
            Log.w(TAG, "Mode is ONLINE but NetworkStatus reports no connectivity — using on-device")
            return onDevice.classify(imageBytes)
        }

        return runCatching { online.classify(imageBytes) }
            .onFailure { Log.w(TAG, "Pl@ntNet call failed — falling back to on-device", it) }
            .getOrElse { onDevice.classify(imageBytes) }
    }
}
package com.floralens.domain.usecase

import com.floralens.domain.model.IdResult
import com.floralens.ml.PlantIdentifier
import javax.inject.Inject

/**
 * Routes raw classifier output to a confidence-aware IdResult.
 * Threshold routing (high vs low variant) is the second unit-test showcase.
 */
class IdentifyPlant @Inject constructor(
    private val identifier: PlantIdentifier,
) {
    companion object {
        const val HIGH_CONFIDENCE_THRESHOLD = 0.75f
        const val MIN_CANDIDATE_CONFIDENCE = 0.20f
    }

    suspend operator fun invoke(imageBytes: ByteArray): IdResult {
        val candidates = identifier.classify(imageBytes)
            .filter { it.confidence >= MIN_CANDIDATE_CONFIDENCE }
            .sortedByDescending { it.confidence }

        if (candidates.isEmpty()) return IdResult.NoPlantDetected
        val best = candidates.first()
        return if (best.confidence >= HIGH_CONFIDENCE_THRESHOLD) {
            IdResult.HighConfidence(best = best, alternates = candidates.drop(1).take(2))
        } else {
            IdResult.LowConfidence(possibilities = candidates.take(3))
        }
    }
}

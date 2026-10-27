package com.floralens.domain.model

/**
 * One candidate from a classifier. [source] identifies which one answered.
 * [imageUrls] are representative photos, populated only by the online path
 * (Pl@ntNet related-images) — on-device candidates leave it empty and the
 * UI falls back to a placeholder. Both default so existing callers and
 * their tests need no changes.
 */
data class IdCandidate(
    val scientificName: String,
    val commonName: String,
    val confidence: Float, // 0f..1f
    val source: IdentificationSource = IdentificationSource.ON_DEVICE,
    val imageUrls: List<String> = emptyList(),
)

/**
 * Confidence-aware identification result. High-confidence → one answer +
 * alternates; low-confidence → "Not sure — here are some possibilities".
 */
sealed interface IdResult {
    data class HighConfidence(val best: IdCandidate, val alternates: List<IdCandidate>) : IdResult
    data class LowConfidence(val possibilities: List<IdCandidate>) : IdResult
    data object NoPlantDetected : IdResult
}
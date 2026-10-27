package com.floralens.ml

import com.floralens.domain.model.IdCandidate

/**
 * The architecture centerpiece. Two implementations:
 *  - [TfLiteIdentifier] — real on-device model
 *  - [FakeIdentifier]   — deterministic, for tests / previews / demo
 * The app doesn't know or care which it gets — swap via Hilt qualifier
 * (see di/MlModule.kt).
 */
interface PlantIdentifier {
    suspend fun classify(imageBytes: ByteArray): List<IdCandidate>
}

package com.floralens.ml

import com.floralens.domain.model.IdCandidate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic identifier for tests, Compose previews, and demo builds.
 * Always returns a Monstera at 92% — matching the design-system screenshots.
 */
@Singleton
class FakeIdentifier @Inject constructor() : PlantIdentifier {

    override suspend fun classify(imageBytes: ByteArray): List<IdCandidate> = listOf(
        IdCandidate("Monstera deliciosa", "Swiss cheese plant", 0.92f),
        IdCandidate("Thaumatophyllum bipinnatifidum", "Split-leaf philodendron", 0.61f),
        IdCandidate("Monstera adansonii", "Swiss cheese vine", 0.44f),
    )
}

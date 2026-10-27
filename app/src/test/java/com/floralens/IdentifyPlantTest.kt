package com.floralens

import com.floralens.domain.model.IdCandidate
import com.floralens.domain.model.IdResult
import com.floralens.domain.usecase.IdentifyPlant
import com.floralens.ml.PlantIdentifier
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Confidence-threshold routing to high/low result variants. */
class IdentifyPlantTest {

    private class StubIdentifier(private val candidates: List<IdCandidate>) : PlantIdentifier {
        override suspend fun classify(imageBytes: ByteArray) = candidates
    }

    @Test
    fun `high confidence routes to HighConfidence with alternates`() = runTest {
        val identify = IdentifyPlant(
            StubIdentifier(
                listOf(
                    IdCandidate("Monstera deliciosa", "Swiss cheese plant", 0.92f),
                    IdCandidate("Monstera adansonii", "Swiss cheese vine", 0.44f),
                )
            )
        )
        val result = identify(ByteArray(0))
        assertTrue(result is IdResult.HighConfidence)
        result as IdResult.HighConfidence
        assertEquals("Monstera deliciosa", result.best.scientificName)
        assertEquals(1, result.alternates.size)
    }

    @Test
    fun `below threshold routes to LowConfidence`() = runTest {
        val identify = IdentifyPlant(
            StubIdentifier(
                listOf(
                    IdCandidate("Epipremnum aureum", "Pothos", 0.61f),
                    IdCandidate("Philodendron hederaceum", "Heartleaf philodendron", 0.40f),
                )
            )
        )
        val result = identify(ByteArray(0))
        assertTrue(result is IdResult.LowConfidence)
        result as IdResult.LowConfidence
        assertEquals(2, result.possibilities.size)
    }

    @Test
    fun `no candidates above minimum routes to NoPlantDetected`() = runTest {
        val identify = IdentifyPlant(
            StubIdentifier(listOf(IdCandidate("?", "?", 0.05f)))
        )
        assertEquals(IdResult.NoPlantDetected, identify(ByteArray(0)))
    }
}

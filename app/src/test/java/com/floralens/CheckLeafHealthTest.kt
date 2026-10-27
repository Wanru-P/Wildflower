package com.floralens

import com.floralens.domain.usecase.CheckLeafHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the color-heuristic semantics of CheckLeafHealth.analyze() using
 * synthetic ARGB pixel arrays — no bitmaps, pure JVM. If thresholds get
 * tuned later, these tests define the contract that must keep holding.
 */
class CheckLeafHealthTest {

    private val check = CheckLeafHealth()

    // Synthetic pixels chosen to land squarely inside each HSV bucket.
    private val green = argb(40, 140, 60)    // hue ~132, healthy foliage
    private val yellow = argb(210, 190, 40)  // hue ~53, bright yellowing
    private val brown = argb(140, 90, 40)    // hue ~30, dry/brown
    private val gray = argb(200, 200, 200)   // desaturated — ignored as background

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** Builds a 1000-pixel frame from bucket counts (padded with gray). */
    private fun frame(greenPx: Int = 0, yellowPx: Int = 0, brownPx: Int = 0): IntArray {
        val total = 1000
        val vegetation = greenPx + yellowPx + brownPx
        require(vegetation <= total)
        return IntArray(total) { i ->
            when {
                i < greenPx -> green
                i < greenPx + yellowPx -> yellow
                i < vegetation -> brown
                else -> gray
            }
        }
    }

    @Test
    fun `mostly green foliage is healthy`() {
        val result = check.analyze(frame(greenPx = 600))
        assertEquals(CheckLeafHealth.Result.Healthy, result)
    }

    @Test
    fun `heavy yellowing flags a possible issue with hedged watering language`() {
        // 40% of vegetation is yellow — well above the 28% threshold.
        val result = check.analyze(frame(greenPx = 300, yellowPx = 200))
        assertTrue(result is CheckLeafHealth.Result.PossibleIssue)
        val message = (result as CheckLeafHealth.Result.PossibleIssue).friendlyMessage
        assertTrue(message.contains("yellow", ignoreCase = true))
        assertTrue(message.contains("isn't a diagnosis", ignoreCase = true))
    }

    @Test
    fun `browning flags a possible issue and outranks yellowing`() {
        // Both over threshold — brown wins (it's checked first: drier = more urgent).
        val result = check.analyze(frame(greenPx = 200, yellowPx = 150, brownPx = 150))
        assertTrue(result is CheckLeafHealth.Result.PossibleIssue)
        val message = (result as CheckLeafHealth.Result.PossibleIssue).friendlyMessage
        assertTrue(message.contains("brown", ignoreCase = true))
    }

    @Test
    fun `mild yellowing below threshold stays healthy`() {
        // 20% of vegetation yellow — under the 28% threshold: no false alarm.
        val result = check.analyze(frame(greenPx = 400, yellowPx = 100))
        assertEquals(CheckLeafHealth.Result.Healthy, result)
    }

    @Test
    fun `frame without enough vegetation is unclear`() {
        // 10% vegetation — under the 15% floor. Gray background dominates.
        val result = check.analyze(frame(greenPx = 100))
        assertEquals(CheckLeafHealth.Result.Unclear, result)
    }

    @Test
    fun `desaturated pixels are ignored rather than misclassified`() {
        // All gray: zero vegetation → Unclear, never a confident answer.
        val result = check.analyze(frame())
        assertEquals(CheckLeafHealth.Result.Unclear, result)
    }
}
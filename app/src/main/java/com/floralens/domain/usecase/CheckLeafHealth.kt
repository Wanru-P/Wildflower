package com.floralens.domain.usecase

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Second on-device pass — flags possible leaf issues in plain, hedged
 * language ("a little yellow — not a diagnosis, just a friendly nudge").
 *
 * Honesty note: this is a color-balance heuristic, not a trained model.
 * It looks at the ratio of yellow / brown to green among vegetation-ish
 * pixels — enough for a friendly nudge, never for a diagnosis, and the
 * copy reflects that. A leaf-health ML model could later slot in behind
 * the same use case, PlantIdentifier-style.
 */
class CheckLeafHealth @Inject constructor() {

    sealed interface Result {
        data object Healthy : Result
        data class PossibleIssue(val friendlyMessage: String) : Result

        /** Not enough leaf in frame to say anything useful. */
        data object Unclear : Result
    }

    suspend operator fun invoke(imageBytes: ByteArray): Result =
        withContext(Dispatchers.Default) {
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: return@withContext Result.Unclear
            val scaled = Bitmap.createScaledBitmap(bitmap, SAMPLE_SIZE, SAMPLE_SIZE, true)
            val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
            scaled.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
            analyze(pixels)
        }

    /**
     * Pure pixel analysis — separated from bitmap decoding so it unit-tests
     * on the JVM with synthetic pixel arrays.
     */
    internal fun analyze(pixels: IntArray): Result {
        var green = 0
        var yellow = 0
        var brown = 0

        for (argb in pixels) {
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            val (h, s, v) = rgbToHsv(r, g, b)

            // Ignore washed-out or near-black pixels — background, pot, glare.
            if (s < 0.18f || v < 0.12f) continue

            when {
                h in 70f..170f -> green++
                h in 40f..70f && v > 0.35f -> yellow++
                h in 10f..40f && v <= 0.75f -> brown++
            }
        }

        val vegetation = green + yellow + brown
        if (vegetation < pixels.size * MIN_VEGETATION_FRACTION) return Result.Unclear

        val yellowFraction = yellow.toFloat() / vegetation
        val brownFraction = brown.toFloat() / vegetation

        return when {
            brownFraction > BROWN_THRESHOLD -> Result.PossibleIssue(
                "Some leaf areas look brown or dry — that can just mean low humidity " +
                        "or an older leaf on its way out. Keep an eye on it over the next " +
                        "week. This isn't a diagnosis, just a friendly nudge.",
            )
            yellowFraction > YELLOW_THRESHOLD -> Result.PossibleIssue(
                "A fair bit of yellow in there — often that just means the plant " +
                        "wants slightly less water or a touch more light. Worth watching " +
                        "for a week. This isn't a diagnosis, just a friendly nudge.",
            )
            else -> Result.Healthy
        }
    }

    /** Hue in degrees 0–360, saturation and value 0–1. Pure Kotlin, JVM-testable. */
    private fun rgbToHsv(r: Int, g: Int, b: Int): Triple<Float, Float, Float> {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val delta = max - min

        val h = when {
            delta == 0f -> 0f
            max == rf -> 60f * (((gf - bf) / delta).mod(6f))
            max == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }
        val s = if (max == 0f) 0f else delta / max
        return Triple(h, s, max)
    }

    companion object {
        private const val SAMPLE_SIZE = 96
        private const val MIN_VEGETATION_FRACTION = 0.15f
        internal const val YELLOW_THRESHOLD = 0.28f
        internal const val BROWN_THRESHOLD = 0.22f
    }
}
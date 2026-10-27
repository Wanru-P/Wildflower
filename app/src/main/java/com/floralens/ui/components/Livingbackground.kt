package com.floralens.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animated "living" background — parallax foliage drifting down the screen.
 *
 * Three depth layers move at different speeds (classic parallax): big faint
 * leaves far back, small more-visible ones up front. Every leaf's fall and
 * sway use *integer* cycle counts over the animation period, so when the
 * 40s loop restarts the frame is pixel-identical — no visible jump.
 *
 * Usage — put it first inside a Box so content draws on top:
 * ```
 * Box(Modifier.fillMaxSize()) {
 *     LivingBackground()
 *     // ...screen content...
 * }
 * ```
 *
 * All state lives in one animated float read inside the draw phase only —
 * the composable never recomposes per frame.
 */
@Composable
fun LivingBackground(
    modifier: Modifier = Modifier,
    leafColor: Color = Color(0xFF52664F),   // sage — secondary
    accentColor: Color = Color(0xFF1B4332), // forest — primary
    intensity: Float = 1f,                  // 0..1 scales alpha; 0 disables
) {
    if (intensity <= 0f) return

    val leaves = remember { buildLeaves() }

    val transition = rememberInfiniteTransition(label = "livingBg")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 40_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "t",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        for (leaf in leaves) {
            // Fall: baseY + integer cycles of full-screen travel, wrapped
            // with 15% overshoot so leaves enter/exit off-screen smoothly.
            val yFrac = (leaf.baseY + t * leaf.fallCycles).mod(1.3f) - 0.15f
            // Sway: sine with an integer frequency → seamless at the loop.
            val xFrac = leaf.baseX +
                    leaf.swayAmp * sin(2f * Math.PI.toFloat() * (t * leaf.swayCycles + leaf.phase))
            val rotation = leaf.baseRotation + 360f * t * leaf.spinCycles

            drawLeaf(
                center = Offset(xFrac * w, yFrac * h),
                length = leaf.sizeFrac * w,
                rotationDegrees = rotation,
                color = if (leaf.accent) accentColor else leafColor,
                alpha = leaf.alpha * intensity,
            )
        }
    }
}

/** One drifting leaf. All fractions are relative to canvas size. */
private data class Leaf(
    val baseX: Float,
    val baseY: Float,
    val sizeFrac: Float,      // leaf length as a fraction of canvas width
    val alpha: Float,
    val baseRotation: Float,
    val fallCycles: Int,      // full screen-heights per 40s loop
    val spinCycles: Int,      // full rotations per loop
    val swayCycles: Int,      // sway oscillations per loop
    val swayAmp: Float,       // horizontal sway amplitude (fraction of width)
    val phase: Float,
    val accent: Boolean,
)

/** Deterministic layout — same forest every launch. */
private fun buildLeaves(): List<Leaf> {
    val rng = Random(42)
    val leaves = mutableListOf<Leaf>()

    fun layer(count: Int, sizeRange: ClosedFloatingPointRange<Float>, alphaRange: ClosedFloatingPointRange<Float>, fallCycles: Int) {
        repeat(count) {
            leaves += Leaf(
                baseX = rng.nextFloat(),
                baseY = rng.nextFloat(),
                sizeFrac = sizeRange.start + rng.nextFloat() * (sizeRange.endInclusive - sizeRange.start),
                alpha = alphaRange.start + rng.nextFloat() * (alphaRange.endInclusive - alphaRange.start),
                baseRotation = rng.nextFloat() * 360f,
                fallCycles = fallCycles,
                spinCycles = if (rng.nextBoolean()) 1 else 2,
                swayCycles = 2 + rng.nextInt(3),          // 2..4
                swayAmp = 0.02f + rng.nextFloat() * 0.04f, // 2–6% of width
                phase = rng.nextFloat(),
                accent = rng.nextFloat() < 0.3f,
            )
        }
    }

    // Back → front: bigger/fainter/slower → smaller/bolder/faster.
    layer(count = 7, sizeRange = 0.22f..0.34f, alphaRange = 0.05f..0.08f, fallCycles = 1)
    layer(count = 6, sizeRange = 0.12f..0.20f, alphaRange = 0.09f..0.13f, fallCycles = 2)
    layer(count = 5, sizeRange = 0.06f..0.11f, alphaRange = 0.13f..0.18f, fallCycles = 3)
    return leaves
}

/** A simple two-curve leaf silhouette with a center vein. */
private fun DrawScope.drawLeaf(
    center: Offset,
    length: Float,
    rotationDegrees: Float,
    color: Color,
    alpha: Float,
) {
    val halfLen = length / 2f
    val halfWidth = length * 0.28f

    translate(center.x, center.y) {
        rotate(rotationDegrees, pivot = Offset.Zero) {
            val path = Path().apply {
                moveTo(0f, -halfLen)
                quadraticBezierTo(halfWidth, -halfLen * 0.2f, 0f, halfLen)
                quadraticBezierTo(-halfWidth, -halfLen * 0.2f, 0f, -halfLen)
                close()
            }
            drawPath(path, color = color, alpha = alpha)
            // Vein — slightly darker line down the middle.
            drawLine(
                color = color,
                start = Offset(0f, -halfLen * 0.85f),
                end = Offset(0f, halfLen * 0.85f),
                strokeWidth = length * 0.02f,
                alpha = alpha * 0.7f,
            )
        }
    }
}
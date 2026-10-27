package com.floralens.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.floralens.ui.theme.FloraLensTheme

/** Circular confidence ring for identification results. */
@Composable
fun ConfidenceMeter(
    value: Int, // 0..100
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val ringColor = MaterialTheme.colorScheme.primary
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = trackColor, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke),
            )
            drawArc(
                color = ringColor, startAngle = -90f, sweepAngle = 360f * value / 100f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Text("$value%", style = MaterialTheme.typography.labelLarge)
    }
}

@Preview
@Composable
private fun ConfidenceMeterPreview() {
    FloraLensTheme { ConfidenceMeter(value = 92) }
}

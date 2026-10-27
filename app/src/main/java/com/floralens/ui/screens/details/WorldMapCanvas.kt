package com.floralens.ui.screens.details

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.floralens.domain.model.GeoPoint
import com.floralens.ui.theme.Spacing
import org.json.JSONObject

/**
 * Detailed equirectangular world map drawn on Canvas from a bundled
 * Natural Earth coastline asset (assets/world_map.json) — no tile server, no
 * network. Pins plotted from lat/long.
 *
 * Projection: x = (lon + 180) / 360; y = (90 - lat) / 180.
 */
@Composable
fun WorldMapCanvas(
    points: List<GeoPoint>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Parse the asset once and cache for this composition.
    val rings = remember {
        runCatching {
            val json = context.assets.open("world_map.json").bufferedReader().use { it.readText() }
            val arr = JSONObject(json).getJSONArray("rings")
            (0 until arr.length()).map { i ->
                val flat = arr.getJSONArray(i)
                FloatArray(flat.length()) { j -> flat.getDouble(j).toFloat() }
            }
        }.getOrDefault(emptyList())
    }

    val ocean = MaterialTheme.colorScheme.surfaceContainerHigh
    val land = MaterialTheme.colorScheme.secondaryContainer
    val landStroke = MaterialTheme.colorScheme.outlineVariant
    val pinColor = MaterialTheme.colorScheme.error
    val pinCore = MaterialTheme.colorScheme.onError

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f)
                .clip(RoundedCornerShape(16.dp))
                .background(ocean),
        ) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(2f)) {
                val w = size.width
                val h = size.height

                rings.forEach { flat ->
                    val path = Path().apply {
                        var i = 0
                        while (i < flat.size) {
                            val x = flat[i] * w
                            val y = flat[i + 1] * h
                            if (i == 0) moveTo(x, y) else lineTo(x, y)
                            i += 2
                        }
                        close()
                    }
                    drawPath(path, color = land)
                    drawPath(path, color = landStroke, style = Stroke(width = 1f))
                }

                points.forEach { p ->
                    val px = ((p.longitude + 180.0) / 360.0).toFloat() * w
                    val py = ((90.0 - p.latitude) / 180.0).toFloat() * h
                    drawCircle(color = pinColor.copy(alpha = 0.25f), radius = 16f, center = Offset(px, py))
                    drawCircle(color = pinColor, radius = 7f, center = Offset(px, py))
                    drawCircle(color = pinCore, radius = 2.5f, center = Offset(px, py))
                }
            }
        }

        if (points.isNotEmpty()) {
            Spacer(Modifier.size(Spacing.md))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                points.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(pinColor))
                        Spacer(Modifier.width(Spacing.sm))
                        Text(p.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
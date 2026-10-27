package com.floralens.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.floralens.ui.theme.FloraLensTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * M3 modal bottom sheet — scrim + drag handle, slides up over a screen.
 * Parent should be a Box (position relative). Tap the scrim, or drag the
 * sheet down past the threshold, to dismiss.
 *
 * The container is a [Surface] (not a raw background) so LocalContentColor
 * resolves to onSurface — default-colored Text stays readable in both the
 * light app theme and the dark camera theme.
 */
@Composable
fun BottomSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!open) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offsetY = remember { Animatable(0f) }
    val dismissThresholdPx = with(density) { 96.dp.toPx() }

    // Reset drag offset whenever a fresh sheet opens.
    LaunchedEffect(open) { offsetY.snapTo(0f) }

    Box(modifier = modifier.fillMaxSize().zIndex(10f)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offsetY.snapTo((offsetY.value + delta).coerceAtLeast(0f))
                        }
                    },
                    onDragStopped = { velocity ->
                        if (offsetY.value > dismissThresholdPx || velocity > 1000f) {
                            onDismiss()
                        } else {
                            offsetY.animateTo(0f, tween(200))
                        }
                    },
                ),
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 32.dp, height = 4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                content()
            }
        }
    }
}

@Preview
@Composable
private fun BottomSheetPreview() {
    FloraLensTheme {
        Box(Modifier.fillMaxSize()) {
            BottomSheet(open = true, onDismiss = {}) {
                Text(
                    "Monstera deliciosa",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}
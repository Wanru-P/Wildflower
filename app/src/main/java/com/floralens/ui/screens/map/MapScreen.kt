package com.floralens.ui.screens.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.floralens.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clipToBounds

/**
 * Observation map.
 *
 * ONLINE mode → consented Baidu SDK tiles with observation-ID markers.
 *
 * OFFLINE mode → "garden path" edition: an illustrated, fully local
 * naturalist's-journal terrain. No tile server exists to phone home to:
 * pins are projected from real GPS coordinates (equirectangular, auto-fit
 * bounds); the illustration is drawn around them, never instead of them.
 * The dirt trail is generated dynamically through the projected pin
 * positions, so the scene adapts to any number of pins.
 */

// ── Illustration palette (from the design comp; decoration-only, not theme) ──
private val Fleck = Color(0xFFE3EBD6)
private val GrassA = Color(0xFFE7EFDA)
private val GrassB = Color(0xFFE4EDD6)
private val MeadowA = Color(0xFFF1F6E8)
private val MeadowB = Color(0xFFE9F1DD)
private val PondOuter = Color(0xFFD5E4DD)
private val PondInner = Color(0xFFE0EBE5)
private val PondRipple = Color(0xFFC2D6CC)
private val PathEdge = Color(0xFFA9805A)
private val PathTread = Color(0xFFDABB8F)
private val Stone = Color(0xFFCBD5BF)
private val Tuft = Color(0xFF9FBB8A)
private val MudDark = Color(0xFFA87C4F)
private val MudLight = Color(0xFFC79E6E)
private val MudSpeckle = Color(0xFF8A6440)
private val SproutStem = Color(0xFF4F7A48)
private val SproutLeafA = Color(0xFF6FA05E)
private val SproutLeafB = Color(0xFF87B472)
private val LeafDriftA = Color(0xFF94B57C)
private val LeafDriftB = Color(0xFF7AA063)
private val SelectedAmber = Color(0xFFE8A33D)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: MapViewModel = hiltViewModel(), onPlant: (Long) -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var localOnly by rememberSaveable { mutableStateOf(false) }
    var showRecords by rememberSaveable { mutableStateOf(false) }
    var focusId by rememberSaveable { mutableStateOf<Long?>(null) }
    var focusRequest by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    var fitRequest by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    var displayed by remember { mutableStateOf(emptyMap<Long, com.floralens.domain.model.DisplayCoordinate>()) }
    val selected = uiState.pins.find { it.observationId == selectedId }
    val online = uiState.useBaiduMap && !localOnly && uiState.mapPrivacy == com.floralens.data.settings.MapPrivacyChoice.GRANTED
    LaunchedEffect(uiState.loaded, uiState.pins.map { it.observationId }) {
        if (uiState.loaded && selectedId != null && selected == null) selectedId = null
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (online) "观察地图" else "离线观察地图", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            if (!online) OfflineBadge()
        }
        BaiduMapPrivacyControl(uiState.mapPrivacy, viewModel::setMapPrivacy,
            autoPrompt = uiState.useBaiduMap && !localOnly)
        val permission = mapPermissionNotice()
        if (uiState.invalidLocations > 0) Text("部分位置记录无法显示（${uiState.invalidLocations} 条），原记录已保留。",
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp).testTag("map-invalid-locations"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (uiState.onlineRequested && !uiState.useBaiduMap) Text("在线地图服务当前不可用，以下为离线观察示意。",
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
        Box(Modifier.weight(1f)) {
            if (online) BaiduObservationMap(uiState.pins, { selectedId = it }, agreed = true,
                focusedObservationId = focusId, focusRequest = focusRequest, fitRequest = fitRequest, onFitAll = { fitRequest++ }, hasLocationPermission = permission,
                onProjected = { displayed = it })
            else GardenCanvas(uiState.pins, selectedId) { selectedId = it }

            if (uiState.loaded && uiState.pins.isEmpty()) EmptyMapState(Modifier.align(Alignment.Center).testTag("map-no-location"))
            if (!online && uiState.pins.isNotEmpty()) Surface(Modifier.align(Alignment.TopCenter).padding(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = .94f), shape = MaterialTheme.shapes.medium) {
                Text("离线观察示意 · 非街道底图", Modifier.padding(10.dp), style = MaterialTheme.typography.labelSmall)
            }
            if (selected != null) Column(Modifier.align(Alignment.BottomCenter).padding(12.dp)
                .heightIn(max = 280.dp).verticalScroll(rememberScrollState())
                .padding(bottom = if (online) 32.dp else 0.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                val nearby = ObservationMapProjection.nearby(selected, uiState.pins, if (online) displayed else null)
                Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (nearby.size > 1) androidx.compose.material3.TextButton({
                            val index = nearby.indexOfFirst { it.observationId == selectedId }
                            selectedId = nearby[(index + 1) % nearby.size].observationId
                        }, Modifier.weight(1f).testTag("map-nearby-next")) {
                            Text("附近 ${nearby.size} 条观察 · 查看下一条", style = MaterialTheme.typography.labelSmall)
                        } else Text("已保存的观察地点", Modifier.weight(1f).padding(8.dp), style = MaterialTheme.typography.labelSmall)
                        androidx.compose.material3.TextButton({ selectedId = null }) { Text("收起") }
                    }
                }
                PinCard(selected, { onPlant(selected.plant.id) }, Modifier.testTag("map-selected-record"))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedButton({ showRecords = true },
                Modifier.weight(1f).testTag("map-local-records"), enabled = uiState.pins.isNotEmpty()) {
                Text("本地观察 · ${uiState.pins.size} 条")
            }
            if (uiState.useBaiduMap) androidx.compose.material3.TextButton({ localOnly = !localOnly }) {
                Text(if (localOnly) "在线地图" else "离线示意")
            }
        }
    }
    if (showRecords) androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showRecords = false }) {
        Text("已有观察地点", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge)
        Text("来自已保存记录；浏览地图不会新增位置。", Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall)
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().height(380.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            items(uiState.pins.size, key = { uiState.pins[it].observationId }) { index ->
                val pin = uiState.pins[index]
                PinCard(pin, {
                    selectedId = pin.observationId; focusId = pin.observationId; focusRequest++; showRecords = false
                }, Modifier.testTag("map-record-${pin.observationId}"))
            }
        }
    }
}

// ── Scene ───────────────────────────────────────────────────────────
@Composable
private fun GardenCanvas(
    pins: List<MapPin>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        // ── Projection (unchanged: equirectangular, auto-fit, north up) ──
        val positions: List<Pair<MapPin, Offset>> = remember(pins, widthPx, heightPx) {
            if (pins.isEmpty()) return@remember emptyList()
            val minLat = pins.minOf { it.latitude }
            val maxLat = pins.maxOf { it.latitude }
            val minLng = pins.minOf { it.longitude }
            val maxLng = pins.maxOf { it.longitude }
            val midLat = (minLat + maxLat) / 2
            val lngScale = cos(Math.toRadians(midLat)).coerceAtLeast(0.1)
            val latSpan = (maxLat - minLat).coerceAtLeast(0.005)
            val lngSpan = ((maxLng - minLng) * lngScale).coerceAtLeast(0.005)
            val pad = 0.18f
            pins.map { pin ->
                val xNorm = if (maxLng == minLng) .5f else (((pin.longitude - minLng) * lngScale) / lngSpan).toFloat()
                val yNorm = if (maxLat == minLat) .5f else ((maxLat - pin.latitude) / latSpan).toFloat()
                pin to Offset(
                    x = widthPx * (pad + xNorm * (1 - 2 * pad)),
                    y = heightPx * (pad + yNorm * (1 - 2 * pad)),
                )
            }
        }

        // Trail through the pins + its draw-in progress (once per pin set).
        val trail = remember(positions) {
            buildTrail(positions.map { it.second }, Size(widthPx, heightPx))
        }
        val trailProgress = remember(positions) { Animatable(0f) }
        LaunchedEffect(positions) {
            if (positions.isNotEmpty()) trailProgress.animateTo(1f, tween(1600, easing = FastOutSlowInEasing))
        }

        Canvas(Modifier.fillMaxSize().clipToBounds()) {
            val s = size.width / 720f // design comp is 720 wide; scale decorations
            drawTerrain(s)
            if (positions.isNotEmpty()) {
                drawTrail(trail, trailProgress.value, s)
                positions.forEach { (_, pos) -> drawMudMound(pos, s) }
            }
        }

        // ── Pins: staggered drop-in, tap targets, pulse on selected ──
        positions.forEachIndexed { index, (pin, pos) ->
            key(pin.observationId) {
                val isSelected = pin.observationId == selectedId

                val drop = remember { Animatable(-80f) }
                val fade = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    delay(150L + index * 130L)
                    fade.animateTo(1f, tween(120))
                    drop.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
                }

                if (isSelected) {
                    val pulse = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by pulse.animateFloat(
                        0.7f, 1.55f,
                        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Restart),
                        label = "pulseScale",
                    )
                    val pulseAlpha by pulse.animateFloat(
                        0.75f, 0f,
                        infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
                        label = "pulseAlpha",
                    )
                    Box(
                        Modifier
                            .offset {
                                IntOffset(
                                    (pos.x - with(density) { 38.dp.toPx() }).roundToInt(),
                                    (pos.y - with(density) { 62.dp.toPx() }).roundToInt(),
                                )
                            }
                            .size(76.dp)
                            .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale; alpha = pulseAlpha }
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .border(3.dp, SelectedAmber, CircleShape),
                    )
                }

                IconButton(
                    onClick = { onSelect(pin.observationId) },
                    modifier = Modifier.testTag("map-pin-${pin.observationId}")
                        .offset {
                            IntOffset(
                                (pos.x - with(density) { 24.dp.toPx() }).roundToInt(),
                                (pos.y - with(density) { if (isSelected) 50.dp.toPx() else 44.dp.toPx() }).roundToInt(),
                            )
                        }
                        .graphicsLayer { translationY = drop.value; alpha = fade.value },
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = pin.plant.nickname,
                        tint = if (isSelected) SelectedAmber else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(if (isSelected) 52.dp else 42.dp),
                    )
                }
            }
        }

        DriftingLeaf(
            Modifier.offset {
                IntOffset((widthPx * 0.72f).roundToInt(), (heightPx * 0.26f).roundToInt())
            },
        )
    }
}

// ── Terrain (blobs transcribed from the design comp, scaled) ────────
private fun DrawScope.drawTerrain(s: Float) {
    // paper flecks
    listOf(
        88f to 520f, 212f to 760f, 352f to 300f, 470f to 210f, 612f to 520f,
        662f to 760f, 120f to 960f, 420f to 1060f, 530f to 1240f, 300f to 1290f,
        170f to 1345f, 560f to 640f,
    ).forEach { (x, y) -> drawCircle(Fleck, radius = 2.4f * s, center = Offset(x * s, y * s)) }

    // sage grass patch, top-left
    drawPath(
        Path().apply {
            moveTo(-50f * s, 190f * s)
            cubicTo(50f * s, 140f * s, 240f * s, 150f * s, 305f * s, 250f * s)
            cubicTo(355f * s, 330f * s, 290f * s, 430f * s, 170f * s, 458f * s)
            cubicTo(50f * s, 486f * s, -60f * s, 430f * s, -80f * s, 330f * s)
            close()
        },
        GrassA,
    )

    // light meadow, center-right
    drawPath(
        Path().apply {
            moveTo(390f * s, 560f * s)
            cubicTo(470f * s, 495f * s, 650f * s, 505f * s, 740f * s, 590f * s)
            lineTo(740f * s, 870f * s)
            cubicTo(650f * s, 930f * s, 470f * s, 910f * s, 405f * s, 805f * s)
            cubicTo(360f * s, 730f * s, 345f * s, 625f * s, 390f * s, 560f * s)
            close()
        },
        MeadowA,
    )
    drawPath(
        Path().apply {
            moveTo(480f * s, 620f * s)
            cubicTo(545f * s, 585f * s, 660f * s, 595f * s, 710f * s, 655f * s)
            cubicTo(745f * s, 700f * s, 715f * s, 780f * s, 630f * s, 800f * s)
            cubicTo(545f * s, 820f * s, 465f * s, 775f * s, 460f * s, 705f * s)
            cubicTo(458f * s, 670f * s, 462f * s, 640f * s, 480f * s, 620f * s)
            close()
        },
        MeadowB,
    )

    // sage grass patch, bottom-left
    drawPath(
        Path().apply {
            moveTo(-70f * s, 1035f * s)
            cubicTo(40f * s, 985f * s, 225f * s, 1010f * s, 268f * s, 1120f * s)
            cubicTo(300f * s, 1205f * s, 210f * s, 1292f * s, 85f * s, 1302f * s)
            cubicTo(-25f * s, 1310f * s, -85f * s, 1250f * s, -95f * s, 1155f * s)
            close()
        },
        GrassB,
    )

    // pond
    drawOval(PondOuter, topLeft = Offset(100f * s, 812f * s), size = Size(160f * s, 126f * s))
    drawOval(PondInner, topLeft = Offset(120f * s, 830f * s), size = Size(118f * s, 90f * s))
    drawArc(
        PondRipple, startAngle = 200f, sweepAngle = 140f, useCenter = false,
        topLeft = Offset(148f * s, 866f * s), size = Size(30f * s, 16f * s),
        style = Stroke(3f * s, cap = StrokeCap.Round),
    )
    drawArc(
        PondRipple, startAngle = 200f, sweepAngle = 140f, useCenter = false,
        topLeft = Offset(174f * s, 890f * s), size = Size(26f * s, 14f * s),
        style = Stroke(3f * s, cap = StrokeCap.Round),
    )

    // grass tufts
    listOf(64f to 606f, 618f to 296f, 486f to 1256f, 238f to 986f).forEach { (tx, ty) ->
        val o = Offset(tx * s, ty * s)
        val stroke = Stroke(3.5f * s, cap = StrokeCap.Round)
        drawPath(Path().apply { moveTo(o.x, o.y); cubicTo(o.x - 2 * s, o.y - 10 * s, o.x - 5 * s, o.y - 14 * s, o.x - 8 * s, o.y - 18 * s) }, Tuft, style = stroke)
        drawPath(Path().apply { moveTo(o.x + 5 * s, o.y + 1 * s); cubicTo(o.x + 5 * s, o.y - 12 * s, o.x + 5 * s, o.y - 17 * s, o.x + 4 * s, o.y - 23 * s) }, Tuft, style = stroke)
        drawPath(Path().apply { moveTo(o.x + 10 * s, o.y); cubicTo(o.x + 13 * s, o.y - 10 * s, o.x + 16 * s, o.y - 13 * s, o.x + 19 * s, o.y - 16 * s) }, Tuft, style = stroke)
    }
}

// ── Trail: generated through real pin positions, draw-in animated ───
private fun buildTrail(points: List<Offset>, canvas: Size): Path {
    val path = Path()
    if (points.isEmpty()) return path
    val sorted = points.sortedBy { it.y }
    path.moveTo(sorted.first().x, -60f)
    var prev = Offset(sorted.first().x, -60f)
    sorted.forEachIndexed { i, p ->
        val sway = if (i % 2 == 0) -1f else 1f
        path.quadraticTo(
            (prev.x + p.x) / 2f + sway * canvas.width * 0.18f,
            (prev.y + p.y) / 2f,
            p.x, p.y,
        )
        prev = p
    }
    path.quadraticTo(
        prev.x + canvas.width * 0.14f,
        (prev.y + canvas.height + 60f) / 2f,
        (prev.x + canvas.width * 0.35f).coerceAtMost(canvas.width * 0.92f),
        canvas.height + 60f,
    )
    return path
}

private fun DrawScope.drawTrail(trail: Path, progress: Float, s: Float) {
    if (progress <= 0f) return
    val measure = PathMeasure().apply { setPath(trail, false) }
    val visible = Path()
    measure.getSegment(0f, measure.length * progress, visible, true)

    // dotted darker edges under the solid tread (design: 38/28 wide, dash 3/21)
    drawPath(
        visible, PathEdge.copy(alpha = 0.85f),
        style = Stroke(
            width = 38f * s, cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * s, 21f * s)),
        ),
    )
    drawPath(visible, PathTread, style = Stroke(width = 28f * s, cap = StrokeCap.Round))

    // stones alongside, appearing as the trail reaches them
    listOf(0.16f, 0.46f, 0.78f).forEach { f ->
        if (progress >= f) {
            val pos = measure.getPosition(measure.length * f)
            val side = Offset(pos.x + 26f * s, pos.y + 8f * s)
            drawOval(Stone, topLeft = Offset(side.x - 7f * s, side.y - 5f * s), size = Size(14f * s, 10f * s))
            drawOval(Stone, topLeft = Offset(side.x + 8f * s, side.y - 1f * s), size = Size(9f * s, 6f * s))
        }
    }
}

// ── Mud mound + sprout under a pin tip ──────────────────────────────
private fun DrawScope.drawMudMound(tip: Offset, s: Float) {
    drawOval(MudDark, topLeft = Offset(tip.x - 38f * s, tip.y - 6f * s), size = Size(76f * s, 26f * s))
    drawOval(MudLight, topLeft = Offset(tip.x - 35f * s, tip.y - 9.5f * s), size = Size(70f * s, 23f * s))
    listOf(-15f to 1f, 5f to 5f, 17f to -1f).forEach { (dx, dy) ->
        drawCircle(MudSpeckle, radius = 2.4f * s, center = Offset(tip.x + dx * s, tip.y + dy * s))
    }
    // sprout beside the mound
    val sx = tip.x + 37f * s
    val sy = tip.y + 1f * s
    drawPath(
        Path().apply { moveTo(sx, sy); cubicTo(sx, sy - 8 * s, sx - 1 * s, sy - 13 * s, sx - 2 * s, sy - 18 * s) },
        SproutStem, style = Stroke(3f * s, cap = StrokeCap.Round),
    )
    drawPath(
        Path().apply {
            moveTo(sx - 2 * s, sy - 15 * s)
            cubicTo(sx - 10 * s, sy - 19 * s, sx - 14 * s, sy - 25 * s, sx - 14 * s, sy - 31 * s)
            cubicTo(sx - 6 * s, sy - 31 * s, sx - 1 * s, sy - 24 * s, sx - 2 * s, sy - 15 * s)
            close()
        },
        SproutLeafA,
    )
    drawPath(
        Path().apply {
            moveTo(sx - 1 * s, sy - 12 * s)
            cubicTo(sx + 6 * s, sy - 16 * s, sx + 11 * s, sy - 22 * s, sx + 11 * s, sy - 28 * s)
            cubicTo(sx + 3 * s, sy - 28 * s, sx - 2 * s, sy - 20 * s, sx - 1 * s, sy - 12 * s)
            close()
        },
        SproutLeafB,
    )
}

// ── Drifting leaf flourish ──────────────────────────────────────────
@Composable
private fun DriftingLeaf(modifier: Modifier = Modifier) {
    val drift = rememberInfiniteTransition(label = "leafDrift")
    val t by drift.animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "leafT",
    )
    Canvas(modifier.size(30.dp)) {
        val dx = -14f * sin(t) * density
        val dy = 12f * (1f - cos(t)) * density
        rotate(degrees = 18f * sin(t * 1.3f), pivot = center) {
            val p = Path().apply {
                moveTo(4f * density + dx, 26f * density + dy)
                cubicTo(
                    4f * density + dx, 14f * density + dy,
                    14f * density + dx, 4f * density + dy,
                    27f * density + dx, 3f * density + dy,
                )
                cubicTo(
                    27f * density + dx, 16f * density + dy,
                    18f * density + dx, 26f * density + dy,
                    6f * density + dx, 26f * density + dy,
                )
                close()
            }
            drawPath(p, LeafDriftA)
            drawPath(
                Path().apply {
                    moveTo(7f * density + dx, 24f * density + dy)
                    cubicTo(
                        12f * density + dx, 18f * density + dy,
                        18f * density + dx, 12f * density + dy,
                        24f * density + dx, 7f * density + dy,
                    )
                },
                LeafDriftB, style = Stroke(2f * density, cap = StrokeCap.Round),
            )
        }
    }
}

// ── Offline badge ───────────────────────────────────────────────────
@Composable
private fun OfflineBadge(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            "离线 · 本地绘制",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ── Pin card (unchanged from before) ────────────────────────────────
@Composable
private fun PinCard(pin: MapPin, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dateText = remember(pin.timestamp) {
        DateTimeFormatter.ofPattern("yyyy.MM.dd · HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(pin.timestamp)
    }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(Spacing.md),
        ) {
            AsyncImage(
            model = pin.displayPhotoUri,
                contentDescription = pin.plant.nickname,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(pin.plant.nickname, style = MaterialTheme.typography.titleSmall)
                Text(com.floralens.ui.presentation.PlantDisplayNames.chinese(pin.plant.scientificName, pin.plant.commonName),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    pin.plant.scientificName,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "观察于 $dateText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                pin.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

// ── Empty state ─────────────────────────────────────────────────────
@Composable
private fun EmptyMapState(modifier: Modifier = Modifier) {
    Surface(modifier.padding(16.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface.copy(alpha = .96f)) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(Spacing.xl),
    ) {
        Icon(
            Icons.Filled.Map,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(Spacing.md))
        Text("还没有带位置的观察记录。", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "拍摄时允许记录位置，便能在这里回望相遇的地点。" +
                    "不记录位置，也可以继续观察与收藏。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    }
}

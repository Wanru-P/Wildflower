package com.floralens.ui.screens.identify

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.floralens.ui.theme.Editorial
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.floralens.domain.model.IdCandidate
import com.floralens.domain.model.IdResult
import com.floralens.domain.model.IdentificationMode
import com.floralens.ui.components.PrivacyBadge
import com.floralens.ui.theme.FloraLensTheme
import com.floralens.ui.theme.Spacing
import androidx.compose.material.icons.filled.Tune
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import com.floralens.R
import com.floralens.ui.components.KitIcon
import com.floralens.ui.components.BotanicalEmptyArtwork
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.sp

/**
 * Camera / Identify — dark themed regardless of system setting.
 *
 * Viewfinder additions (works identically in OFFLINE and ONLINE mode — the
 * framing heuristic below is model-free and never touches the network):
 *  - Smart framing guide: the dashed white box turns solid green when a plant
 *    is centered, close enough, sharp, and well lit; a hint chip gives the
 *    user a concrete direction ("Aim left", "Move closer", "Hold steady"…).
 *  - Torch toggle (only shown when the device has a flash unit).
 *  - Tap-to-focus with an animated focus ring; capture uses MAXIMIZE_QUALITY.
 *  - Freeze-on-capture: the moment the shutter fires, the captured photo is
 *    drawn over the live preview so the screen stops moving while the
 *    backend (on-device or Pl@ntNet) identifies it. Retake unfreezes.
 */
@Composable
fun IdentifyScreen(
    viewModel: IdentifyViewModel = hiltViewModel(),
    onDetails: (String) -> Unit = {},
    onChat: (String) -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(uiState.navigationCaptureId) {
        uiState.navigationCaptureId?.let { onDetails(it); viewModel.consumeNavigation() }
    }
    LaunchedEffect(uiState.navigationChatCaptureId) {
        uiState.navigationChatCaptureId?.let { onChat(it); viewModel.consumeChatNavigation() }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                viewModel.onResume()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.usedOfflineFallback) {
        if (uiState.usedOfflineFallback) {
            snackbarHostState.showSnackbar("网络不可用，已使用本机识别")
            viewModel.consumeFallbackNotice()
        }
    }

    FloraLensTheme(darkTheme = true) {
        Scaffold(
            containerColor = Color(0xFF0A1A12),
            contentWindowInsets = WindowInsets(0, 0, 0, 0), // outer NavHost already handles system bars
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (!hasCameraPermission) {
                    PermissionDeniedContent(
                        onRequestAgain = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        onOpenSettings = { context.openAppSettings() },
                    )
                } else {
                    CameraViewfinder(
                        onSettings = onSettings,
                        isProcessing = uiState.isProcessing,
                        capturedImage = uiState.capturedImage,
                        liveCandidate = uiState.liveCandidate,
                        identificationMode = uiState.identificationMode,
                        onCapture = viewModel::onCapture,
                        onLiveFrame = viewModel::onLiveFrame,
                    )
                    if (uiState.result is IdResult.NoPlantDetected) {
                        NoPlantDetectedOverlay(
                            onDismiss = viewModel::dismissResult,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    uiState.error?.let { message ->
                        Column(Modifier.align(Alignment.Center).background(Color(0xEE10291A)).padding(24.dp)) {
                            Text(message, color = Color.White)
                            Button(viewModel::retry) { Text("重试识别") }
                            TextButton(viewModel::dismissResult) { Text("重新拍摄") }
                        }
                    }
                }
            }

            val result = uiState.result
            if (result != null && result != IdResult.NoPlantDetected) {
                IdentifyResultSheet(
                    result = result,
                    capturedImage = uiState.capturedImage,
                    isSaving = uiState.isSaving,
                    onAdd = viewModel::openDetails,
                    onChat = viewModel::openChat,
                    onAddManually = viewModel::openManualDetails,
                    onRetake = viewModel::dismissResult,
                    photoUri = uiState.photoUri,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Framing-quality heuristic
// ─────────────────────────────────────────────────────────────────────────

/** What the viewfinder currently sees, in priority order. */
private enum class FrameStatus { LOW_LIGHT, SEARCHING, OFF_CENTER, MOVE_CLOSER, HOLD_STEADY, GOOD }

/**
 * All the knobs in one place — tune these on-device.
 * The analyzer samples a sparse grid of pixels, converts YUV→RGB, and scores
 * the frame on vegetation coverage, centering, sharpness, and brightness.
 */
private object FrameTuning {
    /** Mean luma below this → LOW_LIGHT ("turn on the torch"). */
    const val LOW_LIGHT_LUMA = 55.0

    /** Fraction of *any* sampled pixel that is green-ish below this → SEARCHING. */
    const val MIN_GREEN_ANYWHERE = 0.05

    /** Green fraction *inside the guide box* below this → MOVE_CLOSER. */
    const val MIN_GREEN_IN_GUIDE = 0.18

    /** Normalized centroid offset (display coords) beyond this → OFF_CENTER. */
    const val CENTROID_OFFSET = 0.20

    /** Mean |ΔY| between neighboring luma samples below this → HOLD_STEADY (blurry). */
    const val MIN_SHARPNESS = 7.0

    /** Green-dominance margin: G must beat R and B by this much (0-255 scale). */
    const val GREEN_MARGIN = 12

    /** Don't publish a status change faster than this. */
    const val PUBLISH_INTERVAL_MS = 250L
}

/** Result of analyzing a single frame. */
private data class FrameFeedback(val status: FrameStatus, val aimDirection: String? = null)

@Composable
private fun CameraViewfinder(
    isProcessing: Boolean,
    capturedImage: ByteArray?,
    liveCandidate: com.floralens.domain.model.IdCandidate?,
    identificationMode: IdentificationMode,
    onCapture: (ByteArray) -> Unit,
    onLiveFrame: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    onSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val imageCapture = remember {
        ImageCapture.Builder()
            // Sharper stills for the classifier — worth the extra ~100ms.
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var focusRingAt by remember { mutableStateOf<Offset?>(null) }
    var lastLiveFrameAt by remember { mutableStateOf(0L) }
    // Pinch zoom — ratio mirrors CameraX's zoomState; indicator shows briefly.
    var zoomRatio by remember { mutableStateOf(1f) }
    var zoomIndicatorVisible by remember { mutableStateOf(false) }

    // Frame feedback published from the analyzer thread (MutableState is
    // snapshot-safe to write off the main thread — same pattern as avgLuma was).
    var feedback by remember { mutableStateOf(FrameFeedback(FrameStatus.SEARCHING)) }

    // The screen is "frozen" whenever a captured photo exists in state —
    // set the instant the shutter callback fires, cleared on retake/save.
    val isFrozen = capturedImage != null
    val frozenState = rememberUpdatedState(isFrozen)

    // Live classification runs offline only — online mode would burn the
    // Pl@ntNet quota at ~2fps. rememberUpdatedState lets the analyzer closure
    // (created once, at bind time) always read the *current* mode without
    // rebinding the camera when the mode changes.
    val liveEnabled = identificationMode == IdentificationMode.OFFLINE
    val liveEnabledState = rememberUpdatedState(liveEnabled)
    val onLiveFrameState = rememberUpdatedState(onLiveFrame)

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    // Haptic tick when the box locks green — a physical "you're good" cue.
    LaunchedEffect(feedback.status) {
        if (feedback.status == FrameStatus.GOOD) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // Shutter flash — brief white blink at the moment of capture.
    val shutterAlpha = remember { Animatable(0f) }

    // Focus ring auto-dismiss.
    LaunchedEffect(focusRingAt) {
        if (focusRingAt != null) {
            delay(900)
            focusRingAt = null
        }
    }

    // One initial center AF/AE metering once the camera is bound, so the
    // first seconds aren't spent focused on the background.
    LaunchedEffect(camera) {
        val cam = camera ?: return@LaunchedEffect
        val pv = previewViewRef ?: return@LaunchedEffect
        val point = pv.meteringPointFactory.createPoint(pv.width / 2f, pv.height / 2f)
        val action = FocusMeteringAction.Builder(point)
            .setAutoCancelDuration(2, TimeUnit.SECONDS)
            .build()
        runCatching { cam.cameraControl.startFocusAndMetering(action) }
    }

    // Zoom indicator auto-hide.
    LaunchedEffect(zoomIndicatorVisible, zoomRatio) {
        if (zoomIndicatorVisible) {
            delay(1200)
            zoomIndicatorVisible = false
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val guideHeightFraction = (((maxHeight.value - 286f) * .82f) / maxHeight.value).coerceIn(.28f, .58f)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                // Binding happens HERE, inside factory, against this
                // PreviewView's own live surfaceProvider. Doing it in a
                // separate LaunchedEffect races the surface and leaves a
                // frozen first frame until a recomposition — which is what
                // caused the "switch tabs to fix it" bug.
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                previewViewRef = previewView

                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()

                    val preview = Preview.Builder().build().apply {
                        setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .apply {
                            setAnalyzer(cameraExecutor) { image ->
                                // While frozen there's nothing useful to compute.
                                if (!frozenState.value) {
                                    runCatching { image.analyzeFraming() }
                                        .getOrNull()
                                        ?.let { feedback = it }

                                    val now = System.currentTimeMillis()
                                    if (liveEnabledState.value && now - lastLiveFrameAt >= 450L) {
                                        lastLiveFrameAt = now
                                        runCatching { image.toJpegBytes() }.getOrNull()
                                            ?.let(onLiveFrameState.value)
                                    }
                                }
                                image.close()
                            }
                        }

                    runCatching {
                        provider.unbindAll()
                        camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                            analysis,
                        )
                    }.onFailure { Log.e("IdentifyScreen", "Camera bind failed", it) }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
        )

        // Tap-to-focus + pinch-to-zoom layer — sits over the preview, under
        // all controls, so buttons still receive their own taps. Two separate
        // pointerInput blocks: tap detection and transform (pinch) detection
        // coexist without stealing each other's events. Disabled while frozen.
        if (!isFrozen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(camera) {
                        detectTapGestures { offset ->
                            val cam = camera ?: return@detectTapGestures
                            val pv = previewViewRef ?: return@detectTapGestures
                            val point = pv.meteringPointFactory.createPoint(offset.x, offset.y)
                            val action = FocusMeteringAction.Builder(
                                point,
                                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
                            )
                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                .build()
                            runCatching { cam.cameraControl.startFocusAndMetering(action) }
                            focusRingAt = offset
                        }
                    }
                    .pointerInput(camera) {
                        detectTransformGestures { _, _, zoomChange, _ ->
                            val cam = camera ?: return@detectTransformGestures
                            val state = cam.cameraInfo.zoomState.value
                            val min = state?.minZoomRatio ?: 1f
                            val max = state?.maxZoomRatio ?: 1f
                            val newRatio = (zoomRatio * zoomChange).coerceIn(min, max)
                            if (newRatio != zoomRatio) {
                                zoomRatio = newRatio
                                zoomIndicatorVisible = true
                                runCatching { cam.cameraControl.setZoomRatio(newRatio) }
                            }
                        }
                    },
            )
        }

        // ── FREEZE OVERLAY ─────────────────────────────────────────────
        // The captured JPEG drawn over the live preview: the screen stops
        // moving the instant the shutter fires. Coil applies EXIF rotation.
        if (isFrozen) {
            AsyncImage(
                model = capturedImage,
                contentDescription = "Captured photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (isProcessing) {
                // Decoding animation — scanning beam over the frozen still.
                PlantDecodingOverlay()
            }
        }

        // ── LIVE OVERLAYS — hidden while frozen ────────────────────────
        if (!isFrozen) {
            val isGood = feedback.status == FrameStatus.GOOD
            val guideColor by animateColorAsState(
                targetValue = if (isGood) Color(0xFFDCE6D6) else Color.White.copy(alpha = 0.55f),
                animationSpec = tween(220),
                label = "guideColor",
            )

            // Framing guide — dashed white while searching, solid green when
            // the leaf is placed well.
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .fillMaxHeight(guideHeightFraction)
                        .framingBorder(
                            color = guideColor,
                            cornerRadiusDp = 22.dp,
                            dashed = !isGood,
                            strokeWidthDp = if (isGood) 3.dp else 2.dp,
                        ),
                )
                Spacer(Modifier.height(Spacing.md))
                FramingHintChip(feedback = feedback)
            }

            Box(Modifier.fillMaxWidth().height(110.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .62f), Color.Transparent))))
            Column(Modifier.align(Alignment.TopCenter).padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("识别植物", style = Editorial.sectionTitle.copy(fontSize = 21.sp), color = Color.White)
                Text("WILDFLOWER LENS", style = Editorial.eyebrow, color = Color.White.copy(alpha = .7f))
            }
            IconButton(onSettings, Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 12.dp)) { KitIcon(R.drawable.wf_icon_filter, description = "识别设置", tint = Color.White) }
            PrivacyBadge(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 20.dp, top = 76.dp),
                icon = if (identificationMode == IdentificationMode.ONLINE) Icons.Filled.CloudQueue else Icons.Filled.Lock,
                label = if (identificationMode == IdentificationMode.ONLINE) "在线 · Pl@ntNet" else "离线 · 本机识别",
                online = identificationMode == IdentificationMode.ONLINE,
            )

            // Torch toggle — only when the device actually has a flash unit.
            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                IconButton(
                    onClick = {
                        torchOn = !torchOn
                        camera?.cameraControl?.enableTorch(torchOn)
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 76.dp, end = 20.dp)
                        .clip(CircleShape)
                        .background(
                            if (torchOn) Color(0xFFFFDFA6).copy(alpha = 0.9f)
                            else Color.Black.copy(alpha = 0.35f),
                        ),
                ) {
                    Icon(
                        imageVector = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = if (torchOn) "关闭补光" else "开启补光",
                        tint = if (torchOn) Color(0xFF452B00) else Color.White,
                    )
                }
            }

            // Live overlay chip — offline only.
            AnimatedVisibility(
                visible = liveEnabled && liveCandidate != null && !isProcessing,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                            .padding(top = 126.dp, start = 20.dp, end = 20.dp),
            ) {
                liveCandidate?.let { candidate ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.Black.copy(alpha = 0.62f))
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            "可能是 · ${com.floralens.ui.presentation.PlantDisplayNames.chinese(candidate.scientificName, candidate.commonName)} · ${(candidate.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                }
            }

            // Zoom indicator — brief "2.1×" chip while pinching.
            AnimatedVisibility(
                visible = zoomIndicatorVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 124.dp),
            ) {
                Text(
                    text = String.format("%.1f×", zoomRatio),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
            }

            // Focus ring at the tap point.
            focusRingAt?.let { at ->
                val density = LocalDensity.current
                val ringSize = 64.dp
                val halfPx = with(density) { (ringSize / 2).toPx() }
                Box(
                    modifier = Modifier
                        .offset { IntOffset((at.x - halfPx).roundToInt(), (at.y - halfPx).roundToInt()) }
                        .size(ringSize)
                        .border(2.dp, Color(0xFFFFDFA6), CircleShape),
                )
            }
        }

        // Shutter flash overlay.
        if (shutterAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(shutterAlpha.value)
                    .background(Color.White),
            )
        }

        // Capture button — hidden once frozen (the still + result sheet take over).
        if (!isFrozen) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .65f)))))
            // On a short viewport the framing hint already fills this strip; keep the shutter clear.
            if (maxHeight >= 600.dp) Text("轻触对焦 · 双指缩放", Modifier.align(Alignment.BottomCenter).padding(bottom = 116.dp), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .8f))
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
            ) {
                CaptureButton(
                    enabled = !isProcessing,
                    isProcessing = isProcessing,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // Torch off so it doesn't stay burning behind the frozen frame.
                        if (torchOn) {
                            torchOn = false
                            camera?.cameraControl?.enableTorch(false)
                        }
                        imageCapture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val buffer = image.planes[0].buffer
                                    val bytes = ByteArray(buffer.remaining())
                                    buffer.get(bytes)
                                    image.close()
                                    onCapture(bytes)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    Log.e("IdentifyScreen", "Capture failed", exception)
                                }
                            },
                        )
                    },
                )
            }
        }
    }

    // Fire the shutter flash whenever we transition into frozen.
    LaunchedEffect(isFrozen) {
        if (isFrozen) {
            shutterAlpha.snapTo(0.65f)
            shutterAlpha.animateTo(0f, animationSpec = tween(260))
        }
    }
}

/**
 * "Decoding plant…" overlay shown while identification is in flight.
 * Deliberately mode-agnostic — no engine names, just a scanning beam
 * sweeping the frozen capture with a pulsing glyph and stable processing text.
 */
@Composable
private fun PlantDecodingOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "decode")

    // Beam sweeps top→bottom→top.
    val beamY by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "beamY",
    )
    // Pulsing glyph.
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val beamColor = Color(0xFFDCE6D6)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.30f)),
    ) {
        val beamBandHeight = 120.dp
        val travel = maxHeight - beamBandHeight
        // Glow band trailing the beam core.
        Box(
            modifier = Modifier
                .offset(y = travel * beamY)
                .fillMaxWidth()
                .height(beamBandHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            beamColor.copy(alpha = 0.22f),
                            beamColor.copy(alpha = 0.45f),
                            beamColor.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        // Bright core line at the band's center.
        Box(
            modifier = Modifier
                .offset(y = travel * beamY + beamBandHeight / 2)
                .fillMaxWidth()
                .height(2.dp)
                .background(beamColor.copy(alpha = 0.95f)),
        )

        // Center chip: pulsing glyph + "Decoding plant…"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        ) {
            Icon(
                imageVector = Icons.Filled.Eco,
                contentDescription = null,
                tint = beamColor,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    },
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                "正在识别植物",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
        }
    }
}

/** Hint chip under the framing guide — one concrete instruction at a time. */
@Composable
private fun FramingHintChip(feedback: FrameFeedback, modifier: Modifier = Modifier) {
    val (text, accent) = when (feedback.status) {
        FrameStatus.LOW_LIGHT -> "光线偏暗，请补充光线" to Color(0xFFFFDFA6)
        FrameStatus.SEARCHING -> "将镜头对准一株植物" to Color.White
        FrameStatus.OFF_CENTER -> "让植物位于画面中央" to Color.White
        FrameStatus.MOVE_CLOSER -> "靠近一些，让植物填满取景框" to Color.White
        FrameStatus.HOLD_STEADY -> "保持镜头稳定" to Color.White
        FrameStatus.GOOD -> "构图合适，点击拍摄" to Color(0xFFDCE6D6)
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CaptureButton(enabled: Boolean, isProcessing: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White.copy(alpha = 0.85f), CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .semantics { contentDescription = "拍摄植物" }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isProcessing) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun PermissionDeniedContent(onRequestAgain: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BotanicalEmptyArtwork(R.drawable.wf_empty_identification, Modifier.size(140.dp))
        Spacer(Modifier.height(Spacing.lg))
        Text("开启镜头，发现自然", style = Editorial.sectionTitle, color = Color.White)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "草木知时需要相机权限。离线模式在本机识别；在线模式会将照片发送至 Pl@ntNet。",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        Button(onClick = onRequestAgain, modifier = Modifier.fillMaxWidth(0.8f), shape = RoundedCornerShape(28.dp)) {
            Text("允许使用相机")
        }
        Spacer(Modifier.height(Spacing.sm))
        TextButton(onClick = onOpenSettings) {
            Text("打开系统设置", color = Color.White)
        }
    }
}

@Composable
private fun NoPlantDetectedOverlay(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KitIcon(R.drawable.wf_icon_alert, modifier = Modifier.size(40.dp), tint = Color.White.copy(alpha = .85f))
        Spacer(Modifier.height(Spacing.sm))
        Text("暂未发现植物", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "靠近一些，让植物位于画面中央。",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.sm))
        TextButton(onClick = onDismiss) { Text("再试一次", color = Color.White) }
    }
}

/** Dashed (searching) or solid (locked) rounded-rect border for the framing guide. */
private fun Modifier.framingBorder(
    color: Color,
    cornerRadiusDp: Dp,
    dashed: Boolean,
    strokeWidthDp: Dp = 2.dp,
): Modifier =
    this.drawBehind {
        drawRoundRect(
            color = color,
            style = Stroke(
                width = strokeWidthDp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f) else null,
            ),
            cornerRadius = CornerRadius(cornerRadiusDp.toPx(), cornerRadiusDp.toPx()),
        )
        if (dashed) {
            val length = 36.dp.toPx()
            listOf(Offset.Zero, Offset(size.width - length, 0f), Offset(0f, size.height - length), Offset(size.width - length, size.height - length)).forEach { origin ->
                clipRect(origin.x, origin.y, origin.x + length, origin.y + length) {
                    drawRoundRect(color = Color.White.copy(alpha = .85f), cornerRadius = CornerRadius(cornerRadiusDp.toPx(), cornerRadiusDp.toPx()), style = Stroke(strokeWidthDp.toPx()))
                }
            }
        }
    }

// ─────────────────────────────────────────────────────────────────────────
// Frame analysis (runs on the camera executor — keep it cheap)
// ─────────────────────────────────────────────────────────────────────────

/** Hysteresis: only publish a status after seeing it twice in a row. */
private var pendingStatus: FrameStatus? = null
private var lastPublished: FrameFeedback? = null
private var lastPublishAt = 0L

/**
 * Model-free framing heuristic. Samples a sparse grid inside the central
 * region of the YUV frame, converts to RGB, and scores:
 *  - vegetation ratio (green-dominant pixels), anywhere + inside the guide
 *  - green centroid → directional "aim left/right/up/down" hint
 *  - sharpness proxy (mean |ΔY| between neighboring luma samples)
 *  - mean luminance
 * Works in ONLINE mode too — never calls a model or the network.
 */
private fun ImageProxy.analyzeFraming(): FrameFeedback? {
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]
    val yBuf = yPlane.buffer
    val uBuf = uPlane.buffer
    val vBuf = vPlane.buffer

    // Wide region (centroid + "any green") and inner region ≈ the guide box.
    val wx0 = (width * 0.08).toInt(); val wx1 = (width * 0.92).toInt()
    val wy0 = (height * 0.08).toInt(); val wy1 = (height * 0.92).toInt()
    val ix0 = (width * 0.22).toInt(); val ix1 = (width * 0.78).toInt()
    val iy0 = (height * 0.24).toInt(); val iy1 = (height * 0.76).toInt()

    val step = maxOf(4, width / 72)

    var samples = 0
    var lumaSum = 0L
    var gradSum = 0L
    var gradSamples = 0
    var greenAny = 0
    var greenInner = 0
    var innerSamples = 0
    var greenXSum = 0L
    var greenYSum = 0L

    var row = wy0
    while (row < wy1) {
        val yRowBase = row * yPlane.rowStride
        val cRow = row / 2
        var col = wx0
        while (col < wx1) {
            val y = yBuf.get(yRowBase + col).toInt() and 0xFF
            val uIdx = cRow * uPlane.rowStride + (col / 2) * uPlane.pixelStride
            val vIdx = cRow * vPlane.rowStride + (col / 2) * vPlane.pixelStride
            val u = (uBuf.get(uIdx).toInt() and 0xFF) - 128
            val v = (vBuf.get(vIdx).toInt() and 0xFF) - 128

            // Fast integer YUV→RGB.
            val r = y + (1436 * v shr 10)
            val g = y - (352 * u shr 10) - (731 * v shr 10)
            val b = y + (1814 * u shr 10)

            val inInner = col in ix0 until ix1 && row in iy0 until iy1
            samples++
            if (inInner) innerSamples++
            lumaSum += y

            val isGreen = g > r + FrameTuning.GREEN_MARGIN &&
                    g > b + FrameTuning.GREEN_MARGIN && g > 50
            if (isGreen) {
                greenAny++
                greenXSum += col
                greenYSum += row
                if (inInner) greenInner++
            }

            // Sharpness only matters where the subject is — inner region.
            if (inInner && col + 2 < width) {
                val yRight = yBuf.get(yRowBase + col + 2).toInt() and 0xFF
                gradSum += abs(y - yRight)
                gradSamples++
            }
            col += step
        }
        row += step
    }
    if (samples == 0) return null

    val meanLuma = lumaSum.toDouble() / samples
    val greenAnyRatio = greenAny.toDouble() / samples
    val greenInnerRatio = if (innerSamples > 0) greenInner.toDouble() / innerSamples else 0.0
    val sharpness = if (gradSamples > 0) gradSum.toDouble() / gradSamples else 0.0

    // Green centroid → normalized offset from center, mapped through the
    // sensor rotation so "left/right/up/down" match what's on screen.
    var aim: String? = null
    if (greenAny > 0) {
        val cx = greenXSum.toDouble() / greenAny
        val cy = greenYSum.toDouble() / greenAny
        val dx = (cx - width / 2.0) / width
        val dy = (cy - height / 2.0) / height
        val (ddx, ddy) = when (imageInfo.rotationDegrees) {
            90 -> -dy to dx
            180 -> -dx to -dy
            270 -> dy to -dx
            else -> dx to dy
        }
        aim = when {
            abs(ddx) < FrameTuning.CENTROID_OFFSET && abs(ddy) < FrameTuning.CENTROID_OFFSET -> null
            abs(ddx) >= abs(ddy) -> if (ddx < 0) "left" else "right"
            else -> if (ddy < 0) "up" else "down"
        }
    }

    val status = when {
        meanLuma < FrameTuning.LOW_LIGHT_LUMA -> FrameStatus.LOW_LIGHT
        greenAnyRatio < FrameTuning.MIN_GREEN_ANYWHERE -> FrameStatus.SEARCHING
        aim != null -> FrameStatus.OFF_CENTER
        greenInnerRatio < FrameTuning.MIN_GREEN_IN_GUIDE -> FrameStatus.MOVE_CLOSER
        sharpness < FrameTuning.MIN_SHARPNESS -> FrameStatus.HOLD_STEADY
        else -> FrameStatus.GOOD
    }

    // Hysteresis + throttle so the box/hint don't flicker.
    val now = System.currentTimeMillis()
    if (status != lastPublished?.status) {
        if (pendingStatus != status) {
            pendingStatus = status
            return lastPublished // wait for a second confirming frame
        }
    }
    pendingStatus = null
    if (status == lastPublished?.status && now - lastPublishAt < FrameTuning.PUBLISH_INTERVAL_MS) {
        return lastPublished
    }
    lastPublishAt = now
    val fb = FrameFeedback(status, if (status == FrameStatus.OFF_CENTER) aim else null)
    lastPublished = fb
    return fb
}

/**
 * ImageAnalysis frame (YUV_420_888) → rotated JPEG bytes for the live
 * classifier. Stride-aware NV21 assembly, then native YuvImage compression;
 * rotation applied so the model sees the frame upright.
 */
private fun ImageProxy.toJpegBytes(quality: Int = 80): ByteArray {
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val nv21 = ByteArray(width * height * 3 / 2)

    var pos = 0
    val yBuffer = yPlane.buffer
    for (row in 0 until height) {
        yBuffer.position(row * yPlane.rowStride)
        yBuffer.get(nv21, pos, width)
        pos += width
    }

    val chromaHeight = height / 2
    val chromaWidth = width / 2
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer
    for (row in 0 until chromaHeight) {
        for (col in 0 until chromaWidth) {
            val vIndex = row * vPlane.rowStride + col * vPlane.pixelStride
            val uIndex = row * uPlane.rowStride + col * uPlane.pixelStride
            nv21[pos++] = vBuffer.get(vIndex)
            nv21[pos++] = uBuffer.get(uIndex)
        }
    }

    val jpegStream = ByteArrayOutputStream()
    YuvImage(nv21, ImageFormat.NV21, width, height, null)
        .compressToJpeg(Rect(0, 0, width, height), quality, jpegStream)
    val jpeg = jpegStream.toByteArray()

    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return jpeg

    val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return jpeg
    val rotated = android.graphics.Bitmap.createBitmap(
        bitmap, 0, 0, bitmap.width, bitmap.height,
        Matrix().apply { postRotate(rotation.toFloat()) }, true,
    )
    val out = ByteArrayOutputStream()
    rotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
    return out.toByteArray()
}

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
    }
    startActivity(intent)
}

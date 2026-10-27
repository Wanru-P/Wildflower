package com.floralens.ui.screens.leafhealth

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.floralens.domain.usecase.CheckLeafHealth
import com.floralens.ui.theme.Spacing
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Leaf health check — camera capture of a single leaf → on-device color
 * heuristic → healthy / possible-issue / unclear banner, hedged language,
 * "Save to log" writes a LEAF_CHECK observation.
 *
 * In ONLINE mode an "AI insights" card streams in below the heuristic
 * result (Gemini vision). Offline, that card never appears — the photo
 * stays on-device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeafHealthScreen(
    onBack: () -> Unit,
    viewModel: LeafHealthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalActivity.current

    // Pop back once the observation is saved.
    LaunchedEffect(uiState.saved) { if (uiState.saved) onBack() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRequested = true
        hasCameraPermission = granted
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("叶片健康检查") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
        )

        when {
            !hasCameraPermission -> {
                val permanentlyDenied = permissionRequested && activity != null &&
                        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
                PermissionPrompt(
                    permanentlyDenied = permanentlyDenied,
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onOpenSettings = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        })
                    },
                )
            }
            uiState.result != null -> ResultContent(
                result = uiState.result!!,
                capturedImage = uiState.capturedImage,
                isSaving = uiState.isSaving,
                aiInsight = uiState.aiInsight,
                aiInsightLoading = uiState.aiInsightLoading,
                onSave = viewModel::saveToLog,
                onRetake = viewModel::retake,
            )
            else -> LeafViewfinder(
                isAnalyzing = uiState.isAnalyzing,
                onCapture = viewModel::onCapture,
            )
        }
    }
}

// ── Viewfinder ──────────────────────────────────────────────────────
@Composable
private fun LeafViewfinder(
    isAnalyzing: Boolean,
    onCapture: (ByteArray) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // TextureView compatibility is more reliable on the API 35 emulator and
            // across page/background transitions than the default SurfaceView path.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val preview = remember(previewView) {
        Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }
    }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewState by remember { mutableStateOf(CameraPreviewState.STARTING) }
    var captureError by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }

    DisposableEffect(previewView, lifecycleOwner) {
        val observer = androidx.lifecycle.Observer<PreviewView.StreamState> { state ->
            if (state == PreviewView.StreamState.STREAMING) {
                previewState = CameraPreviewState.STREAMING
            }
        }
        previewView.previewStreamState.observe(lifecycleOwner, observer)
        onDispose { previewView.previewStreamState.removeObserver(observer) }
    }

    LaunchedEffect(lifecycleOwner, retryKey) {
        previewState = CameraPreviewState.STARTING
        captureError = null
        runCatching {
            val manager = context.getSystemService(CameraManager::class.java)
            val hasRearCamera = manager.cameraIdList.any { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            if (!hasRearCamera) {
                previewState = CameraPreviewState.UNAVAILABLE
                return@runCatching
            }
            val provider = context.getCameraProvider()
            cameraProvider = provider
            if (!provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                previewState = CameraPreviewState.UNAVAILABLE
                return@runCatching
            }
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
        }.onFailure {
            previewState = CameraPreviewState.ERROR
            Log.e("LeafHealthScreen", "Camera bind failed", it)
        }
    }

    DisposableEffect(preview, imageCapture) {
        onDispose { cameraProvider?.unbind(preview, imageCapture) }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        when (previewState) {
            CameraPreviewState.STARTING -> CameraStatusMessage("正在准备相机…", showProgress = true)
            CameraPreviewState.ERROR -> CameraStatusMessage(
                "相机暂时无法使用，请确认没有被其他应用占用。",
                action = "重试" to { retryKey += 1 },
            )
            CameraPreviewState.UNAVAILABLE -> CameraStatusMessage("此设备没有可用的后置摄像头。")
            CameraPreviewState.STREAMING -> Unit
        }

        Text(
            "让一片叶子占满画面，尽量拍清叶面与叶背",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(Spacing.lg)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )

        val shutterEnabled = !isAnalyzing && previewState == CameraPreviewState.STREAMING
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .size(76.dp)
                .clip(CircleShape)
                .border(4.dp, if (shutterEnabled) Color.White.copy(alpha = .85f) else MaterialTheme.colorScheme.outline.copy(alpha = .45f), CircleShape)
                .background(if (shutterEnabled) Color.White.copy(alpha = .18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .20f))
                .semantics { contentDescription = "拍摄叶片" }
                .clickable(enabled = shutterEnabled) {
                    captureError = null
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
                                captureError = "拍摄没有完成，请保持页面开启后重试。"
                                Log.e("LeafHealthScreen", "Capture failed", exception)
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (isAnalyzing) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
            } else {
                Box(Modifier.size(58.dp).clip(CircleShape).background(if (shutterEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .38f)))
            }
        }

        captureError?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp, start = Spacing.lg, end = Spacing.lg)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color.Black.copy(alpha = .65f))
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }
    }
}

private enum class CameraPreviewState { STARTING, STREAMING, ERROR, UNAVAILABLE }

@Composable
private fun BoxScope.CameraStatusMessage(
    message: String,
    showProgress: Boolean = false,
    action: Pair<String, () -> Unit>? = null,
) {
    Surface(
        color = Color.Black.copy(alpha = .68f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (showProgress) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Text(message, color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            action?.let { (label, onClick) ->
                TextButton(onClick = onClick) { Text(label, color = Color.White) }
            }
        }
    }
}

// ── Result ──────────────────────────────────────────────────────────
@Composable
private fun ResultContent(
    result: CheckLeafHealth.Result,
    capturedImage: ByteArray?,
    isSaving: Boolean,
    aiInsight: String?,
    aiInsightLoading: Boolean,
    onSave: () -> Unit,
    onRetake: () -> Unit,
) {
    val bitmap = remember(capturedImage) {
        capturedImage?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().weight(0.45f),
            )
        }

        Column(
            modifier = Modifier
                .weight(0.55f)
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        ) {
            // Scrollable result area — the AI card can push past one screen.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                when (result) {
                    CheckLeafHealth.Result.Healthy -> {
                        ResultBanner(
                            icon = Icons.Filled.CheckCircle,
                            text = "叶色看起来正常",
                            bg = MaterialTheme.colorScheme.secondaryContainer,
                            fg = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            "暂未见明显黄化或褐变。" +
                                    "这只是照片颜色观察，不代替专业诊断。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is CheckLeafHealth.Result.PossibleIssue -> {
                        ResultBanner(
                            icon = Icons.Filled.Warning,
                            text = "叶色有些变化",
                            bg = MaterialTheme.colorScheme.tertiaryContainer,
                            fg = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            "照片中有叶色变化，建议在自然光下再观察，并留意光照与浇水。颜色变化不等于病害。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CheckLeafHealth.Result.Unclear -> {
                        ResultBanner(
                            icon = Icons.Filled.HelpOutline,
                            text = "这次还没看清",
                            bg = MaterialTheme.colorScheme.surfaceVariant,
                            fg = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "画面中的叶片不够清晰。" +
                                    "请在光线充足处，让一片叶子占满画面后重拍。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Online-only AI note — appears while loading and when loaded;
                // absent entirely in offline mode or on silent failure.
                if (aiInsightLoading || aiInsight != null) {
                    AiInsightCard(insight = aiInsight, loading = aiInsightLoading)
                }

                Spacer(Modifier.height(Spacing.xs))
            }

            Spacer(Modifier.height(Spacing.md))

            if (result is CheckLeafHealth.Result.Unclear) {
                Button(onClick = onRetake, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(Spacing.sm))
                    Text("重新拍摄")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    OutlinedButton(onClick = onRetake, enabled = !isSaving, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(Spacing.sm))
                        Text("重新拍摄")
                    }
                    Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.weight(1f)) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.size(Spacing.sm))
                        Text("保存观察")
                    }
                }
            }
        }
    }
}

/** Gemini's second opinion — clearly labeled as AI, clearly not a diagnosis. */
@Composable
private fun AiInsightCard(insight: String?, loading: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(Spacing.sm))
                Text("辅助观察", style = MaterialTheme.typography.titleSmall)
            }
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(Spacing.sm))
                    Text(
                        "正在观察叶片…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (insight != null) {
                Text(
                    insight,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "AI 辅助内容仅供观察参考，不代替专业诊断。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun ResultBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    bg: Color,
    fg: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(bg)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = fg)
        Spacer(Modifier.size(Spacing.sm))
        Text(text, style = MaterialTheme.typography.titleSmall, color = fg)
    }
}

// ── Permission prompt ───────────────────────────────────────────────
@Composable
private fun PermissionPrompt(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("需要相机权限", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            if (permanentlyDenied)
                "相机权限已关闭。请在系统设置中允许相机权限，再返回继续拍摄。"
            else
                "相机用于拍摄叶片照片。离线模式在本机观察；在线辅助可能发送本次照片。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        Button(onClick = if (permanentlyDenied) onOpenSettings else onRequest) {
            Text(if (permanentlyDenied) "前往系统设置" else "允许使用相机")
        }
    }
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({ cont.resumeWith(runCatching { future.get() }) }, ContextCompat.getMainExecutor(this))
    }

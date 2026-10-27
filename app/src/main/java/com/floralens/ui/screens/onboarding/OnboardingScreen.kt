package com.floralens.ui.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floralens.data.settings.SettingsRepository
import com.floralens.domain.model.IdentificationMode
import com.floralens.ui.components.IdentificationModePicker
import com.floralens.ui.components.LivingBackground
import com.floralens.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {
    fun markOnboarded() = viewModelScope.launch { settings.setOnboarded() }
    fun setIdentificationMode(mode: IdentificationMode) =
        viewModelScope.launch { settings.setIdentificationMode(mode) }
}

private data class OnboardingStep(
    val icon: ImageVector?,          // null → custom content (illustration or picker) instead of a fixed icon
    val title: String,
    val body: String,
    val actionLabel: String,
    val skippable: Boolean,
)

/** Index into `steps` that renders the offline/online picker instead of the generic icon layout. */
private const val STEP_MODE = 1

/**
 * 5-step onboarding — local archives, clearly disclosed online recognition.
 * Welcome · identification mode (online/offline) · camera (required) ·
 * notifications · location (optional). Denying anything still lets the
 * user through: every screen downstream handles its own missing
 * permission gracefully.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var selectedMode by rememberSaveable { mutableStateOf(IdentificationMode.DEFAULT) }

    val steps = listOf(
        OnboardingStep(
            icon = null,
            title = "草木知时 · Wildflower Lens",
            body = "发现自然，收藏四季。照片与收藏保存在本机，默认使用 Pl@ntNet 在线识别，会发送本次识别照片。也可选择离线模式。",
            actionLabel = "开始发现",
            skippable = false,
        ),
        OnboardingStep(
            icon = null, // rendered via IdentificationModePicker below instead
            title = "选择植物识别方式",
            body = "离线识别无需网络。在线模式使用 Pl@ntNet，覆盖更多物种并会发送本次识别照片。可随时在设置中切换。",
            actionLabel = "继续",
            skippable = false,
        ),
        OnboardingStep(
            icon = Icons.Filled.PhotoCamera,
            title = "开启镜头，观察植物",
            body = "拍照识别与叶片检查需要相机权限。离线模式在本机分析；在线模式发送本次照片。",
            actionLabel = "允许使用相机",
            skippable = false,
        ),
        OnboardingStep(
            icon = Icons.Filled.Notifications,
            title = "轻柔的养护提醒",
            body = "在植物需要浇水时收到通知，也可以稍后再开启。",
            actionLabel = "开启通知",
            skippable = true,
        ),
        OnboardingStep(
            icon = Icons.Filled.LocationOn,
            title = "记录相遇的位置（可选）",
            body = "为识别记录添加地图坐标。拒绝位置权限不会影响其他功能。",
            actionLabel = "允许位置权限",
            skippable = true,
        ),
    )

    fun advance() {
        if (step < steps.lastIndex) {
            step++
        } else {
            viewModel.markOnboarded()
            onFinished()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { advance() } // granted or not — Identify handles its own denied state

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { advance() }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { advance() }

    fun onPrimaryAction() = when (step) {
        0 -> advance() // welcome — no permission
        STEP_MODE -> {
            viewModel.setIdentificationMode(selectedMode)
            advance()
        }
        2 -> cameraLauncher.launch(Manifest.permission.CAMERA)
        3 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            advance()
        }
        else -> locationLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    val current = steps[step]

    Box(Modifier.fillMaxSize()) {
        // Drifting foliage behind the whole flow — the first thing a new
        // user sees, so it earns full intensity here.
        LivingBackground(intensity = 1f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xxl, vertical = Spacing.x8),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                // Progress bars
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    steps.indices.forEach { i ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (i <= step) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                                ),
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.x10))

                when (step) {
                    0 -> WelcomeIllustration(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.9f),
                    )
                    STEP_MODE -> Unit // picker renders below, after the body text
                    else -> {
                        val stepIcon = current.icon
                        if (stepIcon != null) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    stepIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.xl))
                Text(current.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(Spacing.md))
                Text(
                    current.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.1,
                )

                if (step == STEP_MODE) {
                    Spacer(Modifier.height(Spacing.xl))
                    IdentificationModePicker(
                        selected = selectedMode,
                        onSelect = { selectedMode = it },
                    )
                }
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(Spacing.sm))
                    Text(
                        if (selectedMode == IdentificationMode.ONLINE) {
                            "在线模式仅发送本次照片，不上传个人收藏与观察历史。"
                        } else {
                            "离线模式在本机识别，无需账号与云端服务。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.lg))
                Button(onClick = ::onPrimaryAction, modifier = Modifier.fillMaxWidth()) {
                    Text(current.actionLabel)
                }
                if (current.skippable) {
                    TextButton(onClick = ::advance, modifier = Modifier.fillMaxWidth()) {
                        Text("暂时跳过")
                    }
                } else {
                    Spacer(Modifier.height(48.dp)) // keep button position stable across steps
                }
            }
        }
    }
}

/** Mini garden vignette — the map's illustration language as a welcome mark. */
@Composable
private fun WelcomeIllustration(modifier: Modifier = Modifier) {
    val pinColor = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val s = size.width / 360f

        // grass patches
        drawOval(Color(0xFFE7EFDA), topLeft = Offset(-30f * s, 90f * s), size = Size(180f * s, 90f * s))
        drawOval(Color(0xFFF1F6E8), topLeft = Offset(210f * s, 20f * s), size = Size(190f * s, 100f * s))

        // winding trail
        val trail = Path().apply {
            moveTo(-20f * s, 150f * s)
            cubicTo(90f * s, 120f * s, 150f * s, 160f * s, 210f * s, 120f * s)
            cubicTo(260f * s, 88f * s, 320f * s, 96f * s, 390f * s, 60f * s)
        }
        drawPath(
            trail, Color(0xFFA9805A).copy(alpha = 0.85f),
            style = Stroke(
                22f * s, cap = StrokeCap.Round,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2f * s, 13f * s)),
            ),
        )
        drawPath(trail, Color(0xFFDABB8F), style = Stroke(16f * s, cap = StrokeCap.Round))

        // mud mound + pin at trail midpoint
        val mx = 185f * s
        val my = 138f * s
        drawOval(Color(0xFFA87C4F), topLeft = Offset(mx - 34f * s, my - 4f * s), size = Size(68f * s, 22f * s))
        drawOval(Color(0xFFC79E6E), topLeft = Offset(mx - 31f * s, my - 7f * s), size = Size(62f * s, 19f * s))
        listOf(-13f, 4f, 15f).forEach { dx ->
            drawCircle(Color(0xFF8A6440), 2f * s, Offset(mx + dx * s, my + 3f * s))
        }

        // the pin
        val pin = Path().apply {
            moveTo(mx, my)
            cubicTo(mx - 26f * s, my - 30f * s, mx - 18f * s, my - 62f * s, mx, my - 62f * s)
            cubicTo(mx + 18f * s, my - 62f * s, mx + 26f * s, my - 30f * s, mx, my)
            close()
        }
        drawPath(pin, pinColor)
        drawCircle(Color(0xFFF7FAF2), 7f * s, Offset(mx, my - 42f * s))

        // sprout
        val sx = mx + 42f * s
        drawPath(
            Path().apply { moveTo(sx, my + 2f * s); cubicTo(sx, my - 6f * s, sx - 1f * s, my - 10f * s, sx - 2f * s, my - 15f * s) },
            Color(0xFF4F7A48), style = Stroke(3f * s, cap = StrokeCap.Round),
        )
        drawPath(
            Path().apply {
                moveTo(sx - 2f * s, my - 12f * s)
                cubicTo(sx - 10f * s, my - 16f * s, sx - 13f * s, my - 22f * s, sx - 13f * s, my - 27f * s)
                cubicTo(sx - 5f * s, my - 27f * s, sx - 1f * s, my - 20f * s, sx - 2f * s, my - 12f * s)
                close()
            },
            Color(0xFF6FA05E),
        )
        drawPath(
            Path().apply {
                moveTo(sx - 1f * s, my - 10f * s)
                cubicTo(sx + 6f * s, my - 14f * s, sx + 10f * s, my - 19f * s, sx + 10f * s, my - 24f * s)
                cubicTo(sx + 3f * s, my - 24f * s, sx - 1f * s, my - 17f * s, sx - 1f * s, my - 10f * s)
                close()
            },
            Color(0xFF87B472),
        )
    }
}

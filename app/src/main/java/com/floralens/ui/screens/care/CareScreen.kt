package com.floralens.ui.screens.care

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil.compose.AsyncImage
import com.floralens.BuildConfig
import com.floralens.R
import com.floralens.reminders.WateringWorker
import com.floralens.ui.components.BottomSheet
import com.floralens.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.floralens.ui.components.LivingBackground

/**
 * Care dashboard — Overdue / Today / This week.
 *
 * Mark-watered flow (mis-tap protected, twice over):
 *  1. Tapping a row's circle asks for confirmation in a dialog. The
 *     quick-view sheet's button skips the dialog — opening the sheet and
 *     pressing a labeled button is already a deliberate action.
 *  2. On confirm: check fills and pops → row slides out → snackbar with
 *     UNDO. Nothing is written until the Undo window passes; Undo brings
 *     the row back with nothing to roll back.
 * Tapping the row itself opens a quick-view sheet for the plant.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CareScreen(viewModel: CareViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingIds by remember { mutableStateOf(setOf<Long>()) }
    var quickView by remember { mutableStateOf<CareItem?>(null) }
    var confirmItem by remember { mutableStateOf<CareItem?>(null) }

    fun onMarkWatered(item: CareItem) {
        val id = item.plant.id
        if (id in pendingIds) return
        pendingIds = pendingIds + id
        scope.launch {
            delay(620) // check fills (~150ms) + slide-out (~400ms) complete first
            // Mis-tap protection layer 2: nothing is written until the Undo
            // window passes. Undo restores the row — and since the write
            // never happened, there's nothing to roll back.
            val result = snackbarHostState.showSnackbar(
                message = "已为 ${item.plant.nickname} 记录浇水 💧",
                actionLabel = "撤销",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                pendingIds = pendingIds - id // row slides back in, untouched
            } else {
                viewModel.onMarkWatered(id) // commit for real
                pendingIds = pendingIds - id // list re-emits; row moves sections
            }
        }
    }

    // Mis-tap protection layer 1: confirm before the animation even starts.
    confirmItem?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmItem = null },
            title = { Text("记录本次浇水？") },
            text = { Text("为 ${item.plant.nickname} 留下今天的浇水记录，确认后仍可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmItem = null
                    onMarkWatered(item)
                }) { Text("记录浇水") }
            },
            dismissButton = {
                TextButton(onClick = { confirmItem = null }) { Text("取消") }
            },
        )
    }

    // ── POST_NOTIFICATIONS runtime permission (API 33+) ─────────────
    val needsPermissionApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var hasNotificationPermission by remember {
        mutableStateOf(
            !needsPermissionApi || ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasNotificationPermission = granted }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = !needsPermissionApi || ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize()) {
        LivingBackground(intensity = 0.6f)
        Column(Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.tab_care),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .padding(Spacing.xl)
                    .then(
                        if (BuildConfig.DEBUG && BuildConfig.INTERNAL_TOOLS) {
                            Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    WorkManager.getInstance(context)
                                        .enqueue(OneTimeWorkRequestBuilder<WateringWorker>().build())
                                    scope.launch {
                                        snackbarHostState.showSnackbar("提醒已安排")
                                    }
                                },
                            )
                        } else {
                            Modifier
                        },
                    ),
            )

            if (!hasNotificationPermission) {
                EnableRemindersBanner(
                    onEnable = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                )
            }

            if (uiState.allCaughtUp) {
                AllCaughtUp()
            } else {
                LazyColumn {
                    if (uiState.overdue.isNotEmpty()) {
                        item(key = "header_overdue") {
                            SectionHeader(stringResource(R.string.care_section_overdue), error = true)
                        }
                        items(uiState.overdue, key = { it.plant.id }) { item ->
                            CareRow(
                                item = item,
                                checked = item.plant.id in pendingIds,
                                onClick = { quickView = item },
                                onMarkWatered = { confirmItem = item },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    if (uiState.today.isNotEmpty()) {
                        item(key = "header_today") {
                            SectionHeader(stringResource(R.string.care_section_today))
                        }
                        items(uiState.today, key = { it.plant.id }) { item ->
                            CareRow(
                                item = item,
                                checked = item.plant.id in pendingIds,
                                onClick = { quickView = item },
                                onMarkWatered = { confirmItem = item },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    if (uiState.thisWeek.isNotEmpty()) {
                        item(key = "header_week") {
                            SectionHeader(stringResource(R.string.care_section_this_week))
                        }
                        items(uiState.thisWeek, key = { it.plant.id }) { item ->
                            CareRow(
                                item = item,
                                checked = item.plant.id in pendingIds,
                                onClick = { quickView = item },
                                onMarkWatered = { confirmItem = item },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }

        quickView?.let { item ->
            QuickViewSheet(
                item = item,
                isPending = item.plant.id in pendingIds,
                onMarkWatered = {
                    // Sheet button = already deliberate; no dialog, straight
                    // to the animated mark + undo window.
                    onMarkWatered(item)
                    quickView = null
                },
                onDismiss = { quickView = null },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ── Quick-view sheet ────────────────────────────────────────────────
@Composable
private fun QuickViewSheet(
    item: CareItem,
    isPending: Boolean,
    onMarkWatered: () -> Unit,
    onDismiss: () -> Unit,
) {
    val lastWateredText = item.plant.lastWateredAt?.let {
        "上次浇水 · " + DateTimeFormatter.ofPattern("MM月dd日")
            .withZone(ZoneId.systemDefault())
            .format(it)
    } ?: "还没有浇水记录"

    BottomSheet(open = true, onDismiss = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = item.plant.photoUri,
                    contentDescription = item.plant.nickname,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                Column(Modifier.padding(start = Spacing.md)) {
                    Text(item.plant.nickname, style = MaterialTheme.typography.titleLarge)
                    Text(
                        item.plant.scientificName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        item.dueText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.urgent) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.WaterDrop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Column {
                    Text(
                        "每 ${item.plant.waterIntervalDays} 天留意浇水",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        lastWateredText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = onMarkWatered,
                enabled = !isPending,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("记录浇水")
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

// ── Enable-reminders banner ─────────────────────────────────────────
@Composable
private fun EnableRemindersBanner(onEnable: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.xs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            Icon(
                Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                Text(
                    stringResource(R.string.care_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    stringResource(R.string.care_banner_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            TextButton(onClick = onEnable) {
                Text(stringResource(R.string.care_banner_action))
            }
        }
    }
}

// ── All caught up ───────────────────────────────────────────────────
@Composable
private fun AllCaughtUp() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x8, vertical = Spacing.x16),
    ) {
        Icon(
            Icons.Filled.Yard,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(56.dp),
        )
        Text(
            stringResource(R.string.care_empty_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            stringResource(R.string.care_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Section header & row ────────────────────────────────────────────
@Composable
private fun SectionHeader(text: String, error: Boolean = false) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm),
    )
}

@Composable
private fun CareRow(
    item: CareItem,
    checked: Boolean,
    onClick: () -> Unit,
    onMarkWatered: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Exit: after the check fills, slide the whole row out to the left.
    val exit by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = 160),
        label = "exit",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = -size.width * exit
                alpha = 1f - exit
            }
            .clickable(enabled = !checked, onClick = onClick)
            .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
    ) {
        AsyncImage(
            model = item.plant.photoUri,
            contentDescription = item.plant.nickname,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
            Text(item.plant.nickname, style = MaterialTheme.typography.titleSmall)
            Text(
                com.floralens.ui.presentation.PlantDisplayNames.chinese(item.plant.scientificName, item.plant.commonName),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                item.dueText,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.urgent) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MarkWateredCircle(checked = checked, onClick = onMarkWatered)
    }
}

/**
 * Circle-check affordance. At rest: outlined circle. On tap: fills with
 * primary + white check and pops — then the row slides out.
 */
@Composable
private fun MarkWateredCircle(checked: Boolean, onClick: () -> Unit) {
    val fillColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
        label = "fill",
    )
    val borderColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        label = "border",
    )
    val pop by animateFloatAsState(
        if (checked) 1.15f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pop",
    )

    IconButton(onClick = onClick, enabled = !checked) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .scale(pop)
                .clip(CircleShape)
                .background(fillColor)
                .border(2.dp, borderColor, CircleShape),
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "记录浇水",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { alpha = if (checked) 1f else 0f },
            )
        }
    }
}

package com.floralens.ui.screens.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Optional foreground permission only; browsing never writes or requests a location fix. */
@Composable
internal fun mapPermissionNotice(): Boolean {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycle = LocalLifecycleOwner.current
    var fine by remember { mutableStateOf(false) }
    var coarse by remember { mutableStateOf(false) }
    var asked by rememberSaveable { mutableStateOf(false) }
    var explanation by rememberSaveable { mutableStateOf(false) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    fun refresh() {
        fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true; refresh()
    }
    DisposableEffect(lifecycle, context) {
        refresh()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(when { fine -> "精确位置已允许"; coarse -> "大致位置已允许"; else -> "未开启定位 · 可浏览已有地点" },
            modifier = Modifier.weight(1f).padding(vertical = 10.dp).testTag("map-permission-status"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton({ explanation = true }) { Text("位置权限") }
    }
    if (explanation) {
        val permanent = asked && !fine && !coarse && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
        AlertDialog(onDismissRequest = { explanation = false }, title = { Text("可选的位置记录") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("保存观察时，可按授权读取系统已有定位。不进行后台持续定位；浏览地图不会新增坐标。未获得定位时，仍可保存照片和观察。")
                Text("观察归档位置不等于导入照片的原拍摄地点。位置不会发送给花间问答或 AI 自然表达。")
                settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = {
            if (!fine) TextButton({
                if (permanent) {
                    settingsError = try {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                        explanation = false; null
                    } catch (_: Exception) { "暂时无法打开系统设置，请手动检查位置权限。" }
                } else {
                    explanation = false
                    permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }) { Text(if (permanent) "前往系统设置" else "允许位置权限") }
        }, dismissButton = { TextButton({ explanation = false }) { Text("关闭") } })
    }
    return fine || coarse
}

package com.floralens.ui.screens.map

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.floralens.data.settings.MapPrivacyChoice

@Composable
fun BaiduMapPrivacyControl(choice: MapPrivacyChoice, onChoice: (MapPrivacyChoice) -> Unit, autoPrompt: Boolean = false) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    var linkError by remember { mutableStateOf(false) }
    LaunchedEffect(autoPrompt, choice) { if (autoPrompt && choice == MapPrivacyChoice.PENDING) open = true }
    LaunchedEffect(choice) { if (choice != MapPrivacyChoice.GRANTED) BaiduMapRuntime.revokeIfStarted(context) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (choice == MapPrivacyChoice.GRANTED) "百度在线地图已允许" else "百度地图待授权 · 本地记录可用",
            Modifier.weight(1f).padding(vertical = 10.dp), style = MaterialTheme.typography.bodySmall)
        TextButton({ open = true }, Modifier.testTag("map-privacy-settings")) { Text("地图隐私") }
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text("百度地图服务与隐私") },
        modifier = Modifier.testTag("baidu-privacy-dialog"), text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("在线底图由北京百度网讯科技有限公司提供，用于展示观察地点和地图交互。")
                Text("使用时，百度 SDK 将处理 Android ID、设备与应用信息、网络状态、IP 地址及地图请求等必要信息。地图视野可关联所浏览的地点；本应用不向地图服务发送植物照片、昵称或观察备注。")
                Text("地图授权与系统定位权限分别管理。拒绝后仍可查看本地观察记录；不新增后台定位。")
                TextButton({
                    linkError = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://lbsyun.baidu.com/docs/pcsa?title=compliance/openprivacy"))) }.isFailure
                }) { Text("查看百度地图隐私政策") }
                if (linkError) Text("暂时无法打开链接，请检查浏览器。")
            }
        }, confirmButton = { TextButton({ onChoice(MapPrivacyChoice.GRANTED); open = false }, Modifier.testTag("map-privacy-agree")) { Text("同意并使用地图") } },
        dismissButton = { TextButton({
            onChoice(MapPrivacyChoice.DECLINED)
            // This immediately removes the MapView through the persisted gate.
            // The runtime deliberately does not mutate the SDK's global native
            // privacy switch after its engine has started; see BaiduMapRuntime.
            BaiduMapRuntime.revokeIfStarted(context)
            open = false
        }, Modifier.testTag("map-privacy-decline")) {
            Text(if (choice == MapPrivacyChoice.GRANTED) "撤回地图授权" else "仅看本地记录")
        } })
}

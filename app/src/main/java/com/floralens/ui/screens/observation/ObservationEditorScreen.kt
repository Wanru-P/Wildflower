package com.floralens.ui.screens.observation

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.floralens.ui.theme.Editorial
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.floralens.ui.components.*
import com.floralens.R

@Composable
fun ObservationEditorScreen(onBack: () -> Unit, vm: ObservationEditorViewModel = hiltViewModel()) {
    val s by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), vm::cameraResult)
    fun launchCamera() { try { camera.launch(vm.cameraOutput()) } catch(e: Exception) { vm.cameraResult(false); vm.failure("暂时无法打开相机，请检查权限后重试。") } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if(it) launchCamera() else vm.failure("未授予相机权限。仍可从相册选择照片。") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { vm.importPhoto(it) }
    val exit = { if(!s.saving && !s.importing) { vm.cancel(); onBack() } }
    BackHandler { exit() }
    LaunchedEffect(s.saved) { if(s.saved) onBack() }
    Column(Modifier.fillMaxSize()) {
        WildflowerTopBar(if(s.editing) "编辑观察" else "添加观察", exit)
        if(s.loading) EditorialLoading() else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
          Column(Modifier.widthIn(max = 620.dp).fillMaxWidth().padding(Editorial.pageMargin), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BotanicalHeading("留下一次相遇", subtitle = "OBSERVATION / 一次观察")
            if (s.photoUri == null) BotanicalEmptyArtwork(R.drawable.wf_empty_observations, Modifier.fillMaxWidth().height(170.dp))
            else BotanicalPhoto(s.photoUri, "观察照片预览", Modifier.fillMaxWidth().aspectRatio(1.8f).clip(MaterialTheme.shapes.medium))
            if(!s.editing) {
                Button({ if(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera() else permission.launch(Manifest.permission.CAMERA) }, Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(28.dp), enabled = !s.importing && !s.saving) { KitIcon(R.drawable.wf_icon_camera, tint = Editorial.paper); Spacer(Modifier.width(8.dp)); Text("拍摄新照片") }
                OutlinedButton({ try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } catch(e: Exception) { vm.failure("相册无法打开，请重试") } }, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(28.dp), enabled = !s.importing && !s.saving) { KitIcon(R.drawable.wf_icon_gallery); Spacer(Modifier.width(8.dp)); Text("从相册选择照片") }
            }
            Text("为这株植物添一张照片，记录下一次相遇。", style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown)
            if(s.importing) EditorialLoading("正在复制照片…")
            HorizontalDivider(color = Editorial.hairline)
            EditorialSectionHeader("记录此刻", "DATE & MEMORY")
            OutlinedTextField(s.date, vm::date, Modifier.fillMaxWidth(), label = { Text("观察日期 YYYY-MM-DD") }, singleLine = true, enabled = !s.saving, shape = RoundedCornerShape(12.dp), trailingIcon = {
                IconButton({
                    val date = runCatching { java.time.LocalDate.parse(s.date) }.getOrElse { java.time.LocalDate.now() }
                    android.app.DatePickerDialog(context, { _, year, month, day -> vm.date(java.time.LocalDate.of(year, month + 1, day).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                }, enabled = !s.saving) { KitIcon(R.drawable.wf_icon_calendar, description = "选择观察日期") }
            })
            OutlinedTextField(s.time, vm::time, Modifier.fillMaxWidth(), label = { Text("观察时间 HH:mm") }, singleLine = true, enabled = !s.saving, shape = RoundedCornerShape(12.dp), trailingIcon = {
                IconButton({
                    val time = runCatching { java.time.LocalTime.parse(s.time) }.getOrElse { java.time.LocalTime.now() }
                    android.app.TimePickerDialog(context, { _, hour, minute -> vm.time(java.time.LocalTime.of(hour, minute).toString()) }, time.hour, time.minute, true).show()
                }, enabled = !s.saving) { KitIcon(R.drawable.wf_icon_clock, description = "选择观察时间") }
            })
            OutlinedTextField(s.note, vm::note, Modifier.fillMaxWidth(), label = { Text("观察备注") }, minLines = 3, maxLines = 6, enabled = !s.saving, shape = RoundedCornerShape(12.dp), textStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp), supportingText = { Text("可选 · ${s.note.length}/500") })
            Text("按本地时间记录。是否留下观察地点，由你决定。", style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown)
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
          }
        }
        if (!s.loading) Surface(color = Editorial.paper) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Editorial.pageMargin, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(exit, enabled = !s.saving && !s.importing) { Text("取消") }
                Button(vm::save, Modifier.weight(1f).heightIn(min = 54.dp), shape = RoundedCornerShape(28.dp), enabled = !s.saving && !s.importing && (s.editing || s.photoUri != null)) { Text(if(s.saving) "正在保存…" else "保存观察") }
            }
        }
    }
}

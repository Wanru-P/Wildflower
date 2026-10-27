package com.floralens.ui.screens.observation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.foundation.shape.RoundedCornerShape
import com.floralens.ui.theme.Editorial
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.floralens.data.db.FloraDatabase
import com.floralens.data.db.entity.toDomain
import com.floralens.data.repo.PlantRepository
import com.floralens.domain.model.*
import com.floralens.domain.usecase.*
import com.floralens.ui.components.*
import com.floralens.ui.presentation.ObservationNotePresenter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import com.floralens.R

data class GalleryState(val plant: Plant? = null, val photos: List<Observation> = emptyList())
@HiltViewModel
class ObservationGalleryViewModel @Inject constructor(handle: SavedStateHandle, db: FloraDatabase, plants: PlantRepository,
    private val setCover: SetPlantCoverUseCase, private val deleteObservation: DeleteObservationUseCase) : ViewModel() {
    private val id = checkNotNull(handle.get<Long>("plantId"))
    val state = combine(plants.observeById(id), db.archiveDao().gallery(id)) { plant, photos -> GalleryState(plant, photos.map { it.toDomain() }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GalleryState())
    private val _busy = MutableStateFlow(false); val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null); val message = _message.asStateFlow()
    private fun mutate(action: suspend () -> Unit) {
        if(_busy.value) return
        _busy.value = true; _message.value = null
        viewModelScope.launch { try { action() } catch(e: Exception) { _message.value = com.floralens.ui.presentation.ProductMessages.error(e, "暂时无法完成操作，请重试。") } finally { _busy.value = false } }
    }
    fun cover(observationId: Long) = mutate { setCover(id, observationId); _message.value = "封面已更新，历史照片保持不变" }
    fun delete(observationId: Long) = mutate { deleteObservation(id, observationId); _message.value = "观察已删除，其他引用的照片已保留" }
}

@Composable
fun ObservationGalleryScreen(onBack: () -> Unit, onEdit: (Long) -> Unit,
    onAddObservation: () -> Unit, vm: ObservationGalleryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val selected = state.photos.firstOrNull { it.id == selectedId }
    ObservationGalleryContent(state, onBack, onAddObservation, { selectedId = it }, message = message)
    if(selected != null) androidx.compose.ui.window.Dialog({ if(!busy) selectedId = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val dialogView = LocalView.current
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, dialogView).apply {
                    isAppearanceLightStatusBars = true
                    isAppearanceLightNavigationBars = true
                }
            }
        }
        val note = remember(selected.note) { ObservationNotePresenter.split(selected.note) }
        val detailScroll = rememberScrollState()
        Surface(Modifier.padding(16.dp).widthIn(max = 620.dp).fillMaxWidth().fillMaxHeight(.92f).testTag("gallery-photo-detail"), shape = MaterialTheme.shapes.medium, color = Editorial.paper) {
            Column(Modifier.testTag("photo-detail-scroll").verticalScroll(detailScroll).padding(20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BotanicalPhoto(selected.photoUri, "观察照片", Modifier.fillMaxWidth().aspectRatio(1.2f).clip(MaterialTheme.shapes.small))
                if (detailScroll.canScrollForward) Text("向上滑动，阅读完整说明与操作 ↓", style = MaterialTheme.typography.labelSmall, color = Editorial.captionBrown)
                Text("观察时间", style = MaterialTheme.typography.labelMedium, color = Editorial.captionBrown)
                Text(selected.timestamp.atZone(ZoneId.systemDefault()).format(galleryDate), style = Editorial.sectionTitle, color = MaterialTheme.colorScheme.primary)
                Text(if(selected.type == ObservationType.IDENTIFIED) "识别记录 · 置信度仅属于此次事件" else "照片观察 · 未重新识别", style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown)
                note.observation?.let {
                    Text("观察内容", style = MaterialTheme.typography.labelMedium, color = Editorial.captionBrown)
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                note.photoSource?.let {
                    HorizontalDivider(color = Editorial.hairline)
                    Text("照片来源与许可", style = MaterialTheme.typography.labelMedium, color = Editorial.captionBrown)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        it.lineSequence().forEach { line -> Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                selected.identificationJson?.let { Text("本次识别信息已随照片保存。", style = MaterialTheme.typography.bodySmall) }
                HorizontalDivider(color = Editorial.hairline)
                Button({ vm.cover(selected.id) }, Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(28.dp), enabled = !busy) { KitIcon(R.drawable.wf_icon_bookmark, modifier = Modifier.size(22.dp), tint = Editorial.paper); Spacer(Modifier.width(8.dp)); Text("设为植物封面") }
                OutlinedButton({ selectedId = null; onEdit(selected.id) }, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(28.dp), enabled = !busy) { KitIcon(R.drawable.wf_icon_clock, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)); Text("编辑日期与备注") }
                TextButton({ deleting = true }, enabled = !busy) { Text("删除这条观察", color = MaterialTheme.colorScheme.error) }
                message?.let { Text(it) }
                TextButton({ selectedId = null }, enabled = !busy) { Text("关闭照片") }
            }
        }
    }
    if(deleting && selected != null) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("删除观察？") },
        text = { Text("仅删除这次观察。若它是当前封面，会安全选择其他照片；共享照片不会被误删。") },
        confirmButton = { TextButton({ vm.delete(selected.id); deleting = false; selectedId = null }, enabled = !busy) { Text("确认删除") } },
        dismissButton = { TextButton({ deleting = false }) { Text("取消") } })
}
private val galleryDate = DateTimeFormatter.ofPattern("yyyy.MM.dd · HH:mm")

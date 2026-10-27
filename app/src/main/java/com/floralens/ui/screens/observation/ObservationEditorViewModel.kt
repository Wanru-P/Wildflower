package com.floralens.ui.screens.observation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floralens.data.db.FloraDatabase
import com.floralens.data.photos.*
import com.floralens.domain.usecase.AddObservationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.*
import javax.inject.Inject

data class ObservationEditorState(val loading: Boolean = true, val photoUri: String? = null, val date: String = "", val time: String = "",
    val note: String = "", val importing: Boolean = false, val saving: Boolean = false, val error: String? = null, val saved: Boolean = false,
    val editing: Boolean = false)

@HiltViewModel
class ObservationEditorViewModel @Inject constructor(private val handle: SavedStateHandle, private val db: FloraDatabase,
    private val captures: CaptureStore, private val input: PhotoInput, private val add: AddObservationUseCase,
    private val archive: ArchiveService) : ViewModel() {
    private val plantId = checkNotNull(handle.get<Long>("plantId"))
    private val observationId = handle.get<Long>("observationId")?.takeIf { it > 0 }
    private var baselineAt: Long? = handle.get<Long>("baselineAt")
    private val state = MutableStateFlow(ObservationEditorState(editing = observationId != null))
    val uiState = state.asStateFlow()
    init { viewModelScope.launch {
        if(db.archiveDao().plant(plantId) == null) { state.update { it.copy(loading = false, error = "植物档案已不存在") }; return@launch }
        val observed = observationId?.let { db.archiveDao().observation(it) }
        if(observationId != null && observed?.plantId != plantId) { state.update { it.copy(loading = false, error = "观察记录已不存在") }; return@launch }
        val pending = handle.get<String>("editorCapture")?.let { captures.get(it) }
        baselineAt = baselineAt ?: observed?.timestampEpochMs ?: pending?.createdAt ?: System.currentTimeMillis()
        handle["baselineAt"] = baselineAt
        val at = Instant.ofEpochMilli(baselineAt!!).atZone(ZoneId.systemDefault())
        state.update { it.copy(loading = false, photoUri = observed?.photoUri ?: pending?.photoUri,
            date = handle.get<String>("date") ?: at.toLocalDate().toString(), time = handle.get<String>("time") ?: at.toLocalTime().withSecond(0).withNano(0).toString(),
            note = handle.get<String>("note") ?: observed?.note.orEmpty(), error = if(handle.get<String>("editorCapture") != null && pending == null) "临时照片已失效，请重新选择" else null) }
    } }
    fun date(value: String) { handle["date"] = value; state.update { it.copy(date = value, error = null) } }
    fun time(value: String) { handle["time"] = value; state.update { it.copy(time = value, error = null) } }
    fun note(value: String) { if(value.length <= 500) { handle["note"] = value; state.update { it.copy(note = value) } } }
    fun failure(message: String) = state.update { it.copy(error = message) }
    fun cameraOutput(): Uri = input.cameraOutput().also { handle["cameraOutput"] = it.toString() }
    fun cameraResult(success: Boolean) {
        val output = handle.remove<String>("cameraOutput")?.let(Uri::parse) ?: return
        if(success) importPhoto(output, camera = true) else input.releaseCamera(output)
    }
    fun importPhoto(uri: Uri?, camera: Boolean = false) {
        if(uri == null || state.value.importing || state.value.saving || observationId != null) return
        state.update { it.copy(importing = true, error = null) }
        viewModelScope.launch {
            try {
                val capture = input.importPhoto(uri)
                handle.get<String>("editorCapture")?.let { captures.cancel(it) }
                handle["editorCapture"] = capture.id
                val baseline = baselineAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
                val unchanged = state.value.date == baseline?.toLocalDate().toString() && state.value.time == baseline?.toLocalTime()?.withSecond(0)?.withNano(0).toString()
                if(unchanged) {
                    baselineAt = capture.createdAt; handle["baselineAt"] = capture.createdAt
                    val at = Instant.ofEpochMilli(capture.createdAt).atZone(ZoneId.systemDefault())
                    date(at.toLocalDate().toString()); time(at.toLocalTime().withSecond(0).withNano(0).toString())
                }
                state.update { it.copy(importing = false, photoUri = capture.photoUri) }
            } catch(e: Exception) { state.update { it.copy(importing = false, error = com.floralens.ui.presentation.ProductMessages.error(e, "照片导入失败，请重新选择。")) } }
            finally { if(camera) input.releaseCamera(uri) }
        }
    }
    fun save() {
        val s = state.value
        if(s.loading || s.saving || s.importing || s.saved) return
        val baseline = baselineAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
        // Editing only a note must not silently truncate an existing timestamp to minute precision.
        val at = if(s.date == baseline?.toLocalDate().toString() && s.time == baseline?.toLocalTime()?.withSecond(0)?.withNano(0).toString()) baselineAt
            else runCatching { LocalDateTime.of(LocalDate.parse(s.date), LocalTime.parse(s.time)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
        if(at == null) { failure("请输入有效日期 YYYY-MM-DD 与时间 HH:mm"); return }
        val capture = handle.get<String>("editorCapture")
        if(observationId == null && capture == null) { failure("请先拍摄或选择照片"); return }
        state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                if(observationId != null) archive.edit(plantId, observationId, at, s.note) else add(plantId, capture!!, at, s.note)
                handle.remove<String>("editorCapture"); state.update { it.copy(saving = false, saved = true) }
            } catch(e: Exception) { state.update { it.copy(saving = false, error = com.floralens.ui.presentation.ProductMessages.error(e, "暂时无法保存，请检查日期与照片后重试。")) } }
        }
    }
    fun cancel() {
        if(state.value.saving || state.value.importing) return
        handle.remove<String>("editorCapture")?.let { id -> viewModelScope.launch { captures.cancel(id) } }
        handle.remove<String>("cameraOutput")?.let { input.releaseCamera(Uri.parse(it)) }
    }
}

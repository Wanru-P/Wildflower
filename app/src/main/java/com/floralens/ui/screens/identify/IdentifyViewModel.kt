package com.floralens.ui.screens.identify
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floralens.data.photos.CaptureStore
import com.floralens.data.settings.SettingsProvider
import com.floralens.di.RealIdentifier
import com.floralens.domain.model.*
import com.floralens.domain.usecase.IdentifyPlant
import com.floralens.ml.PlantIdentifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

data class IdentifyUiState(
    val isProcessing: Boolean = false, val isSaving: Boolean = false,
    val result: IdResult? = null, val capturedImage: ByteArray? = null,
    val captureId: String? = null, val photoUri: String? = null,
    val navigationCaptureId: String? = null, val navigationChatCaptureId: String? = null, val error: String? = null,
    val liveCandidate: IdCandidate? = null, val identificationSource: IdentificationSource? = null,
    val usedOfflineFallback: Boolean = false, val identificationMode: IdentificationMode = IdentificationMode.DEFAULT,
)
/** Disk captures own the lifetime; ByteArray is only the transient CameraX freeze. No collection here. */
@HiltViewModel
class IdentifyViewModel @Inject constructor(
    private val identifyPlant: IdentifyPlant, private val captures: CaptureStore,
    private val settings: SettingsProvider,
    @param:RealIdentifier private val realIdentifier: PlantIdentifier,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val state = MutableStateFlow(IdentifyUiState())
    val uiState = state.asStateFlow()
    private val liveIdentifyPlant = IdentifyPlant(realIdentifier)
    private val liveBusy = AtomicBoolean(false)
    private val captureBusy = AtomicBoolean(false)
    private var lastLiveAt = 0L
    init {
        viewModelScope.launch { settings.identificationMode.collect { mode -> state.update { it.copy(identificationMode = mode) } } }
        viewModelScope.launch {
            val id = savedStateHandle.get<String>("captureId")
            val restored = if(id != null) captures.get(id) else captures.latest()
            if(restored?.result != null) {
                savedStateHandle["captureId"] = restored.id
                state.update { it.copy(captureId = restored.id, photoUri = restored.photoUri, result = restored.result) }
            } else if(id != null) state.update { it.copy(error = "上次照片已失效，请重新拍摄") }
        }
    }
    /** Original 500ms/drop-while-busy live policy; NEVER calls the online identifier. */
    fun onLiveFrame(bytes: ByteArray) {
        val s = state.value
        if(s.isProcessing || s.result != null || s.captureId != null) return
        val now = System.currentTimeMillis()
        if(now - lastLiveAt < 500L || !liveBusy.compareAndSet(false, true)) return
        lastLiveAt = now
        viewModelScope.launch {
            try {
                val result = runCatching { liveIdentifyPlant(bytes) }.getOrNull()
                val top = when(result) { is IdResult.HighConfidence -> result.best; is IdResult.LowConfidence -> result.possibilities.firstOrNull(); else -> null }
                state.update { it.copy(liveCandidate = top) }
            } finally { liveBusy.set(false) }
        }
    }
    fun onCapture(bytes: ByteArray) {
        if(!captureBusy.compareAndSet(false, true)) return
        val previous = state.value.captureId
        state.update { it.copy(isProcessing = true, capturedImage = bytes, result = null, error = null, liveCandidate = null, usedOfflineFallback = false) }
        viewModelScope.launch {
            try {
                val capture = captures.create(bytes)
                if(previous != null) captures.cancel(previous)
                savedStateHandle["captureId"] = capture.id
                state.update { it.copy(captureId = capture.id, photoUri = capture.photoUri) }
                val requested = settings.identificationMode.first()
                val result = identifyPlant(bytes)
                captures.result(capture.id, result)
                val source = when(result) { is IdResult.HighConfidence -> result.best.source; is IdResult.LowConfidence -> result.possibilities.firstOrNull()?.source; else -> null }
                state.update { it.copy(isProcessing = false, result = result, identificationSource = source,
                    usedOfflineFallback = requested == IdentificationMode.ONLINE && source == IdentificationSource.ON_DEVICE) }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { state.update { it.copy(isProcessing = false, error = com.floralens.ui.presentation.ProductMessages.error(e, "暂时无法识别，请检查网络后重新拍摄。")) } }
            finally { captureBusy.set(false) }
        }
    }
    fun retry() { state.value.capturedImage?.let(::onCapture) ?: dismissResult() }
    fun openDetails(candidate: IdCandidate) = select(Species.identified(candidate.scientificName, candidate.commonName), chat = false)
    fun openChat(candidate: IdCandidate) = select(Species.identified(candidate.scientificName, candidate.commonName), chat = true)
    fun openManualDetails(name: String) { if(name.isNotBlank()) select(Species.manual(name)) }
    private fun select(species: Species, chat: Boolean = false) {
        val id = state.value.captureId ?: return
        if(state.value.isSaving) return
        state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try { captures.select(id, species); state.update { it.copy(isSaving = false,
                navigationCaptureId = if (chat) null else id, navigationChatCaptureId = if (chat) id else null) } }
            catch(e: Exception) { state.update { it.copy(isSaving = false, error = com.floralens.ui.presentation.ProductMessages.error(e, "本次照片不可用，请重新拍摄。")) } }
        }
    }
    fun consumeNavigation() = state.update { it.copy(navigationCaptureId = null) }
    fun consumeChatNavigation() = state.update { it.copy(navigationChatCaptureId = null) }
    fun dismissResult() {
        val id = state.value.captureId
        savedStateHandle.remove<String>("captureId")
        state.update { IdentifyUiState(identificationMode = it.identificationMode) }
        if(id != null) viewModelScope.launch { captures.cancel(id) }
    }
    fun onResume() = viewModelScope.launch {
        val id = state.value.captureId ?: return@launch
        if(!state.value.isProcessing && captures.get(id) == null) {
            savedStateHandle.remove<String>("captureId")
            state.update { IdentifyUiState(identificationMode = it.identificationMode) }
        }
    }
    fun consumeFallbackNotice() = state.update { it.copy(usedOfflineFallback = false) }
}

package com.floralens.ui.screens.leafhealth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floralens.data.insights.PlantInfoService
import com.floralens.data.network.NetworkStatus
import com.floralens.data.photos.PhotoStore
import com.floralens.data.repo.ObservationRepository
import com.floralens.data.settings.SettingsProvider
import com.floralens.domain.model.IdentificationMode
import com.floralens.domain.model.Observation
import com.floralens.domain.model.ObservationType
import com.floralens.domain.usecase.CheckLeafHealth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Leaf health flow: capture → CheckLeafHealth heuristic → result banner →
 * optional "Save to log" writing a LEAF_CHECK Observation with the photo.
 *
 * Online extra: when the user's mode is ONLINE and there's connectivity,
 * a Gemini vision note streams in *below* the heuristic result. The on-device
 * heuristic is always the primary answer — the AI note is a bonus, so its
 * failures are silent (loading just stops) rather than surfacing an error.
 * In OFFLINE mode the photo never leaves the device, exactly as before.
 */
data class LeafHealthUiState(
    val isAnalyzing: Boolean = false,
    val isSaving: Boolean = false,
    val result: CheckLeafHealth.Result? = null,
    val capturedImage: ByteArray? = null,
    val saved: Boolean = false,
    /** Gemini's short hedged note — null until loaded (or if unavailable). */
    val aiInsight: String? = null,
    val aiInsightLoading: Boolean = false,
)

@HiltViewModel
class LeafHealthViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val checkLeafHealth: CheckLeafHealth,
    private val observationRepository: ObservationRepository,
    private val photoStore: PhotoStore,
    private val plantInfoService: PlantInfoService,
    private val settings: SettingsProvider,
    private val networkStatus: NetworkStatus,
) : ViewModel() {

    private val plantId: Long = checkNotNull(savedStateHandle["plantId"])

    private val _uiState = MutableStateFlow(LeafHealthUiState())
    val uiState: StateFlow<LeafHealthUiState> = _uiState.asStateFlow()

    fun onCapture(imageBytes: ByteArray) {
        if (_uiState.value.isAnalyzing) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAnalyzing = true,
                    capturedImage = imageBytes,
                    result = null,
                    aiInsight = null,
                    aiInsightLoading = false,
                )
            }
            val result = checkLeafHealth(imageBytes)
            _uiState.update { it.copy(isAnalyzing = false, result = result) }

            maybeFetchAiInsight(imageBytes, result)
        }
    }

    /**
     * Gemini vision note — ONLINE mode + connectivity only, and never for
     * Unclear results (if the local check couldn't read the photo, asking
     * the cloud to squint at it just produces confident-sounding noise).
     */
    private fun maybeFetchAiInsight(imageBytes: ByteArray, result: CheckLeafHealth.Result) {
        if (result is CheckLeafHealth.Result.Unclear) return
        viewModelScope.launch {
            val mode = settings.identificationMode.first()
            if (mode != IdentificationMode.ONLINE || !networkStatus.isConnected()) return@launch

            _uiState.update { it.copy(aiInsightLoading = true) }
            val summary = when (result) {
                CheckLeafHealth.Result.Healthy -> "the leaf looks healthy"
                is CheckLeafHealth.Result.PossibleIssue -> result.friendlyMessage
                CheckLeafHealth.Result.Unclear -> return@launch // unreachable, guarded above
            }
            val insight = runCatching { plantInfoService.leafInsight(imageBytes, summary) }.getOrNull()
            // A newer capture may have replaced the photo while we waited —
            // don't attach this note to the wrong leaf.
            _uiState.update {
                if (it.capturedImage === imageBytes) {
                    it.copy(aiInsight = insight, aiInsightLoading = false)
                } else it
            }
        }
    }

    /**
     * Writes the LEAF_CHECK observation. The note is a short summary (the
     * history list is dense); the full hedged message lives on this screen only.
     */
    fun saveToLog() {
        val state = _uiState.value
        val result = state.result ?: return
        if (state.isSaving || result is CheckLeafHealth.Result.Unclear) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val photoUri = state.capturedImage?.let { photoStore.save(it) }
            val note = when (result) {
                CheckLeafHealth.Result.Healthy -> "叶片颜色观察：暂未见明显黄化或褐变。"
                is CheckLeafHealth.Result.PossibleIssue -> "叶片颜色观察：照片中有颜色变化，建议结合光照与浇水继续观察。"
                CheckLeafHealth.Result.Unclear -> return@launch // unreachable, guarded above
            }
            observationRepository.add(
                Observation(
                    plantId = plantId,
                    type = ObservationType.LEAF_CHECK,
                    photoUri = photoUri,
                    note = note,
                ),
            )
            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }

    /** Retake — clear the result and return to the viewfinder. */
    fun retake() = _uiState.update {
        it.copy(result = null, capturedImage = null, aiInsight = null, aiInsightLoading = false)
    }
}

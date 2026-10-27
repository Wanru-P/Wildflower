package com.floralens.ui.screens.plantdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floralens.data.repo.ObservationRepository
import com.floralens.data.repo.PlantRepository
import com.floralens.domain.model.CareStatus
import com.floralens.domain.model.Observation
import com.floralens.domain.model.Plant
import com.floralens.domain.usecase.ComputeCareStatus
import com.floralens.domain.usecase.MarkWatered
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class PlantDetailUiState(
    val isLoading: Boolean = true,
    val plant: Plant? = null,
    val status: CareStatus? = null,
    val nextDue: LocalDate? = null,
    val history: List<Observation> = emptyList(),
)

@HiltViewModel
class PlantDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val plantRepository: PlantRepository,
    observationRepository: ObservationRepository,
    computeCareStatus: ComputeCareStatus,
    private val markWatered: MarkWatered,
    private val clock: Clock,
    private val archive: com.floralens.data.photos.ArchiveService,
) : ViewModel() {

    private val plantId: Long = checkNotNull(savedStateHandle["plantId"])
    val selectedObservationId: Long = savedStateHandle.get<Long>("observationId") ?: 0

    val uiState: StateFlow<PlantDetailUiState> = combine(
        plantRepository.observeById(plantId),
        observationRepository.observeForPlant(plantId),
    ) { plant, history ->
        PlantDetailUiState(
            isLoading = false,
            plant = plant,
            status = plant?.let { computeCareStatus(it) },
            nextDue = plant?.nextDueDate(),
            history = history,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlantDetailUiState())

    /**
     * Same derivation ComputeCareStatus uses internally:
     * lastWateredAt + intervalDays, in the clock's zone. Never persisted.
     * Never-watered plants are due today.
     */
    private fun Plant.nextDueDate(): LocalDate {
        val lastWatered = lastWateredAt ?: return LocalDate.now(clock)
        return lastWatered.atZone(clock.zone).toLocalDate().plusDays(waterIntervalDays.toLong())
    }

    fun onMarkWatered() = viewModelScope.launch { markWatered(plantId) }

    fun onToggleReminders(enabled: Boolean) =
        viewModelScope.launch { plantRepository.setRemindersEnabled(plantId, enabled) }
    val editError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    fun rename(name: String) = viewModelScope.launch {
        try { archive.rename(plantId, name); editError.value = null }
        catch(e: Exception) { editError.value = com.floralens.ui.presentation.ProductMessages.error(e, "昵称保存失败，请输入 1–100 个字符后重试。") }
    }
}

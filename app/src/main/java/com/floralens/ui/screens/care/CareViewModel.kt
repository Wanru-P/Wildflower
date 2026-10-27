package com.floralens.ui.screens.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floralens.data.repo.PlantRepository
import com.floralens.domain.model.CareStatus
import com.floralens.domain.model.Plant
import com.floralens.domain.usecase.ComputeCareStatus
import com.floralens.domain.usecase.MarkWatered
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row on the Care screen — plant plus its precomputed subtitle. */
data class CareItem(
    val plant: Plant,
    val dueText: String,
    val urgent: Boolean = false,
)

data class CareUiState(
    val loaded: Boolean = false,
    val overdue: List<CareItem> = emptyList(),
    val today: List<CareItem> = emptyList(),
    val thisWeek: List<CareItem> = emptyList(),
) {
    /** All caught up — nothing due or upcoming this week. */
    val allCaughtUp: Boolean
        get() = loaded && overdue.isEmpty() && today.isEmpty() && thisWeek.isEmpty()
}

@HiltViewModel
class CareViewModel @Inject constructor(
    plantRepository: PlantRepository,
    private val computeCareStatus: ComputeCareStatus,
    private val markWatered: MarkWatered,
) : ViewModel() {

    val uiState: StateFlow<CareUiState> = plantRepository.observeAll()
        .map { plants ->
            val byStatus = plants.groupBy { computeCareStatus(it) }
            CareUiState(
                loaded = true,
                overdue = byStatus[CareStatus.OVERDUE].orEmpty().map(::toItem),
                today = byStatus[CareStatus.DUE_TODAY].orEmpty().map(::toItem),
                thisWeek = byStatus[CareStatus.THIS_WEEK].orEmpty().map(::toItem),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CareUiState())

    private fun toItem(plant: Plant): CareItem {
        val days = computeCareStatus.daysUntilDue(plant)
        return when {
            days < 0 -> {
                val n = -days
                CareItem(plant, "已到养护时间 $n 天", urgent = true)
            }
            days == 0L -> CareItem(plant, "今天留意浇水")
            days == 1L -> CareItem(plant, "明天留意浇水")
            else -> CareItem(plant, "$days 天后留意浇水")
        }
    }

    fun onMarkWatered(plantId: Long) {
        viewModelScope.launch { markWatered(plantId) }
    }
}

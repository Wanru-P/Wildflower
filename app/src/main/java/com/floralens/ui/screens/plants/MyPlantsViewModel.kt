package com.floralens.ui.screens.plants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floralens.data.repo.PlantInfoRepository
import com.floralens.data.repo.PlantRepository
import com.floralens.data.settings.SettingsProvider
import com.floralens.domain.model.CareStatus
import com.floralens.domain.model.IdentificationMode
import com.floralens.domain.model.Plant
import com.floralens.domain.usecase.ComputeCareStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import androidx.lifecycle.SavedStateHandle
import com.floralens.data.season.SeasonSource
import com.floralens.domain.season.Season

data class PlantWithStatus(
    val plant: Plant,
    val status: CareStatus,
    /** True when this species' details are saved for offline viewing. */
    val infoSaved: Boolean = false,
    /** Presentation-only date projection; no persisted field or seasonal rule changes. */
    val latestObservationAt: java.time.Instant? = null,
)

data class MyPlantsUiState(
    val plants: List<PlantWithStatus> = emptyList(),
    val isLoading: Boolean = true,
    /**
     * True when the user's identification mode is OFFLINE — the grid shows
     * the info-availability chip only then. Online, details are always a
     * fetch away, so the chip would be noise.
     */
    val offlineMode: Boolean = false,
    val selectedSeason: Season? = null,
    val totalPlants: Int = plants.size,
)

@HiltViewModel
class MyPlantsViewModel @Inject constructor(
    plantRepository: PlantRepository,
    plantInfoRepository: PlantInfoRepository,
    settings: SettingsProvider,
    computeCareStatus: ComputeCareStatus,
    seasons: SeasonSource,
    private val saved: SavedStateHandle,
) : ViewModel() {

    val uiState: StateFlow<MyPlantsUiState> = combine(
        seasons.observe(),
        plantInfoRepository.observeSavedSpecies(),
        settings.identificationMode,
        saved.getStateFlow<String?>("gardenSeason", null),
    ) { archive, savedSpecies, mode, seasonName ->
        val season = seasonName?.let { Season.valueOf(it) }
        val plants = archive.garden(season)
        val latestDates = archive.records.filter { season == null || it.period.season == season }
            .groupBy { it.plant.id }.mapValues { (_, records) -> records.maxOf { it.observation.timestamp } }
        MyPlantsUiState(
            plants = plants.map {
                PlantWithStatus(
                    plant = it,
                    status = computeCareStatus(it),
                    infoSaved = it.scientificName in savedSpecies,
                    latestObservationAt = latestDates[it.id],
                )
            },
            isLoading = false,
            offlineMode = mode == IdentificationMode.OFFLINE,
            selectedSeason = season,
            totalPlants = archive.plants.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyPlantsUiState())
    fun selectSeason(season: Season?) { saved["gardenSeason"] = season?.name }
}

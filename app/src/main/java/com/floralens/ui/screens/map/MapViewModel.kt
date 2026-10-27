package com.floralens.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floralens.BuildConfig
import com.floralens.data.repo.ObservationRepository
import com.floralens.data.repo.PlantRepository
import com.floralens.data.settings.SettingsProvider
import com.floralens.domain.model.IdentificationMode
import com.floralens.domain.model.Plant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MapUiState(
    val loaded: Boolean = false,
    val pins: List<MapPin> = emptyList(),
    /** Online map availability; a separate explicit privacy grant gates actual SDK use. */
    val useBaiduMap: Boolean = false,
    val invalidLocations: Int = 0,
    val onlineRequested: Boolean = false,
    val mapPrivacy: com.floralens.data.settings.MapPrivacyChoice = com.floralens.data.settings.MapPrivacyChoice.PENDING,
)

@HiltViewModel
class MapViewModel @Inject constructor(
    observationRepository: ObservationRepository,
    plantRepository: PlantRepository,
    settings: SettingsProvider,
    private val privacy: com.floralens.data.settings.BaiduMapPrivacy,
    coordinateSources: com.floralens.data.location.CoordinateProvenanceStore,
) : ViewModel() {

    val uiState: StateFlow<MapUiState> = combine(
        observationRepository.observeMapPins(),
        plantRepository.observeAll(),
        settings.identificationMode,
        privacy.choice,
        coordinateSources.changes,
    ) { observations, plants, mode, consent, _ ->
        val projection = ObservationMapProjection.join(observations, plants)
        MapUiState(
            loaded = true,
            pins = projection.pins.map { it.copy(coordinateSystem = coordinateSources.source(it.observationId, it.latitude, it.longitude)) },
            useBaiduMap = mode == IdentificationMode.ONLINE && BuildConfig.BAIDU_MAP_CONFIGURED,
            invalidLocations = projection.invalidLocations,
            onlineRequested = mode == IdentificationMode.ONLINE,
            mapPrivacy = consent,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())
    fun setMapPrivacy(value: com.floralens.data.settings.MapPrivacyChoice) = privacy.choose(value)
}

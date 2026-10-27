package com.floralens.ui.screens.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floralens.data.gbif.GbifService
import com.floralens.data.geo.GeocodingService
import com.floralens.data.insights.PlantInfoService
import com.floralens.data.network.NetworkStatus
import com.floralens.data.repo.PlantInfoRepository
import com.floralens.data.settings.SettingsProvider
import com.floralens.data.youtube.YouTubeService
import com.floralens.domain.model.GeoPoint
import com.floralens.domain.model.IdentificationMode
import com.floralens.domain.model.OccurrenceData
import com.floralens.domain.model.PlantInsights
import com.floralens.domain.model.VideoSection
import com.floralens.domain.model.VideoAvailability
import com.floralens.domain.model.cachedVideoAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.time.Instant
import com.floralens.data.explore.ExploreCatalog
import javax.inject.Inject
import androidx.compose.runtime.*
import com.floralens.data.photos.CaptureStore
import com.floralens.data.photos.PendingCapture
import com.floralens.data.photos.ArchiveService
import com.floralens.domain.model.Species
import com.floralens.domain.model.Plant
import com.floralens.domain.usecase.CollectPlantUseCase
import java.util.UUID

data class CollectionState(val existing: List<Plant> = emptyList(), val loading: Boolean = false,
    val saving: Boolean = false, val error: String? = null, val savedPlantId: Long? = null)

sealed interface DetailsUiState {
    val imageUrls: List<String>

    data class Loading(override val imageUrls: List<String>) : DetailsUiState
    data class Loaded(
        val insights: PlantInsights,
        override val imageUrls: List<String>,
        val geoPoints: List<GeoPoint> = emptyList(),
        val occurrence: OccurrenceData? = null,
        val occurrenceLoading: Boolean = true,
        val videoSections: List<VideoSection> = emptyList(),
        val videosLoading: Boolean = true,
        /** True when this render came from the offline store, not the network. */
        val fromOfflineCache: Boolean = false,
        /** When the offline copy was saved — shown in the cache banner. */
        val savedAt: Instant? = null,
        val videoAvailability: VideoAvailability = if (fromOfflineCache) VideoAvailability.UNAVAILABLE else if (videosLoading) VideoAvailability.LOADING else VideoAvailability.EMPTY,
    ) : DetailsUiState

    data class Error(
        val scientificName: String,
        override val imageUrls: List<String> = emptyList(),
        /** True when we're offline (by mode or connectivity) AND nothing is saved — wording differs. */
        val offlineAndNotSaved: Boolean = false,
    ) : DetailsUiState
}

/**
 * Load strategy:
 *  - ONLINE mode + connectivity → fetch fresh; every successful piece
 *    (insights → geo points → occurrence) is auto-saved to the offline
 *    store as it arrives, so simply *viewing* details makes them available
 *    offline later. Videos are never saved.
 *  - Otherwise → serve the saved copy if one exists. OFFLINE mode never
 *    touches the network here — the mode is a privacy promise, and the
 *    details screen honors it the same way identification does.
 *  - [refresh] re-runs the online path (only meaningful when it's possible;
 *    the screen gates the button on [canRefresh]).
 */
@HiltViewModel
class PlantDetailsViewModel @Inject constructor(
    private val infoService: PlantInfoService,
    private val geocodingService: GeocodingService,
    private val gbifService: GbifService,
    private val youTubeService: YouTubeService,
    private val plantInfoRepository: PlantInfoRepository,
    private val settings: SettingsProvider,
    private val networkStatus: NetworkStatus,
    private val savedStateHandle: SavedStateHandle,
    private val catalog: ExploreCatalog,
    private val captures: CaptureStore,
    private val archive: ArchiveService,
    private val collectPlant: CollectPlantUseCase,
) : ViewModel() {

    private fun decode(raw: String?): String? =
        raw?.let { URLDecoder.decode(it, "UTF-8") }?.takeIf { it.isNotBlank() }

    var scientificName: String by mutableStateOf(decode(savedStateHandle.get<String>("species")).orEmpty())
        private set
    var commonName: String? by mutableStateOf(decode(savedStateHandle.get<String>("common")))
        private set
    val reference get() = catalog.find(scientificName)
    private val captureId = savedStateHandle.get<String>("captureId")
    var capture: PendingCapture? by mutableStateOf(null)
        private set
    var collectionReady by mutableStateOf(captureId == null)
        private set
    var captureError: String? by mutableStateOf(null)
        private set
    val species get() = capture?.selected ?: Species.identified(scientificName, commonName.orEmpty())
    private val operationId: String = captureId?.let { "collect-$it" } ?: savedStateHandle.get<String>("collectionOperation")
        ?: "collect-${UUID.randomUUID()}".also { savedStateHandle["collectionOperation"] = it }
    private val _collection = MutableStateFlow(CollectionState())
    val collection = _collection.asStateFlow()

    private var imageUrls: List<String> =
        decode(savedStateHandle.get<String>("images"))
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading(imageUrls))
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    /** Reactive "saved for offline" indicator for the top bar. */
    private val scientificKey = MutableStateFlow(scientificName)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val isSaved: StateFlow<Boolean> = scientificKey
        .flatMapLatest { plantInfoRepository.observeIsSaved(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** True when a fresh fetch is possible right now — gates the refresh button. */
    var canRefresh: Boolean = false
        private set

    init {
        if (captureId == null) load() else viewModelScope.launch {
            capture = captures.get(captureId)
            if (capture?.selected == null) {
                captureError = "本次识别照片已失效，请返回重新拍摄。不会创建无照片档案。"
                _uiState.value = DetailsUiState.Error("", offlineAndNotSaved = true)
            } else {
                scientificName = capture!!.selected!!.scientificName.orEmpty()
                scientificKey.value = scientificName
                commonName = capture!!.selected!!.commonName
                imageUrls = listOf(capture!!.photoUri)
                collectionReady = true
                load()
            }
        }
    }

    fun existingPlants() = viewModelScope.launch {
        _collection.update { it.copy(loading = true, error = null) }
        try { val existing = archive.sameSpecies(species.key); _collection.update { it.copy(existing = existing, loading = false) } }
        catch(e: Exception) { _collection.update { it.copy(loading = false, error = com.floralens.ui.presentation.ProductMessages.error(e, "档案暂时无法读取，请重试。")) } }
    }
    fun collect(nickname: String, target: Long?) {
        if (!collectionReady || _collection.value.saving || _collection.value.loading) return
        _collection.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try { val id = collectPlant(operationId, species, nickname, captureId, target); _collection.update { it.copy(savedPlantId = id) } }
            catch(e: Exception) { _collection.update { it.copy(saving = false, error = com.floralens.ui.presentation.ProductMessages.error(e, "暂时无法收藏，照片与已有档案未改变，请重试。")) } }
        }
    }

    fun load() {
        if (captureId != null && !collectionReady) return
        if (scientificName.isBlank()) { _uiState.value = DetailsUiState.Error("", imageUrls, offlineAndNotSaved = true); return }
        _uiState.update { DetailsUiState.Loading(imageUrls) }
        viewModelScope.launch {
            val mode = settings.identificationMode.first()
            val online = mode == IdentificationMode.ONLINE && networkStatus.isConnected()
            canRefresh = online

            if (online) {
                loadFromNetwork(fallBackToCache = true)
            } else {
                loadFromCache(offlineByChoiceOrNecessity = true)
            }
        }
    }

    /** Manual "refresh saved copy" — online path only; the screen hides the button otherwise. */
    fun refresh() = load()

    private suspend fun loadFromNetwork(fallBackToCache: Boolean) {
        val insights = runCatching { infoService.fetch(scientificName, commonName) }.getOrNull()
        if (insights == null) {
            // Network said no — a saved copy is still better than an error.
            if (fallBackToCache && loadFromCacheOrNull()) return
            _uiState.update { DetailsUiState.Error(scientificName, imageUrls) }
            return
        }

        _uiState.update { DetailsUiState.Loaded(insights, imageUrls) }
        // Auto-save #1 — insights alone already make the offline copy useful.
        saveQuietly(insights, emptyList(), null)

        viewModelScope.launch {
            // Map pins (throttled geocoding).
            if (insights.nativeRegions.isNotEmpty()) {
                launch {
                    val points = runCatching { geocodingService.geocodeAll(insights.nativeRegions) }.getOrDefault(emptyList())
                    _uiState.update { s -> if (s is DetailsUiState.Loaded) s.copy(geoPoints = points) else s }
                    if (points.isNotEmpty()) saveCurrent()
                }
            }

            // GBIF occurrence chart.
            launch {
                val occ = runCatching { gbifService.occurrencesByCountry(scientificName) }.getOrNull()
                _uiState.update { s -> if (s is DetailsUiState.Loaded) s.copy(occurrence = occ, occurrenceLoading = false) else s }
                if (occ != null) saveCurrent()
            }

            // YouTube video sections — never persisted.
            launch {
                val result = try { youTubeService.lookup(commonName.orEmpty(), scientificName) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { com.floralens.data.youtube.VideoLookup(emptyList(), if (networkStatus.isConnected()) VideoAvailability.UNAVAILABLE else VideoAvailability.NO_NETWORK) }
                val availability = if (result.availability == VideoAvailability.UNAVAILABLE && youTubeService.configured() && !networkStatus.isConnected()) VideoAvailability.NO_NETWORK else result.availability
                _uiState.update { s -> if (s is DetailsUiState.Loaded) s.copy(videoSections = result.sections, videosLoading = false, videoAvailability = availability) else s }
            }
        }
    }

    private suspend fun loadFromCache(offlineByChoiceOrNecessity: Boolean) {
        if (!loadFromCacheOrNull()) {
            _uiState.update {
                DetailsUiState.Error(
                    scientificName = scientificName,
                    imageUrls = imageUrls,
                    offlineAndNotSaved = offlineByChoiceOrNecessity,
                )
            }
        }
    }

    /** Renders the saved copy if it exists. Returns false when nothing is saved. */
    private suspend fun loadFromCacheOrNull(): Boolean {
        val saved = plantInfoRepository.getSaved(scientificName) ?: return false
        val videoState = cachedVideoAvailability(youTubeService.configured(), settings.identificationMode.first() == IdentificationMode.ONLINE, networkStatus.isConnected())
        _uiState.update {
            DetailsUiState.Loaded(
                insights = saved.insights,
                imageUrls = imageUrls,
                geoPoints = saved.geoPoints,
                occurrence = saved.occurrence,
                occurrenceLoading = false,
                videoSections = emptyList(),
                videosLoading = false,
                fromOfflineCache = true,
                savedAt = saved.savedAt,
                videoAvailability = videoState,
            )
        }
        return true
    }

    /** Re-saves whatever the state currently holds — called as pieces stream in. */
    private fun saveCurrent() {
        val s = _uiState.value as? DetailsUiState.Loaded ?: return
        saveQuietly(s.insights, s.geoPoints, s.occurrence)
    }

    private fun saveQuietly(insights: PlantInsights, geoPoints: List<GeoPoint>, occurrence: OccurrenceData?) {
        viewModelScope.launch {
            runCatching { plantInfoRepository.save(insights, geoPoints, occurrence, commonName) }
        }
    }
}

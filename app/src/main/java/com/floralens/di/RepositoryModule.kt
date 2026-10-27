package com.floralens.di

import com.floralens.data.species.CareDefaults
import com.floralens.data.species.SpeciesReference
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.floralens.data.photos.FilePhotoStore
import com.floralens.data.photos.PhotoStore
import com.floralens.data.location.LocationProvider
import com.floralens.data.location.SystemLocationProvider
import com.floralens.data.network.NetworkStatus
import com.floralens.data.network.SystemNetworkStatus
import com.floralens.data.settings.SettingsProvider
import com.floralens.data.settings.SettingsRepository
import com.floralens.data.insights.PlantInfoService
import com.floralens.data.insights.OpenAiPlantInfoService
import com.floralens.data.geo.GeocodingService
import com.floralens.data.geo.NominatimGeocodingService
import com.floralens.data.gbif.GbifService
import com.floralens.data.gbif.GbifOccurrenceService
import com.floralens.data.youtube.YouTubeService
import com.floralens.data.youtube.YouTubeDataService

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindSeasonSource(impl: com.floralens.data.season.SeasonRepository): com.floralens.data.season.SeasonSource

    @Binds @Singleton
    abstract fun bindCaptureStore(impl: com.floralens.data.photos.PendingCaptureStore): com.floralens.data.photos.CaptureStore

    @Binds @Singleton
    abstract fun bindCareDefaults(impl: SpeciesReference): CareDefaults

    @Binds @Singleton
    abstract fun bindLocationProvider(impl: SystemLocationProvider): LocationProvider

    @Binds @Singleton
    abstract fun bindPhotoStore(impl: FilePhotoStore): PhotoStore

    @Binds @Singleton
    abstract fun bindNetworkStatus(impl: SystemNetworkStatus): NetworkStatus

    @Binds @Singleton
    abstract fun bindSettingsProvider(impl: SettingsRepository): SettingsProvider

    // AI insights provider — swapped from GeminiPlantInfoService to OpenAI by
    // changing only this binding; nothing above the PlantInfoService
    // interface knows or cares. (Gemini impl kept in the tree as the living
    // proof of the pattern.)
    @Binds @Singleton
    abstract fun bindPlantInfoService(impl: OpenAiPlantInfoService): PlantInfoService

    @Binds @Singleton
    abstract fun bindGeocodingService(impl: NominatimGeocodingService): GeocodingService

    @Binds @Singleton
    abstract fun bindGbifService(impl: GbifOccurrenceService): GbifService

    @Binds @Singleton
    abstract fun bindYouTubeService(impl: YouTubeDataService): YouTubeService
}

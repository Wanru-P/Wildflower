package com.floralens

import com.floralens.data.db.FloraDatabase
import com.floralens.data.photos.PhotoStore
import com.floralens.data.repo.PlantRepository
import com.floralens.data.repo.ObservationRepository
import com.floralens.data.repo.PlantInfoRepository
import com.floralens.data.settings.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Debug-only instrumentation access; no fixture UI, receiver, or production write path. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface Phase1TestAccess {
    fun database(): FloraDatabase
    fun plants(): PlantRepository
    fun observations(): ObservationRepository
    fun settings(): SettingsRepository
    fun photos(): PhotoStore
    fun info(): PlantInfoRepository
    fun captures(): com.floralens.data.photos.CaptureStore
    fun archive(): com.floralens.data.photos.ArchiveService
    fun seasons(): com.floralens.data.season.SeasonRepository
    fun culture(): com.floralens.data.culture.CultureRepository
    fun cards(): com.floralens.data.card.CardContentAssembler
}

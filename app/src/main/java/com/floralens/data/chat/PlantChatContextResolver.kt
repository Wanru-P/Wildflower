package com.floralens.data.chat

import com.floralens.data.culture.CultureRepository
import com.floralens.data.explore.ExploreCatalog
import com.floralens.data.photos.PendingCaptureStore
import com.floralens.data.repo.ObservationRepository
import com.floralens.data.repo.PlantRepository
import com.floralens.domain.chat.ApprovedCultureReference
import com.floralens.domain.chat.PlantChatContext
import com.floralens.domain.chat.PlantChatSource
import com.floralens.domain.culture.CultureIdentificationPolicy
import com.floralens.domain.culture.CultureIdentityResolver
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlantChatContextResolver @Inject constructor(
    private val captures: PendingCaptureStore,
    private val plants: PlantRepository,
    private val observations: ObservationRepository,
    private val explore: ExploreCatalog,
    private val culture: CultureRepository,
) {
    suspend fun resolve(source: PlantChatSource, speciesKey: String?, plantId: Long, captureId: String?): PlantChatContext? = when (source) {
        PlantChatSource.CAPTURE -> captureId?.takeIf(String::isNotBlank)?.let { id ->
            val capture = captures.get(id) ?: return@let null
            val species = capture.selected ?: return@let null
            val certain = CultureIdentificationPolicy.certain(capture.result, species.key)
            val identity = CultureIdentityResolver.resolve(species.key, species.scientificName, species.commonName, certain)
            val confidence = when (val result = capture.result) {
                is com.floralens.domain.model.IdResult.HighConfidence -> (listOf(result.best) + result.alternates)
                    .firstOrNull { CultureIdentityResolver.resolve(scientific = it.scientificName).key == identity.key }?.confidence
                is com.floralens.domain.model.IdResult.LowConfidence -> result.possibilities
                    .firstOrNull { CultureIdentityResolver.resolve(scientific = it.scientificName).key == identity.key }?.confidence
                else -> null
            }
            context(source, identity, species.commonName, species.scientificName, capture.photoUri, confidence)
        }
        PlantChatSource.SPECIES -> speciesKey?.takeIf(String::isNotBlank)?.let { key ->
            val identity = CultureIdentityResolver.resolve(key)
            val reference = identity.scientific?.let(explore::find)
            context(source, identity, reference?.name ?: identity.chinese.orEmpty(), identity.scientific, reference?.photo, null)
        }
        PlantChatSource.PLANT -> plantId.takeIf { it > 0 }?.let { id ->
            val plant = plants.observeById(id).first() ?: return@let null
            val history = observations.observeForPlant(id).first()
            val identity = CultureIdentityResolver.resolve(plant.speciesKey, plant.scientificName, plant.commonName, certain = true)
            val cover = history.firstOrNull { it.id == plant.coverObservationId }?.photoUri?.takeIf(String::isNotBlank)
                ?: plant.photoUri
            context(source, identity, plant.commonName.ifBlank { plant.nickname }, plant.scientificName, cover, null)
        }
    }

    private suspend fun context(
        source: PlantChatSource,
        identity: com.floralens.domain.culture.CultureIdentity,
        fallbackName: String,
        scientificName: String?,
        imageUri: String?,
        confidence: Float?,
    ): PlantChatContext {
        val approved = if (identity.certain) culture.approvedContent(identity.key) else null
        val reference = approved?.literaryWorks?.firstOrNull()?.let { work ->
            val title = work.title ?: return@let null
            val author = work.author ?: return@let null
            val excerpt = work.excerpt ?: return@let null
            val url = work.sourceUrl ?: return@let null
            ApprovedCultureReference(title, author, excerpt, url)
        }
        val localName = identity.chinese ?: fallbackName.trim().takeIf { it.isNotEmpty() } ?: "未知植物"
        return PlantChatContext(source, identity, localName, scientificName?.takeIf(String::isNotBlank), imageUri, confidence, reference)
    }
}

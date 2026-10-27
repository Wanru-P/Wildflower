package com.floralens.data.card

import android.content.Context
import android.graphics.BitmapFactory
import androidx.room.withTransaction
import com.floralens.data.culture.CultureRepository
import com.floralens.data.db.FloraDatabase
import com.floralens.data.db.entity.toDomain
import com.floralens.domain.card.*
import com.floralens.domain.model.Observation
import com.floralens.domain.season.SeasonResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.time.ZoneId
import javax.inject.Inject

class CardContentAssembler @Inject constructor(private val db: FloraDatabase, private val culture: CultureRepository,
    @param:ApplicationContext private val context: Context,
    private val expressions: com.floralens.data.culture.NatureExpressionRepository) {
    fun readable(uri: String?): Boolean = runCatching {
        val file = File(URI(uri ?: return false)).canonicalFile
        require(file.parentFile == File(context.filesDir, "photos").canonicalFile && file.isFile && file.canRead())
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong()*bounds.outHeight <= 80_000_000)
        val sample = BitmapFactory.Options().apply {
            inSampleSize = 1
            while(bounds.outWidth/inSampleSize > 256 || bounds.outHeight/inSampleSize > 256) inSampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeFile(file.path, sample) ?: return false
        bitmap.recycle(); true
    }.getOrDefault(false)

    suspend fun photos(plantId: Long): List<Observation> = withContext(Dispatchers.IO) {
        db.archiveDao().history(plantId).map { it.toDomain() }
            .filter { readable(it.photoUri) }
    }

    /** Fresh transaction on every preview/save/share: deletion, edits and ownership are rechecked. */
    suspend fun assemble(plantId: Long, observationId: Long): ValidatedCardContent = withContext(Dispatchers.IO) {
        val (plant, observation) = db.withTransaction {
            val plant = requireNotNull(db.archiveDao().plant(plantId)) { "植物档案已删除" }.toDomain()
            val observation = requireNotNull(db.archiveDao().observation(observationId)) { "观察已删除，请重新选择照片" }.toDomain()
            plant to observation
        }
        val zone = ZoneId.systemDefault()
        val uncertain = db.archiveDao().history(plantId).any { record ->
            com.floralens.data.culture.CultureSnapshotPolicy.uncertain(record.identificationJson) }
        val identity = com.floralens.domain.culture.CultureIdentityResolver.resolve(plant.speciesKey, plant.scientificName, certain = !uncertain)
        val approved = if (identity.certain) culture.approvedContent(identity.key) else null
        CardValidation.validate(CardContent(plant.id, observation.id, plant.nickname,
            plant.commonName, plant.scientificName, observation.photoUri.orEmpty(),
            observation.timestamp.atZone(zone).toLocalDate(), observation.note?.takeIf(String::isNotBlank),
            SeasonResolver.period(observation.timestamp, zone).season.label, identity.key, approved, expressions.current(identity)),
            observation.plantId, readable(observation.photoUri))
    }
}

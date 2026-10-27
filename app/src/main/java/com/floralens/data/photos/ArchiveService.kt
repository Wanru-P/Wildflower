package com.floralens.data.photos

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.room.withTransaction
import com.floralens.data.db.*
import com.floralens.data.db.entity.*
import com.floralens.data.location.LocationProvider
import com.floralens.data.species.CareDefaults
import com.floralens.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recoverable protocol, not a claimed filesystem/SQLite distributed transaction:
 * durable intent -> validated final file -> Room transaction + receipt -> pending/journal cleanup.
 * A crash before receipt deletes the unreferenced file; after receipt retains it and completes cleanup.
 * All app archive mutations/recovery share one lock. Legacy PhotoStore callers never get swept wholesale.
 */
@Singleton
class ArchiveService @Inject constructor(
    private val db: FloraDatabase,
    private val photos: FilePhotoStore,
    private val pending: CaptureStore,
    private val defaults: CareDefaults,
    private val location: LocationProvider,
    @ApplicationContext private val context: Context,
) {
    private val lock = Mutex()
    private val dao get() = db.archiveDao()
    private val coordinateSources = com.floralens.data.location.CoordinateProvenanceStore(context)
    private val journals get() = File(context.filesDir, "photo-operations").apply { mkdirs() }
    /** Fault injection on an isolated test instance; never configured by production UI. */
    internal var checkpoint: (String) -> Unit = {}
    private fun journal(id: String): AtomicFile {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,100}")))
        return AtomicFile(File(journals, "$id.json"))
    }
    private fun intent(id: String, uri: String?, captureId: String?, deletion: Boolean = false) {
        val atomic = journal(id); val output = atomic.startWrite()
        try {
            output.write(JSONObject().apply { put("uri", uri ?: JSONObject.NULL); put("capture", captureId ?: JSONObject.NULL); put("deletion", deletion) }.toString().toByteArray())
            atomic.finishWrite(output)
        } catch (e: Exception) { atomic.failWrite(output); throw e }
    }
    suspend fun recover() = withContext(Dispatchers.IO) { lock.withLock { recoverLocked() } }
    private suspend fun recoverLocked() {
        journals.listFiles().orEmpty().filter { it.name.endsWith(".json") || it.name.endsWith(".json.bak") }
            .map { it.name.removeSuffix(".bak").removeSuffix(".json") }.distinct().forEach { id ->
                val atomic = journal(id)
                val data = JSONObject(atomic.openRead().bufferedReader().use { it.readText() })
                val uri = data.optString("uri").takeUnless { it.isBlank() || it == "null" }
                val capture = data.optString("capture").takeUnless { it.isBlank() || it == "null" }
                val committed = dao.operation(id) != null
                if (uri != null && ((!committed) || data.optBoolean("deletion")) && dao.references(uri) == 0) photos.delete(uri)
                // Discard an incomplete bounded copy too, but never touch a legacy/shared file.
                photos.target(id).let { File(it.parentFile, it.name + ".part").delete() }
                if (committed && capture != null) pending.cancel(capture)
                atomic.delete()
            }
    }
    suspend fun collect(operationId: String, species: Species, nickname: String, captureId: String?, existingPlantId: Long?): Long =
        commit(operationId, species, nickname, captureId, existingPlantId, true, null, null)
    suspend fun add(plantId: Long, captureId: String, observedAt: Long, note: String?): Long =
        commit("observe-$captureId", null, "", captureId, plantId, false, observedAt, note)

    private suspend fun commit(id: String, species: Species?, nickname: String, captureId: String?, target: Long?, identified: Boolean, at: Long?, note: String?): Long =
        withContext(Dispatchers.IO) { lock.withLock {
            recoverLocked()
            dao.operation(id)?.let { receipt ->
                check(dao.plant(receipt.plantId) != null) { "该植物档案已删除" }
                return@withLock receipt.plantId
            }
            val capture = captureId?.let { requireNotNull(pending.get(it)) { "本次照片已失效，请重新拍摄" } }
            require(captureId == null || capture != null)
            if (target == null) { require(species != null); require(nickname.isNotBlank() && nickname.length <= 100) { "昵称应为 1–100 个字符" } }
            if (target != null) {
                val plant = requireNotNull(dao.plant(target)) { "植物档案已不存在" }
                if (identified) require(plant.speciesKey == species?.key) { "请选择同物种的植物档案" }
            }
            require(note == null || note.length <= 500) { "备注最多 500 个字符" }
            val uri = capture?.let { photos.target(id).toURI().toString() }
            intent(id, uri, captureId)
            checkpoint("intent")
            try {
                if (capture != null) {
                    val input = requireNotNull(context.contentResolver.openInputStream(Uri.parse(capture.photoUri))) { "照片无法读取" }
                    photos.write(uri!!, input)
                }
                checkpoint("file")
                val coordinates = runCatching { location.lastKnownLocation() }.getOrNull()
                val plantId = db.withTransaction {
                    val pid = target ?: dao.insertPlant(Plant(
                        nickname = nickname.trim(), scientificName = species!!.scientificName.orEmpty(), commonName = species.commonName,
                        speciesKey = species.key, waterIntervalDays = defaults.defaultWaterInterval(species.scientificName.orEmpty()),
                        remindersEnabled = false).toEntity())
                    checkpoint("plant")
                    val oid = if (capture != null) db.observationDao().insert(Observation(
                        plantId = pid, type = if(identified) ObservationType.IDENTIFIED else ObservationType.PHOTO_OBSERVATION,
                        photoUri = uri, timestamp = java.time.Instant.ofEpochMilli(at ?: capture.createdAt),
                        note = note?.trim()?.takeIf { it.isNotBlank() }, latitude = coordinates?.latitude, longitude = coordinates?.longitude,
                        identificationJson = if (identified) capture.snapshot() else null).toEntity()) else null
                    if (oid != null && dao.plant(pid)?.coverObservationId == null) dao.cover(pid, oid, uri)
                    dao.receipt(PhotoOperation(id, pid, oid, uri)); pid
                }
                checkpoint("committed")
                // Optional provenance is written only after Room commits. Failure safely leaves an UNKNOWN source.
                if (coordinates != null) runCatching {
                    dao.operation(id)?.observationId?.let { oid -> coordinateSources.record(oid,
                        coordinates.latitude, coordinates.longitude, CoordinateSystem.WGS84) }
                }
                withContext(NonCancellable) { if(captureId != null) pending.cancel(captureId); journal(id).delete() }
                plantId
            } catch (e: Exception) {
                // Cancellation also compensates, but not a committed operation. The journal survives cleanup failure.
                withContext(NonCancellable) { recoverLocked() }
                throw e
            }
        } }

    suspend fun sameSpecies(key: String) = dao.sameSpecies(key).map { it.toDomain() }
    suspend fun cover(plantId: Long, observationId: Long) = withContext(Dispatchers.IO) { lock.withLock { db.withTransaction {
        val observation = requireNotNull(dao.observation(observationId)) { "观察记录不存在" }
        require(observation.plantId == plantId && observation.photoUri != null) { "封面必须来自本株植物的照片" }
        require(runCatching { File(java.net.URI(observation.photoUri)).isFile }.getOrDefault(false)) { "照片已丢失，不能设为封面" }
        dao.cover(plantId, observationId, observation.photoUri)
    } } }
    suspend fun edit(plantId: Long, observationId: Long, at: Long, note: String?) = db.withTransaction {
        require(dao.observation(observationId)?.plantId == plantId); require(note == null || note.length <= 500)
        dao.edit(observationId, at, note?.trim()?.takeIf { it.isNotBlank() })
    }
    suspend fun rename(plantId: Long, nickname: String) {
        require(nickname.isNotBlank() && nickname.length <= 100); dao.nickname(plantId, nickname.trim())
    }
    suspend fun delete(plantId: Long, observationId: Long) = withContext(Dispatchers.IO) { lock.withLock {
        recoverLocked()
        val observation = requireNotNull(dao.observation(observationId)) { "观察已删除" }
        require(observation.plantId == plantId)
        val operation = "delete-${UUID.randomUUID()}"
        intent(operation, observation.photoUri, null, true)
        db.withTransaction {
            val plant = requireNotNull(dao.plant(plantId))
            if (plant.coverObservationId == observationId || plant.photoUri == observation.photoUri) {
                val next = dao.history(plantId).firstOrNull { it.id != observationId && it.photoUri != null && runCatching { File(java.net.URI(it.photoUri)).isFile }.getOrDefault(false) }
                dao.cover(plantId, next?.id, next?.photoUri)
            }
            dao.deleteObservation(observationId)
            dao.receipt(PhotoOperation(operation, plantId, observationId, observation.photoUri))
        }
        coordinateSources.remove(observationId)
        recoverLocked()
    } }
}

package com.floralens.data.photos

import android.content.Context
import android.util.AtomicFile
import com.floralens.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PendingCapture(val id: String, val photoUri: String, val createdAt: Long,
    val result: IdResult? = null, val selected: Species? = null) {
    // Long-lived event metadata must not point at a temporary photo that is deleted after commit.
    fun snapshot(): String = CaptureCodec.encode(this).apply { remove("photoUri") }.toString()
}

interface CaptureStore {
    suspend fun create(bytes: ByteArray): PendingCapture
    suspend fun result(id: String, result: IdResult): PendingCapture
    suspend fun select(id: String, species: Species): PendingCapture
    suspend fun get(id: String): PendingCapture?
    suspend fun latest(): PendingCapture?
    suspend fun cancel(id: String)
    suspend fun cleanup()
}

/** Disk, not SavedStateHandle bytes, is the authority across navigation and process recreation. */
@Singleton
class PendingCaptureStore @Inject constructor(@ApplicationContext private val context: Context) : CaptureStore {
    private val lock = Mutex()
    private val directory get() = File(context.filesDir, "pending-captures").apply { mkdirs() }
    private fun file(id: String, extension: String): File {
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false))
        return File(directory, "$id.$extension")
    }
    override suspend fun create(bytes: ByteArray): PendingCapture = withContext(Dispatchers.IO) { lock.withLock {
        require(bytes.isNotEmpty() && bytes.size <= 32 * 1024 * 1024) { "照片为空或超过 32 MB" }
        val id = UUID.randomUUID().toString(); val photo = file(id, "jpg")
        try {
            FileOutputStream(photo).use { it.write(bytes); it.fd.sync() }
            FilePhotoStore(context).validate(photo)
            PendingCapture(id, photo.toURI().toString(), System.currentTimeMillis()).also { write(it) }
        } catch (e: Exception) { photo.delete(); throw e }
    } }
    private fun write(capture: PendingCapture) {
        val atomic = AtomicFile(file(capture.id, "json")); val output = atomic.startWrite()
        try { output.write(CaptureCodec.encode(capture).toString().toByteArray()); atomic.finishWrite(output) }
        catch (e: Exception) { atomic.failWrite(output); throw e }
    }
    private fun read(id: String): PendingCapture? = runCatching {
        val value = CaptureCodec.decode(JSONObject(AtomicFile(file(id, "json")).openRead().bufferedReader().use { it.readText() }))
        value.takeIf { it.id == id && file(id, "jpg").isFile && System.currentTimeMillis() - it.createdAt < TTL }
            ?.copy(photoUri = file(id, "jpg").toURI().toString())
    }.getOrNull()
    override suspend fun get(id: String) = withContext(Dispatchers.IO) { lock.withLock { read(id) } }
    override suspend fun result(id: String, result: IdResult) = update(id) { it.copy(result = result) }
    override suspend fun select(id: String, species: Species) = update(id) { it.copy(selected = species) }
    private suspend fun update(id: String, transform: (PendingCapture) -> PendingCapture) = withContext(Dispatchers.IO) { lock.withLock {
        transform(requireNotNull(read(id)) { "本次照片已失效，请重新拍摄" }).also { write(it) }
    } }
    override suspend fun latest() = withContext(Dispatchers.IO) { lock.withLock {
        directory.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull { read(it.nameWithoutExtension) }
            .filter { it.result != null }.maxByOrNull { it.createdAt }
    } }
    override suspend fun cancel(id: String) = withContext(Dispatchers.IO) { lock.withLock {
        AtomicFile(file(id, "json")).delete(); file(id, "jpg").delete(); Unit
    } }
    override suspend fun cleanup() = withContext(Dispatchers.IO) { lock.withLock {
        directory.listFiles().orEmpty().filter { System.currentTimeMillis() - it.lastModified() >= TTL }
            .filter { it.name.matches(Regex("[0-9a-f-]{36}\\.(jpg|json|json.bak|json.new)")) }.forEach { it.delete() }
    } }
    companion object { const val TTL = 48L * 60 * 60 * 1000 }
}

internal object CaptureCodec {
    fun encode(c: PendingCapture): JSONObject = JSONObject().apply {
        put("version", 1); put("id", c.id); put("photoUri", c.photoUri); put("createdAt", c.createdAt)
        put("kind", when(c.result) { is IdResult.HighConfidence -> "high"; is IdResult.LowConfidence -> "low"; IdResult.NoPlantDetected -> "none"; null -> "pending" })
        val candidates = when(val r = c.result) { is IdResult.HighConfidence -> listOf(r.best) + r.alternates; is IdResult.LowConfidence -> r.possibilities; else -> emptyList() }
        put("candidates", JSONArray().apply { candidates.forEach { candidate -> put(JSONObject().apply {
            put("scientific", candidate.scientificName); put("common", candidate.commonName); put("confidence", candidate.confidence.toDouble())
            put("source", candidate.source.name); put("images", JSONArray(candidate.imageUrls))
        }) } })
        c.selected?.let { put("selected", JSONObject().apply { put("key", it.key); put("scientific", it.scientificName ?: JSONObject.NULL); put("common", it.commonName) }) }
    }
    fun decode(j: JSONObject): PendingCapture {
        val a = j.getJSONArray("candidates")
        val candidates = (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
            val images = o.getJSONArray("images")
            IdCandidate(o.getString("scientific"), o.getString("common"), o.getDouble("confidence").toFloat(), IdentificationSource.valueOf(o.getString("source")), (0 until images.length()).map { images.getString(it) })
        } }
        val result = when(j.getString("kind")) { "high" -> IdResult.HighConfidence(candidates.first(), candidates.drop(1)); "low" -> IdResult.LowConfidence(candidates); "none" -> IdResult.NoPlantDetected; else -> null }
        val selected = j.optJSONObject("selected")?.let { Species(it.getString("key"), if(it.isNull("scientific")) null else it.getString("scientific"), it.getString("common")) }
        return PendingCapture(j.getString("id"), j.getString("photoUri"), j.getLong("createdAt"), result, selected)
    }
}

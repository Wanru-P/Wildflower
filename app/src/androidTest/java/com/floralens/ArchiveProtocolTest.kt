package com.floralens

import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.data.db.FloraDatabase
import com.floralens.data.db.entity.*
import com.floralens.data.location.LocationProvider
import com.floralens.data.location.Coordinates
import com.floralens.data.photos.*
import com.floralens.data.species.CareDefaults
import com.floralens.domain.model.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.net.URI
import java.util.UUID

/** Real SQLite + real files. Each test owns an isolated directory/database; never clears app records. */
class ArchiveProtocolTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var root: File
    private lateinit var context: ContextWrapper
    private lateinit var db: FloraDatabase
    private lateinit var photos: FilePhotoStore
    private lateinit var captures: PendingCaptureStore
    private lateinit var service: ArchiveService
    private val species = Species.identified("Ginkgo biloba", "银杏")
    private val bytes get() = base.assets.open("explore/ginkgo.jpg").use { it.readBytes() }
    private val defaults = object : CareDefaults { override fun defaultWaterInterval(scientificName: String) = 7 }
    private val location = object : LocationProvider { override fun lastKnownLocation(): Coordinates? = null }
    @Before fun setup() {
        root = File(base.filesDir, "protocol-test-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(base) {
            override fun getFilesDir() = root
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
        db = openDatabase()
        photos = FilePhotoStore(context); captures = PendingCaptureStore(context); service = fresh()
    }
    @After fun cleanup() { db.close(); check(root.parentFile!!.canonicalFile == base.filesDir.canonicalFile); root.deleteRecursively() }
    private fun openDatabase() = Room.databaseBuilder(context, FloraDatabase::class.java, File(root, "archive.db").absolutePath).build()
    private fun fresh() = ArchiveService(db, photos, captures, defaults, location, context)
    private suspend fun capture(): PendingCapture {
        val c = captures.create(bytes)
        captures.result(c.id, IdResult.HighConfidence(IdCandidate("Ginkgo biloba", "银杏", .91f, IdentificationSource.ON_DEVICE), emptyList()))
        return captures.select(c.id, species)
    }
    private suspend fun collect(c: PendingCapture? = null, target: Long? = null): Long {
        val photo = c ?: capture()
        return service.collect("collect-${photo.id}", species, "Debug · 银杏", photo.id, target)
    }
    private fun readable(uri: String?) = uri != null && File(URI(uri)).readBytes().contentEquals(bytes)
    private suspend fun failure(action: suspend () -> Unit) { var failed = false; try { action() } catch(e: Exception) { failed = true }; assertTrue("Expected a real rejected operation", failed) }
    private suspend fun death(stage: String, action: suspend () -> Unit) {
        service.checkpoint = { if(it == stage) throw SimulatedDeath() }
        try { action(); fail("Checkpoint did not fire") } catch(_: SimulatedDeath) { }
        db.close(); db = openDatabase()
        captures = PendingCaptureStore(context); photos = FilePhotoStore(context); service = fresh()
    }
    private class SimulatedDeath : Error("Simulated process interruption, not a handled application error")

    @Test fun firstCollection_andConcurrentRetry_areExactlyOnce() = runBlocking {
        val c = capture()
        val ids = coroutineScope { List(4) { async { collect(c) } }.awaitAll() }
        assertEquals(1, ids.distinct().size); assertEquals(1, db.plantDao().count())
        val history = db.archiveDao().history(ids.first()); assertEquals(1, history.size)
        assertEquals("IDENTIFIED", history.single().type); assertNotNull(history.single().identificationJson)
        assertTrue(history.single().identificationJson!!.contains("0.91"))
        assertFalse(org.json.JSONObject(history.single().identificationJson!!).has("photoUri"))
        assertNull(history.single().latitude); assertTrue(readable(history.single().photoUri))
        assertEquals(history.single().id, db.archiveDao().plant(ids.first())!!.coverObservationId)
        assertNull(captures.get(c.id))
    }
    @Test fun noPhotoCollection_createsNoObservation_andNeverAutoMergesSpecies() = runBlocking {
        val a = service.collect("manual-a", species, "一号", null, null)
        val b = service.collect("manual-b", species, "二号", null, null)
        assertNotEquals(a, b); assertEquals(2, service.sameSpecies(species.key).size)
        assertTrue(db.archiveDao().history(a).isEmpty()); assertTrue(db.archiveDao().history(b).isEmpty())
        assertNull(db.archiveDao().plant(a)!!.photoUri)
    }
    @Test fun explicitExistingPlant_appends_andExplicitNewPlant_remainsSeparate() = runBlocking {
        val a = collect(); val old = db.archiveDao().history(a).single().photoUri
        assertEquals(a, collect(target = a)); assertEquals(2, db.archiveDao().history(a).size)
        val b = collect(); assertNotEquals(a, b); assertEquals(2, db.plantDao().count())
        assertTrue(readable(old)); assertEquals(old, db.archiveDao().plant(a)!!.photoUri)
    }
    @Test fun append_edit_cover_andDelete_keepOtherHistory_andUseNoRecognition() = runBlocking {
        val a = collect(); val first = db.archiveDao().history(a).single()
        val second = captures.create(bytes); service.add(a, second.id, 1000L, "第二次观察")
        val third = captures.create(bytes); service.add(a, third.id, 2000L, "第三次观察")
        var history = db.archiveDao().history(a); assertEquals(3, history.size)
        history.filter { it.id != first.id }.forEach { assertEquals("PHOTO_OBSERVATION", it.type); assertNull(it.identificationJson) }
        val cover = history.first { it.id != first.id }; service.cover(a, cover.id); service.edit(a, cover.id, 3000L, "修改后的备注")
        assertEquals(cover.id, db.archiveDao().plant(a)!!.coverObservationId)
        assertTrue(readable(first.photoUri)); assertEquals(3000L, db.archiveDao().observation(cover.id)!!.timestampEpochMs)
        assertEquals("修改后的备注", db.archiveDao().observation(cover.id)!!.note)
        service.delete(a, cover.id); history = db.archiveDao().history(a)
        assertEquals(2, history.size); assertFalse(File(URI(cover.photoUri)).exists())
        assertTrue(readable(first.photoUri)); assertNotEquals(cover.id, db.archiveDao().plant(a)!!.coverObservationId)
        history.forEach { service.delete(a, it.id) }; assertNull(db.archiveDao().plant(a)!!.coverObservationId)
        assertNull(db.archiveDao().plant(a)!!.photoUri)
    }
    @Test fun roomFailure_rollsBackPlant_andCompensatesPhoto_thenRetryWorks() = runBlocking {
        val c = capture(); val id = "collect-${c.id}"
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_observation BEFORE INSERT ON observations BEGIN SELECT RAISE(ABORT, 'test Room write failure'); END")
        failure { collect(c) }
        assertEquals(0, db.plantDao().count()); assertFalse(photos.target(id).exists()); assertNotNull(captures.get(c.id))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_observation")
        val a = collect(c); assertEquals(1, db.archiveDao().history(a).size)
    }
    @Test fun photoWriteFailure_doesNotCreatePartialArchive_andCanRetry() = runBlocking {
        val c = capture(); val id = "collect-${c.id}"
        service.checkpoint = { if(it == "intent") photos.target(id).mkdir() } // actual rename failure, no mocked exception
        failure { collect(c) }; assertEquals(0, db.plantDao().count()); assertNotNull(captures.get(c.id))
        service.checkpoint = {}; collect(c); assertEquals(1, db.plantDao().count())
    }
    @Test fun interruptionAfterFile_beforeTransaction_recoversOrphan_andRetry() = runBlocking {
        val c = capture(); val id = "collect-${c.id}"
        death("file") { collect(c) }; assertTrue(photos.target(id).isFile); assertEquals(0, db.plantDao().count())
        service.recover(); assertFalse(photos.target(id).exists()); assertNotNull(captures.get(c.id))
        val a = collect(c); assertTrue(readable(db.archiveDao().history(a).single().photoUri))
    }
    @Test fun interruptionInsideRoom_rollsBack_thenRecoveryRemovesFile() = runBlocking {
        val c = capture(); death("plant") { collect(c) }; assertEquals(0, db.plantDao().count())
        service.recover(); assertFalse(photos.target("collect-${c.id}").exists()); collect(c)
        assertEquals(1, db.plantDao().count())
    }
    @Test fun interruptionAfterReceipt_keepsCommittedPhoto_andCompletesCleanup() = runBlocking {
        val c = capture(); val id = "collect-${c.id}"
        death("committed") { collect(c) }; assertEquals(1, db.plantDao().count()); assertNotNull(captures.get(c.id))
        service.recover(); assertNull(captures.get(c.id)); val a = collect(c)
        assertEquals(1, db.archiveDao().history(a).size); assertTrue(photos.target(id).isFile)
    }
    @Test fun sharedUri_isNotDeletedWhileAnotherPlantStillReferencesIt() = runBlocking {
        val a = collect(); val first = db.archiveDao().history(a).single()
        val b = service.collect("another-plant", species, "另一株", null, null)
        val oid = db.observationDao().insert(Observation(plantId = b, type = ObservationType.PHOTO_OBSERVATION, photoUri = first.photoUri).toEntity())
        service.cover(b, oid); service.delete(a, first.id)
        assertTrue(readable(first.photoUri)); assertEquals(oid, db.archiveDao().plant(b)!!.coverObservationId)
        service.delete(b, oid); assertFalse(File(URI(first.photoUri)).exists())
    }
    @Test fun plantUpsert_preservesHistory_andCoverRejectsOtherPlantOrMissingFile() = runBlocking {
        val a = collect(); val first = db.archiveDao().history(a).single()
        db.plantDao().upsert(db.archiveDao().plant(a)!!.copy(nickname = "修改昵称"))
        assertEquals(1, db.archiveDao().history(a).size)
        val b = service.collect("other", species, "另一株", null, null)
        failure { service.cover(b, first.id) }; assertNull(db.archiveDao().plant(b)!!.coverObservationId)
        File(URI(first.photoUri)).delete(); failure { service.cover(a, first.id) }
        assertEquals(1, db.archiveDao().history(a).size)
    }
    @Test fun pendingRestore_cancel_expiry_invalidBytes_andSafeDeletion() = runBlocking {
        val c = capture(); val restored = PendingCaptureStore(context).get(c.id)!!
        assertEquals(species, restored.selected); assertEquals(c.result, restored.result); assertTrue(readable(restored.photoUri))
        captures.cancel(c.id); assertNull(captures.get(c.id)); assertFalse(File(URI(c.photoUri)).exists())
        failure { captures.create("not an image".toByteArray()) }
        failure { captures.create(ByteArray(32 * 1024 * 1024 + 1)) }
        val expired = capture(); val metadata = File(root, "pending-captures/${expired.id}.json")
        val json = org.json.JSONObject(metadata.readText()).put("createdAt", System.currentTimeMillis() - PendingCaptureStore.TTL - 1000)
        metadata.writeText(json.toString()); metadata.setLastModified(System.currentTimeMillis() - PendingCaptureStore.TTL - 1000)
        File(URI(expired.photoUri)).setLastModified(System.currentTimeMillis() - PendingCaptureStore.TTL - 1000)
        assertNull(captures.get(expired.id)); captures.cleanup(); assertFalse(File(URI(expired.photoUri)).exists())
        val outside = File(root, "unrelated.jpg").apply { writeBytes(bytes) }
        photos.delete(outside.toURI().toString()); assertTrue(outside.exists())
        val ordinary = captures.create(bytes); assertNull(captures.latest()); assertNotNull(captures.get(ordinary.id))
    }
    @Test fun pickerUri_isCopied_andReadableAfterExternalSourceDeletion() = runBlocking {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "Debug-protocol-${UUID.randomUUID()}.jpg")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        val external = requireNotNull(base.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        var deleted = false
        try {
            base.contentResolver.openOutputStream(external)!!.use { it.write(bytes) }
            val input = PhotoInput(context, captures).importPhoto(external)
            base.contentResolver.delete(external, null, null)
            deleted = true
            val a = service.collect("no-photo", species, "相册观察", null, null)
            service.add(a, input.id, 1000, "授权参考摄影 fixture")
            assertTrue(readable(db.archiveDao().history(a).single().photoUri))
        } finally { if(!deleted) base.contentResolver.delete(external, null, null) }
    }
    @Test fun databaseAndStoresReopen_preserveThreePhotos_datesNotes_andCover() = runBlocking {
        val a = collect()
        val second = captures.create(bytes); service.add(a, second.id, 1000, "第二次")
        val third = captures.create(bytes); service.add(a, third.id, 2000, "第三次")
        val cover = db.archiveDao().history(a).first { it.timestampEpochMs == 2000L }; service.cover(a, cover.id)
        db.close(); db = openDatabase(); captures = PendingCaptureStore(context); photos = FilePhotoStore(context); service = fresh()
        service.recover()
        assertEquals(3, db.archiveDao().history(a).size)
        assertEquals(cover.id, db.archiveDao().plant(a)!!.coverObservationId)
        db.archiveDao().history(a).forEach { assertTrue(readable(it.photoUri)) }
        assertEquals("第三次", db.archiveDao().observation(cover.id)!!.note)
    }
}

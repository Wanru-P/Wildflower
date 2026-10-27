package com.floralens

import android.content.ComponentName
import android.content.Intent
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.card.export.*
import com.floralens.card.render.*
import com.floralens.card.share.CardShare
import com.floralens.domain.card.*
import com.floralens.domain.model.Species
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.net.URI
import java.time.LocalDate
import java.util.UUID

class CardPipelineTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val access get() = EntryPointAccessors.fromApplication(context,Phase1TestAccess::class.java)
    private fun validated(note: String? = null, long: Boolean = false) = CardValidation.validate(CardContent(1,2,
        if(long) "图书馆旁连续观察的银杏树与自然记忆" else "校园银杏", "银杏", if(long) "Ginkgo biloba var. example (a long test label for typography only)" else "Ginkgo biloba",
        "file:/test/photo.jpg",LocalDate.of(2026,10,2),note,"秋","scientific:ginkgo biloba"),1,true)
    private fun source() = context.assets.open("explore/ginkgo.jpg").use { BitmapFactory.decodeStream(it)!! }
    private fun bitmap(type: CardTemplateId = CardTemplateId.MODERN_JOURNAL) = source().let { photo -> try { CardRenderer(context).render(validated(),type,photo) } finally { photo.recycle() } }
    private fun shell(cmd: String) = instrumentation.uiAutomation.executeShellCommand(cmd).use { FileInputStream(it.fileDescriptor).bufferedReader().readText() }
    private fun owned(action: suspend (Long,Long) -> Unit) = runBlocking {
        val plant = access.archive().collect("card-test-${UUID.randomUUID()}",Species.identified("Ginkgo biloba","银杏"),"Debug · 诗笺管线",null,null)
        try {
            val capture = access.captures().create(context.assets.open("explore/ginkgo.jpg").use { it.readBytes() })
            access.archive().add(plant,capture.id,java.time.Instant.parse("2026-10-02T09:20:00Z").toEpochMilli(),"Debug fixture · 管线观察备注")
            val observation = access.database().archiveDao().history(plant).single().id
            action(plant,observation)
        } finally {
            access.database().archiveDao().history(plant).forEach { access.archive().delete(plant,it.id) }; access.plants().delete(plant)
        }
    }
    @Test fun assemblerOwnDateNotesNoCultureOriginalImmutable() = owned { plant, observation ->
        val before = access.database().archiveDao().observation(observation)!!
        val photo = File(URI(before.photoUri)); val bytes = photo.readBytes()
        val result = access.cards().assemble(plant,observation).content
        assertEquals(plant,result.plantId); assertEquals(observation,result.observationId)
        assertEquals(before.note,result.note); assertEquals(before.photoUri,result.photoUri)
        assertEquals(java.time.Instant.ofEpochMilli(before.timestampEpochMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),result.observedDate)
        assertEquals("scientific:ginkgo biloba", result.approvedCulture?.speciesKey); assertEquals("Ginkgo biloba",result.scientificName)
        assertEquals(before,access.database().archiveDao().observation(observation)); assertArrayEquals(bytes,photo.readBytes())
    }
    @Test fun assemblerWrongOwnerAndDeletionRejected() = owned { plant, observation ->
        val other = access.archive().collect("card-test-${UUID.randomUUID()}",Species.manual("测试"),"Debug · 另一株",null,null)
        try { assertTrue(runCatching { access.cards().assemble(other,observation) }.isFailure) } finally { access.plants().delete(other) }
        access.archive().delete(plant,observation)
        assertTrue(runCatching { access.cards().assemble(plant,observation) }.isFailure)
    }
    @Test fun assemblerMissingPhotoCannotExport() = owned { plant, observation ->
        val uri = access.database().archiveDao().observation(observation)!!.photoUri!!
        access.photos().delete(uri) // only this test's freshly owned photo
        assertTrue(access.cards().photos(plant).isEmpty())
        assertTrue(runCatching { access.cards().assemble(plant,observation) }.isFailure)
    }
    @Test fun threeTemplatesFixedSizeAndLongMixedText() {
        val photo = source()
        try { CardTemplateId.entries.forEach { type ->
            val c = validated("真实观察：field notes 中英文混排与中文标点。".repeat(6),true)
            val b = CardRenderer(context).render(c,type,photo)
            assertEquals(1600,b.width); assertEquals(2400,b.height)
            assertNotEquals(b.getPixel(20,20),b.getPixel(800,1200)); b.recycle()
        } } finally { photo.recycle() }
    }
    @Test fun allTemplatesEmptyNoteCultureAndPreviewSameEngine() {
        val photo = source()
        try { CardTemplateId.entries.forEach { type ->
            val full = CardRenderer(context).render(validated(),type,photo)
            val small = CardRenderer(context).render(validated(),type,photo,640)
            assertEquals(640,small.width); assertEquals(960,small.height)
            // Sampled paper fibres differ slightly by resolution, unlike the former flat fill.
            // Assert the same opaque paper tone and retain the photo/crop comparisons below.
            val paperFull = full.getPixel(20,20); val paperPreview = small.getPixel(8,8)
            assertEquals(255, Color.alpha(paperFull)); assertEquals(255, Color.alpha(paperPreview))
            assertTrue(kotlin.math.abs(Color.red(paperFull)-Color.red(paperPreview)) < 12)
            assertTrue(kotlin.math.abs(Color.green(paperFull)-Color.green(paperPreview)) < 12)
            assertTrue(kotlin.math.abs(Color.blue(paperFull)-Color.blue(paperPreview)) < 12)
            // Same photograph crop and margins across resolution, excluding text antialias edges.
            val scaled = Bitmap.createScaledBitmap(full,640,960,true)
            for(y in listOf(400,450,500)) for(x in listOf(260,300,340)) {
                val a=scaled.getPixel(x,y); val b=small.getPixel(x,y)
                assertTrue(kotlin.math.abs(Color.green(a)-Color.green(b)) < 12)
            }
            scaled.recycle(); small.recycle(); full.recycle()
        } } finally { photo.recycle() }
    }
    @Test fun cropDoesNotStretch() {
        val photo = Bitmap.createBitmap(600,200,Bitmap.Config.ARGB_8888)
        Canvas(photo).apply { drawColor(Color.RED); drawRect(200f,0f,400f,200f,Paint().apply { color=Color.BLUE }) }
        val out = Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888)
        CardPage(Canvas(out)).photo(photo,RectF(0f,0f,200f,200f))
        assertEquals(Color.BLUE,out.getPixel(2,100)); assertEquals(Color.BLUE,out.getPixel(197,100))
        out.recycle(); photo.recycle()
    }
    @Test fun exifOrientationAndDecodeFailure() {
        val file = File(context.cacheDir,"phase4-exif-${UUID.randomUUID()}.jpg")
        try {
            val b = Bitmap.createBitmap(120,60,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
            file.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG,90,it) }; b.recycle()
            ExifInterface(file.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
            val decoded = CardPhotoDecoder().decode(file.toURI().toString()); assertEquals(60,decoded.width); assertEquals(120,decoded.height); decoded.recycle()
            file.writeText("not an image"); assertTrue(runCatching { CardPhotoDecoder().decode(file.toURI().toString()) }.isFailure)
        } finally { file.delete() }
    }
    @Test fun layoutRejectsOverflowRatherThanDropText() {
        val b = Bitmap.createBitmap(1600,2400,Bitmap.Config.ARGB_8888)
        try { assertTrue(runCatching { CardPage(Canvas(b)).text("字".repeat(1000),RectF(10f,10f,100f,30f),40f) }.isFailure)
            assertTrue(runCatching { CardPage(Canvas(b)).text("text",RectF(-1f,10f,100f,30f),26f) }.isFailure)
        } finally { b.recycle() }
    }
    @Test fun pngGalleryMagicDecodeUniqueAndPublished() {
        val b = bitmap(); val exporter = CardExporter(context); val uris = mutableListOf<Uri>()
        try {
            repeat(2) { uris += exporter.saveGallery(b) }
            assertNotEquals(uris[0],uris[1])
            uris.forEach { uri ->
                assertEquals("image/png",context.contentResolver.getType(uri))
                val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                assertArrayEquals(byteArrayOf(-119,80,78,71,13,10,26,10),bytes.copyOfRange(0,8))
                val decoded = BitmapFactory.decodeByteArray(bytes,0,bytes.size)!!; assertEquals(1600,decoded.width); assertEquals(2400,decoded.height); decoded.recycle()
                context.contentResolver.query(uri,arrayOf(MediaStore.Images.Media.IS_PENDING,MediaStore.Images.Media.RELATIVE_PATH,MediaStore.Images.Media.DISPLAY_NAME),null,null,null)!!.use { cursor ->
                    assertTrue(cursor.moveToFirst()); assertEquals(0,cursor.getInt(0)); assertEquals("Pictures/Wildflower Lens/",cursor.getString(1)); assertTrue(cursor.getString(2).endsWith(".png"))
                }
            }
        } finally { b.recycle(); uris.forEach { context.contentResolver.delete(it,null,null) } }
    }
    @Test fun outputWriteAndFinalizeFailuresAbortThenRetry() {
        val b = bitmap()
        try {
            for(failWrite in listOf(true,false)) {
                var aborted=false; var completed=false
                val destination = object:PngDestination {
                    override fun open():OutputStream = if(failWrite) object:OutputStream() { override fun write(value:Int) { throw IOException("injected") } } else ByteArrayOutputStream()
                    override fun complete() { completed=true; error("injected finalize failure") }
                    override fun abort() { aborted=true }
                }
                assertTrue(runCatching { CardPngWriter.write(b,destination) }.isFailure); assertTrue(aborted)
                if(!failWrite) assertTrue(completed)
            }
            val uri=CardExporter(context).saveGallery(b); context.contentResolver.delete(uri,null,null)
        } finally { b.recycle() }
    }
    @Test fun galleryFailedPublishDeletesOwnPendingRow() {
        val resolver=context.contentResolver
        val uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,"phase4-failure-${UUID.randomUUID()}.png"); put(MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Wildflower Lens/"); put(MediaStore.Images.Media.IS_PENDING,1)
        })!!
        val b=bitmap()
        try {
            assertTrue(runCatching { CardPngWriter.write(b,object:PngDestination {
                override fun open()=resolver.openOutputStream(uri)!!
                override fun complete() { error("injected finalization error") }
                override fun abort() { resolver.delete(uri,null,null) }
            }) }.isFailure)
            resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media._ID}=?",arrayOf(android.content.ContentUris.parseId(uri).toString()),null)!!.use { assertFalse(it.moveToFirst()) }
        } finally { b.recycle(); runCatching { resolver.delete(uri,null,null) } }
    }
    @Test fun realReceiverReadsContentWithGrantAndCleanupIsScoped() {
        val b=bitmap(); val share=CardShare(context)
        val first=share.prepare(b); val second=share.prepare(b); b.recycle()
        assertNotEquals(first,second)
        val intent=share.intent(second)
        assertEquals(Intent.ACTION_SEND,intent.action); assertEquals("image/png",intent.type)
        assertEquals("content",second.scheme); assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(second,intent.clipData!!.getItemAt(0).uri)
        val component=ComponentName("com.floralens.test","com.floralens.CardReceiveActivity")
        // Receiver UID initially has no grant to the first URI.
        // ACTION_SEND automatically migrates EXTRA_STREAM into a granted ClipData on Android.
        // An explicit test action avoids that automatic grant for the negative permission check.
        context.startActivity(Intent("com.floralens.TEST_NO_GRANT").setComponent(component).putExtra(Intent.EXTRA_STREAM,first).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var denied=""
        repeat(20) { if(!denied.contains("READ_FAILED")) { Thread.sleep(200); denied=shell("run-as com.floralens.test cat files/phase4-received.txt") } }
        assertTrue(denied,denied.contains("READ_FAILED"))
        context.startActivity(intent.setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var result=""
        repeat(20) { if(!result.contains("READ_OK")) { Thread.sleep(200); result=shell("run-as com.floralens.test cat files/phase4-received.txt") } }
        assertTrue(result,result.contains("READ_OK") && result.contains("size=1600x2400"))
        assertFalse(result.contains("uid=${android.os.Process.myUid()} "))
        android.util.Log.i("Phase4Evidence",result)
        shell("mkdir -p /sdcard/Download/wildflower-phase4"); shell("screencap -p /sdcard/Download/wildflower-phase4/share-receiver.png")
        val directory=File(context.cacheDir,"card-share"); val fresh=listOf(first,second).map { File(directory,Uri.decode(it.lastPathSegment!!)) }
        fresh.forEach { it.setLastModified(System.currentTimeMillis()-8L*24*60*60*1000) }
        val camera=File(context.cacheDir,"observation-camera/phase4-keep.txt").apply { parentFile!!.mkdirs(); writeText("keep") }
        try { share.cleanup(); assertTrue(camera.isFile); assertTrue(fresh.none { it.exists() }) } finally { camera.delete() }
        shell("input keyevent 4")
    }
    @Test fun anyOwnedObservationPhotoIsEligibleWithoutReclassifyingSeasonArchive() = owned { plant, observation ->
        val original=access.database().archiveDao().observation(observation)!!
        val leaf=access.observations().add(com.floralens.domain.model.Observation(plantId=plant,
            type=com.floralens.domain.model.ObservationType.LEAF_CHECK,photoUri=original.photoUri,note="Debug fixture · 既有叶片照片"))
        assertTrue(access.cards().photos(plant).any { it.id==leaf })
        assertEquals(leaf,access.cards().assemble(plant,leaf).content.observationId)
        assertFalse(com.floralens.domain.season.SeasonResolver.natural(access.cards().photos(plant).single { it.id==leaf }))
    }
}

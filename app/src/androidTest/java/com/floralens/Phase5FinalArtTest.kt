package com.floralens

import android.content.ContentValues
import android.graphics.*
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.card.export.CardExporter
import com.floralens.card.render.CardPhotoDecoder
import com.floralens.card.render.CardRenderer
import com.floralens.domain.card.CardTemplateId
import com.floralens.domain.model.Species
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.URI
import java.time.Instant
import java.util.UUID

/** Actual private photos -> approved-content assembler -> renderer -> MediaStore -> decoded PNG.
 * Only this test's Room IDs and gallery URIs are removed. Evidence files are explicit reference art.
 */
class Phase5FinalArtTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val access get() = EntryPointAccessors.fromApplication(context, Phase1TestAccess::class.java)
    @Test fun transparentFurnitureAndBundledChineseFont() {
        val font = context.resources.getFont(R.font.noto_serif_sc)
        val paint = Paint().apply { typeface = font; textSize = 56f }
        for (character in "桂花银杏观察手账四季相遇") assertTrue(paint.hasGlyph(character.toString()))
        for (resource in listOf(R.drawable.botanical_branch, R.drawable.botanical_journal_empty)) {
            val image = BitmapFactory.decodeResource(context.resources, resource)
            try { assertTrue(image.hasAlpha()); assertEquals(0, Color.alpha(image.getPixel(0,0))) }
            finally { image.recycle() }
        }
    }
    @Test fun actualThreeTemplateExportsPreservePhotoAndAuditBoundary() = runBlocking {
        val plantId = access.archive().collect("phase5final-${UUID.randomUUID()}", Species.identified("Osmanthus fragrans", "桂花"), "桂花 · 参考摄影档案", null, null)
        val generated = mutableListOf<android.net.Uri>()
        try {
            val capture = access.captures().create(context.assets.open("explore/osmanthus_kenraiz.jpg").use { it.readBytes() })
            access.archive().add(plantId, capture.id, Instant.parse("2026-10-08T02:35:00Z").toEpochMilli(), "授权参考摄影 · 非用户野外实拍。日期仅为归档示例。")
            val before = access.database().archiveDao().history(plantId).single()
            val photoFile = File(URI(before.photoUri!!)); val original = photoFile.readBytes()
            val content = access.cards().assemble(plantId, before.id)
            assertEquals("scientific:osmanthus fragrans", content.content.approvedCulture?.speciesKey)
            val photo = CardPhotoDecoder().decode(before.photoUri)
            val renderer = CardRenderer(context)
            val round = InstrumentationRegistry.getArguments().getString("phase5FinalRound")?.takeIf { it.matches(Regex("round[123]|final|v2-round[12]|v2-final(?:-pass)?")) } ?: "test"
            try { CardTemplateId.entries.forEach { template ->
                val image = renderer.render(content, template, photo)
                try {
                    val saved = CardExporter(context).saveGallery(image); generated += saved
                    val bytes = context.contentResolver.openInputStream(saved)!!.use { it.readBytes() }
                    val decoded = BitmapFactory.decodeByteArray(bytes,0,bytes.size)
                    try { assertEquals(1600, decoded.width); assertEquals(2400, decoded.height) }
                    finally { decoded.recycle() }
                    // Persist the actual saved PNG unchanged, not a screenshot of the preview.
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "${template.name.lowercase()}-${UUID.randomUUID()}.png")
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/wildflower-phase5-final-$round/")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val evidence = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values))
                    try {
                        context.contentResolver.openOutputStream(evidence)!!.use { it.write(bytes) }
                        context.contentResolver.update(evidence, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) }, null,null)
                    } catch (failure: Throwable) { context.contentResolver.delete(evidence,null,null); throw failure }
                    android.util.Log.i("Phase5FinalExport", "$template / $evidence / ${bytes.size} bytes")
                } finally { image.recycle() }
            } } finally { photo.recycle() }
            assertEquals(before, access.database().archiveDao().history(plantId).single())
            assertArrayEquals(original, photoFile.readBytes())
        } finally {
            generated.forEach { context.contentResolver.delete(it,null,null) }
            access.database().archiveDao().history(plantId).forEach { access.archive().delete(plantId,it.id) }
            access.plants().delete(plantId)
        }
    }
}

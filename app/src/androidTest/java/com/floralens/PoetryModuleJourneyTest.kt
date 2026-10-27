package com.floralens

import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.domain.card.CardTemplateId
import com.floralens.domain.model.Species
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** Owned, explicitly labelled reference archive. Exercises the production editor and export API. */
class PoetryModuleJourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val access get() = EntryPointAccessors.fromApplication(context, Phase1TestAccess::class.java)
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).bufferedReader().readText()
    }
    private fun waitText(text: String) = ui.waitUntil(30_000) {
        ui.onAllNodesWithText(text).fetchSemanticsNodes(false).isNotEmpty()
    }
    private fun click(text: String) {
        waitText(text)
        val node = ui.onNodeWithText(text)
        // Bottom navigation is fixed, while page entries are scrollable.
        try { node.performScrollTo() } catch (_: AssertionError) { }
        node.assertIsDisplayed().performClick()
    }
    private fun ready(type: CardTemplateId) {
        ui.waitUntil(30_000) { ui.onAllNodesWithContentDescription("${type.title}预览").fetchSemanticsNodes(false).isNotEmpty() }
        ui.waitUntil(30_000) { ui.onAllNodesWithContentDescription("${type.title}缩略预览").fetchSemanticsNodes(false).isNotEmpty() }
        ui.waitUntil(30_000) { ui.onAllNodes(hasTestTag("card-save") and isEnabled()).fetchSemanticsNodes(false).isNotEmpty() }
        ui.onNodeWithTag("card-save").assertIsEnabled()
    }
    private fun shot(name: String) {
        ui.waitForIdle(); Thread.sleep(700); ui.waitForIdle()
        val round = InstrumentationRegistry.getArguments().getString("poetryRound") ?: "round1"
        shell("mkdir -p /sdcard/Download/wildflower-poetry/$round")
        shell("screencap -p /sdcard/Download/wildflower-poetry/$round/$name.png")
    }
    @Test fun referenceArchiveThreeTemplatesRealSaveShareAndResponsiveEditor() {
        val name = "桂花 · 参考摄影档案"
        var owner: Long? = null
        val exports = mutableListOf<android.net.Uri>()
        runBlocking { access.settings().setOnboarded() }
        shell("wm size 1080x2400"); shell("wm density 440")
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val id = runBlocking {
                val plant = access.archive().collect("poetry-kit-${UUID.randomUUID()}", Species.identified("Osmanthus fragrans", "桂花"), name, null, null)
                owner = plant
                listOf("2026-10-08T09:30:00" to "osmanthus_kenraiz.jpg", "2026-10-11T10:00:00" to "osmanthus_starr.jpg").forEach { (date, file) ->
                    val capture = access.captures().create(context.assets.open("explore/$file").use { it.readBytes() })
                    val credit = if(file == "osmanthus_kenraiz.jpg") "Kenraiz · CC BY-SA 4.0" else "Forest & Kim Starr · CC BY 2.0"
                    access.archive().add(plant, capture.id, LocalDateTime.parse(date).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        "$credit（裁切）\n授权参考摄影 · 非用户野外实拍。日期用于布局验收，不代表同株生长过程。")
                }
                plant
            }
            val before = runBlocking { access.database().archiveDao().history(id) }
            val bytes = before.associate { it.photoUri!! to File(URI(it.photoUri)).readBytes() }
            waitText("发现自然，收藏四季。")
            click("花园"); click(name); click("制作植物诗笺"); ready(CardTemplateId.MODERN_JOURNAL)
            ui.onNodeWithTag("card-photo-picker").performScrollTo().performClick()
            waitText("选择一次真实观察")
            shot("photo-picker")
            ui.onNodeWithTag("card-photo-${before.last().id}").performClick()
            ready(CardTemplateId.MODERN_JOURNAL)
            ui.onNodeWithTag("card-selected-date").assertTextEquals("本次观察 · 2026.10.08")
            ui.onNodeWithText(name).performScrollTo()
            shot("editor-393-top")
            ui.onNodeWithTag("template-MODERN_JOURNAL").performScrollTo()
            shot("editor-393-templates")
            for (type in CardTemplateId.entries) {
                ui.onNodeWithTag("template-${type.name}").performScrollTo().performClick()
                ready(type)
                ui.onNodeWithTag("template-${type.name}").assertIsSelected()
                ui.onNodeWithTag("card-save").performClick()
                waitText("PNG 已保存到 Pictures/Wildflower Lens")
                val resolver = context.contentResolver
                val uri = resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
                    "${MediaStore.Images.Media.RELATIVE_PATH}=?", arrayOf("Pictures/Wildflower Lens/"), "${MediaStore.Images.Media._ID} DESC")!!.use {
                    check(it.moveToFirst())
                    android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it.getLong(0))
                }
                exports += uri
                val file = File(context.getExternalFilesDir(null), "poetry-evidence/${type.name.lowercase()}.png").apply { parentFile!!.mkdirs() }
                resolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
                val bitmap = BitmapFactory.decodeFile(file.path)!!
                assertEquals(1600, bitmap.width); assertEquals(2400, bitmap.height); bitmap.recycle()
                // Public evidence survives UTP's package uninstall. No shell parsing of spaced paths.
                val round = InstrumentationRegistry.getArguments().getString("poetryRound") ?: "round1"
                val evidence = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, android.content.ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "$round-${type.name.lowercase()}.png")
                    put(MediaStore.Downloads.MIME_TYPE, "image/png")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/wildflower-poetry/exports/")
                })!!
                try {
                    resolver.openInputStream(uri)!!.use { input -> resolver.openOutputStream(evidence)!!.use { input.copyTo(it) } }
                    resolver.openInputStream(evidence)!!.use { input ->
                        val copied = BitmapFactory.decodeStream(input)!!
                        assertEquals(1600, copied.width); assertEquals(2400, copied.height); copied.recycle()
                    }
                } catch (failure: Throwable) {
                    resolver.delete(evidence, null, null); throw failure
                }
            }
            // Return to a clean editor state via its real selection controls after the save evidence.
            ui.onNodeWithTag("template-BOTANICAL_ATLAS").performScrollTo().performClick()
            ready(CardTemplateId.BOTANICAL_ATLAS)
            ui.onNodeWithTag("template-MODERN_JOURNAL").performScrollTo().performClick()
            ready(CardTemplateId.MODERN_JOURNAL)
            // An extended real device viewport captures the complete scrollable page, not a stitched mock.
            shell("wm size 1080x3600"); Thread.sleep(1200)
            ready(CardTemplateId.MODERN_JOURNAL)
            ui.onNodeWithText(name).performScrollTo()
            shot("editor-393-complete")
            shell("wm size 840x1680"); shell("wm density 420"); Thread.sleep(1200)
            ready(CardTemplateId.MODERN_JOURNAL)
            ui.onNodeWithText(name).performScrollTo(); shot("editor-320-top")
            ui.onNodeWithTag("template-MODERN_JOURNAL").performScrollTo(); shot("editor-320-templates")
            val save = ui.onNodeWithTag("card-save").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val share = ui.onNodeWithTag("card-share").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(kotlin.math.abs(save.width-share.width) < 2)
            assertTrue(save.right < share.left)
            ui.onNodeWithTag("card-share").performClick()
            ui.waitUntil(30_000) { shell("dumpsys activity activities").contains("ChooserActivity") }
            shot("system-share")
            shell("input keyevent 4"); waitText("植物诗笺")
            assertEquals(before, runBlocking { access.database().archiveDao().history(id) })
            bytes.forEach { (uri, original) -> assertArrayEquals(original, File(URI(uri)).readBytes()) }
            assertEquals("scientific:osmanthus fragrans", runBlocking { access.cards().assemble(id, before.last().id).content.approvedCulture?.speciesKey })
        } finally {
            scenario.close()
            shell("wm size reset"); shell("wm density reset")
            runBlocking {
                owner?.let { id ->
                    access.database().archiveDao().history(id).forEach { access.archive().delete(id, it.id) }
                    access.plants().delete(id)
                }
            }
            exports.forEach { context.contentResolver.delete(it, null, null) }
        }
    }
}

package com.floralens

import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.domain.card.CardTemplateId
import com.floralens.domain.model.*
import com.floralens.domain.season.Season
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
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

class Phase4JourneyTest {
    @get:Rule val ui=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val access get()=EntryPointAccessors.fromApplication(context,Phase1TestAccess::class.java)
    private val name="Debug · 诗笺银杏"
    private val emptyName="Debug · 诗笺无照片"
    private fun shell(command:String)=instrumentation.uiAutomation.executeShellCommand(command).use { FileInputStream(it.fileDescriptor).bufferedReader().readText() }
    private fun waitText(text:String)=ui.waitUntil(25_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes(false).isNotEmpty() }
    private fun click(text:String) { waitText(text); val node=ui.onNodeWithText(text); try { node.performScrollTo() } catch(_:AssertionError) {}; node.assertIsDisplayed().performClick() }
    private fun tagClick(tag:String) { val node=ui.onNodeWithTag(tag); node.performScrollTo().performClick() }
    private fun dismissSystemShare() {
        // An expanded API35 chooser may consume the first Back by collapsing its sheet.
        // Require the real app to resume; never treat a background Compose tree as success.
        repeat(3) {
            shell("input keyevent 4"); Thread.sleep(500)
            val resumed = shell("dumpsys activity activities").lineSequence().firstOrNull {
                it.contains("mResumedActivity:") || it.contains("topResumedActivity=")
            }.orEmpty()
            if (resumed.contains("com.floralens/.MainActivity")) return
        }
        error("System share did not return to the app")
    }
    private fun screenshot(name:String) {
        ui.waitForIdle(); Thread.sleep(900); ui.waitForIdle(); ui.mainClock.advanceTimeBy(400); ui.waitForIdle()
        shell("mkdir -p /sdcard/Download/wildflower-phase4")
        shell("screencap -p /sdcard/Download/wildflower-phase4/$name.png")
    }
    private fun previewReady(type:CardTemplateId) {
        ui.waitUntil(25_000) { ui.onAllNodesWithContentDescription("${type.title}预览").fetchSemanticsNodes(false).isNotEmpty() }
        ui.onNodeWithTag("card-preview").performScrollTo()
    }
    private fun history(id:Long)=runBlocking { access.database().archiveDao().history(id) }
    private fun at(date:String)=LocalDateTime.parse(date).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun saveEvidence(filename:String): android.net.Uri {
        ui.onNodeWithTag("card-save").performClick()
        waitText("PNG 已保存到 Pictures/Wildflower Lens")
        val resolver=context.contentResolver
        val uri=resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,arrayOf(MediaStore.Images.Media._ID,MediaStore.Images.Media.DISPLAY_NAME),
            "${MediaStore.Images.Media.RELATIVE_PATH}=?",arrayOf("Pictures/Wildflower Lens/"),"${MediaStore.Images.Media._ID} DESC")!!.use {
            check(it.moveToFirst()); assertTrue(it.getString(1).startsWith("Wildflower_")); android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,it.getLong(0))
        }
        val output=File(context.getExternalFilesDir(null),"phase4-evidence/$filename.png").apply { parentFile!!.mkdirs() }
        resolver.openInputStream(uri)!!.use { input -> output.outputStream().use { input.copyTo(it) } }
        val bitmap=BitmapFactory.decodeFile(output.path)!!; assertEquals(1600,bitmap.width); assertEquals(2400,bitmap.height); bitmap.recycle()
        return uri
    }
    @Test fun personalPhotoTemplatesSaveShareAndOriginalHistory() {
        if(InstrumentationRegistry.getArguments().getString("verifyColdPhase4")=="true") { verifyCold(); return }
        runBlocking { access.settings().setOnboarded() }
        var owner:Long?=null; var emptyOwner:Long?=null; var completed=false
        val exports=mutableListOf<android.net.Uri>()
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            val id=runBlocking {
                check(access.plants().observeAll().first().none { it.nickname in setOf(name,emptyName) }) { "Owned evidence already exists; use cold verification or a fresh dedicated AVD" }
                val plant=access.archive().collect("phase4-${UUID.randomUUID()}",Species.identified("Ginkgo biloba","银杏"),name,null,null); owner=plant
                emptyOwner=access.archive().collect("phase4-${UUID.randomUUID()}",Species.manual(emptyName),emptyName,null,null)
                val bytes=context.assets.open("explore/ginkgo.jpg").use { it.readBytes() }
                val first=access.captures().create(bytes); access.archive().add(plant,first.id,at("2026-06-15T09:00:00"),"Debug fixture · 夏季观察日期，授权银杏参考摄影，非野外实录")
                val second=access.captures().create(bytes); access.archive().add(plant,second.id,at("2026-10-02T09:20:00"),"Debug fixture · 秋季观察日期，授权参考摄影，非野外实录")
                plant
            }
            val before=history(id); val originals=before.associate { it.photoUri!! to File(URI(it.photoUri)).readBytes() }
            waitText("发现自然，收藏四季。"); click("花园"); click(name); click("制作植物诗笺")
            waitText("植物诗笺"); previewReady(CardTemplateId.MODERN_JOURNAL); screenshot("card-editor")
            tagClick("card-photo-picker"); waitText("选择一次真实观察"); screenshot("card-photo-picker")
            ui.onNodeWithTag("card-photo-${before.first().id}").performClick()
            previewReady(CardTemplateId.MODERN_JOURNAL)
            assertEquals(before.first().note,runBlocking { access.cards().assemble(id,before.first().id).content.note })
            for(type in listOf(CardTemplateId.BOTANICAL_ATLAS,CardTemplateId.CLASSICAL_POETRY,CardTemplateId.MODERN_JOURNAL)) {
                tagClick("template-${type.name}"); previewReady(type)
                val stem=when(type) { CardTemplateId.BOTANICAL_ATLAS -> "botanical-atlas"; CardTemplateId.CLASSICAL_POETRY -> "classical-poetry"; else -> "modern-journal" }
                screenshot("$stem-preview")
                if(type==CardTemplateId.CLASSICAL_POETRY) {
                    assertEquals("scientific:ginkgo biloba", runBlocking { access.cards().assemble(id,before.first().id).content.approvedCulture?.speciesKey })
                    screenshot("card-no-culture")
                }
                exports+=saveEvidence("$stem-export")
                ui.onNodeWithTag("card-message").performScrollTo(); screenshot("png-save-success")
            }
            ui.onNodeWithTag("card-share").performClick()
            ui.waitUntil(25_000) { shell("dumpsys activity activities").contains("ChooserActivity") }
            screenshot("android-share-sheet")
            dismissSystemShare(); waitText("植物诗笺")
            waitText("已打开系统分享，请选择接收应用")
            ui.onNodeWithContentDescription("返回").performClick(); waitText("我的植物档案")
            ui.onNodeWithContentDescription("返回").performClick(); click(emptyName); click("制作植物诗笺")
            waitText("让下一次相遇成为作品"); screenshot("card-no-photo")
            ui.onAllNodesWithTag("card-save").assertCountEquals(0)
            click("添加观察"); waitText("拍摄新照片"); ui.onNodeWithContentDescription("返回").performClick(); waitText("植物诗笺")
            ui.onNodeWithContentDescription("返回").performClick(); ui.onNodeWithContentDescription("返回").performClick()
            click("四季手账 →"); waitText("把相遇留在四季里。")
            val journal=runBlocking { access.seasons().observe().first() }
            assertTrue(journal.observations(2026,Season.SUMMER).any { it.plant.id==id })
            assertTrue(journal.observations(2026,Season.AUTUMN).any { it.plant.id==id })
            assertEquals(before,history(id)); originals.forEach { (uri,bytes) -> assertArrayEquals(bytes,File(URI(uri)).readBytes()) }
            scenario.recreate(); waitText("四季手账")
            completed=true
        } catch(failure:Throwable) {
            runCatching { screenshot("journey-failure") }
            android.util.Log.e("Phase4Evidence", runCatching { ui.onRoot().printToString() }.getOrDefault("External activity is foreground"), failure)
            throw failure
        }
        finally {
            scenario.close()
            val keep=InstrumentationRegistry.getArguments().getString("keepPhase4Evidence")=="true"
            if(!keep || !completed) runBlocking {
                owner?.let { p -> history(p).forEach { access.archive().delete(p,it.id) }; access.plants().delete(p) }
                emptyOwner?.let { access.plants().delete(it) }
                exports.forEach { context.contentResolver.delete(it,null,null) }
            }
        }
    }
    private fun verifyCold() {
        val plant=runBlocking { access.plants().observeAll().first().single { it.nickname==name } }
        val rows=history(plant.id); assertEquals(2,rows.size)
        rows.forEach { requireNotNull(BitmapFactory.decodeFile(File(URI(it.photoUri)).path)).recycle(); assertTrue(it.note!!.startsWith("Debug fixture")) }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("发现自然，收藏四季。"); click("花园"); click(name); click("制作植物诗笺"); previewReady(CardTemplateId.MODERN_JOURNAL); screenshot("cold-start-card")
            tagClick("template-CLASSICAL_POETRY"); previewReady(CardTemplateId.CLASSICAL_POETRY); screenshot("cold-classical")
            val variant=InstrumentationRegistry.getArguments().getString("visualPhase4")
            if(variant != null) {
                for(type in CardTemplateId.entries) {
                    tagClick("template-${type.name}"); previewReady(type); screenshot("$variant-${type.name.lowercase()}")
                    ui.onNodeWithTag("card-save").assertIsDisplayed().assertIsEnabled()
                    ui.onNodeWithTag("card-share").assertIsDisplayed().assertIsEnabled()
                }
                tagClick("card-photo-picker"); waitText("选择一次真实观察"); screenshot("$variant-photo-picker")
            }
        }
        android.util.Log.i("Phase4Evidence","COLD PASS plant=${plant.id} photos=2 PNG renderer restored")
    }
}

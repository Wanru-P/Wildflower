package com.floralens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.domain.model.Observation
import com.floralens.domain.model.ObservationType
import com.floralens.domain.model.Species
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

/** Dedicated empty AVD only; real Room/PhotoStore archives, never production seed content.
 * Licensed reference photos and test dates explicitly do NOT document one plant's field history. */
class PersonalDetailKitJourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val access get() = EntryPointAccessors.fromApplication(context, Phase1TestAccess::class.java)
    private val round get() = InstrumentationRegistry.getArguments().getString("personalDetailRound")
        ?.takeIf { it in setOf("before", "round1", "round2", "final") } ?: "test"
    private val name = "桂花 · 参考影集"
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).bufferedReader().readText()
    }
    private fun waitText(text: String) = ui.waitUntil(25_000) {
        runCatching { ui.onAllNodesWithText(text).fetchSemanticsNodes(false).isNotEmpty() }.getOrDefault(false)
    }
    private fun click(text: String) {
        waitText(text)
        ui.onNodeWithText(text).apply { runCatching { performScrollTo() } }.assertIsDisplayed().performClick()
    }
    private fun back() { ui.onNodeWithContentDescription("返回").performClick(); ui.waitForIdle() }
    private fun screen(name: String) {
        ui.waitForIdle(); Thread.sleep(1000); ui.waitForIdle()
        val folder = "/sdcard/Download/wildflower-personal-detail/$round"
        shell("mkdir -p $folder"); shell("screencap -p $folder/$name.png")
    }
    private fun viewport(narrow: Boolean) {
        shell(if(narrow) "wm size 840x1680" else "wm size 1080x2400")
        shell(if(narrow) "wm density 420" else "wm density 440")
        ui.waitUntil(20_000) { instrumentation.uiAutomation.takeScreenshot()?.let {
            val match = it.width == if(narrow) 840 else 1080; it.recycle(); match
        } ?: false }
        waitText("我的植物档案")
    }
    private fun timeline() {
        ui.onNodeWithText("我的观察").performScrollTo()
        val header = ui.onNodeWithText("我的观察").fetchSemanticsNode().boundsInRoot.top
        val list = ui.onNodeWithTag("personal-journal").fetchSemanticsNode().boundsInRoot.top
        ui.onNodeWithTag("personal-journal").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, (header - list - 40f).coerceAtLeast(0f)) }
    }
    private fun open(id: Long) {
        ui.onNodeWithTag("garden-grid").performScrollToNode(hasTestTag("garden-plant-$id"))
        ui.onNodeWithTag("garden-plant-$id").performClick(); waitText("我的植物档案")
    }
    private fun history(id: Long) = runBlocking { access.database().archiveDao().history(id) }
    private fun at(date: String) = LocalDateTime.parse(date).atZone(ZoneId.systemDefault()).toInstant()

    @Test fun realArchive_coverGalleryCreationTimelineAndResponsiveStates() {
        check(BuildConfig.DEBUG)
        runBlocking {
            check(access.plants().observeAll().first().isEmpty()) { "Use an empty dedicated AVD; never clear user records" }
            access.settings().setOnboarded()
        }
        val owned = mutableListOf<Long>()
        val main = runBlocking {
            val id = access.archive().collect("pd-kit-${UUID.randomUUID()}", Species.identified("Osmanthus fragrans", "桂花"), name, null, null)
            owned += id
            listOf("osmanthus_starr.jpg", "osmanthus.jpg", "osmanthus_kenraiz.jpg", "osmanthus_starr.jpg").forEachIndexed { i, asset ->
                val capture = access.captures().create(context.assets.open("explore/$asset").use { it.readBytes() })
                access.archive().add(id, capture.id, at(listOf("2024-03-12T09:20:00", "2024-06-20T09:20:00", "2024-10-02T09:20:00", "2024-11-12T09:20:00")[i]).toEpochMilli(),
                    "测试档案 · 授权参考摄影，非用户野外实拍。日期仅用于布局验收，不代表同株生长史。")
            }
            access.observations().add(Observation(plantId = id, type = ObservationType.NOTE, timestamp = at("2024-09-05T10:20:00"),
                note = "测试档案 · 无照片观察笔记。"))
            access.observations().add(Observation(plantId = id, type = ObservationType.WATERED, timestamp = at("2024-09-08T10:20:00"), note = "测试档案 · 浇水事件。"))
            access.archive().cover(id, history(id).first { it.timestampEpochMs == at("2024-10-02T09:20:00").toEpochMilli() }.id)
            id
        }
        val before = history(main)
        val originals = before.mapNotNull { it.photoUri }.associateWith { File(URI(it)).readBytes() }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitText("发现自然，收藏四季。"); click("花园"); open(main)
            viewport(false); screen("personal-detail-393")
            ui.onNodeWithText("4 张归档照片").assertExists()
            viewport(true); screen("personal-detail-320")
            timeline(); screen("personal-timeline-320")
            viewport(false); timeline(); screen("personal-timeline-393")
            ui.onNodeWithText("测试档案 · 无照片观察笔记。").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("测试档案 · 浇水事件。").performScrollTo().assertIsDisplayed()
            if(round != "before") {
                ui.onNodeWithTag("personal-slices").performScrollTo()
                ui.onNodeWithTag("personal-photo-strip").performScrollToNode(hasTestTag("personal-slice-${before.last { it.photoUri != null }.id}"))
                ui.onNodeWithTag("personal-slice-${before.last { it.photoUri != null }.id}").performClick()
                screen("personal-photo-preview-393")
                click("关闭照片")
            }
            click("查看全部照片"); waitText("观察照片集")
            val newCover = before.first { it.photoUri != null }
            ui.onNodeWithContentDescription("查看观察 ${newCover.id}").performClick()
            click("设为植物封面"); waitText("封面已更新，历史照片保持不变")
            click("关闭照片"); back(); waitText("我的植物档案")
            assertEquals(newCover.id, runBlocking { access.database().archiveDao().plant(main)!!.coverObservationId })
            if(round != "before") ui.onNodeWithTag("personal-slice-cover-${newCover.id}", useUnmergedTree = true).assertExists()
            if(round == "final") {
                ui.onNodeWithTag("personal-cover").performScrollTo(); screen("personal-cover-updated-393")
            }
            click("添加观察"); waitText("拍摄新照片"); ui.onNodeWithText("从相册选择照片").assertExists(); back()
            val entry = ui.onNodeWithTag("botanical-creation-entry").performScrollTo().assertHasClickAction()
            if(round == "final") {
                screen("personal-creation-idle")
                entry.performTouchInput { down(center) }; ui.mainClock.advanceTimeBy(240)
                screen("personal-creation-pressed")
                entry.performTouchInput { up() }
            } else entry.performClick()
            waitText("植物诗笺"); back(); waitText("我的植物档案")
            click("认识这个物种  →"); waitText("植物笔记"); back()
            // Recreate the actual Activity/ViewModel; the process-kill chain is also covered by Phase2JourneyTest.
            scenario.recreate(); waitText("我的植物档案")
            assertEquals(before, history(main)); originals.forEach { (uri, bytes) -> assertArrayEquals(bytes, File(URI(uri)).readBytes()) }
            back(); waitText("My Garden")
            assertEquals(newCover.photoUri, runBlocking { access.database().archiveDao().plant(main)!!.photoUri })
            val blank = runBlocking { access.archive().collect("pd-empty-${UUID.randomUUID()}", Species.identified("Osmanthus fragrans", "桂花"), "未拍照的测试档案", null, null).also { owned += it } }
            open(blank); viewport(false); screen("personal-empty-393"); viewport(true); screen("personal-empty-320")
            if(round != "before") ui.onNodeWithText("还没有观察照片").assertExists()
            back(); waitText("My Garden")
            val one = runBlocking {
                val id = access.archive().collect("pd-one-${UUID.randomUUID()}", Species.identified("Osmanthus fragrans", "桂花"), "桂花 · 花香与叶影的参考摄影册", null, null)
                owned += id
                val capture = access.captures().create(context.assets.open("explore/osmanthus_kenraiz.jpg").use { it.readBytes() })
                access.archive().add(id, capture.id, at("2024-10-02T09:20:00").toEpochMilli(), "测试档案 · 授权参考摄影，非用户野外实拍。" + "长备注布局测试。".repeat(20)); id
            }
            open(one); viewport(false); screen("personal-one-long-393"); viewport(true); screen("personal-one-long-320")
            shell("settings put system font_scale 1.3"); Thread.sleep(1000); waitText("我的植物档案")
            click("添加观察"); waitText("拍摄新照片"); back(); waitText("我的植物档案")
            screen("personal-large-font-320"); shell("settings put system font_scale 1.0")
            back(); waitText("My Garden")
            val missing = runBlocking {
                val id = access.archive().collect("pd-missing-${UUID.randomUUID()}", Species.identified("", "未命名植物"), "缺图的测试档案", null, null)
                owned += id
                access.observations().add(Observation(plantId = id, type = ObservationType.PHOTO_OBSERVATION, photoUri = "file:///missing-pd-kit-${UUID.randomUUID()}.jpg", timestamp = at("2024-10-02T09:20:00")))
                id
            }
            open(missing); screen("personal-missing-320")
            ui.onAllNodesWithTag("photo-unavailable", useUnmergedTree = true).onFirst().assertIsDisplayed()
        } catch(failure: Throwable) {
            runCatching { screen("failure") }
            android.util.Log.e("PersonalDetailKitEvidence", runCatching { ui.onRoot().printToString() }.getOrDefault("No root"), failure)
            throw failure
        } finally {
            scenario.close(); shell("settings put system font_scale 1.0"); shell("wm size reset"); shell("wm density reset")
            runBlocking { owned.forEach { id -> history(id).forEach { access.archive().delete(id, it.id) }; access.plants().delete(id) } }
        }
    }
}

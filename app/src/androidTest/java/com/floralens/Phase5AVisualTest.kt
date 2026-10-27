package com.floralens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
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

/** Dedicated AVD only. Licensed reference photos in REAL Room records. Normal tests label Debug;
 * presentation mode labels reference archives, never claims user field photography.
 * No app seeder, no mock UI, no mutation of pre-existing archives. Cleanup owns exact IDs only.
 */
class Phase5AVisualTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val access get() = EntryPointAccessors.fromApplication(context, Phase1TestAccess::class.java)
    private val presentation = InstrumentationRegistry.getArguments().getString("phase5aPresentation") == "true"
    private val mainName get() = if (presentation) "桂花 · 参考影集" else "Debug · 窗边桂花"
    private val longName get() = if (presentation) "桂花 · 花香与叶影的参考摄影册" else "Debug · 山间一株具有很长中文名称的植物观察记录"
    private val evidenceFolder get() = InstrumentationRegistry.getArguments().getString("phase5FinalRound")?.let { "wildflower-phase5-final-$it/phase5a" } ?: if (presentation) "wildflower-phase5a1-presentation" else "wildflower-phase5a1-test"
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).bufferedReader().readText()
    }
    private fun waitText(text: String) = ui.waitUntil(25_000) {
        // A density/font-scale change can briefly remove the Compose hierarchy during recreation.
        runCatching { ui.onAllNodesWithText(text).fetchSemanticsNodes(false).isNotEmpty() }.getOrDefault(false)
    }
    private fun click(text: String) {
        waitText(text)
        val node = ui.onNodeWithText(text)
        try { node.performScrollTo() } catch (_: AssertionError) {}
        node.assertIsDisplayed().performClick()
    }
    private fun screenshot(name: String) {
        ui.waitForIdle(); Thread.sleep(1400); ui.waitForIdle()
        shell("mkdir -p /sdcard/Download/$evidenceFolder")
        shell("screencap -p /sdcard/Download/$evidenceFolder/$name.png")
    }
    private fun at(date: String) = LocalDateTime.parse(date).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun history(id: Long) = runBlocking { access.database().archiveDao().history(id) }

    @Test fun realArchiveEditorialLayoutsAndPreservedActions() {
        check(BuildConfig.DEBUG)
        runBlocking {
            check(access.plants().observeAll().first().isEmpty()) { "Use an empty dedicated AVD; do not clear user records" }
            access.settings().setOnboarded()
        }
        val owned = mutableListOf<Long>()
        var completed = false
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            shell("wm size 1080x2400"); shell("wm density 440")
            waitText("发现自然，收藏四季。")
            screenshot("explore")
            ui.onNodeWithTag("explore-list").performScrollToNode(hasText("银杏"))
            screenshot("explore-featured")
            click("花园"); waitText("你的植物故事，\n从第一次发现开始。")
            ui.onNodeWithTag("garden-season-filter").assertDoesNotExist()
            screenshot("garden-empty")
            shell("wm size 840x1680"); shell("wm density 420")
            waitText("My Garden"); ui.onNodeWithText("去识别").performScrollTo().assertIsDisplayed(); screenshot("garden-empty-320")
            click("发现"); ui.onNodeWithTag("explore-list").performScrollToIndex(0)
            waitText("发现自然，收藏四季。"); screenshot("explore-320")
            shell("wm size 1080x2400"); shell("wm density 440")
            val main = runBlocking {
                val specs = listOf(
                    Triple("Osmanthus fragrans", "桂花", "osmanthus_kenraiz.jpg"),
                    Triple("Ginkgo biloba", "银杏", "ginkgo.jpg"),
                    Triple("Camellia japonica", "山茶", "camellia_pumpkinsky.jpg"))
                for (i in 0..8) {
                    val (scientific, common, photo) = specs[i % 3]
                    val nickname = when (i) { 6 -> longName; 8 -> mainName; else -> if (presentation) listOf("桂花", "银杏叶", "山茶", "桂花枝", "银杏", "山茶花", "桂花", "银杏枝")[i] else "Debug · $common${i + 1}" }
                    val id = access.archive().collect("phase5a-${UUID.randomUUID()}", Species.identified(if(i == 8) "Osmanthus fragrans" else scientific, if(i == 8) "桂花" else common), nickname, null, null)
                    owned += id
                    val files = if (i == 8) listOf("osmanthus_starr.jpg", "osmanthus.jpg", "osmanthus_kenraiz.jpg") else listOf(photo)
                    files.forEachIndexed { index, filename ->
                        val capture = access.captures().create(context.assets.open("explore/$filename").use { it.readBytes() })
                        access.archive().add(id, capture.id, at("2026-${listOf("03-12", "06-20", "10-02")[index]}T09:20:00"),
                            if (presentation) "展示档案 · 授权参考摄影，非用户野外实拍。日期仅用于展示归档布局；不同作者照片不表示同株生长过程。"
                            else "Debug · 授权参考摄影测试记录，非野外实录。" + if (i == 8) listOf("春日初见，叶间藏着细小花朵。", "夏日再访，留住常绿叶的纹理。", "秋日相遇，记录这一季的花香。")[index] else "")
                    }
                    if (i == 8) access.archive().cover(id, access.database().archiveDao().history(id).first().id)
                }
                owned.last()
            }
            val before = history(main)
            assertEquals(3, before.size)
            val bytes = before.associate { it.photoUri!! to File(URI(it.photoUri)).readBytes() }
            click("花园"); waitText(mainName)
            ui.onNodeWithTag("garden-season-filter").assertIsDisplayed()
            click("冬季"); waitText("这一季，尚无植物观察")
            ui.onNodeWithTag("garden-season-filter").assertIsDisplayed()
            click("全部"); waitText(mainName)
            ui.onNodeWithTag("garden-grid").performScrollToIndex(0)
            screenshot("garden-populated")
            if (InstrumentationRegistry.getArguments().getString("phase5FinalRound") != null) {
                shell("settings put system font_scale 1.5")
                Thread.sleep(1500) // Wait for the configuration change, not the preceding Activity's text.
                waitText("My Garden"); ui.onNodeWithTag("garden-grid").performScrollToIndex(0); screenshot("garden-font-150")
                shell("settings put system font_scale 1.0"); Thread.sleep(1500); waitText("My Garden")
            }
            ui.onNodeWithTag("garden-grid").performScrollToNode(hasText(longName))
            ui.onNodeWithText(longName).assertIsDisplayed()
            screenshot("garden-long-name")
            click(longName); waitText("我的植物档案")
            ui.onNodeWithText(longName).assertIsDisplayed(); screenshot("personal-long-name")
            ui.onNodeWithContentDescription("返回").performClick()
            shell("wm size 840x1680"); shell("wm density 420")
            waitText("My Garden"); screenshot("garden-populated-320")
            ui.onNodeWithTag("garden-grid").performScrollToNode(hasText(longName))
            ui.onNodeWithText(longName).assertIsDisplayed(); screenshot("garden-long-name-320")
            click(longName); waitText("我的植物档案")
            ui.onNodeWithText(longName).assertIsDisplayed(); screenshot("personal-long-name-320")
            ui.onNodeWithContentDescription("返回").performClick()
            ui.onNodeWithTag("garden-grid").performScrollToNode(hasText(mainName)); click(mainName)
            waitText("我的植物档案"); screenshot("personal-detail-320")
            shell("wm size 1080x2400"); shell("wm density 440")
            waitText("我的植物档案"); screenshot("personal-detail")
            ui.onNodeWithText("我的观察").performScrollTo()
            ui.onNodeWithTag("personal-journal").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 700f) }
            screenshot("personal-timeline")
            ui.onNodeWithTag("personal-journal").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 650f) }
            screenshot("personal-timeline-continued")
            click("查看全部照片"); waitText("观察照片集"); screenshot("photo-gallery")
            ui.onNodeWithContentDescription("返回").performClick(); waitText("我的植物档案")
            click("添加观察"); waitText("拍摄新照片")
            ui.onNodeWithContentDescription("返回").performClick(); waitText("我的植物档案")
            ui.onNodeWithTag("botanical-creation-entry").performScrollTo().assertHasClickAction()
            ui.onNodeWithTag("botanical-creation-entry").performTouchInput { down(center) }
            // The surrounding scrollable delays press indication. Advance Compose's test clock
            // explicitly; wall-clock sleeping alone does not advance its delayed interaction.
            ui.mainClock.advanceTimeBy(240)
            screenshot("creation-entry-pressed")
            val pressedBitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            var pressedSamples = 0
            for (y in 0 until pressedBitmap.height step 8) for (x in 0 until pressedBitmap.width step 8) {
                if (pressedBitmap.getPixel(x, y) == 0xFFDCE6D6.toInt()) pressedSamples++
            }
            pressedBitmap.recycle()
            assertTrue("Pressed creation card must visibly darken, not just expose a click action", pressedSamples > 1000)
            ui.onNodeWithTag("botanical-creation-entry").performTouchInput { up() }
            waitText("植物诗笺")
            ui.onNodeWithContentDescription("返回").performClick(); waitText("我的植物档案")
            assertEquals(before, history(main))
            bytes.forEach { (uri, original) -> assertArrayEquals(original, File(URI(uri)).readBytes()) }
            completed = true
        } catch (failure: Throwable) {
            screenshot("visual-test-failure")
            android.util.Log.e("Phase5AEvidence", ui.onRoot().printToString(), failure)
            throw failure
        } finally {
            scenario.close()
            shell("settings put system font_scale 1.0")
            shell("wm size reset"); shell("wm density reset")
            if (!completed || InstrumentationRegistry.getArguments().getString("keepPhase5aEvidence") != "true") runBlocking {
                owned.forEach { id -> history(id).forEach { access.archive().delete(id, it.id) }; access.plants().delete(id) }
            }
        }
    }
}

package com.floralens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.domain.model.Observation
import com.floralens.domain.model.ObservationType
import com.floralens.domain.model.Species
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

/** Dedicated AVD only. Reference photography is saved by real archive/PhotoStore APIs.
 * Dates/names are explicit test records, not field observations or production seed data. */
class MyGardenKitJourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val access get() = EntryPointAccessors.fromApplication(context, Phase1TestAccess::class.java)
    private val round get() = InstrumentationRegistry.getArguments().getString("gardenKitRound")
        ?.takeIf { it in setOf("before", "round1", "round2", "final") } ?: "test"
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).bufferedReader().readText()
    }
    private fun waitText(text: String) = ui.waitUntil(20_000) {
        runCatching { ui.onAllNodesWithText(text).fetchSemanticsNodes(false).isNotEmpty() }.getOrDefault(false)
    }
    private fun waitGrid() = ui.waitUntil(20_000) {
        runCatching { ui.onAllNodesWithTag("garden-grid").fetchSemanticsNodes(false).isNotEmpty() }.getOrDefault(false)
    }
    private fun click(text: String) {
        waitText(text)
        val node = ui.onNodeWithText(text)
        try { node.performScrollTo() } catch (_: AssertionError) { }
        node.assertIsDisplayed().performClick()
    }
    private fun back() { ui.onNodeWithContentDescription("返回").performClick(); ui.waitForIdle() }
    private fun screen(name: String) {
        ui.waitForIdle(); Thread.sleep(1000); ui.waitForIdle()
        val folder = "/sdcard/Download/wildflower-my-garden/$round"
        shell("mkdir -p $folder")
        shell("screencap -p $folder/$name.png")
    }
    private fun viewport(narrow: Boolean) {
        shell(if (narrow) "wm size 840x1680" else "wm size 1080x2400")
        shell(if (narrow) "wm density 420" else "wm density 440")
        ui.waitUntil(20_000) {
            instrumentation.uiAutomation.takeScreenshot()?.let {
                val matches = it.width == if (narrow) 840 else 1080
                it.recycle(); matches
            } ?: false
        }
    }
    private fun at(date: String) = LocalDateTime.parse(date).atZone(ZoneId.systemDefault()).toInstant()
    private fun checkLatinRow(left: Long, right: Long) {
        val a = ui.onNodeWithTag("garden-latin-$left", useUnmergedTree = true)
        val b = ui.onNodeWithTag("garden-latin-$right", useUnmergedTree = true)
        assertEquals("Long nicknames must not stagger the Latin-name baseline", a.fetchSemanticsNode().boundsInRoot.top,
            b.fetchSemanticsNode().boundsInRoot.top, 1f)
        for (node in listOf(a, b)) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
        }
    }

    @Test fun realNinePlantWall_filtersMissingPhotosRoutesAndScrollRetention() {
        check(BuildConfig.DEBUG)
        runBlocking {
            check(access.plants().observeAll().first().isEmpty()) { "Use an empty dedicated AVD; never clear user records" }
            access.settings().setOnboarded()
        }
        val owned = mutableMapOf<Int, Long>()
        val names = listOf("桂花 · 参考影集", "银杏枝", "桂花 · 花香与叶影的参考摄影册", "山茶花",
            "缺图的参考档案", "长学名的参考档案", "桂花枝", "银杏叶", "未拍照的参考档案")
        val months = listOf("10-02", "03-12", "09-21", "06-20", "01-28", "04-18", "11-03", "12-02")
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            viewport(false); waitText("发现自然，收藏四季。")
            click("花园"); waitText("你的植物故事，\n从第一次发现开始。")
            ui.onNodeWithTag("garden-season-filter").assertDoesNotExist()
            screen("garden-empty-393")
            viewport(true); waitText("My Garden"); screen("garden-empty-320")
            viewport(false)
            runBlocking {
                for (i in 8 downTo 0) {
                    val scientific = when (i) {
                        1, 7 -> "Ginkgo biloba"
                        3 -> "Camellia japonica"
                        5 -> "Osmanthus fragrans var. aurantiacus"
                        else -> "Osmanthus fragrans"
                    }
                    val id = access.archive().collect("garden-kit-${UUID.randomUUID()}",
                        Species.identified(scientific, if (i == 3) "山茶花" else if (i == 1 || i == 7) "银杏" else "桂花"),
                        names[i], null, null)
                    owned[i] = id
                    if (i == 8) continue // Actual collection, no invented observation date.
                    val note = "测试档案 · 授权参考摄影，非用户野外实拍。日期仅归档示例，不代表同株生长史。"
                    if (i == 4) {
                        access.observations().add(Observation(plantId = id, type = ObservationType.PHOTO_OBSERVATION,
                            photoUri = "file:///missing-garden-kit-${UUID.randomUUID()}.jpg", note = note,
                            timestamp = at("2024-${months[i]}T09:20:00")))
                    } else {
                        val asset = if (i == 1 || i == 7) "ginkgo.jpg" else if (i == 3) "camellia_pumpkinsky.jpg" else "osmanthus_kenraiz.jpg"
                        val capture = access.captures().create(context.assets.open("explore/$asset").use { it.readBytes() })
                        access.archive().add(id, capture.id, at("2024-${months[i]}T09:20:00").toEpochMilli(), note)
                    }
                }
            }
            waitText(names[0]); ui.onNodeWithText("9 株收藏", substring = true).assertExists()
            ui.onNodeWithTag("garden-grid").performScrollToIndex(0)
            screen("garden-populated-393")
            checkLatinRow(owned.getValue(0), owned.getValue(1))
            // Every season uses the unchanged real repository projection, not plant bloom periods.
            for (season in Season.entries) {
                click("${season.label}季")
                val expected = runBlocking { access.seasons().observe().first { it.loaded }.garden(season) }
                assertTrue(expected.isNotEmpty())
                expected.forEach { ui.onNodeWithTag("garden-grid").performScrollToNode(hasTestTag("garden-plant-${it.id}")); ui.onNodeWithTag("garden-plant-${it.id}").assertIsDisplayed() }
                ui.onNodeWithTag("garden-plant-${owned.getValue(8)}").assertDoesNotExist()
            }
            click("全部"); waitText(names[0])
            ui.onNodeWithTag("garden-grid").performScrollToNode(hasText("去发现新的植物"))
            ui.onNodeWithText("去发现新的植物").assertIsDisplayed()
            screen("garden-bottom-393")
            val scrollBefore = ui.onNodeWithTag("garden-grid").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            click("发现"); waitText("发现自然，收藏四季。"); click("花园"); waitGrid()
            ui.onNodeWithText("去发现新的植物").assertIsDisplayed()
            val scrollAfter = ui.onNodeWithTag("garden-grid").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            assertEquals("Returning from Explore preserves real grid position", scrollBefore, scrollAfter, 1f)
            for (index in listOf(0, 2, 4, 8)) {
                ui.onNodeWithTag("garden-grid").performScrollToNode(hasTestTag("garden-plant-${owned.getValue(index)}"))
                ui.onNodeWithTag("garden-plant-${owned.getValue(index)}").performClick()
                waitText("我的植物档案"); ui.onNodeWithText(names[index]).assertIsDisplayed(); back()
            }
            ui.onNodeWithTag("garden-grid").performScrollToIndex(0)
            click("四季手账 →"); waitText("把相遇留在四季里。"); back()
            click("观察地图"); waitText("观察地图"); back()
            click("养护提醒"); waitText("养护提醒"); back()
            click("设置"); waitText("设置与帮助"); back()
            viewport(true); waitText("My Garden"); ui.onNodeWithTag("garden-grid").performScrollToIndex(0)
            ui.onNodeWithTag("garden-season-filter").assertIsDisplayed()
            screen("garden-populated-320")
            ui.onNodeWithTag("garden-grid").performScrollToNode(hasText(names[4]))
            ui.onAllNodesWithTag("photo-unavailable", useUnmergedTree = true).onFirst().assertIsDisplayed()
            screen("garden-missing-long-320")
            checkLatinRow(owned.getValue(4), owned.getValue(5))
            ui.onNodeWithTag("garden-grid").performScrollToNode(hasText("去发现新的植物"))
            ui.onNodeWithText("去发现新的植物").assertIsDisplayed(); screen("garden-bottom-320")
            shell("settings put system font_scale 1.3")
            // Font changes recreate the activity asynchronously. Verify the real
            // enlarged configuration before interacting with the replacement tree.
            ui.waitUntil(20_000) {
                var applied = false
                scenario.onActivity { applied = kotlin.math.abs(it.resources.configuration.fontScale - 1.3f) < .01f }
                applied
            }
            scenario.recreate(); waitGrid()
            ui.onNodeWithTag("garden-grid").performScrollToIndex(0)
            waitText("My Garden")
            ui.onNodeWithText("养护提醒").assertIsDisplayed()
            ui.onNodeWithText("设置").assertIsDisplayed()
            ui.onNodeWithText("四季手账 →").assertIsDisplayed()
            ui.onNodeWithTag("garden-season-filter").assertIsDisplayed()
            screen("garden-large-font-320")
            shell("settings put system font_scale 1.0")
            viewport(false)
            val observations = runBlocking { owned.values.associateWith { access.database().archiveDao().history(it) } }
            val bytes = observations.values.flatten().mapNotNull { it.photoUri }.filter { File(URI(it)).exists() }
                .associateWith { File(URI(it)).readBytes() }
            scenario.recreate(); waitGrid()
            ui.onNodeWithTag("garden-grid").performScrollToNode(hasTestTag("garden-plant-${owned.getValue(0)}"))
            assertEquals(9, runBlocking { access.database().plantDao().count() })
            observations.forEach { (id, history) -> assertEquals(history, runBlocking { access.database().archiveDao().history(id) }) }
            bytes.forEach { (uri, original) -> assertArrayEquals(original, File(URI(uri)).readBytes()) }
        } catch (failure: Throwable) {
            runCatching { screen("failure") }
            android.util.Log.e("GardenKitEvidence", runCatching { ui.onRoot().printToString() }.getOrDefault("No Compose root"), failure)
            throw failure
        } finally {
            scenario.close()
            shell("settings put system font_scale 1.0"); shell("wm size reset"); shell("wm density reset")
            runBlocking { owned.values.forEach { id ->
                access.database().archiveDao().history(id).forEach { access.archive().delete(id, it.id) }
                access.plants().delete(id)
            } }
        }
    }
}

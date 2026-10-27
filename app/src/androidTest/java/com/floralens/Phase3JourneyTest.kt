package com.floralens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.domain.model.*
import com.floralens.domain.season.Season
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.URI
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** Dedicated AVD only. Real Room and private files; dates/photos explicitly Debug reference fixtures. */
class Phase3JourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val access get() = EntryPointAccessors.fromApplication(context, Phase1TestAccess::class.java)
    private val name = "Debug · 四季银杏"
    private fun waitText(text: String) = ui.waitUntil(20_000) {
        // An external Activity transition may temporarily leave no Compose roots.
        try { ui.onAllNodesWithText(text).fetchSemanticsNodes(false).isNotEmpty() }
        catch (_: IllegalStateException) { false }
    }
    private fun click(text: String) { waitText(text); val node = ui.onNodeWithText(text); try { node.performScrollTo() } catch (_: AssertionError) {}; node.assertIsDisplayed().performClick() }
    private fun clickInList(tag: String, text: String) { ui.onNodeWithTag(tag).performScrollToNode(hasText(text)); click(text) }
    private fun back() { Espresso.pressBack(); ui.waitForIdle() }
    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use { java.io.FileInputStream(it.fileDescriptor).bufferedReader().readText() }
    private fun screenshot(name: String) {
        ui.waitForIdle()
        // Image IO runs outside Compose's test clock. Wait, then allow its completed state to draw.
        Thread.sleep(1000); ui.waitForIdle(); ui.mainClock.advanceTimeBy(400); ui.waitForIdle()
        shell("mkdir -p /sdcard/Download/wildflower-phase3")
        shell("screencap -p /sdcard/Download/wildflower-phase3/$name.png")
    }
    private fun at(date: String) = LocalDateTime.parse(date).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun history(id: Long) = runBlocking { access.database().archiveDao().history(id) }
    private fun journal() = runBlocking { access.seasons().observe().first() }

    @Test fun culture_seasonalGarden_editDate_andPersist() {
        if (InstrumentationRegistry.getArguments().getString("verifyColdPhase3") == "true") { verifyCold(); return }
        runBlocking { check(access.plants().observeAll().first().isEmpty()) { "Empty dedicated test AVD required; never clear user records" }; access.settings().setOnboarded() }
        var owner: Long? = null
        var noPhotoOwner: Long? = null
        var completed = false
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitText("发现自然，收藏四季。")
            ui.onNodeWithTag("explore-hero").performClick()
            click("阅读植物文化 →"); waitText("植物文化"); screenshot("culture-detail")
            // Project-owner publishing approval preserves the original verification classes.
            ui.onNodeWithTag("culture-list").performScrollToNode(hasTestTag("culture-excerpt"))
            ui.onNodeWithTag("culture-excerpt").assertTextEquals("暗淡轻黄体性柔")
            ui.scrollCultureTo("nature-text")
            click("阅读自然文字 →"); waitText("自然文字"); screenshot("culture-natural-reading")
            click("返回植物文化")
            waitText("植物文化"); back(); back(); waitText("发现自然，收藏四季。")
            if (BuildConfig.INTERNAL_TOOLS) {
            clickInList("explore-list", "资料审阅 · Debug"); waitText("50 份文化阅读笔记")
            click("未核草稿"); clickInList("culture-review-list", "春兰"); waitText("文化资料 · 编辑预览")
            screenshot("culture-editorial-preview"); ui.onAllNodesWithTag("culture-excerpt").assertCountEquals(0)
            back(); clickInList("culture-review-list", "物种待核"); clickInList("culture-review-list", "月季"); waitText("文化资料 · 编辑预览")
            ui.onNodeWithText("资料审阅 · 非种级认定\n文化名称与现代物种对应待核实").performScrollTo().assertIsDisplayed(); screenshot("culture-taxon-pending")
            back(); back(); ui.onNodeWithTag("explore-list").assertExists()
            } else {
                ui.onNodeWithTag("explore-list").performScrollToNode(hasText(com.floralens.ui.components.EDITORIAL_ARTWORK_CREDIT, substring = true))
                ui.onAllNodesWithText("资料审阅 · Debug").assertCountEquals(0)
            }

            val id = runBlocking {
                val id = access.archive().collect("phase3-${UUID.randomUUID()}", Species.identified("Ginkgo biloba", "银杏"), name, null, null)
                owner = id
                noPhotoOwner = access.archive().collect("phase3-${UUID.randomUUID()}", Species.manual("Debug · 无观察档案"), "Debug · 无观察档案", null, null)
                val bytes = context.assets.open("explore/ginkgo.jpg").use { it.readBytes() }
                val spring = access.captures().create(bytes)
                access.archive().add(id,spring.id,at("2026-04-15T09:00:00"),"Debug fixture · 春季日期，授权银杏参考摄影，非野外实录")
                val autumn = access.captures().create(bytes)
                access.archive().add(id,autumn.id,at("2026-10-02T09:20:00"),"Debug fixture · 秋季日期，独立私有照片，非野外实录")
                id
            }
            val first = history(id).single { it.note!!.contains("春季") }
            val second = history(id).single { it.note!!.contains("秋季") }
            val originalUris = history(id).map { it.photoUri }.toSet()
            click("花园"); click("春季"); waitText(name); screenshot("garden-season-filter")
            assertEquals(first.photoUri, journal().garden(Season.SPRING).single().photoUri)
            click("秋季"); waitText(name); screenshot("garden-multi-season")
            assertEquals(second.photoUri, journal().garden(Season.AUTUMN).single().photoUri)
            click("夏季"); waitText("这一季，尚无植物观察"); screenshot("garden-empty-season")
            click("冬季"); waitText("这一季，尚无植物观察")
            click("全部"); waitText("Debug · 无观察档案")
            // A third, temporary owned observation verifies actual multi-year UI navigation.
            val winter = runBlocking {
                val capture = access.captures().create(context.assets.open("explore/ginkgo.jpg").use { it.readBytes() })
                access.archive().add(id, capture.id, at("2027-01-15T09:00:00"), "Debug fixture · 跨年冬季，验后删除本测试记录")
                history(id).single { it.note!!.contains("跨年冬季") }
            }
            click("四季手账 →"); waitText("把相遇留在四季里。")
            ui.onNodeWithTag("year-2027").assertIsSelected()
            ui.onNodeWithTag("seasons-list").performScrollToNode(hasTestTag("season-WINTER"))
            ui.onNodeWithTag("season-WINTER").performClick(); waitText("2027 · 冬季"); screenshot("seasons-cross-year-winter")
            // The year control is now a real dropdown; retain the multi-year archive assertion.
            ui.onNodeWithTag("seasons-list").performScrollToNode(hasTestTag("year-2027"))
            ui.onNodeWithTag("year-2027").performClick()
            ui.onNodeWithTag("year-option-2026").performClick(); waitText("2026 · 冬季"); waitText("这一季，尚无观察")
            runBlocking { access.archive().delete(id, winter.id) }
            click("返回四季总览"); screenshot("seasons-overview")
            ui.onNodeWithTag("seasons-list").performScrollToNode(hasTestTag("season-SPRING"))
            ui.onNodeWithTag("season-SPRING").performClick(); waitText("2026 · 春季"); screenshot("seasons-spring")
            ui.onNodeWithTag("season-observation-${first.id}").performScrollTo().performClick(); waitText("我的植物档案")
            ui.onNodeWithText("来自四季的本次观察").performScrollTo().assertIsDisplayed(); screenshot("season-observation-plant")
            click("查看全部照片"); waitText("2 张照片 · 按观察时间排序")
            ui.onNodeWithContentDescription("查看观察 ${first.id}").performClick(); click("编辑日期与备注")
            ui.onNodeWithText("观察日期 YYYY-MM-DD").performScrollTo().performTextReplacement("2026-06-15")
            scenario.onActivity { it.window.insetsController?.hide(android.view.WindowInsets.Type.ime()) }
            click("保存观察"); waitText("观察照片集")
            ui.waitUntil(15_000) { history(id).single { it.id == first.id }.timestampEpochMs == at("2026-06-15T09:00:00") }
            assertEquals(originalUris,history(id).map { it.photoUri }.toSet()); assertTrue(history(id).single { it.id == first.id }.note!!.contains("春季"))
            ui.onNodeWithContentDescription("返回").performClick(); waitText("我的植物档案")
            ui.onNodeWithContentDescription("返回").performClick(); waitText("四季手账")
            ui.waitUntil(15_000) { journal().observations(2026,Season.SPRING).isEmpty() }
            waitText("这一季，尚无观察"); screenshot("seasons-empty-after-edit")
            click("返回四季总览"); ui.onNodeWithTag("seasons-list").performScrollToNode(hasTestTag("season-AUTUMN")); ui.onNodeWithTag("season-AUTUMN").performClick(); waitText("2026 · 秋季"); screenshot("seasons-autumn")
            back(); waitText("My Garden"); click("春季"); waitText("这一季，尚无植物观察")
            click("夏季"); waitText(name); screenshot("garden-after-date-edit")
            click(name); click("阅读植物文化 →"); waitText("植物文化"); screenshot("culture-personal-photo")
            back(); back(); click("全部"); click("Debug · 无观察档案"); click("认识这个物种  →")
            waitText("植物与自然文字"); screenshot("species-culture-empty")
            assertEquals(3,access.database().openHelper.readableDatabase.version)
            completed = true
        } catch (failure: Throwable) {
            screenshot("journey-failure")
            // Evidence collection must not replace the actual failure with a missing-root error.
            android.util.Log.e("Phase3Evidence", runCatching { ui.onRoot().printToString() }.getOrDefault("No Compose root; see native screenshot"), failure)
            throw failure
        } finally {
            scenario.close()
            val keep = InstrumentationRegistry.getArguments().getString("keepPhase3Evidence") == "true"
            if (!keep || !completed) runBlocking {
                owner?.let { id -> history(id).forEach { access.archive().delete(id,it.id) }; access.plants().delete(id) }
                noPhotoOwner?.let { access.plants().delete(it) }
            }
        }
    }
    private fun verifyCold() {
        val plant = runBlocking { access.plants().observeAll().first().single { it.nickname == name } }
        val rows = history(plant.id); assertEquals(2,rows.size)
        assertTrue(journal().garden(Season.SPRING).isEmpty()); assertEquals(1,journal().garden(Season.SUMMER).size); assertEquals(1,journal().garden(Season.AUTUMN).size)
        rows.forEach { val file = File(URI(it.photoUri)); assertTrue(file.canRead()); requireNotNull(android.graphics.BitmapFactory.decodeFile(file.absolutePath)).recycle(); assertTrue(it.note!!.startsWith("Debug fixture")) }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("发现自然，收藏四季。"); click("花园"); click("夏季"); click("四季手账 →")
            waitText("把相遇留在四季里。"); screenshot("cold-start-seasons")
            ui.onNodeWithTag("seasons-list").performScrollToNode(hasTestTag("season-SUMMER")); ui.onNodeWithTag("season-SUMMER").performClick(); waitText("2026 · 夏季"); screenshot("cold-start-summer")
        }
        android.util.Log.i("Phase3Evidence","COLD PASS plant=${plant.id} photos=2 spring=0 summer=1 autumn=1")
        if (InstrumentationRegistry.getArguments().getString("cleanupPhase3Evidence") == "true") runBlocking {
            // Explicit opt-in only on our dedicated AVD, after validating these retained test fixtures.
            check(rows.all { it.note!!.startsWith("Debug fixture") && it.identificationJson == null })
            val emptyFixture = access.plants().observeAll().first().single { it.nickname == "Debug · 无观察档案" }
            check(history(emptyFixture.id).isEmpty())
            rows.forEach { access.archive().delete(plant.id, it.id) }
            access.plants().delete(plant.id); access.plants().delete(emptyFixture.id)
        }
    }
}

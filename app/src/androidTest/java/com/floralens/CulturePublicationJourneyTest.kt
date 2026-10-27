package com.floralens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.data.culture.CulturePolicy
import com.floralens.domain.model.Species
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.FileInputStream
import java.util.UUID

/** Uses production routes and only removes the record created by this test. */
class CulturePublicationJourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val access get() = EntryPointAccessors.fromApplication(context, Phase1TestAccess::class.java)
    private fun waitText(text: String) = ui.waitUntil(25_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes(false).isNotEmpty() }
    private fun click(text: String) {
        waitText(text)
        val node = ui.onNodeWithText(text)
        try { node.performScrollTo() } catch (_: AssertionError) { }
        node.performClick()
    }
    private fun back() { Espresso.pressBack(); ui.waitForIdle() }
    private fun shot(name: String) {
        ui.waitForIdle()
        instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/wildflower-culture-publication").close()
        instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/wildflower-culture-publication/$name.png").use {
            FileInputStream(it.fileDescriptor).readBytes()
        }
    }
    private fun classic() {
        waitText("植物文化")
        ui.onNodeWithTag("culture-list").performScrollToNode(hasTestTag("culture-excerpt"))
        ui.onNodeWithTag("culture-excerpt").assertTextEquals("暗淡轻黄体性柔")
        ui.onNodeWithTag("culture-list").performScrollToNode(hasText("李清照 · 《鹧鸪天·桂》"))
        ui.onNodeWithText("李清照 · 《鹧鸪天·桂》").assertIsDisplayed()
        ui.onNodeWithText("查看作品出处 ↗").assertHasClickAction()
    }
    @Test fun exploreSpeciesAndPersonalRoutesSharePublishedCultureAndPreserveArchive() = runBlocking {
        val before = access.plants().observeAll().first()
        access.settings().setOnboarded()
        val owner = access.archive().collect("culture-publication-${UUID.randomUUID()}",
            Species.identified("Osmanthus fragrans", "桂花"), "文化回归 · 桂花参考影集", null, null)
        val capture = access.captures().create(context.assets.open("explore/osmanthus_kenraiz.jpg").use { it.readBytes() })
        access.archive().add(owner, capture.id, java.time.Instant.parse("2026-10-08T02:35:00Z").toEpochMilli(), "授权参考摄影；日期用于归档回归，不代表现场实拍。")
        val history = access.database().archiveDao().history(owner)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitText("发现自然，收藏四季。")
            ui.onNodeWithTag("explore-list").performScrollToNode(hasText("桂花 · 阅读文化"))
            click("桂花 · 阅读文化"); classic(); shot("01-explore-culture-classic")
            ui.onNodeWithTag("culture-list").performScrollToIndex(0); shot("02-culture-top")
            ui.onNodeWithTag("culture-list").performScrollToNode(hasTestTag("nature-source"))
            ui.onNodeWithTag("nature-source").assertExists(); shot("03-culture-nature-expression")
            click("阅读自然文字 →"); waitText("自然文字"); click("返回植物文化")
            back()
            ui.waitUntil(25_000) { ui.onAllNodesWithTag("explore-list").fetchSemanticsNodes(false).isNotEmpty() }
            ui.onNodeWithTag("explore-list").performScrollToIndex(0)
            waitText("发现自然，收藏四季。")
            ui.onNodeWithTag("explore-hero").performClick(); waitText("植物笔记")
            click("阅读植物文化 →"); classic(); shot("04-species-culture-classic")
            back(); waitText("植物笔记"); back(); click("花园")
            waitText("文化回归 · 桂花参考影集"); click("文化回归 · 桂花参考影集"); waitText("我的植物档案")
            click("阅读植物文化 →"); classic(); shot("05-personal-culture-classic")
            back(); waitText("我的植物档案")
            click("制作植物诗笺"); waitText("植物诗笺")
            ui.waitUntil(30_000) { ui.onAllNodesWithContentDescription("自然手账预览").fetchSemanticsNodes(false).isNotEmpty() }
            ui.onNodeWithTag("template-CLASSICAL_POETRY").performScrollTo().performClick()
            ui.waitUntil(30_000) { ui.onAllNodesWithContentDescription("古典诗笺预览").fetchSemanticsNodes(false).isNotEmpty() }
            ui.onNodeWithTag("card-expression-source").performScrollTo().assertTextContains("作品引文 · 李清照《鹧鸪天·桂》", substring = true)
            shot("06-classic-card-attribution")
            assertEquals(history, access.database().archiveDao().history(owner))
            val assembled = access.cards().assemble(owner, history.single().id).content
            assertEquals("暗淡轻黄体性柔", assembled.approvedCulture!!.literaryWorks.single().excerpt)
            assertTrue(CulturePolicy.eligible(assembled.approvedCulture!!, false))
            assertEquals(history.single().photoUri, assembled.photoUri)
            assertEquals(before, access.plants().observeAll().first().filterNot { it.id == owner })
        } finally {
            scenario.close()
            access.database().archiveDao().history(owner).forEach { access.archive().delete(owner, it.id) }
            access.plants().delete(owner)
        }
    }
}

package com.floralens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.FileInputStream
import kotlin.math.abs

/** Reads existing app data only. Captures real navigation; never seeds archives or sends AI requests. */
class EditorialPolishJourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).bufferedReader().readText()
    }
    private fun waitTag(tag: String) = ui.waitUntil(20_000) {
        ui.onAllNodesWithTag(tag).fetchSemanticsNodes(false).isNotEmpty()
    }
    private fun waitText(text: String) = ui.waitUntil(20_000) {
        ui.onAllNodesWithText(text).fetchSemanticsNodes(false).isNotEmpty()
    }
    private fun shot(name: String) {
        ui.waitForIdle(); Thread.sleep(500)
        shell("screencap -p /sdcard/Download/wildflower-ui-polish/$name.png")
    }

    @Test fun corePages_remainReachable_andAdaptToPhoneAndTablet() {
        check(runBlocking { SettingsRepository(instrumentation.targetContext).onboarded.first() })
        val args = InstrumentationRegistry.getArguments()
        val label = args.getString("polishEvidence", "after")!!.also { require(it.matches(Regex("[a-z0-9-]+"))) }
        val widths = args.getString("polishWidths", "393,320,800")!!.split(',').map(String::toInt)
        val size = shell("wm size").lineSequence().firstOrNull { it.startsWith("Override size:") }?.substringAfter(": ") ?: "reset"
        val density = shell("wm density").lineSequence().firstOrNull { it.startsWith("Override density:") }?.substringAfter(": ") ?: "reset"
        var scenario: ActivityScenario<MainActivity>? = null
        shell("mkdir -p /sdcard/Download/wildflower-ui-polish")
        try {
            for (width in widths) {
                require(width in listOf(320, 393, 800))
                shell("wm size ${width * 2}x${if (width >= 600) 2134 else 1704}")
                shell("wm density 320")
                scenario = ActivityScenario.launch(MainActivity::class.java)
                waitTag("explore-hero"); shot("$label-$width-explore")
                ui.onNodeWithContentDescription("设置").performClick()
                waitText("设置与帮助"); shot("$label-$width-settings")
                ui.onNodeWithText("在线 · Pl@ntNet").performScrollTo().assertIsDisplayed()
                Espresso.pressBack(); waitTag("explore-hero")
                ui.onNodeWithTag("explore-hero").performClick()
                waitTag("species-chat-entry")
                shot("$label-$width-species")
                ui.onNodeWithTag("species-chat-entry").performScrollTo().performClick()
                waitTag("chat-input"); shot("$label-$width-chat")
                if (label != "before") {
                    val title = ui.onNodeWithText("花间问答").fetchSemanticsNode().boundsInWindow
                    assertTrue("Toolbar title must stay centered with its menu", abs(title.center.x - width) < 6f)
                }
                ui.onNodeWithTag("plant-chat-list").performScrollToNode(hasText("下次观察可以重点记录什么？"))
                ui.onNodeWithText("下次观察可以重点记录什么？").assertIsDisplayed()
                Espresso.pressBack(); waitText("植物笔记")
                ui.onNodeWithText("阅读植物文化 →").performScrollTo().performClick()
                waitTag("culture-list"); shot("$label-$width-culture")
                Espresso.pressBack(); waitText("植物笔记")
                Espresso.pressBack(); waitTag("explore-list")
                ui.onNodeWithTag("explore-list").performScrollToNode(hasText("桂花 · 阅读文化"))
                shot("$label-$width-explore-reading")
                ui.onNodeWithText("花园").performClick(); waitTag("garden-grid")
                shot("$label-$width-garden")
                scenario!!.close(); scenario = null
            }
        } finally {
            scenario?.close()
            shell("wm size $size"); shell("wm density $density")
        }
    }
}

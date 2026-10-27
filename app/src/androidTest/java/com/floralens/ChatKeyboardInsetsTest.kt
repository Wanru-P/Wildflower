package com.floralens

import android.view.WindowInsets
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.floralens.data.settings.SettingsRepository
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.FileInputStream
import kotlin.math.abs

/** Real navigation and system IME; no model requests and no archive mutations. */
class ChatKeyboardInsetsTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).bufferedReader().readText()
    }
    private fun waitTag(tag: String) = ui.waitUntil(20_000) {
        ui.onAllNodesWithTag(tag).fetchSemanticsNodes(false).isNotEmpty()
    }

    @Test fun actualNavigation_composerTracksKeyboardAndKeepsDraft() {
        val settings = SettingsRepository(instrumentation.targetContext)
        check(runBlocking { settings.onboarded.first() }) { "Use the existing onboarded test AVD" }
        val oldSize = shell("wm size").lineSequence().firstOrNull { it.startsWith("Override size:") }?.substringAfter(": ")
        val oldDensity = shell("wm density").lineSequence().firstOrNull { it.startsWith("Override density:") }?.substringAfter(": ")
        val oldIme = shell("settings get secure show_ime_with_hard_keyboard").trim()
        val label = InstrumentationRegistry.getArguments().getString("keyboardEvidence", "after")
        shell("mkdir -p /sdcard/Download/wildflower-keyboard")
        shell("settings put secure show_ime_with_hard_keyboard 1")
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            for (width in listOf(393, 320)) {
                shell("wm size ${width * 3}x2556"); shell("wm density 480")
                scenario = ActivityScenario.launch(MainActivity::class.java)
                waitTag("explore-hero")
                ui.onNodeWithTag("explore-hero").performClick()
                waitTag("species-chat-entry")
                ui.onNodeWithTag("species-chat-entry").performScrollTo().performClick()
                waitTag("chat-input")
                ui.onNodeWithTag("chat-input").performClick().performTextInput("下次观察可以记录什么？")
                var keyboardTop = 0
                var density = 1f
                ui.waitUntil(8_000) {
                    var visible = false
                    scenario!!.onActivity { activity ->
                        val view = activity.window.decorView
                        val insets = ViewCompat.getRootWindowInsets(view)
                        visible = insets?.isVisible(WindowInsetsCompat.Type.ime()) == true
                        keyboardTop = activity.windowManager.currentWindowMetrics.bounds.bottom -
                            (insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0)
                        density = activity.resources.displayMetrics.density
                    }
                    visible
                }
                Thread.sleep(800)
                ui.waitForIdle()
                shell("screencap -p /sdcard/Download/wildflower-keyboard/$label-$width-keyboard.png")
                val input = ui.onNodeWithTag("chat-input").fetchSemanticsNode().boundsInWindow
                val send = ui.onNodeWithTag("chat-send").fetchSemanticsNode().boundsInWindow
                println("IME_GEOMETRY width=$width keyboardTop=$keyboardTop input=$input send=$send density=$density")
                assertTrue("Input covered by keyboard: $input / $keyboardTop", input.bottom <= keyboardTop + 2)
                assertTrue("Input floats above keyboard: $input / $keyboardTop", keyboardTop - input.bottom <= 16 * density)
                assertTrue("Send and input are misaligned", abs(send.bottom - input.bottom) <= 2 * density)
                ui.onNodeWithTag("chat-input").assertTextContains("下次观察可以记录什么？")
                ui.onNodeWithTag("chat-input").performTextInput("\n叶片有哪些变化？\n怎样持续记录？")
                ui.waitForIdle()
                val multiline = ui.onNodeWithTag("chat-input").fetchSemanticsNode().boundsInWindow
                assertTrue("Multiline input covered by keyboard", multiline.bottom <= keyboardTop + 2)
                assertTrue("Multiline input floats above keyboard", keyboardTop - multiline.bottom <= 16 * density)
                scenario!!.onActivity { it.window.insetsController?.hide(WindowInsets.Type.ime()) }
                Thread.sleep(500)
                ui.onNodeWithTag("chat-input").assertIsDisplayed().assertTextContains("下次观察可以记录什么？", substring = true)
                ui.onNodeWithTag("chat-input").performClick()
                Thread.sleep(500)
                ui.onNodeWithTag("chat-input").assertTextContains("下次观察可以记录什么？", substring = true)
                scenario!!.close(); scenario = null
            }
        } finally {
            scenario?.close()
            shell("wm size ${oldSize ?: "reset"}"); shell("wm density ${oldDensity ?: "reset"}")
            if (oldIme == "null") shell("settings delete secure show_ime_with_hard_keyboard")
            else shell("settings put secure show_ime_with_hard_keyboard $oldIme")
        }
    }

    @Test fun savedModeSwitch_survivesRecreation() {
        val settings = SettingsRepository(instrumentation.targetContext)
        val original = runBlocking { settings.identificationMode.first() }
        check(runBlocking { settings.onboarded.first() })
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitTag("explore-hero")
            ui.onNodeWithContentDescription("设置").performClick()
            for (mode in listOf(com.floralens.domain.model.IdentificationMode.ONLINE,
                com.floralens.domain.model.IdentificationMode.OFFLINE)) {
                val label = if (mode == com.floralens.domain.model.IdentificationMode.ONLINE) "在线 · Pl@ntNet" else "离线 · 本机识别"
                ui.onNodeWithText(label).performScrollTo().performClick()
                ui.waitUntil(5_000) { runBlocking { settings.identificationMode.first() } == mode }
                scenario.recreate()
                ui.waitForIdle()
                assertEquals(mode, runBlocking { settings.identificationMode.first() })
            }
        } finally {
            scenario.close()
            runBlocking { settings.setIdentificationMode(original) }
        }
    }
}

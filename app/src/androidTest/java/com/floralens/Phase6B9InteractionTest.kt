package com.floralens

import android.Manifest
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.domain.model.IdentificationMode
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.FileInputStream

/** Final interaction checks which are intentionally absent from the visual journeys.
 * Runs only on the owned, empty Phase 6B test AVD and never clears application data. */
class Phase6B9InteractionTest {
    @get:Rule val ui = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val access get() = EntryPointAccessors.fromApplication(context, Phase1TestAccess::class.java)

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use {
            FileInputStream(it.fileDescriptor).bufferedReader().readText()
        }

    private fun waitText(text: String) = ui.waitUntil(20_000) {
        ui.onAllNodesWithText(text, substring = true).fetchSemanticsNodes(false).isNotEmpty()
    }

    private fun click(text: String) {
        waitText(text)
        ui.onNodeWithText(text).assertIsDisplayed().performClick()
    }

    @Test
    fun topLevelNavigation_secondaryBackStack_andCareTitleSemantics() {
        assertEquals(
            "Wildflower_Phase6B7_API35",
            shell("getprop ro.boot.qemu.avd_name").trim(),
        )
        val originalMode = runBlocking { access.settings().identificationMode.first() }
        val originalSize = Regex("Override size: (\\d+x\\d+)")
            .find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        val originalDensity = Regex("Override density: (\\d+)")
            .find(shell("wm density"))?.groupValues?.get(1) ?: "reset"
        val originalFont = shell("settings get system font_scale").trim()
            .takeIf { it.toFloatOrNull() != null } ?: "1.0"

        runBlocking {
            check(access.plants().observeAll().first().isEmpty()) {
                "Dedicated empty AVD required; never clear an existing garden"
            }
            access.settings().setOnboarded()
            access.settings().setIdentificationMode(IdentificationMode.OFFLINE)
        }
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitText("发现自然，收藏四季。")
            ui.onNodeWithText("发现").assertIsSelected()

            click("花园")
            waitText("My Garden")
            ui.onNodeWithText("花园").assertIsSelected()

            // Re-selecting the active top-level destination must not add a duplicate.
            click("花园")
            Espresso.pressBack()
            waitText("发现自然，收藏四季。")
            ui.onNodeWithText("发现").assertIsSelected()

            click("花园")
            click("四季手账 →")
            waitText("把相遇留在四季里。")
            Espresso.pressBack()
            waitText("My Garden")
            ui.onNodeWithText("花园").assertIsSelected()

            click("养护提醒")
            waitText("暂时无需浇水")
            // The production title is informational. It must not expose the
            // internal long-press worker hook as a dead click target.
            ui.onAllNodesWithText("养护提醒").assertAll(hasNoClickAction())
            Espresso.pressBack()
            waitText("My Garden")

            click("设置")
            waitText("设置与帮助")
            Espresso.pressBack()
            waitText("My Garden")

            click("去识别")
            waitText("识别植物")
            ui.onNodeWithText("识别").assertIsSelected()
            click("发现")
            waitText("发现自然，收藏四季。")
            ui.onNodeWithTag("explore-hero").performClick()
            waitText("植物笔记")
            Espresso.pressBack()
            waitText("发现自然，收藏四季。")

            // Narrow screen + 130% text: the main Garden utilities remain usable.
            shell("wm size 880x1904")
            shell("wm density 440")
            shell("settings put system font_scale 1.3")
            click("花园")
            scenario.recreate()
            waitText("My Garden")
            click("养护提醒")
            waitText("暂时无需浇水")
            Espresso.pressBack()
            waitText("My Garden")

            assertEquals(0, runBlocking { access.database().plantDao().count() })
        } finally {
            scenario.close()
            shell("wm size $originalSize")
            shell("wm density $originalDensity")
            shell("settings put system font_scale $originalFont")
            runBlocking { access.settings().setIdentificationMode(originalMode) }
        }
    }
}

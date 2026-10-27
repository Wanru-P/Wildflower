package com.floralens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.activity.compose.LocalActivity
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.data.culture.*
import com.floralens.ui.screens.culture.*
import com.floralens.ui.screens.details.*
import com.floralens.ui.theme.FloraLensTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.FileInputStream

/** State-level production Composables. This is not an end-to-end release APK test. */
class Phase5BStateTest {
    @get:Rule val ui = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val folder get() = InstrumentationRegistry.getArguments().getString("phase5FinalRound")?.let { "wildflower-phase5-final-$it/phase5b" } ?: if (InstrumentationRegistry.getArguments().getString("phase5bPresentation") == "true") "wildflower-phase5b-presentation" else "wildflower-phase5b-test"
    private fun shell(cmd: String) = instrumentation.uiAutomation.executeShellCommand(cmd).use { FileInputStream(it.fileDescriptor).readBytes() }
    @Test fun releasePolicyRestoresApprovedClassicAndRetainsNatureExpression() {
        val repository = CultureRepository(CultureCatalog(instrumentation.targetContext))
        val approved = runBlocking { repository.approvedContent("scientific:osmanthus fragrans") }
        assertNotNull(approved)
        var back = false
        ui.setContent { FloraLensTheme {
            val view = LocalView.current
            val window = LocalActivity.current?.window
            SideEffect { window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = true } }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Box(Modifier.safeDrawingPadding()) {
            CultureContent(CultureUiState(loading = false, entry = approved, photo = "file:///android_asset/explore/osmanthus_kenraiz.jpg",
                photoCaption = "授权参考摄影 · Krzysztof Ziarnek, Kenraiz / CC BY-SA 4.0", chineseName = "桂花", scientificName = "Osmanthus fragrans"), { back = true }) { assertEquals(approved!!.literaryWorks.single().sourceUrl, it) }
            } }
        } }
        ui.onNodeWithTag("culture-list").performScrollToNode(hasTestTag("culture-excerpt"))
        ui.onNodeWithTag("culture-excerpt").assertTextEquals("暗淡轻黄体性柔")
        ui.onNodeWithText("查看作品出处 ↗").assertHasClickAction()
        ui.scrollCultureTo("nature-text")
        ui.onNodeWithTag("nature-text").assertIsDisplayed()
        ui.onNodeWithTag("nature-source").assertTextEquals("自然观察文字 · 草木知时")
        ui.waitForIdle(); Thread.sleep(1000); ui.waitForIdle()
        shell("mkdir -p /sdcard/Download/$folder"); shell("screencap -p /sdcard/Download/$folder/culture-empty.png")
        ui.onNodeWithContentDescription("返回").performClick(); assertTrue(back)
    }
    @Test fun narrowLongSpeciesAndBrokenPhotoRetainRetryAndBack() {
        var retry = false; var back = false
        try {
            shell("wm size 840x1680"); shell("wm density 420")
            ui.setContent { FloraLensTheme {
                val view = LocalView.current
                val window = LocalActivity.current?.window
                SideEffect { window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = true } }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Box(Modifier.safeDrawingPadding()) {
                SpeciesDetailContent(DetailsUiState.Error("", listOf("file:///nonexistent/phase5b-owned-missing.jpg"), true),
                    "Osmanthus fragrans var. very long name used only for typography testing", "名称很长且没有可读取摄影的植物资料测试页面", null, false, false, { back = true }, { retry = true })
                } }
            } }
            ui.onNodeWithText("名称很长且没有可读取摄影的植物资料测试页面").assertIsDisplayed()
            // Both the hero and the optional culture illustration can reference the missing URI.
            assertTrue(ui.onAllNodesWithTag("photo-unavailable").fetchSemanticsNodes(false).isNotEmpty())
            ui.onNodeWithText("重新加载").performScrollTo().performClick(); assertTrue(retry)
            // Semantics can be ready before the native window finishes its size/launch transition.
            // The supplied album illustration replaces the old flat green placeholder. Confirm
            // the real native retry control and display size, not a colour at a fixed coordinate.
            fun nativeRetry(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
                if (node == null) return false
                if (node.text?.toString() == "重新加载") return true
                return (0 until node.childCount).any { nativeRetry(node.getChild(it)) }
            }
            ui.waitUntil(20_000) {
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    val ready = bitmap.width == 840 && bitmap.height == 1680 && nativeRetry(instrumentation.uiAutomation.rootInActiveWindow)
                    bitmap.recycle()
                    ready
                } ?: false
            }
            ui.waitForIdle(); Thread.sleep(400)
            shell("mkdir -p /sdcard/Download/$folder"); shell("screencap -p /sdcard/Download/$folder/species-offline-broken-320.png")
            ui.onNodeWithContentDescription("返回").performClick(); assertTrue(back)
        } finally { shell("wm size reset"); shell("wm density reset") }
    }
}

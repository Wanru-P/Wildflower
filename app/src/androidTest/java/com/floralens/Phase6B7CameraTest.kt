package com.floralens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.camera.view.PreviewView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.domain.model.IdentificationMode
import com.floralens.domain.model.Species
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.FileInputStream
import java.util.UUID

/** Real CameraX frames, system permission dialog and lifecycle; no image injection. */
class Phase6B7CameraTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val access get() = EntryPointAccessors.fromApplication(context, Phase1TestAccess::class.java)
    private val args get() = InstrumentationRegistry.getArguments()
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use { FileInputStream(it.fileDescriptor).bufferedReader().readText() }
    private fun waitText(text: String) = ui.waitUntil(25_000) { ui.onAllNodesWithText(text, substring = true).fetchSemanticsNodes(false).isNotEmpty() }
    private fun click(text: String) {
        waitText(text)
        val node = ui.onNodeWithText(text)
        try { node.performScrollTo() } catch (_: AssertionError) { }
        node.assertIsDisplayed().performClick()
    }
    private fun back() { Espresso.pressBack(); ui.waitForIdle() }
    private fun shot(name: String) {
        ui.waitForIdle(); Thread.sleep(700)
        val directory = if (args.getString("phase6b8Evidence") == "true") "wildflower-phase6b8"
            else if (args.getString("baselineCamera") == "true") "wildflower-phase6b7-baseline" else "wildflower-phase6b7"
        shell("mkdir -p /sdcard/Download/$directory")
        shell("screencap -p /sdcard/Download/$directory/$name.png")
    }
    private fun nativeButton(resource: String) {
        var node: AccessibilityNodeInfo? = null
        ui.waitUntil(15_000) {
            node = instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/$resource")?.firstOrNull()
            node != null
        }
        check(node!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun preview(scenario: ActivityScenario<MainActivity>): PreviewView? {
        var found: PreviewView? = null
        scenario.onActivity { activity ->
            fun search(view: View): PreviewView? = when (view) {
                is PreviewView -> view
                is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { search(view.getChildAt(it)) }
                else -> null
            }
            found = search(activity.window.decorView)
        }
        return found
    }
    private fun streaming(scenario: ActivityScenario<MainActivity>) {
        ui.waitUntil(25_000) { preview(scenario)?.previewStreamState?.value == PreviewView.StreamState.STREAMING }
    }
    private fun frameBrightness(scenario: ActivityScenario<MainActivity>): Double {
        var value = 0.0
        scenario.onActivity {
            preview(scenario)?.bitmap?.let { bitmap ->
                var total = 0L; var count = 0
                for (y in 0 until bitmap.height step 20) for (x in 0 until bitmap.width step 20) {
                    val pixel = bitmap.getPixel(x, y)
                    total += (pixel shr 16 and 255) + (pixel shr 8 and 255) + (pixel and 255)
                    count += 3
                }
                value = if (count == 0) 0.0 else total.toDouble() / count
                bitmap.recycle()
            }
        }
        return value
    }

    @Test fun permission_cameraPreview_reentry_background_scanAndCapture() {
        val baseline = args.getString("baselineCamera") == "true"
        val unavailable = args.getString("cameraUnavailable") == "true"
        val oldMode = runBlocking { access.settings().identificationMode.first() }
        var owner: Long? = null
        runBlocking {
            check(access.plants().observeAll().first().isEmpty()) { "Dedicated empty AVD required; never erase archives" }
            access.settings().setOnboarded(); access.settings().setIdentificationMode(IdentificationMode.OFFLINE)
            owner = access.archive().collect("phase6b7-camera-${UUID.randomUUID()}", Species.identified("Osmanthus fragrans", "桂花"), "叶片观察档案", null, null)
        }
        if (baseline || unavailable) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitText("发现自然，收藏四季。"); ui.onNodeWithText("花园").performClick(); click("叶片观察档案")
            click("叶片健康检查")
            if (unavailable) {
                waitText("此设备没有可用的后置摄像头。")
                ui.onNodeWithContentDescription("拍摄叶片").assertIsNotEnabled()
                val pendingBefore = runBlocking { access.captures().latest()?.id }
                ui.onNodeWithContentDescription("拍摄叶片").performTouchInput { click() }
                assertEquals(pendingBefore, runBlocking { access.captures().latest()?.id })
                assertTrue(runBlocking { access.database().archiveDao().history(owner!!).isEmpty() })
                shot(if (args.getString("phase6b8Evidence") == "true") "08-small-polish" else "camera-unavailable")
                return
            }
            if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                waitText("需要相机权限"); click("允许使用相机")
                if (args.getString("cameraPermanentlyDenied") == "true") {
                    waitText("前往系统设置"); click("前往系统设置")
                    ui.waitUntil(10_000) { shell("dumpsys activity activities").contains("com.android.settings/") }
                    instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
                    shell("input keyevent 4")
                    waitText("叶片健康检查")
                } else {
                    nativeButton("permission_deny_button")
                    waitText("需要相机权限"); shot("08-leaf-permission-denied")
                    click("允许使用相机"); nativeButton("permission_allow_foreground_only_button")
                }
            }
            streaming(scenario)
            ui.waitUntil(10_000) { frameBrightness(scenario) > 2.0 }
            val brightness = frameBrightness(scenario)
            android.util.Log.i("Phase6B7", "LEAF_CAMERA baseline=$baseline state=STREAMING frameBrightness=$brightness")
            shot(if (baseline) "baseline-leaf-streaming" else "06-leaf-preview-393")
            if (baseline) return
            back(); waitText("我的植物档案"); click("叶片健康检查"); streaming(scenario)
            // Exercise a real Home/app return. ActivityScenario's synthetic
            // EmptyActivity stop/resume can strand a reused task during a suite.
            shell("input keyevent 3")
            ui.waitUntil(10_000) {
                var stopped = false
                scenario.onActivity { stopped = !it.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
                stopped
            }
            context.startActivity(Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            streaming(scenario)
            back(); back(); waitText("My Garden"); ui.onNodeWithText("识别").performClick()
            waitText("离线 · 本机识别"); streaming(scenario)
            ui.onNodeWithText("花园").performClick(); click("叶片观察档案"); click("叶片健康检查"); streaming(scenario)
            ui.onNodeWithContentDescription("拍摄叶片").performClick()
            ui.waitUntil(25_000) { listOf("保存观察", "这次还没看清").any { ui.onAllNodesWithText(it).fetchSemanticsNodes(false).isNotEmpty() } }
            assertEquals(0, runBlocking { access.database().archiveDao().history(owner!!).size })
            click("重新拍摄"); streaming(scenario)
            android.util.Log.i("Phase6B7", "LEAF_PERMISSION_REENTRY_BACKGROUND_SCAN_CAPTURE_PASS")
        } finally {
            scenario.close()
            runBlocking {
                owner?.let { id -> access.database().archiveDao().history(id).forEach { access.archive().delete(id, it.id) }; access.plants().delete(id) }
                access.settings().setIdentificationMode(oldMode)
            }
        }
    }
}

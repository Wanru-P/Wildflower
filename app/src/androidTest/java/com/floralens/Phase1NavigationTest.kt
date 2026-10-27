package com.floralens

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.floralens.domain.model.Plant
import com.floralens.domain.model.Observation
import com.floralens.domain.model.ObservationType
import com.floralens.reminders.NotificationHelper
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Run only on a dedicated test device. Mutations are owned Debug fixtures, not user records. */
class Phase1NavigationTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val access get() = EntryPointAccessors.fromApplication(context, Phase1TestAccess::class.java)

    // A new Intent / resized window may briefly detach roots. Still require the real text
    // within the original deadline, rather than failing on the first transitional frame.
    private fun waitText(text: String) = ui.waitUntil(15_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    private fun click(text: String) { waitText(text); ui.onNodeWithText(text).performClick() }
    private fun back() { Espresso.pressBack(); ui.waitForIdle() }
    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use {
            java.io.FileInputStream(it.fileDescriptor).bufferedReader().readText()
        }
    private fun waitSize(width: Int, height: Int) = ui.waitUntil(15_000) {
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            val matches = bitmap.width == width && bitmap.height == height
            bitmap.recycle()
            matches
        } ?: false
    }
    private fun isStreaming(scenario: ActivityScenario<MainActivity>): Boolean {
        var streaming = false
        scenario.onActivity { activity ->
            fun search(view: android.view.View): Boolean {
                if (view is androidx.camera.view.PreviewView) return view.previewStreamState.value == androidx.camera.view.PreviewView.StreamState.STREAMING
                return view is android.view.ViewGroup && (0 until view.childCount).any { search(view.getChildAt(it)) }
            }
            streaming = search(activity.window.decorView)
        }
        return streaming
    }
    private fun screenshot(name: String) {
        ui.waitForIdle()
        Thread.sleep(600) // allow Coil draw and activity transition to settle before device capture
        // Shell-owned evidence survives Gradle UTP's app uninstall after testing.
        instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/wildflower-phase1").use {
            java.io.FileInputStream(it.fileDescriptor).readBytes()
        }
        instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/wildflower-phase1/$name.png").use {
            java.io.FileInputStream(it.fileDescriptor).readBytes()
        }
    }

    @Test fun coreRoutes_readRoom_and_doNotCollectSpecies() {
        if (InstrumentationRegistry.getArguments().getString("recoverInterruptedPhase1") == "true") {
            recoverInterruptedOwnedFixtures()
            return // Explicit maintenance invocation, not functional acceptance evidence.
        }
        val owned = mutableListOf<Pair<Long, String>>()
        var ownsCache = false
        runBlocking {
            check(access.plants().observeAll().first().isEmpty()) { "Use an empty dedicated AVD; never clear personal records." }
            access.settings().setOnboarded()
        }
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitText("发现自然，收藏四季。")
            screenshot("explore")
            ui.onNodeWithTag("explore-hero").performClick()
            waitText("公共物种资料", substring = true)
            ui.onNodeWithText("Osmanthus fragrans").assertIsDisplayed()
            screenshot("species-detail")
            assertEquals(0, runBlocking { access.database().plantDao().count() })
            // Exercise the existing offline Room cache and its original extended reader.
            runBlocking {
                check(access.info().getSaved("Osmanthus fragrans") == null) { "Use a fresh dedicated test installation." }
                access.info().save(com.floralens.domain.model.PlantInsights(
                    description = null, regionalNames = emptyList(), origin = null,
                    nativeRegions = emptyList(), growthHabit = null, bloomSeason = null,
                    careLevels = com.floralens.domain.model.CareLevels(), careNotes = null,
                    propagation = emptyList(), commonProblems = emptyList(), funFacts = emptyList(),
                    toxicityNote = null, scientificName = "Osmanthus fragrans"), emptyList(), null, "桂花")
                ownsCache = true
            }
            waitText("重新加载")
            ui.onNodeWithText("重新加载").performScrollTo().performClick()
            waitText("浏览科学、养护与分布资料")
            ui.onNodeWithText("浏览科学、养护与分布资料").performScrollTo().performClick()
            ui.onNodeWithText("AI 辅助生成，可能不准确。养护与安全建议需另行核实。").performScrollTo().assertIsDisplayed()
            screenshot("species-cached-science")
            click("养护"); click("分布"); click("视频")
            back()
            assertEquals(0, runBlocking { access.database().plantDao().count() })
            back()
            ui.onNodeWithTag("explore-list").performScrollToNode(hasText("银杏"))
            click("银杏")
            waitText("Ginkgo biloba")
            ui.onNodeWithText("Ginkgo biloba").assertIsDisplayed()
            assertEquals(0, runBlocking { access.database().plantDao().count() })
            back()
            click("花园")
            waitText("你的植物故事，\n从第一次发现开始。")
            screenshot("garden-empty")

            // Licensed catalog references copied through the REAL PhotoStore into REAL Room.
            runBlocking {
                listOf("桂花" to "osmanthus_starr.jpg", "银杏" to "ginkgo.jpg").forEach { (name, file) ->
                    val uri = access.photos().save(context.assets.open("explore/$file").use { it.readBytes() })
                    val scientific = if (name == "桂花") "Osmanthus fragrans" else "Ginkgo biloba"
                    val id = access.plants().upsert(Plant(nickname = "Debug · $name", scientificName = scientific,
                        commonName = name, photoUri = uri, waterIntervalDays = 7, remindersEnabled = false))
                    owned += id to uri
                    access.observations().add(Observation(plantId = id, type = ObservationType.IDENTIFIED,
                        photoUri = uri, note = "Debug 测试记录 · 授权参考摄影，非用户实拍"))
                }
            }
            waitText("Debug · 桂花") // verifies Room Flow updates without relaunch
            screenshot("garden-populated")
            val originalSize = shell("wm size")
            val originalDensity = shell("wm density")
            val dimensions = Regex("(?:Physical|Override) size: (\\d+)x(\\d+)")
                .findAll(originalSize).last().groupValues
            val restoreSize = Regex("Override size: (\\d+x\\d+)").find(originalSize)?.groupValues?.get(1) ?: "reset"
            val restoreDensity = Regex("Override density: (\\d+)").find(originalDensity)?.groupValues?.get(1) ?: "reset"
            try {
                shell("wm size 840x1680"); shell("wm density 420")
                waitSize(840, 1680)
                waitText("Debug · 桂花")
                screenshot("garden-populated-narrow")
                shell("wm size 1920x2560"); shell("wm density 320")
                waitSize(1920, 2560)
                waitText("Debug · 桂花")
                screenshot("garden-populated-tablet")
            } finally {
                shell("wm size $restoreSize"); shell("wm density $restoreDensity")
                waitSize(dimensions[1].toInt(), dimensions[2].toInt())
            }
            waitText("Debug · 桂花")
            click("Debug · 桂花")
            waitText("我的植物档案")
            ui.onNodeWithText("Debug · 桂花").assertIsDisplayed()
            screenshot("plant-detail")
            ui.onNodeWithText("我的观察").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("Debug 测试记录 · 授权参考摄影，非用户实拍").performScrollTo().assertIsDisplayed()
            screenshot("plant-history")
            ui.onNodeWithText("叶片健康检查").performScrollTo().performClick()
            waitText("叶片健康检查")
            screenshot("leaf-health")
            back()
            ui.onNodeWithText("认识这个物种  →").performScrollTo().performClick()
            waitText("植物笔记")
            assertEquals(2, runBlocking { access.database().plantDao().count() })
            back(); back()

            click("观察地图")
            waitText("观察地图")
            screenshot("map")
            back()
            click("养护提醒")
            waitText("养护提醒")
            screenshot("care")
            back()
            click("设置")
            waitText("设置与帮助")
            screenshot("settings")
            back()
            click("识别")
            waitText("离线 · 本机识别")
            assertTrue("Dedicated AVD must expose a rear camera; page visibility alone is not camera verification",
                context.getSystemService(android.hardware.camera2.CameraManager::class.java).cameraIdList.isNotEmpty())
            ui.waitUntil(15_000) { isStreaming(scenario) }
            ui.onNodeWithContentDescription("拍摄植物").assertIsDisplayed()
            screenshot("scan")
            click("发现"); click("识别")
            waitText("离线 · 本机识别") // re-enter CameraX lifecycle
            ui.waitUntil(15_000) { isStreaming(scenario) }
            screenshot("scan-return")
            ui.onNodeWithContentDescription("拍摄植物").performClick()
            ui.waitUntil(30_000) {
                listOf("暂未发现植物", "查看植物详情").any { ui.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
            }
            screenshot("scan-result") // emulator scene, not a claim of botanical identification accuracy
            runBlocking { access.captures().latest()?.let { access.captures().cancel(it.id) } }
            assertEquals(2, runBlocking { access.database().plantDao().count() })
            click("发现")
            screenshot("navigation")

            // Warm notification: same Intent extra and singleTop/onNewIntent path as PendingIntent.
            context.startActivity(Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(NotificationHelper.EXTRA_NAVIGATE_TO, NotificationHelper.DESTINATION_CARE)
            })
            waitText("养护提醒")
            screenshot("notification-warm")
            back()
        } finally {
            try { scenario.close() } finally {
                runBlocking {
                    owned.forEach { (id, photo) -> access.plants().delete(id); access.photos().delete(photo) }
                    if (ownsCache) access.info().delete("Osmanthus fragrans")
                }
            }
        }
        // Cold notification, original onCreate path, with no saved activity.
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).apply {
            putExtra(NotificationHelper.EXTRA_NAVIGATE_TO, NotificationHelper.DESTINATION_CARE)
        }).use { cold ->
            waitText("养护提醒")
            cold.onActivity { assertFalse(it.intent.hasExtra(NotificationHelper.EXTRA_NAVIGATE_TO)) }
            screenshot("notification-cold")
            back()
            waitText("发现自然，收藏四季。")
            cold.recreate()
            waitText("发现自然，收藏四季。")
            ui.onAllNodesWithText("养护提醒").assertCountEquals(0)
        }
    }

    /** Opt-in recovery after a failed ActivityScenario.close. Never a blanket database reset. */
    private fun recoverInterruptedOwnedFixtures() = runBlocking {
        check(shell("getprop ro.boot.qemu.avd_name").trim() == "Wildflower_Phase6B7_API35")
        val since = requireNotNull(InstrumentationRegistry.getArguments().getString("recoverSinceEpochMs")).toLong()
        val plants = access.plants().observeAll().first()
        check(plants.size == 2)
        val expected = mapOf("Debug · 桂花" to ("Osmanthus fragrans" to "osmanthus_starr.jpg"), "Debug · 银杏" to ("Ginkgo biloba" to "ginkgo.jpg"))
        plants.forEach { plant ->
            val (scientific, asset) = requireNotNull(expected[plant.nickname])
            check(plant.scientificName == scientific && !plant.remindersEnabled)
            check(plant.createdAt.toEpochMilli() in since..(since + 180_000))
            val event = access.observations().observeForPlant(plant.id).first().single()
            check(event.type == ObservationType.IDENTIFIED && event.note == "Debug 测试记录 · 授权参考摄影，非用户实拍")
            check(event.photoUri == plant.photoUri && event.latitude == null && event.longitude == null)
            check(event.timestamp.toEpochMilli() in since..(since + 180_000))
            assertArrayEquals(context.assets.open("explore/$asset").use { it.readBytes() }, File(java.net.URI(plant.photoUri!!)).readBytes())
        }
        val cache = access.info().getSaved("Osmanthus fragrans")
        check(cache?.insights == com.floralens.domain.model.PlantInsights(null, emptyList(), null, emptyList(), null, null,
            com.floralens.domain.model.CareLevels(), null, emptyList(), emptyList(), emptyList(), null, "Osmanthus fragrans"))
        // All identity, time, note and image-byte checks precede any deletion.
        plants.forEach { access.plants().delete(it.id); access.photos().delete(it.photoUri!!) }
        access.info().delete("Osmanthus fragrans")
        android.util.Log.i("Phase6B8", "MAINTENANCE_ONLY recoveredTwoVerifiedInterruptedPhase1Fixtures=true")
    }

    private fun waitText(text: String, substring: Boolean) = ui.waitUntil(15_000) {
        ui.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
}

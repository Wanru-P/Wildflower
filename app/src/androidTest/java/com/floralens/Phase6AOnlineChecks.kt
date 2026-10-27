package com.floralens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.domain.model.*
import com.floralens.ml.PlantNetIdentifier
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import java.io.FileInputStream

/** Explicit opt-in external-service checks. Normal offline regression never consumes API quota. */
internal suspend fun verifyRealPlantNet() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    check(BuildConfig.PLANTNET_API_KEY.isNotBlank()) { "PlantNet credential not configured" }
    val candidates = try {
        PlantNetIdentifier().classify(context.assets.open("explore/ginkgo.jpg").use { it.readBytes() })
    } catch (failure: Exception) {
        val status = Regex("HTTP \\d{3}").find(failure.message.orEmpty())?.value ?: "no HTTP status"
        // Never include a provider response, request URL or exception cause containing credentials.
        throw AssertionError("PLANTNET_REAL_REQUEST_FAILED ${failure.javaClass.simpleName}: $status")
    }
    assertTrue("Real service must return candidates", candidates.isNotEmpty())
    assertTrue(candidates.all { it.source == IdentificationSource.PLANTNET && it.confidence in 0f..1f })
    assertEquals("Ginkgo biloba", candidates.first().scientificName)
    assertEquals("Chinese name must reach the candidate, not just the UI's local Ginkgo mapping",
        "银杏", candidates.first().commonName)
    Log.i("Phase6AOnline", "PLANTNET_REAL_ASSET_PASS top=${candidates.first().scientificName} common=${candidates.first().commonName} confidence=${candidates.first().confidence} candidates=${candidates.size}")
}

internal class Phase6AOnlineChecks(private val ui: ComposeTestRule) {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val access get() = EntryPointAccessors.fromApplication(context, Phase1TestAccess::class.java)
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).bufferedReader().readText()
    }
    private fun guard() {
        check(BuildConfig.DEBUG && shell("getprop ro.boot.qemu.avd_name").trim() in
            setOf("Wildflower_NatureAI_API35", "Wildflower_Phase6B7_API35"))
        check(runBlocking { access.plants().observeAll().first().isEmpty() }) { "Empty dedicated AVD required; do not clear records" }
    }
    private fun waitText(text: String, timeout: Long = 25_000) = ui.waitUntil(timeout) {
        ui.onAllNodesWithText(text).fetchSemanticsNodes(false).isNotEmpty()
    }
    private fun screen(name: String) {
        ui.waitForIdle()
        Thread.sleep(700) // Coil/native map draws may settle after Compose's semantic tree.
        ui.waitForIdle()
        shell("mkdir -p /sdcard/Download/wildflower-phase6a-online")
        shell("screencap -p /sdcard/Download/wildflower-phase6a-online/$name.png")
    }
    private fun candidates(result: IdResult?): List<IdCandidate> = when (result) {
        is IdResult.HighConfidence -> listOf(result.best) + result.alternates
        is IdResult.LowConfidence -> result.possibilities
        else -> emptyList()
    }
    private fun withoutNetwork(action: () -> Unit) {
        val wifi = shell("settings get global wifi_on").trim()
        val data = shell("settings get global mobile_data").trim()
        try {
            shell("svc wifi disable"); shell("svc data disable")
            ui.waitUntil(20_000) { !com.floralens.data.network.SystemNetworkStatus(context).isConnected() }
            action()
        } finally {
            shell("svc wifi ${if (wifi == "1") "enable" else "disable"}")
            shell("svc data ${if (data == "1") "enable" else "disable"}")
        }
    }

    fun onlineCamera(noNetwork: Boolean = false, verifyChatEntry: Boolean = false) {
        guard()
        if (noNetwork) withoutNetwork { cameraUi(true, verifyChatEntry) } else cameraUi(false, verifyChatEntry)
    }
    private fun cameraUi(noNetwork: Boolean, verifyChatEntry: Boolean) {
        guard()
        val oldMode = runBlocking { access.settings().identificationMode.first() }
        var captureId: String? = null
        try {
            check(runBlocking { access.captures().latest() } == null) { "Existing pending capture must not be discarded" }
            runBlocking { access.settings().setOnboarded(); access.settings().setIdentificationMode(IdentificationMode.ONLINE) }
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                waitText("发现自然，收藏四季。")
                ui.onNodeWithText("识别").performClick(); waitText("在线 · Pl@ntNet")
                ui.waitUntil(25_000) {
                    var streaming = false
                    scenario.onActivity { activity ->
                        fun ready(view: android.view.View): Boolean = when (view) {
                            is androidx.camera.view.PreviewView -> view.previewStreamState.value == androidx.camera.view.PreviewView.StreamState.STREAMING
                            is android.view.ViewGroup -> (0 until view.childCount).any { ready(view.getChildAt(it)) }
                            else -> false
                        }
                        streaming = ready(activity.window.decorView)
                    }
                    streaming
                }
                ui.onNodeWithContentDescription("拍摄植物").performClick()
                ui.waitUntil(50_000) { runBlocking { access.captures().latest()?.result != null } }
                val capture = runBlocking { requireNotNull(access.captures().latest()) }
                captureId = capture.id
                val actual = candidates(capture.result)
                val expected = if (noNetwork) IdentificationSource.ON_DEVICE else IdentificationSource.PLANTNET
                assertTrue("Result must come from the actual requested backend", actual.isNotEmpty() && actual.all { it.source == expected })
                ui.waitUntil(15_000) {
                    ui.onAllNodesWithTag("photo-unavailable", useUnmergedTree = true).fetchSemanticsNodes(false).isEmpty()
                }
                if (noNetwork) {
                    waitText("网络不可用，已使用本机识别")
                    waitText("来源：本机模型"); screen("plantnet-network-down-real-offline-fallback")
                } else {
                    waitText("来源：Pl@ntNet 在线识别"); screen("plantnet-camera-result")
                }
                if (verifyChatEntry) {
                    ui.onNodeWithTag("identification-chat-entry").performScrollTo().assertIsDisplayed()
                    screen("phase6b6-identification-chat-entry")
                    ui.onNodeWithTag("identification-chat-entry").performClick(); waitText("花间问答")
                    ui.onNodeWithText("本次识别", substring = true).assertIsDisplayed()
                    assertEquals(0, runBlocking { access.database().plantDao().count() })
                    screen("phase6b6-capture-chat-context")
                    androidx.test.espresso.Espresso.pressBack(); waitText("本次识别结果")
                    ui.onNodeWithText("查看植物详情").performScrollTo().assertIsDisplayed()
                    ui.onNodeWithText("重新拍摄").performScrollTo().assertIsDisplayed()
                }
                ui.onNodeWithText("查看植物详情").performScrollTo().performClick(); waitText("植物笔记")
                assertEquals(0, runBlocking { access.database().plantDao().count() })
                screen(if (noNetwork) "offline-fallback-species" else "plantnet-species-no-auto-collection")
                val selected = runBlocking { requireNotNull(access.captures().get(capture.id)?.selected) }
                val identity = com.floralens.domain.culture.CultureIdentityResolver.resolve(selected.key,
                    selected.scientificName, certain = capture.result !is IdResult.LowConfidence)
                ui.onNodeWithText("阅读植物文化 →").performScrollTo().performClick(); waitText("植物文化")
                ui.scrollCultureTo("nature-text")
                ui.onNodeWithTag("nature-text").assertTextEquals(runBlocking {
                    com.floralens.data.culture.NatureExpressionRepository(context,
                        com.floralens.data.culture.NatureServiceConfig(context)).current(identity).text })
                val reading = runBlocking { if (com.floralens.domain.culture.CultureIdentificationPolicy.certain(capture.result, selected.key))
                    access.culture().readingContent(selected.key) else null }
                val work = reading?.let(com.floralens.data.culture.CulturePolicy::readingWorks)?.firstOrNull()
                if (work != null) {
                    ui.onNodeWithTag("culture-list").performScrollToNode(hasTestTag("culture-excerpt"))
                    ui.onNodeWithTag("culture-excerpt").assertTextEquals(work.excerpt!!)
                } else ui.onAllNodesWithTag("culture-excerpt").assertCountEquals(0)
                assertEquals(0, runBlocking { access.database().plantDao().count() })
                screen(if (noNetwork) "offline-fallback-culture" else "plantnet-culture-same-identity")
                Log.i("Phase6AOnline", "${if (noNetwork) "REAL_NETWORK_DOWN_OFFLINE_FALLBACK_PASS" else "PLANTNET_CAMERA_AND_DETAILS_PASS"} top=${actual.first().scientificName} confidence=${actual.first().confidence} noAutoCollection=true")
            }
        } finally {
            runBlocking {
                captureId?.let { access.captures().cancel(it) }
                access.settings().setIdentificationMode(oldMode)
            }
        }
    }

    fun baiduMap(expectFailure: Boolean = false) {
        guard()
        if (expectFailure) withoutNetwork { mapUi(true) } else mapUi(false)
    }
    private fun mapUi(expectFailure: Boolean) {
        guard()
        check(BuildConfig.BAIDU_MAP_CONFIGURED)
        // Refusal is real. Coordinates below are explicitly synthetic regression input, not GPS.
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION))
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION))
        val oldMode = runBlocking { access.settings().identificationMode.first() }
        val mapPrivacy = com.floralens.data.settings.BaiduMapPrivacy(context)
        val oldMapConsent = mapPrivacy.current()
        mapPrivacy.choose(com.floralens.data.settings.MapPrivacyChoice.GRANTED)
        var plantId: Long? = null
        var photo: String? = null
        val nickname = "Debug · 在线地图测试（合成坐标）"
        try {
            runBlocking {
                access.settings().setOnboarded(); access.settings().setIdentificationMode(IdentificationMode.ONLINE)
                photo = access.photos().save(context.assets.open("explore/ginkgo.jpg").use { it.readBytes() })
                plantId = access.plants().upsert(Plant(nickname = nickname, scientificName = "Ginkgo biloba", commonName = "银杏", photoUri = photo, waterIntervalDays = 7))
                val observationId = access.observations().add(Observation(plantId = plantId!!, type = ObservationType.PHOTO_OBSERVATION,
                    latitude = if (expectFailure) 37.78 else 31.2, longitude = if (expectFailure) -122.42 else 121.4,
                    photoUri = photo, note = "Debug · synthetic coordinates; licensed reference photo, not field observation"))
                com.floralens.data.location.CoordinateProvenanceStore(context).record(observationId,
                    if (expectFailure) 37.78 else 31.2, if (expectFailure) -122.42 else 121.4,
                    CoordinateSystem.WGS84)
                access.observations().add(Observation(plantId = plantId!!, type = ObservationType.NOTE, note = "Debug · no coordinates"))
                assertEquals(1, access.observations().observeMapPins().first().count { it.plantId == plantId })
            }
            ActivityScenario.launch(MainActivity::class.java).use {
                waitText("发现自然，收藏四季。")
                ui.onNodeWithText("花园").performClick(); waitText("My Garden")
                ui.onNodeWithText("观察地图").performClick(); waitText("观察地图")
                if (expectFailure) {
                    ui.waitUntil(35_000) { ui.onAllNodesWithTag("baidu-map-failed").fetchSemanticsNodes(false).isNotEmpty() }
                    ui.onNodeWithText("当前网络不可用").assertIsDisplayed()
                    screen("google-map-no-network-feedback")
                    ui.onNodeWithText("重新加载地图").performClick()
                    // Still disconnected: retry must not pretend success or discard Room data.
                    waitText("当前网络不可用")
                    screen("google-map-no-network-retry")
                    assertEquals(1, runBlocking { access.observations().observeMapPins().first().count { it.plantId == plantId } })
                    Log.i("Phase6AOnline", "GOOGLE_MAP_REAL_NETWORK_DOWN_FEEDBACK_AND_RETRY_PASS recordsPreserved=true")
                    return@use
                }
                try {
                    ui.waitUntil(90_000) { ui.onAllNodesWithTag("baidu-map-loaded").fetchSemanticsNodes(false).isNotEmpty() }
                } catch (_: androidx.compose.ui.test.ComposeTimeoutException) {
                    screen("google-map-not-loaded")
                    throw AssertionError("GOOGLE_MAP_REAL_TILES_NOT_LOADED; inspect sanitized SDK diagnostics, not credential-bearing logcat")
                }
                // The observed legacy SDK does not expose marker accessibility nodes. One
                // owned Room pin is auto-centred by the production camera fit. After its
                // 900ms camera animation, tap the visible pin body above that anchor through
                // the REAL native map, not an injected onSelect callback.
                Thread.sleep(3000)
                screen("google-map-loaded-location-denied")
                ui.onNodeWithTag("baidu-map-loaded").performTouchInput {
                    click(center + androidx.compose.ui.geometry.Offset(0f, -36f))
                }
                waitText(nickname); screen("google-map-selected-room-pin")
                ui.onNodeWithText(nickname).performClick(); waitText("我的植物档案")
                ui.onNodeWithText(nickname).assertIsDisplayed()
                screen("google-map-to-correct-archive")
                Log.i("Phase6AOnline", "GOOGLE_SDK_TILES_AND_ROOM_MARKER_NAV_PASS locationDenied=true syntheticGPS=true")
            }
        } finally {
            runBlocking {
                plantId?.let { access.plants().delete(it) }
                photo?.let { access.photos().delete(it) }
                access.settings().setIdentificationMode(oldMode)
                mapPrivacy.choose(oldMapConsent)
            }
        }
    }
}

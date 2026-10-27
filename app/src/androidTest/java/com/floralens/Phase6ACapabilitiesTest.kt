package com.floralens

import android.Manifest
import android.app.NotificationManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.domain.model.*
import com.floralens.reminders.NotificationHelper
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Explicit synthetic coordinates for regression only, never evidence of physical GPS acquisition. */
class Phase6ACapabilitiesTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val access get() = EntryPointAccessors.fromApplication(context, Phase1TestAccess::class.java)
    private fun waitText(text: String) = ui.waitUntil(20_000) {
        ui.onAllNodesWithText(text).fetchSemanticsNodes(false).isNotEmpty()
    }
    private fun deliveryEvidence(name: String) {
        if (InstrumentationRegistry.getArguments().getString("phase6cEvidence") != "true") return
        ui.waitForIdle(); Thread.sleep(700)
        instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/wildflower-phase6c/capabilities").use {
            java.io.FileInputStream(it.fileDescriptor).readBytes()
        }
        instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/wildflower-phase6c/capabilities/$name.png").use {
            java.io.FileInputStream(it.fileDescriptor).readBytes()
        }
    }
    @Test fun locatedRoomPin_opensCorrectArchive_andActualNotificationPendingIntent_opensCare() {
        val arguments = InstrumentationRegistry.getArguments()
        if (arguments.getString("verifyBaiduMaps") == "true" || arguments.getString("verifyBaiduMapsFailure") == "true") {
            Phase6AOnlineChecks(ui).baiduMap(expectFailure = arguments.getString("verifyBaiduMapsFailure") == "true")
            return
        }
        val nickname = "Debug · Phase6 地图坐标回归（非野外记录）"
        var plantId: Long? = null
        var photo: String? = null
        val originalMode = runBlocking { access.settings().identificationMode.first() }
        val manager = context.getSystemService(NotificationManager::class.java)
        try {
            runBlocking {
                access.settings().setOnboarded()
                access.settings().setIdentificationMode(IdentificationMode.OFFLINE)
                photo = access.photos().save(context.assets.open("explore/ginkgo.jpg").use { it.readBytes() })
                plantId = access.plants().upsert(Plant(nickname = nickname, scientificName = "Ginkgo biloba",
                    commonName = "银杏", photoUri = photo, waterIntervalDays = 7))
                access.observations().add(Observation(plantId = plantId!!, type = ObservationType.PHOTO_OBSERVATION,
                    photoUri = photo, latitude = 31.2, longitude = 121.4, note = "Debug · synthetic coordinates"))
                access.observations().add(Observation(plantId = plantId!!, type = ObservationType.NOTE,
                    note = "Debug · no position"))
                assertEquals(1, access.observations().observeMapPins().first().count { it.plantId == plantId })
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                waitText("发现自然，收藏四季。")
                ui.onNodeWithText("花园").performClick(); waitText("My Garden")
                ui.onNodeWithText("观察地图").performClick(); waitText("离线观察地图")
                ui.waitUntil(20_000) { ui.onAllNodesWithContentDescription(nickname).fetchSemanticsNodes(false).isNotEmpty() }
                ui.onNodeWithContentDescription(nickname).performClick(); waitText(nickname)
                deliveryEvidence("map-selected-synthetic-pin")
                ui.onNodeWithText(nickname).performClick(); waitText("我的植物档案")
                deliveryEvidence("map-correct-archive")
                ui.onNodeWithText(nickname).assertIsDisplayed()
                ui.onNodeWithText("记录本次浇水").performScrollTo().performClick()
                ui.waitUntil(10_000) { runBlocking { access.observations().observeForPlant(plantId!!).first().any { it.type == ObservationType.WATERED } } }
                instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
                ui.onNodeWithText("叶片健康检查").performScrollTo().performClick(); waitText("叶片健康检查")
                ui.waitUntil(20_000) {
                    var streaming = false
                    scenario.onActivity { activity ->
                        fun ready(view: android.view.View): Boolean = when(view) {
                            is androidx.camera.view.PreviewView -> view.previewStreamState.value == androidx.camera.view.PreviewView.StreamState.STREAMING
                            is android.view.ViewGroup -> (0 until view.childCount).any { ready(view.getChildAt(it)) }
                            else -> false
                        }
                        streaming = ready(activity.window.decorView)
                    }
                    streaming
                }
                ui.onNode(hasClickAction() and hasContentDescription("返回").not()).performClick()
                waitText("保存观察")
                ui.onNodeWithText("保存观察").performClick(); waitText("我的植物档案")
                val health = runBlocking { access.observations().observeForPlant(plantId!!).first().single { it.type == ObservationType.LEAF_CHECK } }
                assertTrue(java.io.File(java.net.URI(health.photoUri!!)).canRead())
                instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
                NotificationHelper(context).showCareReminder("Debug · Phase6 通知回归", "只验证本机养护通知跳转")
                ui.waitUntil(10_000) { manager.activeNotifications.any { n -> n.id == NotificationHelper.NOTIFICATION_ID_CARE } }
                manager.activeNotifications.single { n -> n.id == NotificationHelper.NOTIFICATION_ID_CARE }.notification.contentIntent.send()
                waitText("养护提醒")
                android.util.Log.i("Phase6AEvidence", "ROOM_MAP_PIN_NAV_PASS syntheticGPS=true CARE_WATER_PASS LEAF_REAL_CAMERA_AND_SAVE_PASS NOTIFICATION_PENDING_INTENT_PASS")
            }
        } finally {
            manager.cancel(NotificationHelper.NOTIFICATION_ID_CARE)
            runBlocking {
                plantId?.let { id ->
                    val ownedPhotos = access.observations().observeForPlant(id).first().mapNotNull { it.photoUri }.distinct()
                    access.plants().delete(id)
                    ownedPhotos.forEach { access.photos().delete(it) }
                }
                photo?.let { access.photos().delete(it) }
                access.settings().setIdentificationMode(originalMode)
            }
        }
    }
}

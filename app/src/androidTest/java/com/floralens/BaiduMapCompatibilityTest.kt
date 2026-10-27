package com.floralens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.data.location.CoordinateProvenanceStore
import com.floralens.data.settings.BaiduMapPrivacy
import com.floralens.data.settings.MapPrivacyChoice
import com.floralens.domain.model.*
import com.floralens.ui.screens.map.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BaiduMapCompatibilityTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = ins.targetContext
    private val access get() = EntryPointAccessors.fromApplication(context, Phase1TestAccess::class.java)
    private fun exists(tag: String) = ui.onAllNodesWithTag(tag).fetchSemanticsNodes(false).isNotEmpty()
    private fun click(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes(false).isNotEmpty() }
        ui.onNodeWithText(text).performClick()
    }
    @Test fun firstMapPrivacy_isSeparateFromLocation_declineGrantWithdraw_releasesMap() {
        val privacy = BaiduMapPrivacy(context); val original = privacy.current()
        val mode = runBlocking { access.settings().identificationMode.first() }
        try {
            privacy.choose(MapPrivacyChoice.PENDING)
            runBlocking { access.settings().setOnboarded(); access.settings().setIdentificationMode(IdentificationMode.ONLINE) }
            ActivityScenario.launch(MainActivity::class.java).use {
                click("花园"); click("观察地图")
                ui.waitUntil(20_000) { exists("baidu-privacy-dialog") }
                if (InstrumentationRegistry.getArguments().getString("phase6b10Evidence") == "true") {
                    ins.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/wildflower-phase6b10").close()
                    ins.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/wildflower-phase6b10/05-map-privacy.png").use {
                        java.io.FileInputStream(it.fileDescriptor).readBytes()
                    }
                }
                assertFalse(exists("baidu-sdk-ready"))
                ui.onNodeWithTag("map-privacy-decline").performClick()
                ui.waitUntil(10_000) { !exists("baidu-privacy-dialog") }
                assertEquals(MapPrivacyChoice.DECLINED, privacy.current())
                assertFalse(exists("baidu-sdk-ready")); ui.onNodeWithTag("map-local-records").assertExists()
                repeat(3) {
                    ui.onNodeWithTag("map-privacy-settings").performClick()
                    ui.onNodeWithTag("map-privacy-agree").performClick()
                    ui.waitUntil(20_000) { exists("baidu-sdk-ready") || exists("baidu-map-failed") }
                    assertEquals(MapPrivacyChoice.GRANTED, privacy.current())
                    ui.onNodeWithTag("map-privacy-settings").performClick()
                    ui.onNodeWithTag("map-privacy-decline").performClick()
                    ui.waitUntil(15_000) { !exists("baidu-sdk-ready") }
                    assertEquals(MapPrivacyChoice.DECLINED, privacy.current())
                    ui.onNodeWithTag("map-local-records").assertExists()
                    Thread.sleep(1_000) // include asynchronous native teardown, not just Compose removal
                }
            }
        } finally { privacy.choose(original); runBlocking { access.settings().setIdentificationMode(mode) } }
    }

    @Test fun actualSdkCoordinateConversion_andProvenance_doNotRewriteRoom() {
        val privacy = BaiduMapPrivacy(context); val previous = privacy.current()
        val sources = CoordinateProvenanceStore(context)
        val ownedId = -System.nanoTime()
        val before = runBlocking { access.observations().observeMapPins().first() }
        try {
            privacy.choose(MapPrivacyChoice.GRANTED)
            ins.runOnMainSync { BaiduMapRuntime.initialize(context, privacy.current() == MapPrivacyChoice.GRANTED) }
            ui.waitUntil(15_000) { BaiduMapRuntime.ready.value }
            val mask = MapRegionMask(context)
            assertEquals(CoordinateRegion.DOMESTIC, mask.classify(31.2, 121.4))
            assertEquals(CoordinateRegion.OVERSEAS, mask.classify(37.56, 126.97))
            assertEquals(CoordinateRegion.OVERSEAS, mask.classify(35.68, 139.69))
            assertEquals(CoordinateRegion.OVERSEAS, mask.classify(51.5, -.12))
            val gps = baiduCoordinate(31.2, 121.4, CoordinateSystem.WGS84, mask)!!
            val gcj = baiduCoordinate(31.2, 121.4, CoordinateSystem.GCJ02, mask)!!
            assertTrue(kotlin.math.abs(gps.longitude - 121.4) > .001)
            assertTrue(kotlin.math.abs(gcj.longitude - 121.4) > .001)
            assertTrue(kotlin.math.abs(gps.longitude - gcj.longitude) > .0001)
            val bd = baiduCoordinate(gps.latitude, gps.longitude, CoordinateSystem.BD09LL, mask)!!
            assertEquals(gps.latitude, bd.latitude, 0.0); assertEquals(gps.longitude, bd.longitude, 0.0)
            assertEquals(37.56, baiduCoordinate(37.56, 126.97, CoordinateSystem.WGS84, mask)!!.latitude, 0.0)
            assertNull(baiduCoordinate(31.2, 121.4, CoordinateSystem.UNKNOWN, mask))
            assertEquals(CoordinateSystem.UNKNOWN, sources.source(ownedId, 31.2, 121.4))
            sources.record(ownedId, 31.2, 121.4, CoordinateSystem.WGS84)
            assertEquals(CoordinateSystem.WGS84, sources.source(ownedId, 31.2, 121.4))
            assertEquals(CoordinateSystem.UNKNOWN, sources.source(ownedId, 31.201, 121.4))
            assertEquals(before, runBlocking { access.observations().observeMapPins().first() })
        } finally { sources.remove(ownedId); privacy.choose(previous); if(previous != MapPrivacyChoice.GRANTED) BaiduMapRuntime.revokeIfStarted(context) }
    }
}

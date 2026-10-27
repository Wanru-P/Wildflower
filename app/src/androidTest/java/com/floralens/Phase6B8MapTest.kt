package com.floralens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.InputDevice
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import com.floralens.domain.model.*
import com.floralens.ui.screens.map.ObservationMapProjection
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.model.LatLng
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** Dedicated AVD only. Reference photography and synthetic locations are explicitly distinct. */
class Phase6B8MapTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = ins.targetContext
    private val access get() = EntryPointAccessors.fromApplication(context, Phase1TestAccess::class.java)
    private val args get() = InstrumentationRegistry.getArguments()
    private fun shell(command: String) = ins.uiAutomation.executeShellCommand(command).use { FileInputStream(it.fileDescriptor).bufferedReader().readText() }
    private fun exists(tag: String) = ui.onAllNodesWithTag(tag).fetchSemanticsNodes(false).isNotEmpty()
    private fun waitTag(tag: String) {
        try { ui.waitUntil(30_000) { exists(tag) } }
        catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
            shell("screencap -p /sdcard/Download/phase6b8-internal-failure.png")
            android.util.Log.e("Phase6B8", "Missing tag $tag\n${ui.onRoot(useUnmergedTree = true).printToString()}")
            throw failure
        }
    }
    private fun waitText(text: String) = ui.waitUntil(25_000) { ui.onAllNodesWithText(text, substring = true).fetchSemanticsNodes(false).isNotEmpty() }
    private fun click(text: String) {
        waitText(text); val n = ui.onNodeWithText(text)
        try { n.performScrollTo() } catch (_: AssertionError) { }
        n.performClick()
    }
    private fun back() { Espresso.pressBack(); ui.waitForIdle() }
    private fun shot(name: String) {
        if (args.getString("phase6b8Evidence") != "true" && args.getString("phase6b10Evidence") != "true") return
        ui.waitForIdle(); Thread.sleep(800)
        val folder = if (args.getString("phase6b10Evidence") == "true") "wildflower-phase6b10" else "wildflower-phase6b8"
        shell("mkdir -p /sdcard/Download/$folder")
        shell("screencap -p /sdcard/Download/$folder/$name.png")
    }
    private fun guard() {
        check(shell("getprop ro.boot.qemu.avd_name").trim() == "Wildflower_Phase6B7_API35")
        args.getString("recoverMapFixturesSince")?.toLongOrNull()?.let { since -> runBlocking {
            // Explicit recovery of this test's receipt-tagged rows after a native SDK process crash.
            val db = access.database()
            val owned = db.openHelper.readableDatabase.query("SELECT DISTINCT p.id FROM plants p JOIN photo_operations o ON p.id=o.plantId WHERE o.operationId LIKE 'phase6b8-%' AND p.createdAtEpochMs>=?", arrayOf(since)).use { cursor ->
                buildList { while(cursor.moveToNext()) add(cursor.getLong(0)) }
            }
            cleanup(Fixture(plants = owned.toMutableList()))
            com.floralens.data.settings.BaiduMapPrivacy(context).choose(com.floralens.data.settings.MapPrivacyChoice.PENDING)
        } }
        check(runBlocking { access.plants().observeAll().first().isEmpty() }) { "Never clear a user's collection" }
        assertFalse(BuildConfig.SEED_DEMO); assertFalse(BuildConfig.INTERNAL_TOOLS)
    }
    private class Fixture(val plants: MutableList<Long> = mutableListOf(), val observations: MutableList<Long> = mutableListOf())
    private val credit = "地图标记功能演示：位置为测试坐标，不是设备 GPS，也不是摄影拍摄地点。\n摄影：Krzysztof Ziarnek, Kenraiz\n照片来源：https://commons.wikimedia.org/wiki/File:Osmanthus_fragrans_kz1.jpg\n许可：CC BY-SA 4.0；演示日期是归档日期，不是原图拍摄日期。"
    private fun seed(f: Fixture, count: Int = 2) = runBlocking {
        access.settings().setOnboarded()
        repeat(count) { index ->
            val p = access.archive().collect("phase6b8-${UUID.randomUUID()}", Species.identified("Osmanthus fragrans", "桂花"),
                if (index == 0) "自然观察演示记录" else "桂花参考摄影档案", null, null)
            f.plants += p
            val capture = access.captures().create(context.assets.open("explore/osmanthus_kenraiz.jpg").use { it.readBytes() })
            access.archive().add(p, capture.id, 1_792_008_000_000L + index * 86_400_000L, credit)
            f.observations += access.database().archiveDao().history(p).single().id
            // Reference-photo archive dates are not proof of a captured place.
            locate(f.observations.last(), null, null)
        }
    }
    private fun locate(id: Long, lat: Double?, lon: Double?) = runBlocking {
        // Only this test's owned row. No production coordinate editor or demo injection is added.
        access.database().openHelper.writableDatabase.execSQL("UPDATE observations SET latitude=?, longitude=? WHERE id=?", arrayOf<Any?>(lat, lon, id))
        if (lat != null && lon != null) com.floralens.data.location.CoordinateProvenanceStore(context).record(id, lat, lon, CoordinateSystem.WGS84)
        access.database().invalidationTracker.refreshVersionsAsync()
    }
    private fun cleanup(f: Fixture) = runBlocking {
        f.plants.forEach { p -> access.database().archiveDao().history(p).forEach { access.archive().delete(p, it.id) }; access.plants().delete(p) }
    }
    private fun projection() = runBlocking { ObservationMapProjection.join(access.observations().observeMapPins().first(), access.plants().observeAll().first()) }
    private fun enterMap() { waitText("发现自然，收藏四季。"); click("花园"); waitText("My Garden"); click("观察地图"); waitTag("map-local-records") }
    private fun selectedName(name: String) = ui.onNode(hasText(name) and hasAnyAncestor(hasTestTag("map-selected-record")), useUnmergedTree = true)
    private fun selectLocal(id: Long) {
        ui.onNodeWithTag("map-local-records").performClick(); waitTag("map-record-$id")
        ui.onNodeWithTag("map-record-$id").performClick(); waitTag("map-selected-record")
    }
    private fun baidu(scenario: ActivityScenario<MainActivity>): BaiduMap {
        val value = AtomicReference<BaiduMap>()
        scenario.onActivity { activity ->
            fun find(v: View): MapView? = when (v) {
                is MapView -> v
                is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { find(v.getChildAt(it)) }
                else -> null
            }
            value.set(requireNotNull(find(activity.window.decorView)).map)
        }
        ui.waitUntil(10_000) { value.get() != null }; return value.get()
    }
    private fun tiles() {
        // Requires actual first tile + loaded callbacks AND successful SDK authentication.
        ui.waitUntil(90_000) { exists("baidu-map-loaded") && !exists("baidu-map-failed") }; Thread.sleep(1500)
    }
    private fun nativeMarker(scenario: ActivityScenario<MainActivity>) {
        val map = baidu(scenario)
        val origin = IntArray(2)
        scenario.onActivity { activity ->
            fun find(v: View): MapView? = when (v) {
                is MapView -> v
                is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { find(v.getChildAt(it)) }
                else -> null
            }
            requireNotNull(find(activity.window.decorView)).getLocationOnScreen(origin)
        }
        // Target the pin's round body, not its thin pointed anchor. Its bitmap
        // scales with display density and can settle a frame after camera fit.
        for (bodyDp in listOf(24f, 30f, 18f)) {
            var point = android.graphics.Point()
            ins.runOnMainSync { point = map.projection.toScreenLocation(com.baidu.mapapi.utils.CoordinateConverter()
                .from(com.baidu.mapapi.utils.CoordinateConverter.CoordType.GPS).coord(LatLng(31.2, 121.4)).convert()) }
            val now = SystemClock.uptimeMillis()
            val x = (origin[0] + point.x).toFloat(); val y = origin[1] + point.y - bodyDp * context.resources.displayMetrics.density
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
                assertTrue(ins.uiAutomation.injectInputEvent(event, true)); event.recycle(); Thread.sleep(80)
            }
            try { ui.waitUntil(3_000) { exists("map-selected-record") }; return }
            catch (_: androidx.compose.ui.test.ComposeTimeoutException) { /* next real hitbox position */ }
        }
        waitTag("map-selected-record")
    }
    private fun gestures(scenario: ActivityScenario<MainActivity>) {
        val map = baidu(scenario)
        var before = LatLng(0.0, 0.0); var zoom = 0f
        ins.runOnMainSync { before = map.mapStatus.target; zoom = map.mapStatus.zoom }
        ui.onNodeWithTag("baidu-map-loaded").performTouchInput { swipe(center, center + Offset(150f, 120f), 600) }
        Thread.sleep(1200)
        ins.runOnMainSync { assertTrue("Real native pan must change camera target",
            kotlin.math.abs(before.latitude - map.mapStatus.target.latitude) + kotlin.math.abs(before.longitude - map.mapStatus.target.longitude) > .00001) }
        // Inject actual multi-pointer MotionEvents: Compose's batched pinch is
        // not reliably forwarded by AndroidView to this native SDK renderer.
        var centerX = 0f; var centerY = 0f
        scenario.onActivity { activity ->
            fun find(v: View): MapView? = when(v) { is MapView -> v; is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { find(v.getChildAt(it)) }; else -> null }
            val native = requireNotNull(find(activity.window.decorView))
            val origin = IntArray(2); native.getLocationOnScreen(origin)
            centerX = origin[0] + native.width / 2f; centerY = origin[1] + native.height / 2f
            zoom = map.mapStatus.zoom
            assertTrue(map.uiSettings.isScrollGesturesEnabled)
        }
        val down = SystemClock.uptimeMillis()
        val props = Array(2) { index -> MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        fun send(action: Int, spread: Float, count: Int = 2) {
            val coords = Array(count) { index -> MotionEvent.PointerCoords().apply {
                x = centerX + if (index == 0) -spread else spread
                y = centerY + if (index == 0) -spread * .65f else spread * .65f
                pressure = 1f; size = 1f
            } }
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, count, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            assertTrue(ins.uiAutomation.injectInputEvent(event, true)); event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, 90f, 1)
        Thread.sleep(80)
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 90f)
        Thread.sleep(80)
        repeat(24) { step -> send(MotionEvent.ACTION_MOVE, 90f + 10f * step); Thread.sleep(35) }
        send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 320f)
        send(MotionEvent.ACTION_UP, 320f, 1)
        ins.runOnMainSync { android.util.Log.i("Phase6B8", "PINCH initialZoom=$zoom currentZoom=${map.mapStatus.zoom} center=$centerX,$centerY") }
        var changed = false
        ui.waitUntil(8_000) {
            ins.runOnMainSync { changed = kotlin.math.abs(zoom - map.mapStatus.zoom) > .1f }
            changed
        }
        assertTrue("Real native pinch must change SDK zoom", changed)
        ui.onNodeWithTag("map-fit-all").performClick(); Thread.sleep(1500)
    }

    private fun nativeButton(resource: String) {
        var node: AccessibilityNodeInfo? = null
        ui.waitUntil(15_000) {
            val ids = if (resource == "permission_deny_button") listOf(resource, "permission_deny_and_dont_ask_again_button") else listOf(resource)
            node = ids.firstNotNullOfOrNull { ins.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/$it")?.firstOrNull() }
            node != null
        }
        check(node!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    @Test fun optionalLocation_denial_settingsReturn_approximate_precise_noFix_neverWritesCoordinates() {
        guard(); val f = Fixture(); val mode = runBlocking { access.settings().identificationMode.first() }
        val enabled = shell("cmd location is-location-enabled").trim()
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            seed(f, 1); locate(f.observations.single(), 31.2, 121.4)
            runBlocking { access.settings().setIdentificationMode(IdentificationMode.OFFLINE) }
            val original = runBlocking { access.database().archiveDao().observation(f.observations.single())!! }
            scenario = ActivityScenario.launch(MainActivity::class.java); enterMap()
            if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ui.onNodeWithTag("map-permission-status").assertTextEquals("未开启定位 · 可浏览已有地点")
                assertNull(com.floralens.data.location.SystemLocationProvider(context).lastKnownLocation())
                click("位置权限"); click("允许位置权限")
                if (args.getString("locationPermanentlyDenied") != "true") {
                    nativeButton("permission_deny_button"); waitTag("map-permission-status")
                    ui.onNodeWithTag("map-local-records").assertIsEnabled()
                    click("位置权限"); click("允许位置权限"); nativeButton("permission_deny_button")
                    waitTag("map-permission-status"); click("位置权限")
                } else { waitTag("map-permission-status"); click("位置权限") }
                click("前往系统设置")
                ui.waitUntil(10_000) { shell("dumpsys activity activities").contains("com.android.settings/") }
                ins.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
                shell("input keyevent 4"); waitTag("map-permission-status")
                ui.waitUntil(15_000) { ui.onAllNodesWithText("大致位置已允许").fetchSemanticsNodes(false).isNotEmpty() }
            }
            ins.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
            // The UI must re-read permissions on real foreground return.
            shell("input keyevent 3")
            ui.waitUntil(10_000) { var stopped = false; scenario.onActivity { stopped = !it.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }; stopped }
            context.startActivity(Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP })
            ui.waitUntil(10_000) { var resumed = false; scenario.onActivity { resumed = it.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }; resumed }
            waitText("精确位置已允许")
            shell("cmd location set-location-enabled false")
            assertNull(com.floralens.data.location.SystemLocationProvider(context).lastKnownLocation())
            ui.onNodeWithTag("map-local-records").assertIsEnabled(); selectLocal(f.observations.single())
            selectedName("自然观察演示记录").assertExists()
            assertEquals(original, runBlocking { access.database().archiveDao().observation(f.observations.single()) })
            android.util.Log.i("Phase6B8", "MAP_OPTIONAL_PERMISSION_DENIAL_SETTINGS_APPROXIMATE_PRECISE_NO_FIX_PASS noLocationWrites=true")
        } finally {
            scenario?.close(); if (enabled in setOf("true", "false")) shell("cmd location set-location-enabled $enabled")
            cleanup(f); runBlocking { access.settings().setIdentificationMode(mode) }
        }
    }

    @Test fun locatedRecords_nativeOrOfflineMarkers_liveRoomChanges_navigationAndNetworkRecovery() {
        guard(); val f = Fixture(); val live = args.getString("mapsLive") == "true"
        val oldMode = runBlocking { access.settings().identificationMode.first() }
        val privacy = com.floralens.data.settings.BaiduMapPrivacy(context)
        val oldConsent = privacy.current()
        val oldSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        val oldDensity = Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1) ?: "reset"
        val oldFont = shell("settings get system font_scale").trim().takeIf { it.toFloatOrNull() != null } ?: "1.0"
        val wifi = shell("settings get global wifi_on").trim(); val data = shell("settings get global mobile_data").trim()
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            seed(f); f.observations.forEach { locate(it, 31.2, 121.4) }
            val originals = runBlocking { f.observations.associateWith { access.database().archiveDao().observation(it)!! } }
            runBlocking { access.settings().setIdentificationMode(if (live) IdentificationMode.ONLINE else IdentificationMode.OFFLINE) }
            if (live) privacy.choose(com.floralens.data.settings.MapPrivacyChoice.GRANTED)
            shell("wm size 1080x2340"); shell("wm density 440"); shell("settings put system font_scale 1.0")
            scenario = ActivityScenario.launch(MainActivity::class.java); enterMap()
            ui.waitUntil(20_000) { projection().pins.size == 2 }
            if (live) {
                tiles(); shot("01-map-online"); shot("02-map-marker"); gestures(scenario)
                nativeMarker(scenario)
            } else { ui.onNodeWithTag("map-pin-${f.observations.first()}").performClick(); waitTag("map-selected-record") }
            ui.onNodeWithTag("map-nearby-next").assertIsDisplayed().performClick()
            // Exact overlaps are not collapsed: local list guarantees both archives remain reachable.
            click("收起"); selectLocal(f.observations.first()); selectedName("自然观察演示记录").assertExists()
            shot("03-map-selected-plant")
            ui.onNodeWithTag("map-selected-record").performScrollTo().performClick(); waitText("我的植物档案")
            ui.onNodeWithText("自然观察演示记录").assertExists(); shot("04-map-plant-detail")
            back(); waitTag("map-selected-record"); selectedName("自然观察演示记录").assertExists()
            click("收起"); selectLocal(f.observations.last()); selectedName("桂花参考摄影档案").assertExists()
            ui.onNodeWithTag("map-selected-record").performScrollTo().performClick(); waitText("我的植物档案")
            ui.onNodeWithText("桂花参考摄影档案").assertExists(); back(); waitTag("map-selected-record")
            runBlocking { access.database().archiveDao().nickname(f.plants.last(), "桂花参考摄影档案 · 复访") }
            waitText("桂花参考摄影档案 · 复访")
            runBlocking { access.archive().edit(f.plants.last(), f.observations.last(), 1_792_180_800_000L, "$credit\n观察资料复访归档。") }
            ui.waitUntil(15_000) { projection().pins.first().note!!.endsWith("观察资料复访归档。") }
            locate(f.observations.last(), 31.201, 121.401)
            ui.waitUntil(15_000) { projection().pins.first { it.observationId == f.observations.last() }.latitude == 31.201 }
            assertEquals(originals[f.observations.last()]!!.photoUri, projection().pins.first { it.observationId == f.observations.last() }.displayPhotoUri)
            locate(f.observations.last(), 91.0, 121.4); waitTag("map-invalid-locations")
            ui.waitUntil(15_000) { !exists("map-selected-record") }
            locate(f.observations.last(), null, null); ui.waitUntil(15_000) { projection().pins.size == 1 }
            selectLocal(f.observations.first()); runBlocking { access.archive().delete(f.plants.first(), f.observations.first()) }
            waitTag("map-no-location"); ui.onNodeWithTag("map-local-records").assertIsNotEnabled(); shot("05-map-no-location")
            assertFalse(exists("map-selected-record"))
            // A retained, no-location reference photo is still present and untouched.
            val kept = runBlocking { access.database().archiveDao().observation(f.observations.last())!! }
            assertEquals(originals[f.observations.last()]!!.photoUri, kept.photoUri); assertTrue(File(URI(kept.photoUri!!)).canRead())
            locate(f.observations.last(), 31.2, 121.4); ui.waitUntil(15_000) { projection().pins.size == 1 }
            if (live) {
                tiles(); shell("svc wifi disable"); shell("svc data disable")
                waitText("当前网络不可用"); ui.onNodeWithText("重新加载地图").performClick()
                waitText("当前网络不可用"); selectLocal(f.observations.last()); selectedName("桂花参考摄影档案 · 复访").assertExists(); click("收起")
                shell("wm size 880x1904"); shell("settings put system font_scale 1.3"); scenario.recreate()
                waitText("当前网络不可用"); ui.onNodeWithTag("map-local-records").assertIsDisplayed(); shot("06-map-network-error")
                shell("svc wifi enable"); shell("svc data enable"); tiles()
                shell("wm size 1080x2340"); shell("settings put system font_scale 1.0"); scenario.recreate(); tiles()
                // An actual background return, not replacement by a fake map composable.
                shell("input keyevent 3"); Thread.sleep(700)
                context.startActivity(Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP })
                tiles()
            } else { scenario.recreate(); waitTag("map-local-records") }
            repeat(2) { back(); waitText("My Garden"); click("观察地图"); waitTag("map-local-records"); if (live) tiles() }
            back(); waitText("My Garden"); click("发现"); waitText("发现自然，收藏四季。"); click("花园"); click("观察地图")
            waitTag("map-local-records"); if (live) tiles()
            assertEquals(1, projection().pins.size)
            android.util.Log.i("Phase6B8", "MAP_ROOM_LINK_EDIT_DELETE_REENTRY_PASS liveSdk=$live syntheticLocations=true")
        } finally {
            scenario?.close(); shell("svc wifi ${if (wifi == "1") "enable" else "disable"}"); shell("svc data ${if (data == "1") "enable" else "disable"}")
            shell("wm size $oldSize"); shell("wm density $oldDensity"); shell("settings put system font_scale $oldFont")
            cleanup(f); runBlocking { access.settings().setIdentificationMode(oldMode) }
            privacy.choose(oldConsent)
        }
    }

    @Test fun photoCredit_fullRawSourceAndLicense_scrollAt320_font130() {
        guard(); val f = Fixture(); val mode = runBlocking { access.settings().identificationMode.first() }
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            seed(f, 1); runBlocking { access.settings().setIdentificationMode(IdentificationMode.OFFLINE) }
            val original = runBlocking { access.database().archiveDao().observation(f.observations.single())!! }
            shell("wm size 880x1904"); shell("wm density 440"); shell("settings put system font_scale 1.3")
            scenario = ActivityScenario.launch(MainActivity::class.java)
            waitText("发现自然，收藏四季。"); click("花园"); click("自然观察演示记录"); click("查看全部照片")
            waitText("1 张照片 · 按观察时间排序"); ui.onNodeWithContentDescription("查看观察 ${f.observations.single()}").performClick()
            waitTag("photo-detail-scroll")
            ui.onNodeWithText("许可：CC BY-SA 4.0；演示日期是归档日期，不是原图拍摄日期。").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("设为植物封面").performScrollTo().assertIsDisplayed()
            shot("07-photo-detail-320")
            ui.onNodeWithText("删除这条观察").performScrollTo().assertIsDisplayed()
            click("关闭照片"); waitText("观察照片集"); back(); waitText("我的植物档案")
            assertEquals(original, runBlocking { access.database().archiveDao().observation(f.observations.single()) })
        } finally {
            scenario?.close(); shell("wm size 1080x2340"); shell("wm density 440"); shell("settings put system font_scale 1.0")
            cleanup(f); runBlocking { access.settings().setIdentificationMode(mode) }
        }
    }
}

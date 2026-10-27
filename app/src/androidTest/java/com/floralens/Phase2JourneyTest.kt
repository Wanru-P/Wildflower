package com.floralens

import android.Manifest
import android.content.ContentValues
import android.graphics.Rect
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.domain.model.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.URI

/** Real CameraX, system TakePicture, Photo Picker, navigation, Room and private storage. Dedicated AVD only. */
class Phase2JourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val access get() = EntryPointAccessors.fromApplication(context, Phase1TestAccess::class.java)
    private val nickname = "Debug · 持续观察测试"
    // System Photo Picker/Camera temporarily owns the window. Keep polling until the app returns;
    // an empty hierarchy is not success and the original 30s deadline/assertions remain unchanged.
    private fun waitText(text: String) = ui.waitUntil(30_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes(false).isNotEmpty() }
    private fun click(text: String) {
        waitText(text)
        val node = ui.onNodeWithText(text)
        try { node.performScrollTo() } catch(_: AssertionError) { /* Top-level navigation has no scrolling ancestor. */ }
        node.assertIsDisplayed().performClick()
    }
    private fun back() { Espresso.pressBack(); ui.waitForIdle() }
    private fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).use { java.io.FileInputStream(it.fileDescriptor).readBytes() } }
    private fun screenshot(name: String) {
        ui.waitForIdle(); Thread.sleep(700)
        val directory = if (InstrumentationRegistry.getArguments().getString("phase6cEvidence") == "true")
            "wildflower-phase6c/real-flow" else "wildflower-phase2"
        shell("mkdir -p /sdcard/Download/$directory")
        shell("screencap -p /sdcard/Download/$directory/$name.png")
    }
    private fun findNative(node: AccessibilityNodeInfo?, matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if(node == null) return null
        if(matches(node)) return node
        for(i in 0 until node.childCount) findNative(node.getChild(i), matches)?.let { return it }
        return null
    }
    private fun nativeClick(scroll: Boolean = false, matches: (AccessibilityNodeInfo) -> Boolean) {
        var target: AccessibilityNodeInfo? = null
        val end = System.currentTimeMillis() + 30_000
        while(target == null && System.currentTimeMillis() < end) {
            target = findNative(instrumentation.uiAutomation.rootInActiveWindow) { it.isEnabled && it.isVisibleToUser && matches(it) }
            if(target == null) {
                if(scroll) findNative(instrumentation.uiAutomation.rootInActiveWindow) {
                    it.viewIdResourceName == "android:id/resolver_list"
                }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                Thread.sleep(if(scroll) 500 else 250)
            }
        }
        if (target == null) {
            fun dump(node: AccessibilityNodeInfo?) {
                if (node == null) return
                android.util.Log.e("Phase2Native", "text=${node.text} description=${node.contentDescription} resource=${node.viewIdResourceName}")
                for (i in 0 until node.childCount) dump(node.getChild(i))
            }
            dump(instrumentation.uiAutomation.rootInActiveWindow)
            shell("screencap -p /sdcard/Download/wildflower-phase2/native-control-failure.png")
        }
        val node = requireNotNull(target) { "Native activity control not found" }
        // Stock Camera exposes an enabled shutter before StateReadyForCapture / first preview frame.
        if(node.viewIdResourceName == "com.android.camera2:id/shutter_button") Thread.sleep(1800)
        // Picker sheet expansion moves screen coordinates between lookup and tap. Use the
        // clickable accessibility ancestor (e.g. Albums tab / photo tile) wherever supported.
        var control: AccessibilityNodeInfo? = node
        while (control != null && !control.isClickable) control = control.parent
        if (control?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
        Thread.sleep(600)
        val settled = findNative(instrumentation.uiAutomation.rootInActiveWindow) { it.isEnabled && it.isVisibleToUser && matches(it) } ?: node
        val bounds = Rect(); settled.getBoundsInScreen(bounds)
        shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
    }
    private fun field(label: String, value: String, scenario: ActivityScenario<MainActivity>) {
        ui.onNodeWithText(label).performScrollTo().performTextReplacement(value)
        scenario.onActivity { it.window.insetsController?.hide(android.view.WindowInsets.Type.ime()) }
        ui.waitForIdle()
    }
    private fun cancelCreateDocument() {
        // Compose's click can finish before the system picker owns the window. A premature
        // Back is delivered to the editor (or only hides the filename keyboard), not CANCEL.
        fun pickerVisible() = findNative(instrumentation.uiAutomation.rootInActiveWindow) {
            it.viewIdResourceName == "android:id/button1" && it.text?.toString() == "SAVE"
        } != null
        ui.waitUntil(20_000) { pickerVisible() }
        shell("input keyevent 4")
        val end = android.os.SystemClock.elapsedRealtime() + 2_000
        while (pickerVisible() && android.os.SystemClock.elapsedRealtime() < end) Thread.sleep(100)
        if (pickerVisible()) shell("input keyevent 4") // keyboard consumed the first Back
        waitText("已取消保存，观察照片未改变")
    }
    private fun history(id: Long) = runBlocking { access.database().archiveDao().history(id) }

    @Test fun photograph_collect_camera_picker_edit_cover_andRelaunch() {
        val onlineArguments = InstrumentationRegistry.getArguments()
        if (onlineArguments.getString("verifyOnlineCamera") == "true" || onlineArguments.getString("verifyNetworkFallback") == "true" || onlineArguments.getString("verifyChatEntry") == "true") {
            Phase6AOnlineChecks(ui).onlineCamera(noNetwork = onlineArguments.getString("verifyNetworkFallback") == "true",
                verifyChatEntry = onlineArguments.getString("verifyChatEntry") == "true")
            return
        }
        if(InstrumentationRegistry.getArguments().getString("verifyColdPhase2") == "true") { verifyAfterProcessRestart(); return }
        runBlocking {
            check(access.plants().observeAll().first().isEmpty()) { "Dedicated empty test AVD required; never clear user records" }
            access.settings().setOnboarded()
            access.captures().latest()?.let { access.captures().cancel(it.id) }
        }
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        var scenario = ActivityScenario.launch(MainActivity::class.java)
        var plantId: Long? = null
        var pickerUri: android.net.Uri? = null
        var captureId: String? = null
        var completed = false
        var modelFixture = false
        try {
            waitText("发现自然，收藏四季。")
            click("识别")
            waitText("离线 · 本机识别")
            // PreviewView must be STREAMING before the actual shutter is used.
            ui.waitUntil(20_000) {
                var ready = false
                scenario.onActivity { activity ->
                    fun stream(view: android.view.View): Boolean = when(view) {
                        is androidx.camera.view.PreviewView -> view.previewStreamState.value == androidx.camera.view.PreviewView.StreamState.STREAMING
                        is android.view.ViewGroup -> (0 until view.childCount).any { stream(view.getChildAt(it)) }
                        else -> false
                    }
                    ready = stream(activity.window.decorView)
                }; ready
            }
            screenshot("scan-camera")
            ui.onNodeWithContentDescription("拍摄植物").performClick()
            ui.waitUntil(40_000) { listOf("查看植物详情", "暂未发现植物").any { ui.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() } }
            val shot = runBlocking { requireNotNull(access.captures().latest()) }
            captureId = shot.id
            assertTrue(File(URI(shot.photoUri)).isFile)
            screenshot("identification-result")
            if(shot.result == IdResult.NoPlantDetected) {
                check(InstrumentationRegistry.getArguments().getString("phase6aStrict") != "true") {
                    "REAL_CAMERA_MODEL_NO_PLANT: CameraX captured the emulator scene, but real TFLite returned no candidate; no fixture substitution permitted"
                }
                modelFixture = true
                runBlocking { access.captures().result(shot.id, IdResult.HighConfidence(
                    IdCandidate("Ginkgo biloba", "Debug 测试输入 · 银杏", .91f), emptyList())) }
                scenario.close(); scenario = ActivityScenario.launch(MainActivity::class.java)
                waitText("发现自然，收藏四季。"); click("识别"); waitText("查看植物详情")
                screenshot("identification-result-fixture")
            }
            click("查看植物详情"); waitText("植物笔记")
            assertEquals(0, runBlocking { access.database().plantDao().count() })
            screenshot("species-before-save")
            // Culture must retain this actual result's identity without collecting it.
            val detailsCapture = runBlocking { requireNotNull(access.captures().get(shot.id)) }
            val selectedSpecies = requireNotNull(detailsCapture.selected)
            val cultureIdentity = com.floralens.domain.culture.CultureIdentityResolver.resolve(selectedSpecies.key,
                selectedSpecies.scientificName, certain = detailsCapture.result !is IdResult.LowConfidence)
            click("阅读植物文化 →"); waitText("植物文化")
            ui.scrollCultureTo("nature-text")
            ui.onNodeWithTag("nature-text").assertTextEquals(runBlocking {
                com.floralens.data.culture.NatureExpressionRepository(context,
                    com.floralens.data.culture.NatureServiceConfig(context)).current(cultureIdentity).text })
            val reading = runBlocking { if (com.floralens.domain.culture.CultureIdentificationPolicy.certain(detailsCapture.result, selectedSpecies.key))
                access.culture().readingContent(selectedSpecies.key) else null }
            val work = reading?.let(com.floralens.data.culture.CulturePolicy::readingWorks)?.firstOrNull()
            if (work != null) {
                ui.onNodeWithTag("culture-list").performScrollToNode(hasTestTag("culture-excerpt"))
                ui.onNodeWithTag("culture-excerpt").assertTextEquals(work.excerpt!!)
            } else ui.onAllNodesWithTag("culture-excerpt").assertCountEquals(0)
            assertEquals(0, runBlocking { access.database().plantDao().count() })
            back(); waitText("植物笔记")
            click("收藏这株植物"); waitText("收藏确认")
            ui.onNodeWithText("植物昵称").performTextReplacement(nickname)
            scenario.onActivity { it.window.insetsController?.hide(android.view.WindowInsets.Type.ime()) }
            screenshot("collect-confirmation")
            assertEquals(0, runBlocking { access.database().plantDao().count() })
            click("确认收藏"); waitText("我的植物档案")
            ui.waitUntil(15_000) { runBlocking { access.database().plantDao().count() } == 1 }
            plantId = runBlocking { access.plants().observeAll().first().single().id }
            val id = plantId!!; val first = history(id).single()
            assertEquals("IDENTIFIED", first.type); assertNotNull(first.identificationJson)
            assertTrue(File(URI(first.photoUri)).isFile)
            click("查看全部照片"); ui.onNodeWithContentDescription("查看观察 ${first.id}").performClick()
            click("编辑日期与备注"); waitText("编辑观察")
            field("观察备注", "Debug · CameraX 实拍首次记录（模拟器场景，仅测试工程链路）", scenario)
            click("保存观察"); waitText("观察照片集")
            assertEquals("A note-only edit must preserve the full original timestamp", first.timestampEpochMs, history(id).single().timestampEpochMs)
            back(); waitText("我的植物档案")
            click("添加观察"); click("拍摄新照片")
            // IDs observed on this API 35 stock Camera app, not guessed screen coordinates.
            nativeClick { it.viewIdResourceName == "com.android.camera2:id/shutter_button" }
            nativeClick { it.viewIdResourceName == "com.android.camera2:id/done_button" }
            waitText("保存观察")
            ui.waitUntil(15_000) { ui.onAllNodesWithText("保存观察").fetchSemanticsNodes().any { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not() } }
            field("观察日期 YYYY-MM-DD", "2026-02-30", scenario)
            click("保存观察"); waitText("请输入有效日期 YYYY-MM-DD 与时间 HH:mm")
            assertEquals(1, history(id).size)
            field("观察日期 YYYY-MM-DD", java.time.LocalDate.now().toString(), scenario)
            field("观察备注", "Debug · 系统相机第二次观察（模拟器虚拟场景）", scenario)
            screenshot("observation-editor-camera")
            click("保存观察"); waitText("我的植物档案")
            ui.waitUntil(15_000) { history(id).size == 2 }
            val second = history(id).first { it.id != first.id }
            assertEquals("PHOTO_OBSERVATION", second.type); assertNull(second.identificationJson)
            assertNotEquals(first.photoUri, second.photoUri)

            val reference = context.assets.open("explore/ginkgo.jpg").use { it.readBytes() }
            val pickerAlbum = "Debug-P2-${java.util.UUID.randomUUID().toString().take(8)}"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Debug-phase2-authorized-ginkgo.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$pickerAlbum/")
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                put(MediaStore.Images.Media.IS_FAVORITE, 1)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            pickerUri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
            context.contentResolver.openOutputStream(pickerUri!!)!!.use { it.write(reference) }
            context.contentResolver.update(pickerUri!!, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0); put(MediaStore.Images.Media.IS_FAVORITE, 1)
            }, null, null)
            context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.IS_FAVORITE}=1", null, null)!!.use { cursor ->
                assertEquals("Dedicated test device must have just our owned favorite", 1, cursor.count)
                assertTrue(cursor.moveToFirst()); assertEquals(android.content.ContentUris.parseId(pickerUri!!), cursor.getLong(0))
            }
            click("添加观察"); click("从相册选择照片")
            // Select our sole owned favorite via the REAL system picker. "Newest" is not
            // deterministic, and this API35 picker exposes predefined local categories only.
            nativeClick { it.text?.toString() == "Albums" }
            nativeClick { it.text?.toString() in setOf("Favorites", "Favourites") }
            shell("screencap -p /sdcard/Download/wildflower-phase2/picker-owned-favorite.png")
            nativeClick { it.isClickable && it.contentDescription?.toString()?.startsWith("Photo taken on") == true }
            waitText("保存观察")
            ui.waitUntil(15_000) { runBlocking { access.captures().get(captureId!!) } == null && ui.onNodeWithText("保存观察").isEnabled() }
            field("观察日期 YYYY-MM-DD", "2026-10-02", scenario)
            field("观察时间 HH:mm", "09:20", scenario)
            field("观察备注", "Debug fixture · 授权银杏参考摄影，经系统 Photo Picker 导入", scenario)
            screenshot("observation-editor")
            click("保存观察"); waitText("我的植物档案")
            ui.waitUntil(15_000) { history(id).size == 3 }
            val third = history(id).first { it.id != first.id && it.id != second.id }
            assertArrayEquals(reference, File(URI(third.photoUri)).readBytes())
            context.contentResolver.delete(pickerUri!!, null, null); pickerUri = null
            assertTrue(File(URI(third.photoUri)).isFile)
            click("查看全部照片"); waitText("3 张照片 · 按观察时间排序")
            screenshot("observation-gallery")
            ui.onNodeWithContentDescription("查看观察 ${second.id}").performClick()
            click("编辑日期与备注"); waitText("编辑观察")
            field("观察日期 YYYY-MM-DD", "2026-10-01", scenario)
            field("观察时间 HH:mm", "08:30", scenario)
            field("观察备注", "Debug · 日期与备注已修改，历史照片保持不变", scenario)
            screenshot("observation-editor-edit")
            click("保存观察"); waitText("观察照片集")
            val updated = history(id).first { it.id == second.id }
            assertEquals("2026-10-01", java.time.Instant.ofEpochMilli(updated.timestampEpochMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString())
            assertEquals("Debug · 日期与备注已修改，历史照片保持不变", updated.note)
            ui.onNodeWithContentDescription("查看观察 ${third.id}").performClick()
            click("设为植物封面")
            ui.waitUntil(15_000) { runBlocking { access.database().archiveDao().plant(id)!!.coverObservationId } == third.id }
            click("关闭照片"); screenshot("cover-updated-gallery")
            back(); waitText("我的植物档案"); screenshot("cover-updated")
            ui.onNodeWithText("Debug · 日期与备注已修改，历史照片保持不变").performScrollTo(); screenshot("plant-with-observations")
            history(id).forEach { assertTrue(File(URI(it.photoUri)).isFile) }
            assertEquals(3, history(id).size)
            scenario.close(); scenario = ActivityScenario.launch(MainActivity::class.java)
            waitText("发现自然，收藏四季。"); click("花园"); waitText(nickname); screenshot("garden-collected")
            click(nickname); waitText("我的植物档案"); click("查看全部照片")
            waitText("3 张照片 · 按观察时间排序"); screenshot("relaunch-gallery")
            assertEquals(third.id, runBlocking { access.database().archiveDao().plant(id)!!.coverObservationId })
            history(id).forEach { assertTrue(File(URI(it.photoUri)).canRead()) }
            if(InstrumentationRegistry.getArguments().getString("phase6aStrict") == "true") phase6aTail(id, scenario)
            android.util.Log.i("Phase2Evidence", "PASS modelFixture=$modelFixture plantId=$id first=${first.id} second=${second.id} third=${third.id} photos=3")
            completed = true
        } finally {
            runCatching { screenshot("journey-final-state") }
            scenario.close()
            pickerUri?.let { context.contentResolver.delete(it, null, null) }
            val keep = InstrumentationRegistry.getArguments().getString("keepPhase2Evidence") == "true"
            if(!keep || !completed) runBlocking {
                plantId?.let { id -> history(id).forEach { access.archive().delete(id, it.id) }; access.plants().delete(id) }
                captureId?.let { access.captures().cancel(it) }
            }
        }
    }
    private fun SemanticsNodeInteraction.isEnabled(): Boolean = try { assertIsEnabled(); true } catch(_: AssertionError) { false }

    /** Continuous extension of the actual CameraX journey; gated to forbid result injection. */
    private fun phase6aTail(id: Long, scenario: ActivityScenario<MainActivity>) {
        back(); waitText("我的植物档案"); back(); waitText("My Garden")
        click("四季手账 →"); waitText("把相遇留在四季里。")
        ui.onNodeWithTag("seasons-list").performScrollToNode(hasTestTag("season-AUTUMN"))
        ui.onNodeWithTag("season-AUTUMN").performClick(); waitText("2026 · 秋季")
        val record = history(id).first { it.note?.contains("Photo Picker") == true }
        ui.onNodeWithTag("seasons-list").performScrollToNode(hasTestTag("season-observation-${record.id}"))
        ui.onNodeWithTag("season-observation-${record.id}").performClick()
        waitText("我的植物档案"); screenshot("phase6-season-to-archive")
        back(); back(); waitText("My Garden")
        click("观察地图"); waitText("离线观察地图"); screenshot("phase6-map")
        assertTrue("No location permission means no fabricated coordinates", history(id).all { it.latitude == null && it.longitude == null })
        waitText("还没有带位置的观察记录。"); back(); waitText("My Garden"); click(nickname)
        click("阅读植物文化 →"); waitText("植物文化"); screenshot("phase6-culture")
        back(); waitText("我的植物档案"); click("制作植物诗笺"); waitText("植物诗笺")
        fun ready(type: com.floralens.domain.card.CardTemplateId) {
            ui.waitUntil(25_000) { ui.onAllNodesWithContentDescription("${type.title}预览").fetchSemanticsNodes(false).isNotEmpty() }
        }
        ready(com.floralens.domain.card.CardTemplateId.MODERN_JOURNAL)
        ui.onNodeWithTag("card-photo-picker").performScrollTo().performClick(); waitText("选择一次真实观察")
        ui.onNodeWithTag("card-photo-list").performScrollToNode(hasTestTag("card-photo-${record.id}"))
        ui.onNodeWithTag("card-photo-${record.id}").performClick()
        ready(com.floralens.domain.card.CardTemplateId.MODERN_JOURNAL)
        val outputs = mutableListOf<android.net.Uri>()
        try {
            com.floralens.domain.card.CardTemplateId.entries.forEach { type ->
                ui.onNodeWithTag("template-${type.name}").performScrollTo().performClick(); ready(type)
                val assembled = runBlocking { access.cards().assemble(id, record.id).content }
                val expected = runBlocking { if (history(id).any { com.floralens.data.culture.CultureSnapshotPolicy.uncertain(it.identificationJson) }) null
                    else access.culture().approvedContent(assembled.speciesKey) }
                assertEquals(expected, assembled.approvedCulture)
                screenshot("phase6-${type.name.lowercase()}")
                ui.onNodeWithTag("card-save").performClick(); waitText("PNG 已保存到 Pictures/Wildflower Lens")
                val uri = context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID), "${MediaStore.Images.Media.RELATIVE_PATH}=?",
                    arrayOf("Pictures/Wildflower Lens/"), "${MediaStore.Images.Media._ID} DESC")!!.use {
                    check(it.moveToFirst()); android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it.getLong(0))
                }
                outputs += uri
                val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val bitmap = requireNotNull(android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                assertEquals(1600, bitmap.width); assertEquals(2400, bitmap.height); bitmap.recycle()
                // Copy only this newly exported artifact to evidence, never the observation original.
                val output = File(context.getExternalFilesDir(null), "phase6a/${type.name.lowercase()}.png")
                output.parentFile!!.mkdirs(); output.writeBytes(bytes)
            }
            ui.onNodeWithText("另存到所选位置…").performScrollTo().performClick()
            nativeClick { it.viewIdResourceName == "android:id/button1" && it.text?.toString() == "SAVE" }
            waitText("PNG 已保存到所选位置"); screenshot("phase6-saf-saved")
            ui.onNodeWithText("另存到所选位置…").performScrollTo().performClick()
            cancelCreateDocument()
            ready(com.floralens.domain.card.CardTemplateId.MODERN_JOURNAL) // returning from SAF refreshes the real preview
            ui.waitUntil(20_000) { ui.onNodeWithTag("card-share").isEnabled() }
            shell("run-as com.floralens.test rm -f files/phase4-received.txt") // only the receiver's owned test receipt
            ui.onNodeWithTag("card-share").performClick()
            ui.waitUntil(25_000) { shellText("dumpsys activity activities").contains("ChooserActivity") }
            screenshot("phase6-share-chooser")
            nativeClick(scroll = true) { it.text?.toString() == "Wildflower PNG 验收" }
            ui.waitUntil(20_000) { shellText("run-as com.floralens.test cat files/phase4-received.txt").contains("READ_OK") }
            assertTrue(shellText("run-as com.floralens.test cat files/phase4-received.txt").contains("size=1600x2400"))
            screenshot("phase6-share-receiver"); shell("input keyevent 4")
            waitText("植物诗笺"); waitText("已打开系统分享，请选择接收应用")
            scenario.recreate(); waitText("植物诗笺"); ready(com.floralens.domain.card.CardTemplateId.MODERN_JOURNAL)
            android.util.Log.i("Phase6AEvidence", "CONTINUOUS_REAL_PASS plant=$id observations=3 templates=3 SAF=save+cancel SHARE=externalUID")
        } finally { outputs.forEach { context.contentResolver.delete(it, null, null) } }
    }
    private fun shellText(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        java.io.FileInputStream(it.fileDescriptor).bufferedReader().readText()
    }

    /** Same journey's second invocation after adb force-stop, not an extra counted test or a skipped fixture. */
    private fun verifyAfterProcessRestart() {
        val plant = runBlocking { access.plants().observeAll().first().single { it.nickname == nickname } }
        val records = history(plant.id)
        assertEquals(3, records.size); assertEquals(3, records.map { it.photoUri }.distinct().size)
        assertEquals(1, records.count { it.type == "IDENTIFIED" && it.identificationJson != null })
        assertEquals(2, records.count { it.type == "PHOTO_OBSERVATION" && it.identificationJson == null })
        records.forEach { record ->
            val photo = File(URI(record.photoUri)); assertTrue(photo.canRead())
            val bitmap = requireNotNull(android.graphics.BitmapFactory.decodeFile(photo.absolutePath))
            android.util.Log.i("Phase2Evidence", "COLD photo=${record.id} bytes=${photo.length()} dimensions=${bitmap.width}x${bitmap.height}"); bitmap.recycle()
        }
        val cover = records.single { it.id == plant.coverObservationId }
        assertEquals(cover.photoUri, plant.photoUri)
        assertTrue(cover.note!!.contains("Photo Picker"))
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("发现自然，收藏四季。"); click("花园"); click(nickname); waitText("我的植物档案")
            screenshot("cold-start-plant"); click("查看全部照片")
            waitText("3 张照片 · 按观察时间排序"); screenshot("cold-start-gallery")
            assertTrue(records.any { it.note == "Debug · 日期与备注已修改，历史照片保持不变" })
        }
        android.util.Log.i("Phase2Evidence", "COLD PASS plantId=${plant.id} photos=3 cover=${cover.id}")
        if(InstrumentationRegistry.getArguments().getString("cleanupPhase6a") == "true") runBlocking {
            check(records.all { it.note?.startsWith("Debug") == true })
            records.forEach { access.archive().delete(plant.id, it.id) }; access.plants().delete(plant.id)
        }
    }

    @Test fun publicCollection_cancel_existingChoice_andCanceledPicker_createNoFakePhotos() {
        runBlocking {
            check(access.plants().observeAll().first().isEmpty()) { "Dedicated empty AVD required" }
            access.settings().setOnboarded()
        }
        val owned = mutableSetOf<Long>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                waitText("发现自然，收藏四季。"); ui.onNodeWithTag("explore-hero").performClick()
                click("收藏这株植物"); click("取消")
                assertEquals(0, runBlocking { access.database().plantDao().count() })
                click("收藏这株植物"); ui.onNodeWithText("植物昵称").performTextReplacement("Debug · 无照片一号")
                scenario.onActivity { it.window.insetsController?.hide(android.view.WindowInsets.Type.ime()) }
                click("确认收藏"); waitText("我的植物档案")
                val a = runBlocking { access.plants().observeAll().first().single().id }; owned += a
                assertTrue(history(a).isEmpty())
                back(); waitText("发现自然，收藏四季。"); ui.onNodeWithTag("explore-hero").performClick()
                click("收藏这株植物"); waitText("添加到：Debug · 无照片一号")
                click("添加到：Debug · 无照片一号"); screenshot("existing-plant-choice")
                click("确认收藏"); waitText("我的植物档案")
                assertEquals(1, runBlocking { access.database().plantDao().count() }); assertTrue(history(a).isEmpty())
                back(); waitText("发现自然，收藏四季。"); ui.onNodeWithTag("explore-hero").performClick()
                click("收藏这株植物"); waitText("添加到：Debug · 无照片一号")
                ui.onNodeWithText("植物昵称").performTextReplacement("Debug · 无照片二号")
                scenario.onActivity { it.window.insetsController?.hide(android.view.WindowInsets.Type.ime()) }
                click("确认收藏"); waitText("我的植物档案")
                val plants = runBlocking { access.plants().observeAll().first() }; owned += plants.map { it.id }
                assertEquals(2, plants.size); assertEquals(1, plants.map { it.speciesKey }.distinct().size)
                plants.forEach { assertTrue(history(it.id).isEmpty()) }
                click("添加观察"); click("从相册选择照片")
                nativeClick { it.contentDescription?.toString() == "Cancel" && it.isClickable }
                waitText("保存观察"); ui.onNodeWithText("保存观察").assertIsNotEnabled()
                screenshot("picker-cancel-no-observation")
                back(); waitText("我的植物档案")
                plants.forEach { assertTrue(history(it.id).isEmpty()) }
            } finally { runBlocking { owned.forEach { access.plants().delete(it) } } }
        }
    }
}

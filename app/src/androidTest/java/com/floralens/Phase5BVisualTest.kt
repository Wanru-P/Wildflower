package com.floralens

import android.Manifest
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.domain.card.CardTemplateId
import com.floralens.domain.model.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** Production screens and real Room/private files. Only this test's IDs are removed.
 * Presentation records name licensed reference photography, never pretend to be field observations.
 */
class Phase5BVisualTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val access get() = EntryPointAccessors.fromApplication(context, Phase1TestAccess::class.java)
    private val presentation = InstrumentationRegistry.getArguments().getString("phase5bPresentation") == "true"
    private val name get() = if (presentation) "桂花 · 参考摄影档案" else "Debug · Phase5B 桂花"
    private val folder get() = InstrumentationRegistry.getArguments().getString("phase5FinalRound")?.let { "wildflower-phase5-final-$it/phase5b" } ?: if (presentation) "wildflower-phase5b-presentation" else "wildflower-phase5b-test"
    private fun shell(cmd: String) = instrumentation.uiAutomation.executeShellCommand(cmd).use { FileInputStream(it.fileDescriptor).bufferedReader().readText() }
    private fun waitText(text: String) = ui.waitUntil(30_000) {
        runCatching { ui.onAllNodesWithText(text).fetchSemanticsNodes(false).isNotEmpty() }.getOrDefault(false)
    }
    private fun click(text: String) {
        waitText(text); val node = ui.onNodeWithText(text)
        try { node.performScrollTo() } catch (_: AssertionError) { }
        node.assertIsDisplayed().performClick()
    }
    private fun back() { ui.onNodeWithContentDescription("返回").performClick(); ui.waitForIdle() }
    private fun shot(name: String) {
        ui.waitForIdle(); Thread.sleep(1100); ui.mainClock.advanceTimeBy(320); ui.waitForIdle()
        if (name.startsWith("botanical-card")) {
            // Advancing the clock may deliver LifecycleResumeEffect's refresh after resize.
            // Require the rendered image and enabled save action AFTER that, at capture time.
            var stableSince = 0L
            ui.waitUntil(30_000) {
                val ready = runCatching {
                    ui.onAllNodesWithContentDescription("${if(name.endsWith("320")) CardTemplateId.BOTANICAL_ATLAS.title else CardTemplateId.MODERN_JOURNAL.title}预览")
                        .fetchSemanticsNodes(false).isNotEmpty() &&
                        ui.onAllNodesWithText("正在排版…").fetchSemanticsNodes(false).isEmpty() &&
                        ui.onAllNodes(hasTestTag("card-save") and isEnabled()).fetchSemanticsNodes(false).isNotEmpty()
                }.getOrDefault(false)
                if (!ready) { stableSince = 0L; false } else {
                    if (stableSince == 0L) stableSince = android.os.SystemClock.uptimeMillis()
                    android.os.SystemClock.uptimeMillis() - stableSince >= 900
                }
            }
            ui.onNodeWithTag("card-save").assertIsEnabled()
        }
        shell("mkdir -p /sdcard/Download/$folder")
        shell("screencap -p /sdcard/Download/$folder/$name.png")
    }
    private fun history(id: Long) = runBlocking { access.database().archiveDao().history(id) }
    private fun preview(type: CardTemplateId) {
        // A retained bitmap alone may still be visible in the frame before a resize's
        // lifecycle refresh. Require the actual save control and a settled render too.
        var since = 0L
        ui.waitUntil(30_000) {
            val ready = ui.onAllNodesWithContentDescription("${type.title}预览").fetchSemanticsNodes(false).isNotEmpty() &&
                ui.onAllNodes(hasTestTag("card-save") and isEnabled()).fetchSemanticsNodes(false).isNotEmpty() &&
                ui.onAllNodesWithText("正在排版…").fetchSemanticsNodes(false).isEmpty()
            if (!ready) { since = 0L; false } else {
                if (since == 0L) since = android.os.SystemClock.uptimeMillis()
                android.os.SystemClock.uptimeMillis() - since >= 350
            }
        }
        ui.onNodeWithTag("card-preview").performScrollTo()
    }
    private fun streaming(scenario: ActivityScenario<MainActivity>): Boolean {
        var ready = false
        scenario.onActivity { activity ->
            fun find(view: android.view.View): Boolean = when (view) {
                is androidx.camera.view.PreviewView -> view.previewStreamState.value == androidx.camera.view.PreviewView.StreamState.STREAMING
                is android.view.ViewGroup -> (0 until view.childCount).any { find(view.getChildAt(it)) }
                else -> false
            }
            ready = find(activity.window.decorView)
        }
        return ready
    }

    @Test fun editorialScreensActualPhotosAndPreservedFlows() {
        runBlocking {
            check(access.plants().observeAll().first().isEmpty()) { "Empty dedicated AVD required; never clear personal records" }
            access.settings().setOnboarded()
        }
        val owned = mutableListOf<Long>()
        var exported: android.net.Uri? = null
        var captureId: String? = null
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            shell("wm size 1080x2400"); shell("wm density 440")
            waitText("发现自然，收藏四季。")
            ui.onNodeWithTag("explore-hero").performClick(); waitText("植物笔记")
            ui.onNodeWithText("Osmanthus fragrans").assertIsDisplayed(); shot("species-detail")
            click("收藏这株植物"); waitText("收藏确认"); click("取消")
            assertEquals(0, runBlocking { access.database().plantDao().count() })
            click("阅读植物文化 →"); waitText("植物文化"); shot("culture")
            click("阅读自然文字 →"); waitText("自然文字"); shot("culture-reading")
            click("返回植物文化")
            shell("wm size 840x1680"); shell("wm density 420"); waitText("植物文化"); shot("culture-320")
            shell("wm size 1920x2400"); shell("wm density 320"); waitText("植物文化"); shot("culture-tablet")
            shell("wm size 1080x2400"); shell("wm density 440")
            back(); back(); click("花园"); click("四季手账 →")
            waitText("这一年，还没有自然观察"); shot("seasons-empty"); back()

            val main = runBlocking {
                val id = access.archive().collect("phase5b-${UUID.randomUUID()}", Species.identified("Osmanthus fragrans", "桂花"), name, null, null)
                owned += id
                listOf("2026-03-12T09:20:00", "2026-06-20T09:20:00", "2026-10-02T09:20:00", "2026-10-08T10:35:00").forEachIndexed { index, date ->
                    val input = access.captures().create(context.assets.open("explore/" + if(index == 1) "osmanthus_starr.jpg" else "osmanthus_kenraiz.jpg").use { it.readBytes() })
                    access.archive().add(id, input.id, LocalDateTime.parse(date).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        if (presentation) "授权参考摄影 · 非用户野外实拍。日期用于归档展示，不代表同株生长过程。" else "Debug · 授权参考摄影，仅测试归档。")
                }
                val other = access.archive().collect("phase5b-${UUID.randomUUID()}", Species.identified("Camellia japonica", "山茶"),
                    if (presentation) "山茶 · 参考影集" else "Debug · 山茶", null, null)
                owned += other
                val photo = access.captures().create(context.assets.open("explore/camellia_pumpkinsky.jpg").use { it.readBytes() })
                access.archive().add(other, photo.id, LocalDateTime.parse("2026-10-03T09:20:00").atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), "授权参考摄影 · 非用户野外实拍。")
                access.archive().cover(id, history(id).first().id)
                id
            }
            val before = history(main)
            val originalCover = runBlocking { access.plants().observeById(main).first()!!.coverObservationId }
            val originals = before.associate { it.photoUri!! to File(URI(it.photoUri)).readBytes() }
            click("四季手账 →"); waitText("2026 年"); shot("seasons")
            if (InstrumentationRegistry.getArguments().getString("phase5FinalRound") != null) {
                ui.onNodeWithTag("seasons-list").performScrollToNode(hasTestTag("season-WINTER"))
                ui.onNodeWithTag("season-WINTER").performClick(); waitText("这一季，尚无观察"); shot("seasons-winter-empty")
                click("返回四季总览"); waitText("2026 年")
            }
            ui.onNodeWithTag("seasons-list").performScrollToNode(hasTestTag("season-AUTUMN"))
            ui.onNodeWithTag("season-AUTUMN").performClick(); waitText("2026 · 秋季"); shot("seasons-autumn")
            ui.onNodeWithTag("seasons-list").performScrollToNode(hasTestTag("season-observation-${before.first().id}"))
            ui.onNodeWithTag("season-observation-${before.first().id}").performClick(); waitText("我的植物档案")
            click("查看全部照片"); waitText("4 张照片 · 按观察时间排序"); shot("observation-gallery")
            ui.onNodeWithContentDescription("查看观察 ${before.last().id}").performClick(); shot("observation-photo")
            click("设为植物封面"); waitText("封面已更新，历史照片保持不变")
            assertEquals(before.last().id, runBlocking { access.plants().observeById(main).first()!!.coverObservationId })
            click("编辑日期与备注"); waitText("编辑观察"); shot("observation-editor")
            ui.onNodeWithContentDescription("选择观察日期").performScrollTo().performClick()
            fun containsNative(node: android.view.accessibility.AccessibilityNodeInfo?, classPart: String): Boolean {
                if (node == null) return false
                if (node.className?.toString()?.contains(classPart) == true) return true
                return (0 until node.childCount).any { containsNative(node.getChild(it), classPart) }
            }
            ui.waitUntil(10_000) { containsNative(instrumentation.uiAutomation.rootInActiveWindow, "DatePicker") }
            shell("input keyevent 4"); waitText("编辑观察")
            ui.onNodeWithContentDescription("选择观察时间").performScrollTo().performClick()
            ui.waitUntil(10_000) { containsNative(instrumentation.uiAutomation.rootInActiveWindow, "TimePicker") }
            shell("input keyevent 4"); waitText("编辑观察")
            ui.onNodeWithText("观察备注").performScrollTo().assertIsDisplayed()
            shot("observation-editor-fields")
            ui.onNodeWithText("观察备注").performTextReplacement("授权参考摄影，非用户野外实拍。" + "测试长备注的换行与内部滚动。".repeat(28))
            scenario.onActivity { it.window.insetsController?.hide(android.view.WindowInsets.Type.ime()) }
            shell("wm size 840x1680"); shell("wm density 420"); waitText("编辑观察")
            ui.onNodeWithText("观察备注").performScrollTo(); shot("observation-editor-long-note-320")
            ui.onNodeWithText("保存观察").assertIsDisplayed().assertIsEnabled()
            click("取消"); waitText("观察照片集"); back()
            shell("wm size 1080x2400"); shell("wm density 440")
            runBlocking { access.archive().cover(main, originalCover!!) }

            click("制作植物诗笺"); preview(CardTemplateId.MODERN_JOURNAL)
            ui.waitUntil(30_000) { CardTemplateId.entries.all { ui.onAllNodesWithContentDescription("${it.title}缩略预览").fetchSemanticsNodes(false).isNotEmpty() } }
            shot("botanical-card-editor")
            ui.onNodeWithTag("template-CLASSICAL_POETRY").performScrollTo().performClick(); preview(CardTemplateId.CLASSICAL_POETRY)
            assertEquals("scientific:osmanthus fragrans", runBlocking { access.cards().assemble(main, before.first().id).content.approvedCulture?.speciesKey })
            ui.onNodeWithTag("template-BOTANICAL_ATLAS").performScrollTo().performClick(); preview(CardTemplateId.BOTANICAL_ATLAS)
            ui.onNodeWithTag("card-save").performClick(); waitText("PNG 已保存到 Pictures/Wildflower Lens")
            exported = context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.RELATIVE_PATH}=?", arrayOf("Pictures/Wildflower Lens/"), "${MediaStore.Images.Media._ID} DESC")!!.use {
                check(it.moveToFirst()); android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it.getLong(0))
            }
            context.contentResolver.openInputStream(exported!!)!!.use { input ->
                val bitmap = requireNotNull(BitmapFactory.decodeStream(input)); assertEquals(1600, bitmap.width); assertEquals(2400, bitmap.height); bitmap.recycle()
            }
            ui.onNodeWithTag("card-photo-picker").performScrollTo().performClick(); waitText("选择一次真实观察")
            ui.onNodeWithTag("card-photo-${before.last().id}").performClick(); preview(CardTemplateId.BOTANICAL_ATLAS)
            shell("wm size 840x1680"); shell("wm density 420"); waitText("植物诗笺")
            // Resize can recreate the Activity and invalidate the old preview after its first frame.
            // Wait for this fixture's actual green photograph on the native preview, not a loading frame.
            ui.waitUntil(30_000) {
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    var green = 0
                    if (bitmap.width == 840 && bitmap.height == 1680) {
                        for (x in 3..7) for (y in 5..12) {
                            val pixel = bitmap.getPixel(bitmap.width * x / 10, bitmap.height * y / 20)
                            if (android.graphics.Color.green(pixel) > android.graphics.Color.red(pixel) + 6 &&
                                android.graphics.Color.green(pixel) > android.graphics.Color.blue(pixel) + 18) green++
                        }
                    }
                    bitmap.recycle(); green >= 8
                } ?: false
            }
            preview(CardTemplateId.BOTANICAL_ATLAS)
            ui.onNodeWithTag("card-save").assertIsEnabled(); shot("botanical-card-320")
            ui.onNodeWithTag("card-save").assertIsDisplayed().assertIsEnabled(); ui.onNodeWithTag("card-share").assertIsDisplayed()
            back(); click("查看全部照片"); waitText("观察照片集"); shot("observation-gallery-320")
            if (InstrumentationRegistry.getArguments().getString("phase5FinalRound") != null) {
                shell("settings put system font_scale 1.3"); Thread.sleep(1500); waitText("观察照片集"); shot("gallery-font-130-320")
                shell("settings put system font_scale 1.0"); Thread.sleep(1500); waitText("观察照片集")
            }
            back(); back(); waitText("四季手账"); shot("seasons-320")
            shell("wm size 1920x2400"); shell("wm density 320"); waitText("四季手账")
            ui.onNodeWithTag("seasons-list").performScrollToIndex(0); shot("seasons-tablet")
            shell("wm size 1080x2400"); shell("wm density 440"); back(); waitText("My Garden")
            assertEquals(before, history(main)); originals.forEach { (uri, bytes) -> assertArrayEquals(bytes, File(URI(uri)).readBytes()) }
            scenario.recreate(); waitText("My Garden"); click(name); click("查看全部照片"); waitText("4 张照片 · 按观察时间排序"); back(); back()
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
            click("识别"); waitText("离线 · 本机识别")
            ui.waitUntil(30_000) { streaming(scenario) }; shot("scan")
            android.util.Log.i("Phase5BLayout", ui.onRoot().printToString())
            shell("wm size 840x1680"); shell("wm density 420")
            ui.waitUntil(30_000) { streaming(scenario) }; shot("scan-320")
            ui.onNodeWithContentDescription("拍摄植物").assertIsDisplayed().assertIsEnabled()
            ui.onNodeWithText("轻触对焦 · 双指缩放").assertDoesNotExist()
            shell("wm size 1080x2400"); shell("wm density 440")
            ui.waitUntil(30_000) { streaming(scenario) }
            ui.onNodeWithContentDescription("拍摄植物").performClick()
            ui.waitUntil(40_000) { listOf("查看植物详情", "暂未发现植物").any { ui.onAllNodesWithText(it).fetchSemanticsNodes(false).isNotEmpty() } }
            val capture = runBlocking { requireNotNull(access.captures().latest()) }; captureId = capture.id
            assertTrue(File(URI(capture.photoUri)).isFile)
            // No replacement candidate. Evidence shows the actual emulator model result, not a plant accuracy claim.
            shot("identification-result")
            click(if(capture.result == IdResult.NoPlantDetected) "再试一次" else "重新拍摄")
            ui.waitUntil(30_000) { streaming(scenario) }
            assertEquals(owned.size, runBlocking { access.database().plantDao().count() })
        } catch (e: Throwable) { shot("failure"); android.util.Log.e("Phase5B", ui.onRoot().printToString(), e); throw e }
        finally {
            shell("settings put system font_scale 1.0")
            shell("wm size reset"); shell("wm density reset"); scenario.close()
            runBlocking {
                captureId?.let { access.captures().cancel(it) }
                owned.forEach { id -> history(id).forEach { access.archive().delete(id, it.id) }; access.plants().delete(id) }
            }
            exported?.let { context.contentResolver.delete(it, null, null) }
        }
    }
}

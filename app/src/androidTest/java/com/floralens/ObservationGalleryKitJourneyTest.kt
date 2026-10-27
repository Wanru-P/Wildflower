package com.floralens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
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

/** Own archives on an empty dedicated emulator only; never seed an ordinary user's garden. */
class ObservationGalleryKitJourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val access get() = EntryPointAccessors.fromApplication(context, Phase1TestAccess::class.java)
    private val round get() = InstrumentationRegistry.getArguments().getString("galleryRound")
        ?.takeIf { it in setOf("round1", "round2", "final") } ?: "test"
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).bufferedReader().readText()
    }
    private fun waitText(text: String) = ui.waitUntil(25_000) {
        runCatching { ui.onAllNodesWithText(text).fetchSemanticsNodes(false).isNotEmpty() }.getOrDefault(false)
    }
    private fun click(text: String) {
        waitText(text)
        ui.onNodeWithText(text).apply { runCatching { performScrollTo() } }.assertIsDisplayed().performClick()
    }
    private fun screen(name: String) {
        ui.waitForIdle(); Thread.sleep(900); ui.waitForIdle()
        val folder = "/sdcard/Download/wildflower-observation-gallery/$round"
        shell("mkdir -p $folder"); shell("screencap -p $folder/$name.png")
    }
    private fun viewport(narrow: Boolean) {
        shell(if (narrow) "wm size 840x1680" else "wm size 1080x2400")
        shell(if (narrow) "wm density 420" else "wm density 440")
        waitText("观察照片集")
        ui.waitUntil(20_000) { instrumentation.uiAutomation.takeScreenshot()?.let {
            val matches = it.width == if (narrow) 840 else 1080
            it.recycle(); matches
        } ?: false }
        ui.onNodeWithTag("observation-gallery-grid").performScrollToIndex(0)
    }
    private fun history(id: Long) = runBlocking { access.database().archiveDao().history(id) }
    private fun back() { ui.onNodeWithContentDescription("返回").performClick(); ui.waitForIdle() }
    private fun open(id: Long) {
        ui.onNodeWithTag("garden-grid").performScrollToNode(hasTestTag("garden-plant-$id"))
        ui.onNodeWithTag("garden-plant-$id").performClick(); waitText("我的植物档案")
        click("查看全部照片"); waitText("观察照片集")
    }

    /** Opt-in recovery of ONE exact receipt identified after this task's emulator crash.
     * Validate every record and original photo before deleting anything; never clear a database. */
    private fun recoverInterruptedOwnedFixture() {
        val operation = InstrumentationRegistry.getArguments().getString("galleryInterruptedOperation") ?: return
        check(BuildConfig.DEBUG && operation.startsWith("gallery-kit-"))
        check(UUID.fromString(operation.removePrefix("gallery-kit-")).toString() == operation.removePrefix("gallery-kit-"))
        runBlocking {
            val receipt = requireNotNull(access.database().archiveDao().operation(operation))
            val plant = access.database().archiveDao().plant(receipt.plantId) ?: return@runBlocking
            check(access.plants().observeAll().first().map { it.id }.toSet() == setOf(receipt.plantId))
            check(plant.nickname == "桂花 · 参考摄影档案" && plant.scientificName == "Osmanthus fragrans")
            val records = history(receipt.plantId)
            val reference = "授权参考摄影 · 非用户野外实拍。日期用于归档展示，不代表同株生长过程。"
            check(records.all { it.note?.startsWith(reference) == true || it.note == "无照片笔记不计入照片集" })
            val originals = listOf("osmanthus_kenraiz.jpg", "osmanthus_starr.jpg", "osmanthus.jpg")
                .map { context.assets.open("explore/$it").use { stream -> stream.readBytes() } }
            records.mapNotNull { it.photoUri }.forEach { uri ->
                val actual = File(URI(uri)).readBytes()
                check(originals.any { it.contentEquals(actual) }) { "Photo changed; do not remove it" }
            }
            records.forEach { access.archive().delete(receipt.plantId, it.id) }
            access.plants().delete(receipt.plantId)
            println("Recovered only interrupted gallery fixture ${receipt.plantId} / $operation / ${records.size} records")
        }
    }

    @Test fun realPhotos_twoColumnsCoverEditDeleteEmptyAndRecreation() {
        check(BuildConfig.DEBUG)
        recoverInterruptedOwnedFixture()
        runBlocking {
            check(access.plants().observeAll().first().isEmpty()) { "Empty dedicated AVD required; do not clear user records" }
            access.settings().setOnboarded()
        }
        val owned = mutableListOf<Long>()
        val note = "授权参考摄影 · 非用户野外实拍。日期用于归档展示，不代表同株生长过程。"
        val id = runBlocking {
            val plant = access.archive().collect("gallery-kit-${UUID.randomUUID()}",
                Species.identified("Osmanthus fragrans", "桂花"), "桂花 · 参考摄影档案", null, null)
            owned += plant
            listOf("2026-03-12T09:20:00", "2026-06-20T09:20:00", "2026-10-02T09:20:00", "2026-10-08T10:35:00")
                .forEachIndexed { index, date ->
                    val asset = listOf("osmanthus_kenraiz.jpg", "osmanthus_starr.jpg", "osmanthus.jpg", "osmanthus_kenraiz.jpg")[index]
                    val capture = access.captures().create(context.assets.open("explore/$asset").use { it.readBytes() })
                    access.archive().add(plant, capture.id, LocalDateTime.parse(date).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), note)
                }
            access.observations().add(Observation(plantId = plant, type = ObservationType.NOTE, note = "无照片笔记不计入照片集"))
            access.archive().cover(plant, history(plant).first { it.photoUri != null }.id)
            plant
        }
        val original = history(id)
        val photos = original.filter { it.photoUri != null }
        val bytes = photos.associate { it.photoUri!! to File(URI(it.photoUri)).readBytes() }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitText("发现自然，收藏四季。"); click("花园"); open(id)
            waitText("4 张照片 · 按观察时间排序")
            viewport(false); screen("gallery-393")
            assertTwoColumns(photos[0].id, photos[1].id)
            viewport(true); screen("gallery-320")
            assertTwoColumns(photos[0].id, photos[1].id)
            viewport(false)
            ui.onNodeWithContentDescription("查看观察 ${photos[1].id}").performClick()
            ui.onNode(hasText(note) and hasAnyAncestor(hasTestTag("gallery-photo-detail"))).assertExists(); screen("photo-detail")
            click("设为植物封面"); waitText("封面已更新，历史照片保持不变"); click("关闭照片")
            ui.onNodeWithTag("gallery-cover-${photos[1].id}", useUnmergedTree = true).assertExists()
            ui.onNodeWithTag("gallery-cover-${photos[0].id}", useUnmergedTree = true).assertDoesNotExist()
            screen("gallery-cover-updated")
            assertEquals(original, history(id))
            ui.onNodeWithContentDescription("更多观察操作 ${photos[1].id}").performClick()
            click("编辑日期与备注"); waitText("编辑观察")
            val edited = note + "完整备注结尾 · 仅测试摘要换行。".repeat(12)
            ui.onNodeWithText("观察备注").performScrollTo().performTextReplacement(edited)
            click("保存观察"); waitText("观察照片集")
            assertEquals(edited, history(id).first { it.id == photos[1].id }.note)
            ui.onNodeWithContentDescription("更多观察操作 ${photos[1].id}").performClick()
            ui.onNode(hasText(edited) and hasAnyAncestor(hasTestTag("gallery-photo-detail"))).assertExists()
            click("删除这条观察"); waitText("删除观察？"); click("取消")
            assertEquals(5, history(id).size)
            click("删除这条观察"); click("确认删除")
            waitText("3 张照片 · 按观察时间排序")
            val remaining = history(id)
            assertEquals(original.filter { it.id != photos[1].id }, remaining)
            val cover = runBlocking { access.database().archiveDao().plant(id)!!.coverObservationId }
            assertNotEquals(photos[1].id, cover)
            assertTrue(remaining.any { it.id == cover && it.photoUri != null })
            remaining.mapNotNull { it.photoUri }.forEach { assertArrayEquals(bytes[it], File(URI(it)).readBytes()) }
            scenario.recreate(); waitText("3 张照片 · 按观察时间排序")
            assertEquals(remaining, history(id))
            back(); waitText("我的植物档案"); back(); waitText("My Garden")
            val empty = runBlocking { access.archive().collect("gallery-empty-${UUID.randomUUID()}",
                Species.identified("Osmanthus fragrans", "桂花"), "暂无照片的测试档案", null, null).also { owned += it } }
            open(empty); waitText("还没有观察照片"); screen("gallery-empty-393")
            click("添加观察"); waitText("拍摄新照片")
            ui.onNodeWithText("从相册选择照片").assertExists()
            back(); waitText("观察照片集")
            assertTrue(history(empty).isEmpty())
            val missing = runBlocking {
                access.observations().add(Observation(plantId = empty, type = ObservationType.PHOTO_OBSERVATION,
                    photoUri = "file:///missing-gallery-kit-${UUID.randomUUID()}.jpg", note = "缺图测试 · 不删除记录"))
                history(empty).first().id
            }
            waitText("1 张照片 · 按观察时间排序"); viewport(true); screen("gallery-missing-320")
            ui.onAllNodesWithTag("photo-unavailable", useUnmergedTree = true).onFirst().assertIsDisplayed()
            ui.onNodeWithContentDescription("查看观察 $missing").performClick()
            click("关闭照片"); assertEquals(1, history(empty).size)
        } catch (failure: Throwable) {
            runCatching { screen("failure") }
            android.util.Log.e("GalleryKitEvidence", runCatching { ui.onRoot().printToString() }.getOrDefault("No root"), failure)
            throw failure
        } finally {
            scenario.close(); shell("wm size reset"); shell("wm density reset")
            runBlocking { owned.forEach { plant -> history(plant).forEach { access.archive().delete(plant, it.id) }; access.plants().delete(plant) } }
        }
    }

    private fun assertTwoColumns(leftId: Long, rightId: Long) {
        val left = ui.onNodeWithTag("gallery-photo-$leftId", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val right = ui.onNodeWithTag("gallery-photo-$rightId", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(left.top, right.top, 2f)
        assertTrue(right.left > left.right)
        assertEquals(4f / 3f, left.width / left.height, .015f)
    }
}

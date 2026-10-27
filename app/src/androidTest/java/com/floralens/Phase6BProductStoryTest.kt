package com.floralens

import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.domain.card.CardTemplateId
import com.floralens.domain.model.Species
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.URI
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** Staged licensed photography notebook, NOT physical campus or same-tree growth evidence.
 * Real CameraX/model/picker chain is separately exercised by Phase2JourneyTest in strict mode.
 * Only creates and cleans its own archive; refuses a populated device. */
class Phase6BProductStoryTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val access get() = EntryPointAccessors.fromApplication(context, Phase1TestAccess::class.java)
    private val evidenceDirectory get() = if (InstrumentationRegistry.getArguments().getString("phase6cEvidence") == "true")
        "wildflower-phase6c/story" else "wildflower-phase6b"
    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
        java.io.FileInputStream(it.fileDescriptor).bufferedReader().readText()
    }
    private fun waitText(text: String) = ui.waitUntil(30_000) {
        ui.onAllNodesWithText(text).fetchSemanticsNodes(false).isNotEmpty()
    }
    private fun click(text: String) {
        waitText(text)
        val node = ui.onNodeWithText(text)
        try { node.performScrollTo() } catch (_: AssertionError) { }
        node.assertIsDisplayed().performClick()
    }
    private fun back() { Espresso.pressBack(); ui.waitForIdle() }
    private fun noDeveloperCopy() {
        val forbidden = Regex("(?i)\\b(debug|test|mock|sample|demo|temporary|todo|fixme)\\b|开发版|未批准|审核状态|授权参考摄影|非用户野外实拍|HTTP Error|Missing Key|see_matchNote|ready_for_editorial_review")
        ui.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes(false).forEach { root ->
            fun check(node: androidx.compose.ui.semantics.SemanticsNode) {
                node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.forEach {
                    assertFalse("Developer text in product: ${it.text}", forbidden.containsMatchIn(it.text))
                }
                node.children.forEach(::check)
            }
            check(root)
        }
    }
    private fun shot(name: String) {
        ui.waitForIdle(); Thread.sleep(1000); ui.waitForIdle()
        noDeveloperCopy()
        shell("mkdir -p /sdcard/Download/$evidenceDirectory")
        shell("screencap -p /sdcard/Download/$evidenceDirectory/$name.png")
    }
    private fun ready(type: CardTemplateId) = ui.waitUntil(30_000) {
        ui.onAllNodesWithContentDescription("${type.title}预览").fetchSemanticsNodes(false).isNotEmpty()
    }

    @Test fun productNotebook_readCulture_seasons_threeExports_andShare() {
        assertFalse("Run normal presentation build, not internal tools", BuildConfig.INTERNAL_TOOLS)
        assertEquals("草木知时", context.getString(com.floralens.R.string.app_name))
        assertEquals("com.floralens", context.packageName)
        runBlocking {
            check(access.plants().observeAll().first().isEmpty()) { "Dedicated empty AVD required; never clear existing archives" }
            access.settings().setOnboarded()
        }
        var owner: Long? = null
        val outputs = mutableListOf<android.net.Uri>()
        val name = "桂花观察手记"
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitText("发现自然，收藏四季。"); shot("01-explore")
            ui.onNodeWithTag("explore-list").performScrollToNode(hasText(com.floralens.ui.components.EDITORIAL_ARTWORK_CREDIT, substring = true))
            ui.onAllNodesWithText("资料审阅 · Debug").assertCountEquals(0)
            ui.onNodeWithTag("explore-list").performScrollToIndex(0)
            ui.onNodeWithContentDescription("设置").performClick(); waitText("设置与帮助"); shot("02-settings"); back()
            ui.onNodeWithTag("explore-hero").performClick(); waitText("植物笔记"); shot("03-species")
            click("阅读植物文化 →"); waitText("植物文化"); shot("04-culture")
            ui.scrollCultureTo("nature-text")
            click("阅读自然文字 →"); waitText("自然文字"); shot("05-culture-reading")
            ui.onNodeWithTag("culture-list").performScrollToNode(hasTestTag("culture-excerpt"))
            ui.onNodeWithTag("culture-excerpt").assertTextEquals("暗淡轻黄体性柔")
            click("返回植物文化")
            back(); back(); click("花园"); waitText("My Garden"); shot("06-garden-empty")
            val id = runBlocking {
                val id = access.archive().collect("product-story-${UUID.randomUUID()}", Species.identified("Osmanthus fragrans", "桂花"), name, null, null)
                owner = id
                listOf(
                    Triple("2026-10-08T09:30:00", "osmanthus_kenraiz.jpg", "花朵藏于叶间，留住这一抹淡黄。\n摄影：Kenraiz · CC BY-SA 4.0"),
                    Triple("2026-10-14T10:00:00", "osmanthus_starr.jpg", "把目光留给枝叶，记录花朵与光影的细节。\n摄影：Forest & Kim Starr · CC BY 2.0")
                ).forEach { (date, image, note) ->
                    val capture = access.captures().create(context.assets.open("explore/$image").use { it.readBytes() })
                    access.archive().add(id, capture.id, LocalDateTime.parse(date).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), note)
                }
                id
            }
            val original = runBlocking { access.database().archiveDao().history(id) }
            val originals = original.associate { it.photoUri!! to File(URI(it.photoUri)).readBytes() }
            waitText(name); shot("07-garden"); click(name); waitText("我的植物档案"); shot("08-personal-detail")
            ui.onNodeWithTag("journal-observation-${original.first().id}").performScrollTo(); shot("09-timeline")
            click("查看全部照片"); waitText("2 张照片 · 按观察时间排序"); shot("10-gallery")
            ui.onNodeWithContentDescription("查看观察 ${original.first().id}").performClick(); shot("11-photo-detail")
            click("编辑日期与备注"); waitText("编辑观察"); shot("12-observation-editor"); back()
            waitText("观察照片集"); back(); waitText("我的植物档案"); back(); waitText("My Garden")
            click("四季手账 →"); waitText("把相遇留在四季里。"); shot("13-seasons")
            ui.onNodeWithTag("seasons-list").performScrollToNode(hasTestTag("season-AUTUMN"))
            ui.onNodeWithTag("season-AUTUMN").performClick(); waitText("2026 · 秋季"); shot("14-autumn")
            back(); waitText("My Garden"); click(name); click("制作植物诗笺"); ready(CardTemplateId.MODERN_JOURNAL)
            shot("15-poetry-editor")
            ui.onNodeWithTag("card-photo-picker").performScrollTo().performClick(); waitText("选择一次真实观察"); shot("16-select-photo")
            ui.onNodeWithTag("card-photo-${original.last().id}").performClick(); ready(CardTemplateId.MODERN_JOURNAL)
            for (type in CardTemplateId.entries) {
                ui.onNodeWithTag("template-${type.name}").performScrollTo().performClick(); ready(type)
                ui.onNodeWithTag("template-${type.name}").assertIsSelected(); shot("17-template-${type.name.lowercase()}")
                ui.onNodeWithTag("card-save").assertIsEnabled().performClick(); waitText("PNG 已保存到 Pictures/Wildflower Lens")
                val resolver = context.contentResolver
                val uri = resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
                    "${MediaStore.Images.Media.RELATIVE_PATH}=?", arrayOf("Pictures/Wildflower Lens/"), "${MediaStore.Images.Media._ID} DESC")!!.use {
                    check(it.moveToFirst()); android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it.getLong(0))
                }
                outputs += uri
                val png = resolver.openInputStream(uri)!!.use { it.readBytes() }
                val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size)!!
                assertEquals(1600, bitmap.width); assertEquals(2400, bitmap.height); bitmap.recycle()
                val evidence = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, android.content.ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "${type.name.lowercase()}.png")
                    put(MediaStore.Downloads.MIME_TYPE, "image/png")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/$evidenceDirectory/exports/")
                })!!
                resolver.openOutputStream(evidence)!!.use { it.write(png) }
                assertEquals("scientific:osmanthus fragrans", runBlocking { access.cards().assemble(id, original.last().id).content.approvedCulture?.speciesKey })
            }
            ui.onNodeWithTag("card-share").performClick()
            ui.waitUntil(30_000) { shell("dumpsys activity activities").contains("ChooserActivity") }; shot("18-system-share")
            shell("input keyevent 4"); waitText("植物诗笺")
            ui.onNodeWithTag("card-message").assertTextEquals("已打开系统分享，请选择接收应用")
            assertEquals(original, runBlocking { access.database().archiveDao().history(id) })
            originals.forEach { (uri, bytes) -> assertArrayEquals(bytes, File(URI(uri)).readBytes()) }
            if (InstrumentationRegistry.getArguments().getString("phase6cEvidence") == "true") {
                back(); waitText("我的植物档案"); back(); waitText("My Garden")
                click("观察地图"); waitText("离线观察地图"); waitText("还没有带位置的观察记录。")
                shot("21-map-no-location")
                assertTrue(runBlocking { access.database().archiveDao().history(id) }.all { it.latitude == null && it.longitude == null })
                back(); waitText("My Garden")
            }
        } finally {
            scenario.close()
            runBlocking { owner?.let { id ->
                access.database().archiveDao().history(id).forEach { access.archive().delete(id, it.id) }
                access.plants().delete(id)
            } }
            outputs.forEach { context.contentResolver.delete(it, null, null) }
        }
    }
}

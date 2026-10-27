package com.floralens

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.data.culture.*
import com.floralens.domain.card.CardTemplateId
import com.floralens.domain.culture.*
import com.floralens.domain.model.Species
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.URI
import java.util.UUID

/** Real Room/UI/export flow. Online generation is opt-in and never substituted with a fixture.
 * Reference photography is explicitly credited; only this test's new records are removed. */
class Phase6B5JourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val access get() = EntryPointAccessors.fromApplication(context, Phase1TestAccess::class.java)
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use { java.io.FileInputStream(it.fileDescriptor).bufferedReader().readText() }
    private fun waitText(text: String) = ui.waitUntil(35_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes(false).isNotEmpty() }
    private fun click(text: String) { waitText(text); val node = ui.onNodeWithText(text); try { node.performScrollTo() } catch (_: AssertionError) { }; node.assertIsDisplayed().performClick() }
    private fun back() { Espresso.pressBack(); ui.waitForIdle() }
    private fun screenshot(name: String) {
        ui.waitForIdle(); Thread.sleep(800); ui.waitForIdle()
        shell("mkdir -p /sdcard/Download/wildflower-phase6b5")
        shell("screencap -p /sdcard/Download/wildflower-phase6b5/$name.png")
    }
    private fun preview(type: CardTemplateId) = ui.waitUntil(35_000) {
        ui.onAllNodesWithContentDescription("${type.title}预览").fetchSemanticsNodes(false).isNotEmpty() &&
            ui.onAllNodes(hasTestTag("card-save") and isEnabled()).fetchSemanticsNodes(false).isNotEmpty()
    }
    private fun editExpression() {
        ui.onNodeWithTag("card-edit-expression").performScrollTo()
        ui.waitUntil(35_000) {
            ui.onAllNodes(hasTestTag("card-edit-expression") and isEnabled()).fetchSemanticsNodes(false).isNotEmpty()
        }
        ui.onNodeWithTag("card-edit-expression").assertIsEnabled().performClick()
        // The input dialog is rendered after the selected 1600×2400 preview has settled.
        // On a fresh, full-suite API 35 emulator this can legitimately take longer than
        // the old 10-second UI-only budget; still wait for the actual field, never a
        // substitute state or a mocked result.
        ui.waitUntil(35_000) { ui.onAllNodesWithTag("card-expression-input").fetchSemanticsNodes(false).isNotEmpty() }
    }

    @Test fun natureCultureSharedIdentity_editableCards_exports_andDataPreserved() {
        val args = InstrumentationRegistry.getArguments()
        if (args.getString("natureVerifyCold") == "true") { verifyColdCache(); return }
        val live = args.getString("natureLive") == "true"
        val config = NatureServiceConfig(context)
        val prefs = context.getSharedPreferences("nature-service-private", 0)
        val previous = prefs.getString("encrypted", null)
        val identity = CultureIdentityResolver.resolve(scientific = "Osmanthus fragrans")
        val options = context.getSharedPreferences("nature-expression-options", 0)
        val oldStyle = options.getString(identity.key, null)
        val fontScale = shell("settings get system font_scale").trim().takeIf { it.toFloatOrNull() != null } ?: "1.0"
        val cache = File(context.filesDir, "nature-expressions-v1/${NatureTextPolicy.cacheKey(identity, "quiet")}")
        val cacheBefore = cache.takeIf(File::exists)?.readBytes()
        val catalogBefore = context.assets.open("culture/culture_entries.json").use { it.readBytes() }
        val outputs = mutableListOf<android.net.Uri>()
        var owner: Long? = null
        runBlocking {
            check(access.plants().observeAll().first().isEmpty()) { "Dedicated empty AVD required; never erase archives" }
            access.settings().setOnboarded()
        }
        cache.delete(); config.clear(); options.edit().putString(identity.key, "quiet").commit()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitText("发现自然，收藏四季。")
            ui.onNodeWithTag("explore-hero").performClick(); waitText("植物笔记")
            click("阅读植物文化 →"); waitText("植物文化")
            ui.scrollCultureTo("nature-text")
            ui.onNodeWithTag("nature-text").assertIsDisplayed()
            ui.onNodeWithTag("nature-source").assertTextEquals(ExpressionSource.LOCAL.label)
            ui.onNodeWithTag("culture-list").performScrollToNode(hasTestTag("culture-excerpt"))
            ui.onNodeWithTag("culture-excerpt").assertTextEquals("暗淡轻黄体性柔")
            assertEquals(0, runBlocking { access.database().plantDao().count() })
            // User explicitly sees privacy disclosure and can cancel without a network request.
            ui.scrollCultureTo("nature-generate")
            ui.onNodeWithTag("nature-generate").performScrollTo().performClick(); waitText("生成 AI 原创自然短诗")
            ui.onNodeWithText("取消").performClick()
            ui.onNodeWithTag("nature-configure").performScrollTo().performClick(); waitText("安全创作服务")
            ui.onNodeWithTag("nature-proxy-url").performTextInput("https://api.deepseek.com/chat/completions")
            ui.onNodeWithTag("nature-proxy-token").performTextInput("sk-rejected-provider-test-only")
            ui.onNodeWithText("保存配置").performClick()
            waitText("请检查代理 HTTPS 地址和访问凭证。不可填写 DeepSeek 官方地址或 API Key。")
            ui.onNodeWithText("取消").performClick(); assertNull(config.read())
            if (live) {
                ui.onNodeWithTag("nature-configure").performScrollTo().performClick()
                ui.onNodeWithTag("nature-proxy-url").performTextInput("http://127.0.0.1:8787/v1/nature")
                ui.onNodeWithTag("nature-proxy-token").performTextInput(requireNotNull(args.getString("natureProxyToken")))
                ui.onNodeWithText("保存配置").performClick()
                ui.waitUntil(10_000) { config.read() != null }
            }
            ui.onNodeWithTag("nature-generate").performScrollTo().performClick(); ui.onNodeWithText("同意并生成").performClick()
            if (live) {
                ui.waitUntil(40_000) {
                    ui.onAllNodesWithText(ExpressionSource.AI.label).fetchSemanticsNodes(false).isNotEmpty() ||
                        ui.onAllNodesWithTag("nature-message").fetchSemanticsNodes(false).isNotEmpty()
                }
                // A provider error fails this opt-in check; it is never accepted as online success.
                ui.onNodeWithTag("nature-source").assertTextEquals(ExpressionSource.AI.label)
                ui.onNodeWithTag("nature-generate").performScrollTo().performClick()
                ui.onNodeWithText("同意并生成").performClick(); ui.waitForIdle()
                ui.waitUntil(40_000) { ui.onAllNodes(hasTestTag("nature-generate") and isEnabled()).fetchSemanticsNodes(false).isNotEmpty() }
                ui.onNodeWithTag("nature-source").assertTextEquals(ExpressionSource.AI.label)
                ui.onAllNodesWithTag("nature-message").assertCountEquals(0)
            } else {
                waitText(NatureExpressionRepository.message("configuration"))
                ui.onNodeWithTag("nature-source").assertTextEquals(ExpressionSource.LOCAL.label)
                click("自然感悟"); ui.onNodeWithText("自然感悟").assertIsSelected()
                click("静谧短诗"); ui.onNodeWithText("静谧短诗").assertIsSelected()
            }
            val publicText = ui.onNodeWithTag("nature-text").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].single().text
            screenshot(if (live) "01-culture-real-ai" else "01-culture-local")
            click("阅读自然文字 →"); waitText("自然文字"); screenshot("02-reading")
            ui.onNodeWithText("返回植物文化").performClick()
            // Activity recreation reads the cache/local fallback; it must not initiate another call.
            scenario.recreate(); waitText("植物文化")
            ui.scrollCultureTo("nature-text")
            ui.onNodeWithTag("nature-text").assertTextEquals(publicText)
            if (live) assertEquals(publicText, runBlocking { NatureExpressionRepository(context, config).current(identity).text })
            shell("wm size 880x1800"); shell("wm density 440"); shell("settings put system font_scale 1.3")
            waitText("植物文化"); ui.onNodeWithTag("nature-generate").performScrollTo().assertIsDisplayed(); screenshot("03-culture-320-large-font")
            shell("settings put system font_scale $fontScale"); shell("wm size reset"); shell("wm density reset")
            back(); waitText("植物笔记"); back(); waitText("发现自然，收藏四季。")
            assertEquals(0, runBlocking { access.database().plantDao().count() })

            val name = "桂花 · 摄影手记"
            val id = runBlocking {
                val id = access.archive().collect("nature-${UUID.randomUUID()}", Species.identified("Osmanthus fragrans", "桂花"), name, null, null)
                owner = id
                val photo = access.captures().create(context.assets.open("explore/osmanthus_kenraiz.jpg").use { it.readBytes() })
                access.archive().add(id, photo.id, System.currentTimeMillis(), "摄影：Kenraiz · CC BY-SA 4.0；参考影集，非野外实录。")
                id
            }
            val original = runBlocking { access.database().archiveDao().history(id).single() }
            val photoBefore = File(URI(original.photoUri)).readBytes()
            click("花园"); click(name); waitText("我的植物档案")
            click("阅读植物文化 →"); waitText("植物文化")
            ui.onNodeWithTag("nature-text").performScrollTo().assertTextEquals(publicText)
            back(); waitText("我的植物档案"); click("制作植物诗笺"); preview(CardTemplateId.MODERN_JOURNAL)
            val selfWritten = "把一次停留留在纸上，\n让下一次相遇仍有回响。"
            editExpression()
            ui.onNodeWithTag("card-expression-input").performTextReplacement(selfWritten)
            ui.onNodeWithTag("card-expression-input").assertTextContains(selfWritten)
            ui.onNodeWithText("应用到作品").performClick(); preview(CardTemplateId.MODERN_JOURNAL)
            ui.waitUntil(30_000) { ui.onAllNodesWithText(ExpressionSource.USER.label).fetchSemanticsNodes(false).isNotEmpty() }
            ui.onNodeWithTag("card-expression-source").performScrollTo().assertTextEquals(ExpressionSource.USER.label)
            scenario.recreate(); waitText("植物诗笺"); preview(CardTemplateId.MODERN_JOURNAL)
            ui.waitUntil(35_000) {
                runCatching { ui.onNodeWithTag("card-expression-source").assertTextEquals(ExpressionSource.USER.label); true }.getOrDefault(false)
            }
            editExpression()
            ui.onNodeWithTag("card-expression-input").assertTextContains(selfWritten)
            ui.onNodeWithText("取消").performClick()
            // Explicit user action, not asynchronous generation, restores automatic AI/local content.
            click("自动选择文字"); preview(CardTemplateId.MODERN_JOURNAL)
            ui.onNode(isToggleable()).performScrollTo().performClick()
            ui.waitUntil(30_000) { runCatching { ui.onNode(isToggleable()).assertIsOn(); true }.getOrDefault(false) }
            preview(CardTemplateId.MODERN_JOURNAL)
            for (type in CardTemplateId.entries) {
                ui.onNodeWithTag("template-${type.name}").performScrollTo().performClick(); preview(type)
                ui.onNodeWithTag("card-save").performClick(); waitText("PNG 已保存到 Pictures/Wildflower Lens")
                val resolver = context.contentResolver
                val uri = resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
                    "${MediaStore.Images.Media.RELATIVE_PATH}=?", arrayOf("Pictures/Wildflower Lens/"), "${MediaStore.Images.Media._ID} DESC")!!.use {
                    check(it.moveToFirst()); android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it.getLong(0)) }
                outputs += uri
                val png = resolver.openInputStream(uri)!!.use { it.readBytes() }
                val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size)!!
                assertEquals(1600, bitmap.width); assertEquals(2400, bitmap.height); bitmap.recycle()
                val copy = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "${if (live) "real-ai-" else "local-"}${type.name.lowercase()}.png")
                    put(MediaStore.Downloads.MIME_TYPE, "image/png"); put(MediaStore.Downloads.RELATIVE_PATH, "Download/wildflower-phase6b5/exports/")
                })!!
                resolver.openOutputStream(copy)!!.use { it.write(png) }
            }
            screenshot("04-poetry-editor")
            ui.onNodeWithTag("card-share").performClick()
            ui.waitUntil(30_000) { shell("dumpsys activity activities").contains("ChooserActivity") }; screenshot("05-share")
            shell("input keyevent 4"); waitText("植物诗笺")
            assertEquals(original, runBlocking { access.database().archiveDao().history(id).single() })
            assertArrayEquals(photoBefore, File(URI(original.photoUri)).readBytes())
            assertArrayEquals(catalogBefore, context.assets.open("culture/culture_entries.json").use { it.readBytes() })
            assertEquals(3, access.database().openHelper.readableDatabase.version)
        } finally {
            scenario.close()
            shell("settings put system font_scale $fontScale"); shell("wm size reset"); shell("wm density reset")
            runBlocking { owner?.let { id -> access.database().archiveDao().history(id).forEach { access.archive().delete(id, it.id) }; access.plants().delete(id) } }
            outputs.forEach { context.contentResolver.delete(it, null, null) }
            if (live && args.getString("keepNatureCacheForCold") == "true" && cacheBefore == null && cache.exists()) {
                File(context.cacheDir, "phase6b5-owned-cache-proof").writeText(sha(cache.readBytes()))
            } else if (cacheBefore == null) cache.delete() else { cache.parentFile!!.mkdirs(); cache.writeBytes(cacheBefore) }
            prefs.edit().apply { if (previous == null) remove("encrypted") else putString("encrypted", previous) }.commit()
            options.edit().apply { if (oldStyle == null) remove(identity.key) else putString(identity.key, oldStyle) }.commit()
        }
    }
    private fun sha(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun verifyColdCache() {
        val identity = CultureIdentityResolver.resolve(scientific = "Osmanthus fragrans")
        val file = File(context.filesDir, "nature-expressions-v1/${NatureTextPolicy.cacheKey(identity, "quiet")}")
        val proof = File(context.cacheDir, "phase6b5-owned-cache-proof")
        check(proof.isFile && proof.readText() == sha(file.readBytes())) { "Only the previous opt-in test's own cache may be verified/removed" }
        val expected = runBlocking { NatureExpressionRepository(context, NatureServiceConfig(context)).current(identity, "quiet") }
        assertEquals(ExpressionSource.AI, expected.source)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("发现自然，收藏四季。")
            ui.onNodeWithTag("explore-hero").performClick(); waitText("植物笔记")
            click("阅读植物文化 →"); waitText("植物文化")
            ui.onNodeWithTag("nature-text").performScrollTo().assertTextEquals(expected.text)
            ui.onNodeWithTag("nature-source").assertTextEquals(ExpressionSource.AI.label)
            screenshot("06-cold-start-cached-ai")
            assertEquals(0, runBlocking { access.database().plantDao().count() })
        }
        file.delete(); proof.delete()
    }
}

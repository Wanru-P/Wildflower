package com.floralens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.data.culture.NatureServiceConfig
import com.floralens.domain.model.Species
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.FileInputStream
import java.util.UUID

/** Real Room/navigation/UI journey. Real provider calls are explicit through chatLive=true. */
class Phase6B6JourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val access get() = EntryPointAccessors.fromApplication(context, Phase1TestAccess::class.java)
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use { FileInputStream(it.fileDescriptor).bufferedReader().readText() }
    private fun waitText(text: String, timeout: Long = 35_000) = ui.waitUntil(timeout) { ui.onAllNodesWithText(text, substring = true).fetchSemanticsNodes(false).isNotEmpty() }
    private fun waitReply() {
        val errors = listOf("网络或问答服务不可用", "提问有些频繁", "回答不适合展示", "尚未配置安全问答服务", "授权已失效")
        ui.waitUntil(5_000) {
            ui.onAllNodesWithContentDescription("停止回答").fetchSemanticsNodes(false).isNotEmpty() ||
                errors.any { ui.onAllNodesWithText(it, substring = true).fetchSemanticsNodes(false).isNotEmpty() }
        }
        ui.waitUntil(45_000) {
            ui.onAllNodesWithContentDescription("发送问题").fetchSemanticsNodes(false).isNotEmpty() ||
                errors.any { ui.onAllNodesWithText(it, substring = true).fetchSemanticsNodes(false).isNotEmpty() }
        }
        val found = errors.firstOrNull { ui.onAllNodesWithText(it, substring = true).fetchSemanticsNodes(false).isNotEmpty() }
        assertEquals("AI reply failed", null, found)
        assertTrue(ui.onAllNodes(hasTestTag("chat-ai-message")).fetchSemanticsNodes(false).isNotEmpty())
    }
    private fun screenshot(name: String) {
        ui.waitForIdle(); Thread.sleep(700); ui.waitForIdle()
        shell("mkdir -p /sdcard/Download/wildflower-phase6b6")
        shell("screencap -p /sdcard/Download/wildflower-phase6b6/$name.png")
    }
    private fun back() { Espresso.pressBack(); ui.waitForIdle() }

    @Test fun speciesAndPlantEntries_consent_realMultiTurn_recreation_privacyAndFallback() {
        val args = InstrumentationRegistry.getArguments()
        val live = args.getString("chatLive") == "true"
        val config = NatureServiceConfig(context)
        val configPrefs = context.getSharedPreferences("nature-service-private", 0)
        val previousConfig = configPrefs.getString("encrypted", null)
        val consentPrefs = context.getSharedPreferences("plant-chat-consent", 0)
        val previousConsent = consentPrefs.getBoolean("accepted-v1", false)
        val fontScale = shell("settings get system font_scale").trim().takeIf { it.toFloatOrNull() != null } ?: "1.0"
        var plantId: Long? = null
        var photoUri: String? = null
        runBlocking {
            check(access.plants().observeAll().first().isEmpty()) { "Dedicated empty AVD required; never erase user archives" }
            access.settings().setOnboarded()
            val capture = access.captures().create(context.assets.open("explore/ginkgo.jpg").use { it.readBytes() })
            val id = access.archive().collect("phase6b6-${UUID.randomUUID()}", Species.identified("Ginkgo biloba", "银杏"), "银杏观察档案", null, null)
            access.archive().add(id, capture.id, System.currentTimeMillis(), "授权参考影集，用于功能验收；非野外实录。")
            plantId = id
            photoUri = access.database().archiveDao().history(id).single().photoUri
        }
        consentPrefs.edit().remove("accepted-v1").commit()
        if (live) config.save("http://127.0.0.1:8787/v1/nature", requireNotNull(args.getString("chatProxyToken"))) else config.clear()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitText("发现自然，收藏四季。")
            ui.onNodeWithTag("explore-hero").performClick(); waitText("植物笔记")
            ui.onNodeWithTag("species-chat-entry").performScrollTo().assertIsDisplayed(); screenshot("01-species-entry")
            ui.onNodeWithTag("species-chat-entry").performClick(); waitText("花间问答")
            waitText("植物详情"); ui.onAllNodes(hasTestTag("chat-ai-message")).assertCountEquals(0)
            screenshot("02-chat-empty")
            ui.onNodeWithText("下次观察可以重点记录什么？").performScrollTo().performClick(); waitText("使用花间问答")
            screenshot("03-consent")
            ui.onNodeWithText("取消").performClick(); ui.onAllNodes(hasTestTag("chat-user-message")).assertCountEquals(0)
            ui.onNodeWithText("下次观察可以重点记录什么？").performClick(); ui.onNodeWithText("同意并提问").performClick()
            if (live) {
                waitReply()
                for ((index, question) in listOf("怎样避免把相近植物认错？", "季节变化时，我可以怎样持续记录？").withIndex()) {
                    ui.onNodeWithTag("chat-input").performTextInput(question)
                    if (index == 0) ui.onNodeWithTag("chat-input").performImeAction()
                    else ui.onNodeWithContentDescription("发送问题").performClick()
                    waitText(question, 5_000)
                    waitReply()
                }
                screenshot("04-real-multiturn")
                scenario.recreate(); waitText("花间问答")
                waitText("季节变化时，我可以怎样持续记录？")
                assertTrue(ui.onAllNodes(hasTestTag("chat-ai-message")).fetchSemanticsNodes(false).isNotEmpty())
            } else {
                waitText("尚未配置安全问答服务")
                ui.onAllNodes(hasTestTag("chat-ai-message")).assertCountEquals(0)
                ui.onNodeWithText("取消").performClick()
            }
            back(); waitText("植物笔记"); ui.onNodeWithTag("species-chat-entry").performScrollTo().assertIsDisplayed()
            back(); waitText("发现自然，收藏四季。"); ui.onNodeWithText("花园").performClick(); waitText("My Garden")
            ui.onNodeWithText("银杏观察档案").performClick(); waitText("我的植物档案")
            ui.onNodeWithTag("plant-chat-entry").performScrollTo().assertIsDisplayed(); screenshot("05-plant-entry")
            ui.onNodeWithTag("plant-chat-entry").performClick(); waitText("花间问答"); waitText("我的档案")
            ui.onAllNodes(hasTestTag("chat-ai-message")).assertCountEquals(0)
            screenshot("06-plant-context")
            back(); waitText("我的植物档案")
            ui.onNodeWithText("认识这个物种  →").performScrollTo().performClick(); waitText("植物笔记")
            ui.onNodeWithTag("species-chat-entry").performScrollTo().performClick(); waitText("花间问答")
            ui.onAllNodes(hasTestTag("chat-ai-message")).assertCountEquals(0)
            shell("wm size 880x1800"); shell("wm density 440"); shell("settings put system font_scale 1.3")
            ui.waitForIdle(); screenshot("07-chat-320-font130")
            shell("settings put system font_scale $fontScale"); shell("wm size reset"); shell("wm density reset")
            ui.onNodeWithContentDescription("问答设置").performClick(); ui.onNodeWithText("隐私与授权").performClick(); waitText("隐私与授权")
            ui.onNodeWithText("撤回问答授权").performClick(); waitText("已撤回花间问答授权")
            config.clear()
            ui.onNodeWithText("下次观察可以重点记录什么？").performScrollTo().performClick(); waitText("使用花间问答")
            ui.onNodeWithText("同意并提问").performClick(); waitText("尚未配置安全问答服务")
            ui.onAllNodes(hasTestTag("chat-ai-message")).assertCountEquals(0)
            ui.onNodeWithText("取消").performClick(); waitText("尚未配置安全问答服务")
            screenshot("08-service-fallback")
            assertEquals(1, runBlocking { access.database().archiveDao().history(requireNotNull(plantId)).size })
            assertTrue(runBlocking { access.plants().observeById(requireNotNull(plantId)).first() } != null)
        } finally {
            scenario.close()
            shell("settings put system font_scale $fontScale"); shell("wm size reset"); shell("wm density reset")
            runBlocking {
                plantId?.let { id -> access.database().archiveDao().history(id).forEach { access.archive().delete(id, it.id) }; access.plants().delete(id) }
                photoUri?.let { }
            }
            configPrefs.edit().apply { if (previousConfig == null) remove("encrypted") else putString("encrypted", previousConfig) }.commit()
            consentPrefs.edit().putBoolean("accepted-v1", previousConsent).commit()
        }
    }
}

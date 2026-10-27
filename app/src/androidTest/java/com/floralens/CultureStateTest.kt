package com.floralens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.data.culture.CultureCatalog
import com.floralens.ui.screens.culture.*
import com.floralens.ui.screens.seasons.*
import com.floralens.domain.season.*
import com.floralens.ui.theme.FloraLensTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CultureStateTest {
    @get:Rule val ui = createComposeRule()
    private fun entry(key: String) = CultureCatalog.parse(InstrumentationRegistry.getInstrumentation().targetContext.assets.open("culture/culture_entries.json").bufferedReader().use { it.readText() }).single { it.speciesKey == key }
    @Test fun noWorksNoPoetryBlockAndDraftNoticeVisible() {
        ui.setContent { FloraLensTheme { CultureContent(CultureUiState(false,entry("scientific:cymbidium goeringii"),editorial=true),{}){} } }
        ui.onAllNodesWithText(if (BuildConfig.INTERNAL_TOOLS) "资料审阅 · 非种级认定\n编辑草稿／待核实 · 未经独立来源核验" else "待核文化笔记 · 现代整理，尚未独立核实，不作为古典文献或历史事实。").onFirst().performScrollTo().assertIsDisplayed()
        ui.onAllNodesWithTag("culture-excerpt").assertCountEquals(0)
    }
    @Test fun unavailableSourceGivesFeedbackWithoutClaimingVerified() {
        ui.setContent { FloraLensTheme { CultureContent(CultureUiState(false,entry("scientific:ginkgo biloba")),{}){ throw IllegalStateException("No browser") } } }
        ui.onNodeWithTag("culture-list").performScrollToNode(hasText("查看作品出处 ↗"))
        ui.onNodeWithText("查看作品出处 ↗").performClick()
        ui.waitForIdle()
        ui.onNodeWithText("来源暂时无法打开，请检查网络或浏览器后重试。").assertIsDisplayed()
    }
    @Test fun taxonPendingReadingRetainsScopeAndOriginalLiterarySource() {
        val e = entry("scientific:rosa chinensis")
        ui.setContent { FloraLensTheme { CultureContent(CultureUiState(false, e), {}) {} } }
        ui.onAllNodesWithText("文学意象导读 · 古名为文化泛称，不等同于本物种的种级认定。").onFirst().performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("culture-list").performScrollToNode(hasTestTag("culture-excerpt"))
        ui.onNodeWithTag("culture-excerpt").assertTextEquals(e.literaryWorks.single().excerpt!!)
        ui.onNodeWithText("查看作品出处 ↗").assertHasClickAction()
    }
    @Test fun approvedClassicAndAiOriginalRemainSeparate() {
        val identity = com.floralens.domain.culture.CultureIdentityResolver.resolve(scientific = "Osmanthus fragrans")
        val expression = com.floralens.domain.culture.NatureExpression("为一次相遇留下疑问，\n把目光交还给下一次认真。", com.floralens.domain.culture.ExpressionSource.AI, identity.key)
        ui.setContent { FloraLensTheme { CultureContent(CultureUiState(false, entry(identity.key), identity = identity, expression = expression), {}) {} } }
        ui.onNodeWithTag("culture-list").performScrollToNode(hasTestTag("culture-excerpt"))
        ui.onNodeWithTag("culture-excerpt").assertTextEquals("暗淡轻黄体性柔")
        ui.onNodeWithText("李清照 · 《鹧鸪天·桂》").assertExists()
        ui.onNodeWithTag("culture-list").performScrollToNode(hasTestTag("nature-source"))
        ui.onNodeWithTag("nature-source").assertTextEquals(com.floralens.domain.culture.ExpressionSource.AI.label)
        ui.onNodeWithTag("nature-text").assertTextEquals(expression.text)
    }
    @Test fun cultureEmptyRetainsBackAction() {
        var back = false
        ui.setContent { FloraLensTheme { CultureContent(CultureUiState(loading=false),{back=true}){} } }
        ui.onNodeWithTag("nature-text").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("nature-source").assertTextEquals("自然观察文字 · 草木知时")
        ui.onAllNodesWithTag("culture-excerpt").assertCountEquals(0)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("mkdir -p /sdcard/Download/wildflower-phase3").close()
        automation.executeShellCommand("screencap -p /sdcard/Download/wildflower-phase3/culture-empty.png").use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
        ui.onNodeWithText("返回植物笔记").performScrollTo().performClick(); assertTrue(back)
    }
    @Test fun nonexistentYearHasRealScanAction() {
        var scan = false
        ui.setContent { FloraLensTheme { SeasonsContent(SeasonsUiState(SeasonArchive(loaded=true),1999),{}, {scan=true},{_,_->},{},{}) } }
        ui.onNodeWithText("这一年，还没有自然观察").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("去发现植物").performScrollTo().performClick(); assertTrue(scan)
    }
}

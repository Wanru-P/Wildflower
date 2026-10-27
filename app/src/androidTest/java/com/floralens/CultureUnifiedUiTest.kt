package com.floralens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.data.culture.CultureCatalog
import com.floralens.domain.culture.CultureIdentityResolver
import com.floralens.domain.culture.ExpressionSource
import com.floralens.domain.culture.NatureExpression
import com.floralens.ui.screens.culture.CultureContentWithActions
import com.floralens.ui.screens.culture.CultureUiState
import com.floralens.ui.theme.FloraLensTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Component contracts only: no ViewModel, network, shell commands, or device geometry changes. */
class CultureUnifiedUiTest {
    @get:Rule val ui = createComposeRule()

    private var pixelsPerDp = 1f
    private val unresolved = CultureIdentityResolver.resolve()
    private val matchedEntry by lazy {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        CultureCatalog.parse(context.assets.open("culture/culture_entries.json").bufferedReader().use { it.readText() })
            .single { it.speciesKey == "scientific:osmanthus fragrans" }
    }

    private fun matchedState() = CultureUiState(
        loading = false,
        entry = matchedEntry,
        chineseName = matchedEntry.chineseName,
        scientificName = matchedEntry.scientificName,
        identity = CultureIdentityResolver.resolve(scientific = matchedEntry.scientificName),
    )

    private fun fallbackState() = CultureUiState(loading = false, identity = unresolved)

    private fun render(
        state: CultureUiState,
        width: Dp = 320.dp,
        onGenerate: () -> Unit = {},
        onStyle: (String) -> Unit = {},
        onBack: () -> Unit = {},
        openSource: (String) -> Unit = {},
    ) {
        ui.setContent {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val hostDensity = LocalDensity.current
                // Fit an actual 800dp Compose viewport on a phone without wm size/density.
                // Only this composition's density changes; all controls remain on screen.
                val scale = minOf(1f, maxWidth.value / width.value)
                val density = Density(hostDensity.density * scale, fontScale = 1f)
                pixelsPerDp = density.density
                CompositionLocalProvider(LocalDensity provides density) {
                    FloraLensTheme {
                        Box(Modifier.width(width).fillMaxHeight().testTag("culture-test-viewport")) {
                            CultureContentWithActions(
                                state = state,
                                onBack = onBack,
                                openSource = openSource,
                                onGenerate = onGenerate,
                                onStyle = onStyle,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        ui.onNodeWithTag("culture-list").performScrollToNode(matcher)
    }

    private fun assertNoDuplicateTitle() {
        assertTrue(
            "The introduction and section headings must not repeat 自然与文字",
            ui.onAllNodesWithText("自然与文字").fetchSemanticsNodes().size <= 1,
        )
    }

    private fun assertSharedLayout(width: Dp, fallback: Boolean) {
        ui.onAllNodesWithTag("culture-list").assertCountEquals(1)
        ui.onAllNodesWithTag("culture-introduction").assertCountEquals(1)
        ui.onNodeWithTag("culture-introduction").assertIsDisplayed()
        val viewport = ui.onNodeWithTag("culture-test-viewport").fetchSemanticsNode()
        val list = ui.onNodeWithTag("culture-list").fetchSemanticsNode()
        val introduction = ui.onNodeWithTag("culture-introduction").fetchSemanticsNode()
        assertEquals(width.value * pixelsPerDp, viewport.size.width.toFloat(), 2f)
        assertTrue("The reading column must fit the viewport", list.size.width <= viewport.size.width)
        assertTrue("The introduction must fit the reading column", introduction.size.width <= list.size.width)
        assertTrue("The compact introduction must leave room for reading", introduction.size.height < list.size.height)
        assertNoDuplicateTitle()

        scrollTo(hasText("自然观察"))
        ui.onNodeWithText("自然观察").assertIsDisplayed()
        assertNoDuplicateTitle()
        scrollTo(hasText("AI 原创"))
        ui.onNodeWithText("AI 原创").assertIsDisplayed()
        assertNoDuplicateTitle()
        scrollTo(hasTestTag("nature-generate"))
        ui.onNodeWithTag("nature-generate").assertIsDisplayed().assertIsEnabled()
        if (fallback) ui.onAllNodesWithTag("culture-excerpt").assertCountEquals(0)
    }

    @Test fun matchedCultureUsesSharedLayoutAt320dp() {
        render(matchedState())
        assertSharedLayout(320.dp, fallback = false)
        scrollTo(hasTestTag("culture-excerpt"))
        ui.onNodeWithTag("culture-excerpt").assertIsDisplayed()
            .assertTextEquals(matchedEntry.literaryWorks.single().excerpt!!)
    }

    @Test fun fallbackUsesSharedLayoutWithoutClassicExcerptAt320dp() {
        render(fallbackState())
        ui.onAllNodesWithTag("culture-excerpt").assertCountEquals(0)
        assertSharedLayout(320.dp, fallback = true)
    }

    @Test fun matchedCultureUsesSharedLayoutAt800dp() {
        render(matchedState(), width = 800.dp)
        assertSharedLayout(800.dp, fallback = false)
        scrollTo(hasTestTag("culture-excerpt"))
        ui.onNodeWithTag("culture-excerpt").assertIsDisplayed()
    }

    @Test fun fallbackUsesSharedLayoutWithoutClassicExcerptAt800dp() {
        render(fallbackState(), width = 800.dp)
        assertSharedLayout(800.dp, fallback = true)
    }

    @Test fun unresolvedIdentityCanRequestGeneralNatureGeneration() {
        var requests = 0
        assertFalse(unresolved.certain)
        render(fallbackState(), onGenerate = { requests++ })
        scrollTo(hasTestTag("nature-generate"))
        ui.onNodeWithTag("nature-generate").assertIsEnabled().performClick()
        ui.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun defaultGuidanceIsLabelledAsNotGeneratedAndExplainsGeneralMode() {
        render(fallbackState())
        scrollTo(hasTestTag("nature-source"))
        ui.onNodeWithTag("nature-source").assertIsDisplayed()
            .assertTextEquals("自然观察文字 · 草木知时")
        scrollTo(hasTestTag("nature-guidance"))
        ui.onNodeWithTag("nature-guidance").assert(hasText("尚未生成", substring = true))
        scrollTo(hasText("通用", substring = true))
        ui.onAllNodesWithText("通用", substring = true).onFirst().assertIsDisplayed()
        ui.onAllNodesWithText("AI 原创 · DeepSeek · 非古典文献").assertCountEquals(0)
    }

    @Test fun generatingDisablesActionAndShowsProgressCopy() {
        var requests = 0
        render(fallbackState().copy(generating = true), onGenerate = { requests++ })
        scrollTo(hasTestTag("nature-generate"))
        ui.onNodeWithTag("nature-generate").assertIsDisplayed().assertIsNotEnabled()
            .assert(hasText("正在创作…"))
        // A disabled button must not dispatch even when a click is attempted.
        ui.onNodeWithTag("nature-generate").performClick()
        ui.runOnIdle { assertEquals(0, requests) }
    }

    @Test fun loadingDoesNotExposeAnEnabledGenerationAction() {
        var requests = 0
        render(fallbackState().copy(loading = true), onGenerate = { requests++ })
        // Loading may replace the list with a loading state or leave disabled controls visible.
        ui.onAllNodes(hasTestTag("nature-generate") and isEnabled()).assertCountEquals(0)
        ui.runOnIdle { assertEquals(0, requests) }
    }

    @Test fun failedGenerationRetainsAiExpressionAndEnablesRetry() {
        val state = matchedState()
        val expression = NatureExpression(
            "为一次相遇留下疑问，\n把目光交还给下一次认真。",
            ExpressionSource.AI,
            state.identity.key,
        )
        val message = "创作服务暂时无法连接，请稍后重试。"
        var retries = 0
        render(state.copy(expression = expression, generationMessage = message), onGenerate = { retries++ })
        scrollTo(hasTestTag("nature-source"))
        ui.onNodeWithTag("nature-source").assertIsDisplayed()
            .assertTextEquals("AI 原创 · DeepSeek · 非古典文献")
        scrollTo(hasTestTag("nature-text"))
        ui.onNodeWithTag("nature-text").assertIsDisplayed().assertTextEquals(expression.text)
        scrollTo(hasTestTag("nature-message"))
        ui.onNodeWithTag("nature-message").assertIsDisplayed().assertTextEquals(message)
        scrollTo(hasTestTag("nature-generate"))
        ui.onNodeWithTag("nature-generate").assertIsEnabled().performClick()
        ui.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun bothStyleChoicesDispatchTheirOriginalKeys() {
        val styles = mutableListOf<String>()
        render(fallbackState(), onStyle = { styles += it })
        scrollTo(hasText("自然感悟"))
        ui.onNodeWithText("自然感悟").assertIsEnabled().performClick()
        scrollTo(hasText("静谧短诗"))
        ui.onNodeWithText("静谧短诗").assertIsEnabled().performClick()
        ui.runOnIdle { assertEquals(listOf("journal", "quiet"), styles) }
    }

    @Test fun matchedCulturePreservesWorkAndTaxonomySourceCallbacksAndBack() {
        val sources = mutableListOf<String>()
        var backs = 0
        render(matchedState(), openSource = { sources += it }, onBack = { backs++ })
        scrollTo(hasText("查看作品出处 ↗"))
        ui.onNodeWithText("查看作品出处 ↗").assertIsDisplayed().performClick()
        scrollTo(hasText("查看分类学来源 ↗"))
        ui.onNodeWithText("查看分类学来源 ↗").assertIsDisplayed().performClick()
        ui.onNodeWithContentDescription("返回").performClick()
        ui.runOnIdle {
            assertEquals(listOf(matchedEntry.literaryWorks.single().sourceUrl, matchedEntry.taxonomySource!!.url), sources)
            assertEquals(1, backs)
        }
    }

    @Test fun fallbackPreservesTopBarAndReturnToPlantNoteCallbacks() {
        var backs = 0
        render(fallbackState(), onBack = { backs++ })
        scrollTo(hasText("返回植物笔记"))
        ui.onNodeWithText("返回植物笔记").assertIsDisplayed().performClick()
        ui.onNodeWithContentDescription("返回").performClick()
        ui.runOnIdle { assertEquals(2, backs) }
    }
}

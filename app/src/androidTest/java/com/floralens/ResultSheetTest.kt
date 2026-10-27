package com.floralens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import com.floralens.domain.model.IdCandidate
import com.floralens.domain.model.IdResult
import com.floralens.ui.screens.identify.IdentifyResultSheet
import com.floralens.ui.theme.FloraLensTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onParent
/**
 * Compose UI tests for the identify result sheet — confidence-variant
 * rendering and the manual-override flow. The sheet is stateless, so no
 * Hilt test infra is needed.
 */
class ResultSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val monstera = IdCandidate("Monstera deliciosa", "Swiss cheese plant", 0.92f)
    private val pothos = IdCandidate("Epipremnum aureum", "Pothos", 0.44f)

    private fun setSheet(
        result: IdResult,
        onAdd: (IdCandidate) -> Unit = {},
        onAddManually: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            FloraLensTheme {
                Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                    IdentifyResultSheet(
                        result = result,
                        capturedImage = null,
                        isSaving = false,
                        onAdd = onAdd,
                        onAddManually = onAddManually,
                        onRetake = {},
                    )
                }
            }
        }
    }

    @Test
    fun highConfidence_showsBestCandidateAndAddButton() {
        setSheet(IdResult.HighConfidence(best = monstera, alternates = listOf(pothos)))

        composeRule.onAllNodesWithText("未知植物").assertCountEquals(2)
        composeRule.onAllNodesWithText("Swiss cheese plant").assertCountEquals(0)
        composeRule.onNodeWithText("Monstera deliciosa").assertIsDisplayed()
        composeRule.onNodeWithText("查看植物详情").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun lowConfidence_showsNotSureHeaderAndPossibilities() {
        setSheet(IdResult.LowConfidence(possibilities = listOf(monstera, pothos)))

        composeRule.onNodeWithText("还不确定，可能是这些植物").assertIsDisplayed()
        composeRule.onAllNodesWithText("未知植物").assertCountEquals(2)
        composeRule.onAllNodesWithText("Pothos").assertCountEquals(0)
        composeRule.onNodeWithText("Epipremnum aureum").assertIsDisplayed()
        composeRule.onNodeWithText("查看植物详情").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun addBestGuess_invokesOnAddWithTopCandidate() {
        var added: IdCandidate? = null
        setSheet(
            IdResult.LowConfidence(possibilities = listOf(monstera, pothos)),
            onAdd = { added = it },
        )

        composeRule.onNodeWithText("查看植物详情").performScrollTo().performClick()
        assertEquals(monstera, added)
    }

    @Test
    fun chinesePlantNetNameStillShowsFortyPercentAndUncertainHeader() {
        // Explicit UI fixture, not a fabricated live API result.
        val candidate = IdCandidate("Heptaple\u200burum arboricola", "鹅掌藤", .40f,
            com.floralens.domain.model.IdentificationSource.PLANTNET)
        var selected: IdCandidate? = null
        setSheet(IdResult.LowConfidence(listOf(candidate)), onAdd = { selected = it })
        composeRule.onNodeWithText("鹅掌藤").assertIsDisplayed()
        composeRule.onNodeWithText(candidate.scientificName).assertIsDisplayed()
        composeRule.onNodeWithText("识别可信度 40% ✓").assertIsDisplayed()
        composeRule.onNodeWithText("还不确定，可能是这些植物").assertIsDisplayed()
        composeRule.onNodeWithText("来源：Pl@ntNet 在线识别").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("查看植物详情").performScrollTo().performClick()
        assertEquals(candidate, selected)
    }

    @Test
    fun manualOverride_expandsAndSubmitsTypedName() {
        var manualName: String? = null
        setSheet(
            IdResult.LowConfidence(possibilities = listOf(monstera)),
            onAddManually = { manualName = it },
        )

        composeRule.onNodeWithText("我认识它，手动填写名称").performScrollTo().performClick()
        composeRule.onNodeWithText("植物名称").assertIsDisplayed()

        composeRule.onNodeWithText("植物名称").performTextInput("Venus flytrap")
        composeRule.onNodeWithText("按手动名称查看详情").performScrollTo().performClick()

        assertEquals("Venus flytrap", manualName)
    }

    @Test
    fun manualOverride_saveDisabledWhileNameBlank() {
        setSheet(IdResult.LowConfidence(possibilities = listOf(monstera)))

        composeRule.onNodeWithText("我认识它，手动填写名称").performScrollTo().performClick()
        composeRule.onNodeWithText("按手动名称查看详情").performScrollTo().assertIsNotEnabled()
    }
}

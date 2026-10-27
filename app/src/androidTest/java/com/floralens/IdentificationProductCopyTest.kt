package com.floralens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.floralens.domain.model.IdCandidate
import com.floralens.domain.model.IdResult
import com.floralens.ui.screens.identify.IdentifyResultSheet
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/** Synthetic component inputs only; real CameraX/TFLite is tested in the strict journey. */
class IdentificationProductCopyTest {
    @get:Rule val ui = createComposeRule()
    @Test fun chineseNameLatinConfidenceAndClassification_keepOriginalCandidate() {
        val candidate = IdCandidate("Ginkgo biloba", "Ginkgo", .92f)
        var opened: IdCandidate? = null
        ui.setContent { IdentifyResultSheet(IdResult.HighConfidence(candidate, emptyList()), null, false,
            { opened = it }, {}, {}) }
        ui.onNodeWithText("银杏").assertIsDisplayed()
        ui.onNodeWithText("Ginkgo biloba").assertIsDisplayed()
        ui.onNodeWithText("识别可信度 92%", substring = true).assertIsDisplayed()
        ui.onNodeWithText("银杏科 · 银杏属").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("查看植物详情").performScrollTo().performClick()
        assertSame(candidate, opened)
    }
    @Test fun unknownSpeciesRetainsLatinAndDoesNotInventChineseTranslation() {
        val candidate = IdCandidate("Unmapped species", "Unmapped English", .42f)
        ui.setContent { IdentifyResultSheet(IdResult.LowConfidence(listOf(candidate)), null, false, {}, {}, {}) }
        ui.onNodeWithText("未知植物").assertIsDisplayed()
        ui.onNodeWithText("Unmapped species").assertIsDisplayed()
        ui.onAllNodesWithText("Unmapped English").assertCountEquals(0)
        ui.onNodeWithText("还不确定，可能是这些植物").assertIsDisplayed()
    }
}

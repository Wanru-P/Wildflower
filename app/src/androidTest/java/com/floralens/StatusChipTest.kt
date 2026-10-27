package com.floralens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.floralens.domain.model.CareStatus
import com.floralens.ui.components.StatusChip
import com.floralens.ui.theme.FloraLensTheme
import org.junit.Rule
import org.junit.Test

class StatusChipTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun overdueChip_showsOverdueLabel() {
        composeRule.setContent {
            FloraLensTheme { StatusChip(CareStatus.OVERDUE) }
        }
        composeRule.onNodeWithText("Overdue").assertIsDisplayed()
    }
}

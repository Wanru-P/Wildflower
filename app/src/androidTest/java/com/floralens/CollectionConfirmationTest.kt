package com.floralens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.floralens.domain.model.Plant
import com.floralens.ui.screens.details.CollectionConfirmation
import com.floralens.ui.screens.details.CollectionState
import com.floralens.ui.theme.FloraLensTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CollectionConfirmationTest {
    @get:Rule val ui = createComposeRule()
    private val existing = Plant(id = 7, nickname = "我的未知树", scientificName = "", commonName = "树", waterIntervalDays = 7)
    @Test fun unknownName_isNotClaimedAsVerifiedSpecies_andDefaultsToNewArchive() {
        var target: Long? = 99
        ui.setContent { FloraLensTheme { CollectionConfirmation("树", CollectionState(existing = listOf(existing)), true, false, {}, { _, id -> target = id }) } }
        ui.onNodeWithText("已有同名未知物种档案；名称相同不代表同一物种，请自主选择。").assertExists()
        ui.onNodeWithText("确认收藏").performClick()
        assertNull(target)
    }
    @Test fun explicitExistingChoice_preservesNickname_andPassesOnlyChosenId() {
        var target: Long? = null
        ui.setContent { FloraLensTheme { CollectionConfirmation("树", CollectionState(existing = listOf(existing)), true, false, {}, { _, id -> target = id }) } }
        ui.onNodeWithText("添加到：我的未知树").performScrollTo().performClick()
        ui.onNodeWithText("植物昵称").assertIsNotEnabled()
        ui.onNodeWithText("保留已有档案昵称；可在个人植物详情中编辑。").assertExists()
        ui.onNodeWithText("确认收藏").performClick()
        assertEquals(7L, target)
    }
}

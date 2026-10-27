package com.floralens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.floralens.domain.model.*
import com.floralens.ui.screens.plantdetail.PersonalPlantContent
import com.floralens.ui.screens.plantdetail.PlantDetailUiState
import com.floralens.ui.theme.FloraLensTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** UI-only boundary data, not production seeds or presentation screenshots. */
class PersonalDetailKitStateTest {
    @get:Rule val ui = createComposeRule()
    private val plant = Plant(id = 42, nickname = "很长的中文植物档案名称与自然观察的故事",
        scientificName = "Osmanthus fragrans var. aurantiacus", commonName = "桂花", waterIntervalDays = 7)

    @Test fun zeroWidthNavigationFrame_recoversAndRetainsActions() {
        val width = mutableStateOf(0.dp)
        var add = false; var gallery = false
        ui.setContent { FloraLensTheme { Box(Modifier.width(width.value).height(800.dp)) {
            PersonalPlantContent(PlantDetailUiState(false, plant), {}, {}, { _, _ -> }, {}, {},
                onAddObservation = { add = true }, onGallery = { gallery = true })
        } } }
        ui.waitForIdle()
        ui.runOnUiThread { width.value = 320.dp }
        ui.onNodeWithText("添加观察").performScrollTo().performClick()
        ui.onNodeWithText("查看全部照片").performScrollTo().performClick()
        assertTrue(add && gallery)
        ui.onNodeWithText("还没有观察照片").performScrollTo().assertIsDisplayed()
    }

    @Test fun longNameLargeFont_fullNotesAndSecondaryToolsRemainAccessible() {
        var renamed = false; var card = false; var watered = false; var leaf = false
        val note = "完整备注内容。".repeat(50)
        val observation = Observation(id = 11, plantId = 42, type = ObservationType.NOTE, note = note)
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                FloraLensTheme { Box(Modifier.width(320.dp).height(900.dp)) {
                    PersonalPlantContent(PlantDetailUiState(false, plant, history = listOf(observation)),
                        {}, { leaf = true }, { _, _ -> }, { watered = true }, {},
                        onAddObservation = {}, onGallery = {}, onRename = { renamed = true }, onCard = { card = true })
                } }
            }
        }
        ui.onNodeWithText(plant.nickname).assertExists()
        ui.onNodeWithText("查看全部照片").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("botanical-creation-entry").performScrollTo().performClick()
        ui.onNodeWithText(note).performScrollTo().assertExists()
        ui.onNodeWithText("编辑昵称").performScrollTo().performClick()
        ui.onNodeWithText("记录本次浇水").performScrollTo().performClick()
        ui.onNodeWithText("叶片健康检查").performScrollTo().performClick()
        assertTrue(renamed && card && watered && leaf)
    }

    @Test fun blankPhotoUris_doNotInventSlicesOrDiscardTextHistory() {
        ui.setContent { FloraLensTheme {
            PersonalPlantContent(PlantDetailUiState(false, plant.copy(nickname = "", scientificName = ""),
                history = listOf(Observation(id = 11, plantId = 42, type = ObservationType.WATERED, photoUri = "", note = "真实浇水事件"))),
                {}, {}, { _, _ -> }, {}, {}, onAddObservation = {})
        } }
        ui.onNodeWithText("未命名植物").assertExists()
        ui.onNodeWithText("学名未知 · 未核实").assertExists()
        ui.onNodeWithText("0 张归档照片").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("personal-photo-strip").assertDoesNotExist()
        ui.onNodeWithText("真实浇水事件").performScrollTo().assertIsDisplayed()
    }

    @Test fun loadingAndMissingArchive_keepBackAndHonestMessages() {
        val state = mutableStateOf(PlantDetailUiState())
        var back = false
        ui.setContent { FloraLensTheme { PersonalPlantContent(state.value, { back = true }, {}, { _, _ -> }, {}, {}) } }
        ui.onNodeWithText("正在读取个人档案…").assertIsDisplayed()
        ui.runOnUiThread { state.value = PlantDetailUiState(isLoading = false) }
        ui.onNodeWithText("这株植物的档案已不存在。请返回花园。").assertIsDisplayed()
        ui.onNodeWithContentDescription("返回").performClick()
        assertTrue(back)
    }

    @Test fun coverAndCreationEntry_keepSpecifiedProportions() {
        ui.setContent { FloraLensTheme { Box(Modifier.width(393.dp).height(900.dp)) {
            PersonalPlantContent(PlantDetailUiState(false, plant), {}, {}, { _, _ -> }, {}, {}, onCard = {})
        } } }
        val cover = ui.onNodeWithTag("personal-cover").fetchSemanticsNode().boundsInRoot
        assertEquals(1.9f, cover.width / cover.height, .01f)
        ui.onNodeWithTag("botanical-creation-entry").performScrollTo()
        val creation = ui.onNodeWithTag("botanical-creation-entry").fetchSemanticsNode().boundsInRoot
        // ratio removes actual emulator density; baseline font scale permits 72–80dp height.
        val heightDp = creation.height * 361f / cover.width
        assertTrue("Background art must not stretch the creation card: $heightDp dp", heightDp in 72f..80f)
    }
}

package com.floralens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.floralens.data.explore.FeaturedSpecies
import com.floralens.domain.model.Plant
import com.floralens.domain.model.CareStatus
import com.floralens.ui.screens.details.DetailsUiState
import com.floralens.ui.screens.details.SpeciesDetailContent
import com.floralens.ui.screens.plants.MyGardenContent
import com.floralens.ui.screens.plants.MyPlantsUiState
import com.floralens.ui.screens.plants.PlantWithStatus
import com.floralens.ui.screens.plantdetail.PersonalPlantContent
import com.floralens.ui.screens.plantdetail.PlantDetailUiState
import com.floralens.ui.theme.FloraLensTheme
import org.junit.Rule
import org.junit.Test
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class EditorialStateTest {
    @get:Rule val ui = createComposeRule()
    private fun species(state: DetailsUiState) {
        ui.setContent { FloraLensTheme { SpeciesDetailContent(state, "Osmanthus fragrans", "桂花", null, false, false, {}, {}) } }
    }
    @Test fun speciesLoading_keepsTitleAndHeroSlot() {
        species(DetailsUiState.Loading(emptyList()))
        ui.onNodeWithText("桂花").assertIsDisplayed()
        ui.onNodeWithText("正在整理植物资料…").performScrollTo().assertIsDisplayed()
    }
    @Test fun offlineWithoutCache_isHonestAndRecoverable() {
        species(DetailsUiState.Error("Osmanthus fragrans", offlineAndNotSaved = true))
        ui.onNodeWithText("当前离线，尚无此物种的扩展资料缓存。联网后可再试。").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("重新加载").performScrollTo().assertHasClickAction()
        ui.onNodeWithText("文化资料正在整理。这里仅展示核实过的文字与出处。").performScrollTo().assertIsDisplayed()
    }
    @Test fun missingPlant_isNotAnEmptyScreen() {
        ui.setContent { FloraLensTheme { PersonalPlantContent(PlantDetailUiState(isLoading = false), {}, {}, { _, _ -> }, {}, {}) } }
        ui.onNodeWithText("这株植物的档案已不存在。请返回花园。").assertIsDisplayed()
    }
    @Test fun emptyGarden_hasWorkingScanAction() {
        var clicked = false
        ui.setContent { FloraLensTheme { MyGardenContent(MyPlantsUiState(isLoading = false), {}, { clicked = true }, {}, {}, {}) } }
        ui.onNodeWithText("去识别").performScrollTo().performClick()
        org.junit.Assert.assertTrue(clicked)
    }
    @Test fun gardenSurvivesZeroWidthNavigationFrameAndRecoversItsAction() {
        val width = mutableStateOf(0.dp)
        var clicked = false
        ui.setContent {
            FloraLensTheme {
                Box(Modifier.width(width.value).height(700.dp)) {
                    MyGardenContent(MyPlantsUiState(isLoading = false), {}, { clicked = true }, {}, {}, {})
                }
            }
        }
        ui.waitForIdle() // Incoming AnimatedContent may legitimately start at zero width.
        ui.runOnUiThread { width.value = 393.dp }
        ui.onNodeWithText("去识别").performScrollTo().assertIsDisplayed().performClick()
        org.junit.Assert.assertTrue(clicked)
    }
    @Test fun longName_andBrokenPhoto_preserveRealIdAction() {
        var selected = 0L
        val p = Plant(id = 42, nickname = "Debug · 山间一株具有很长中文名称的植物观察记录", scientificName = "Osmanthus fragrans var. aurantiacus",
            commonName = "桂花", photoUri = "file:///missing-photo.jpg", waterIntervalDays = 7)
        ui.setContent { FloraLensTheme { MyGardenContent(MyPlantsUiState(listOf(PlantWithStatus(p, CareStatus.HEALTHY)), false), { selected = it }, {}, {}, {}, {}) } }
        ui.onNodeWithText(p.nickname).assertIsDisplayed().performClick()
        org.junit.Assert.assertEquals(42L, selected)
    }
}

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
import com.floralens.domain.model.CareStatus
import com.floralens.domain.model.Plant
import com.floralens.domain.season.Season
import com.floralens.ui.screens.plants.MyGardenContent
import com.floralens.ui.screens.plants.MyPlantsUiState
import com.floralens.ui.screens.plants.PlantWithStatus
import com.floralens.ui.theme.FloraLensTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** UI boundary tests only; no production fixtures or screenshots are generated here. */
class MyGardenKitStateTest {
    @get:Rule val ui = createComposeRule()
    private val plant = Plant(id = 42, nickname = "测试 · 很长的中文植物档案名称",
        scientificName = "Osmanthus fragrans var. aurantiacus", commonName = "桂花", waterIntervalDays = 7)

    @Test fun populatedGarden_survivesZeroWidthFrameAndPreservesIdClick() {
        val width = mutableStateOf(0.dp)
        var selected = 0L
        ui.setContent {
            FloraLensTheme {
                Box(Modifier.width(width.value).height(900.dp)) {
                    MyGardenContent(MyPlantsUiState(listOf(PlantWithStatus(plant, CareStatus.HEALTHY)), false),
                        { selected = it }, {}, {}, {}, {}, {}, {})
                }
            }
        }
        ui.waitForIdle()
        ui.runOnUiThread { width.value = 320.dp }
        ui.onNodeWithTag("garden-grid").performScrollToNode(hasTestTag("garden-plant-42"))
        ui.onNodeWithTag("garden-plant-42").assertIsDisplayed().performClick()
        assertEquals(42L, selected)
    }

    @Test fun narrowLargeFont_keepsUtilitiesAndSeasonActionsUsable() {
        var season: Season? = null
        var care = false
        var settings = false
        var journal = false
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                FloraLensTheme {
                    Box(Modifier.width(320.dp).height(1000.dp)) {
                        MyGardenContent(MyPlantsUiState(listOf(PlantWithStatus(plant, CareStatus.HEALTHY)), false),
                            {}, {}, { settings = true }, {}, { care = true }, { journal = true }, { season = it })
                    }
                }
            }
        }
        ui.onNodeWithText("养护提醒").performClick()
        ui.onNodeWithText("设置").performClick()
        ui.onNodeWithText("四季手账 →").performClick()
        ui.onNodeWithText("冬季").performClick()
        assertTrue(care && settings && journal)
        assertEquals(Season.WINTER, season)
    }

    @Test fun seasonWithNoMatches_keepsRealCollectionCountAndRecoveryAction() {
        var scan = false
        ui.setContent {
            FloraLensTheme {
                MyGardenContent(MyPlantsUiState(isLoading = false, selectedSeason = Season.SPRING, totalPlants = 1),
                    {}, { scan = true }, {}, {}, {}, {}, {})
            }
        }
        ui.onNodeWithText("1 株收藏", substring = true).assertIsDisplayed()
        ui.onNodeWithText("这一季，尚无植物观察").assertIsDisplayed()
        ui.onNodeWithText("去识别").performScrollTo().performClick()
        assertTrue(scan)
    }
}

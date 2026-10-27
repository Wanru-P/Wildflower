package com.floralens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.floralens.domain.model.*
import com.floralens.ui.screens.observation.GalleryState
import com.floralens.ui.screens.observation.ObservationGalleryContent
import com.floralens.ui.theme.FloraLensTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ObservationGalleryKitStateTest {
    @get:Rule val ui = createComposeRule()
    private val plant = Plant(id = 42, nickname = "很长的中文植物观察档案与四季摄影记忆", scientificName = "Osmanthus fragrans", commonName = "桂花", waterIntervalDays = 7)

    @Test fun zeroWidthFrame_emptyStateRecoversWithRealCallbacks() {
        val width = mutableStateOf(0.dp)
        var added = false
        ui.setContent { FloraLensTheme { Box(Modifier.width(width.value).height(700.dp)) {
            ObservationGalleryContent(GalleryState(plant), {}, { added = true }, {})
        } } }
        ui.waitForIdle(); ui.runOnUiThread { width.value = 320.dp }
        ui.onNodeWithTag("observation-gallery-grid").performScrollToNode(hasTestTag("gallery-add-observation"))
        ui.onNodeWithText("添加观察").assertHasClickAction().performClick()
        assertTrue(added)
    }

    @Test fun narrowLargeFont_threeLineSummaryAnd48dpMenu() {
        val notes = "长备注必须保留到详情，照片集仅显示摘要。".repeat(20)
        var selected = 0L
        val photos = listOf(Observation(id = 11, plantId = 42, type = ObservationType.PHOTO_OBSERVATION, photoUri = "file:///missing.jpg", note = notes),
            Observation(id = 12, plantId = 42, type = ObservationType.PHOTO_OBSERVATION, photoUri = "file:///missing2.jpg", note = "短备注"))
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                FloraLensTheme { Box(Modifier.width(320.dp).height(900.dp)) {
                    ObservationGalleryContent(GalleryState(plant.copy(coverObservationId = 11), photos), {}, {}, { selected = it })
                } }
            }
        }
        ui.onNodeWithTag("gallery-note-11", useUnmergedTree = true).assertExists()
        val results = mutableListOf<TextLayoutResult>()
        ui.onNodeWithTag("gallery-note-11", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue(results.single().lineCount <= 3)
        assertTrue(results.single().hasVisualOverflow)
        ui.onNodeWithTag("gallery-cover-11", useUnmergedTree = true).assertExists()
        ui.onNodeWithTag("gallery-cover-12", useUnmergedTree = true).assertDoesNotExist()
        ui.onNodeWithContentDescription("更多观察操作 11").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        assertEquals(11L, selected)
        assertEquals(notes, photos.first().note)
    }

    @Test fun tablet_keepsTwoColumnsAnd760dpReadingWidth() {
        val photos = (1L..4L).map { Observation(id = it, plantId = 42, type = ObservationType.PHOTO_OBSERVATION, photoUri = "file:///missing$it.jpg") }
        var pixelsPerDp = 1f
        ui.setContent { pixelsPerDp = LocalDensity.current.density; FloraLensTheme { Box(Modifier.requiredWidth(960.dp).height(1000.dp)) {
            ObservationGalleryContent(GalleryState(plant, photos), {}, {}, {})
        } } }
        assertEquals(760f * pixelsPerDp, ui.onNodeWithTag("observation-gallery-grid").fetchSemanticsNode().size.width.toFloat(), 2f)
        val first = ui.onNodeWithTag("gallery-card-1").fetchSemanticsNode().boundsInRoot
        val second = ui.onNodeWithTag("gallery-card-2").fetchSemanticsNode().boundsInRoot
        val third = ui.onNodeWithTag("gallery-card-3").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 2f)
        assertTrue(third.top > first.bottom)
    }
}

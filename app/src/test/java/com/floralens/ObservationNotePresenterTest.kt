package com.floralens

import com.floralens.ui.presentation.ObservationNotePresenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObservationNotePresenterTest {
    @Test fun observationAndLicensedPhotoCreditAreSeparatedWithoutRewriting() {
        val raw = "把目光留给枝叶，记录花朵与光影的细节。\n摄影：Forest & Kim Starr · CC BY 2.0"
        val shown = ObservationNotePresenter.split(raw)
        assertEquals("把目光留给枝叶，记录花朵与光影的细节。", shown.observation)
        assertEquals("摄影：Forest & Kim Starr · CC BY 2.0", shown.photoSource)
    }

    @Test fun ordinaryUserNoteRemainsObservationContent() {
        val shown = ObservationNotePresenter.split("雨后叶片舒展。\n今天第一次记录。")
        assertEquals("雨后叶片舒展。\n今天第一次记录。", shown.observation)
        assertNull(shown.photoSource)
    }

    @Test fun creditOnlyNoteDoesNotInventAnObservation() {
        val shown = ObservationNotePresenter.split("许可：CC BY-SA 4.0")
        assertNull(shown.observation)
        assertEquals("许可：CC BY-SA 4.0", shown.photoSource)
    }
}

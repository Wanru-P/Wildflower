package com.floralens

import com.floralens.domain.model.IdentificationMode
import com.floralens.ui.screens.identify.IdentifyUiState
import com.floralens.ui.screens.about.AboutUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class IdentificationModeTest {
    @Test fun firstInstallDefaultsToOnline() {
        assertEquals(IdentificationMode.ONLINE, IdentificationMode.DEFAULT)
        assertEquals(IdentificationMode.ONLINE, IdentificationMode.fromStored(null))
        assertEquals(IdentificationMode.ONLINE, IdentifyUiState().identificationMode)
        assertEquals(IdentificationMode.ONLINE, AboutUiState().identificationMode)
    }

    @Test fun savedOfflineAndOnlineChoicesArePreserved() {
        assertEquals(IdentificationMode.OFFLINE, IdentificationMode.fromStored("OFFLINE"))
        assertEquals(IdentificationMode.ONLINE, IdentificationMode.fromStored("ONLINE"))
    }

    @Test fun invalidStoredValueUsesCurrentDefault() {
        assertEquals(IdentificationMode.ONLINE, IdentificationMode.fromStored("invalid"))
        assertEquals(IdentificationMode.ONLINE, IdentificationMode.fromStored(""))
    }
}

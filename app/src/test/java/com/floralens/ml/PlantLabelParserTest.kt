package com.floralens.ml

import org.junit.Assert.assertEquals
import org.junit.Test

class PlantLabelParserTest {
    @Test fun backgroundFirstCsv_isMappedByExplicitOutputId_notLineNumber() {
        assertEquals(listOf("Ginkgo biloba", "Camellia japonica", "background"),
            parsePlantLabels(sequenceOf("id,name", "2,background", "0,Ginkgo biloba", "1,\"Camellia japonica\"")))
    }
    @Test fun plainLabels_preserveOrderAndIgnoreBlankRows() {
        assertEquals(listOf("Ginkgo biloba", "background"), parsePlantLabels(sequenceOf("", "Ginkgo biloba", "background")))
    }
    @Test(expected = IllegalArgumentException::class) fun missingOutputId_isRejected() {
        parsePlantLabels(sequenceOf("0,Ginkgo biloba", "2,background"))
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateOutputId_isRejected() {
        parsePlantLabels(sequenceOf("0,Ginkgo biloba", "0,background"))
    }
}

package com.floralens

import com.floralens.ml.PlantNetCommonNames
import com.floralens.ui.presentation.PlantDisplayNames
import org.junit.Assert.assertEquals
import org.junit.Test

class PlantNetCommonNamesTest {
    @Test fun chineseCanFollowEnglishOrPinyin() {
        assertEquals("鹅掌藤", PlantNetCommonNames.preferred(
            listOf("Dwarf umbrella tree", "E zhang teng", " 鹅掌藤 ", "七叶莲"), "Heptapleurum arboricola"))
        assertEquals("銀杏", PlantNetCommonNames.preferred(listOf("Ginkgo", "銀杏"), "Ginkgo biloba"))
        assertEquals("㭘木", PlantNetCommonNames.preferred(listOf("Tree", "㭘木"), "Unmapped species"))
    }

    @Test fun firstChineseNameKeepsProviderOrder() {
        assertEquals("鹅掌藤", PlantNetCommonNames.preferred(listOf("鹅掌藤", "七叶莲"), "Heptapleurum arboricola"))
    }

    @Test fun missingChineseNeverInventsTranslation() {
        val scientific = "Heptaple\u200burum arboricola"
        val common = PlantNetCommonNames.preferred(listOf(" ", " Dwarf umbrella tree "), scientific)
        assertEquals("Dwarf umbrella tree", common)
        assertEquals("未知植物", PlantDisplayNames.chinese(scientific, common))
        assertEquals(scientific, PlantNetCommonNames.preferred(listOf("", " \t"), scientific))
        assertEquals(scientific, PlantNetCommonNames.preferred(emptyList(), scientific))
    }

    @Test fun unknownSpeciesCanUseProviderChineseWithoutLocalHardcoding() {
        val scientific = "Heptaple\u200burum arboricola"
        val name = PlantNetCommonNames.preferred(listOf("鹅掌藤", "七叶莲"), scientific)
        assertEquals("鹅掌藤", PlantDisplayNames.chinese(scientific, name))
        assertEquals("Heptaple\u200burum arboricola", scientific)
        assertEquals("识别可信度 40%", PlantDisplayNames.confidence(.40f))
    }
}

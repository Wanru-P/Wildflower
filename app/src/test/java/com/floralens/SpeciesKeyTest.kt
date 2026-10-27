package com.floralens
import com.floralens.domain.model.*
import org.junit.Test
import org.junit.Assert.*
class SpeciesKeyTest {
    @Test fun scientificIdentity_isStableAcrossWhitespaceCase() {
        assertEquals(SpeciesKey.fromLegacy("Osmanthus fragrans", "桂花"), SpeciesKey.fromLegacy(" osmanthus  fragrans ", "金桂"))
    }
    @Test fun manualName_doesNotForgeLatinIdentity() {
        assertNull(Species.manual("Osmanthus fragrans").scientificName)
        assertTrue(Species.manual("Osmanthus fragrans").key.startsWith("unknown:"))
    }
    @Test fun unknownNames_haveStableDistinctKeys() {
        assertEquals(SpeciesKey.unknown("  图书馆的树 "), SpeciesKey.unknown("图书馆的树"))
        assertNotEquals(SpeciesKey.unknown("图书馆的树"), SpeciesKey.unknown("操场的树"))
    }
    @Test fun speciesHasNoConfidence_andPlantIdentityNotUnique() {
        val a = Plant(nickname = "树一", scientificName = "Ginkgo biloba", commonName = "银杏", waterIntervalDays = 7)
        val b = a.copy(id = 2, nickname = "树二")
        assertEquals(a.speciesKey, b.speciesKey); assertNotEquals(a.id, b.id)
        assertNull(Observation(plantId = 1, type = ObservationType.PHOTO_OBSERVATION).identificationJson)
    }
}

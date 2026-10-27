package com.floralens

import com.floralens.domain.card.*
import com.floralens.data.culture.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CardValidationTest {
    private val c = CardContent(1,2,"校园银杏","银杏","Ginkgo biloba","file:/private/photo.jpg", LocalDate.of(2026,10,2),"真实备注","秋","scientific:ginkgo biloba")
    private fun denied(content: CardContent = c, owner: Long = 1, readable: Boolean = true) {
        assertTrue(runCatching { CardValidation.validate(content, owner, readable) }.exceptionOrNull() is IllegalArgumentException)
    }
    @Test fun validSnapshotDoesNotAlterContent() { assertEquals(c,CardValidation.validate(c,1,true).content) }
    @Test fun wrongOwnerRejected() { denied(owner = 9) }
    @Test fun missingPhotoRejected() { denied(readable = false) }
    @Test fun invalidIdsAndDateRejected() { denied(c.copy(observationId = 0)); denied(c.copy(observedDate = LocalDate.of(10000,1,1))) }
    @Test fun longInputRejectedNotTruncated() { denied(c.copy(nickname = "花".repeat(101))); denied(c.copy(note = "字".repeat(601))); denied(c.copy(scientificName = "a".repeat(301))) }
    @Test fun longChineseMixedNamesAndNotesRetained() {
        val long = c.copy(nickname = "图书馆旁连续观察的银杏树",scientificName = "Ginkgo biloba var. example (a test label, not taxonomy)",note = "真实备注，field notes。".repeat(12))
        assertEquals(long,CardValidation.validate(long,1,true).content)
    }
    @Test fun emptyCultureAndNoteRemainEmpty() { assertNull(CardValidation.validate(c.copy(note = null),1,true).content.approvedCulture) }
    @Test fun editorialCultureAndUnsafeApprovalRejected() {
        val entry = CultureEntry(c.speciesKey,c.scientificName,"银杏","","",emptyList(),
            listOf(LiteraryWork("测试作者","测试作品","测试文字","https://example.org/source","test")),TaxonomySource("test","https://example.org/taxon"),null,
            "source_checked","ready_for_editorial_review",true,null,null,null,true,true)
        denied(c.copy(approvedCulture = entry))
        denied(c.copy(approvedCulture = entry.copy(publicationStatus = "approved_for_publication",safeForAutomaticSpeciesMatch = false)))
        denied(c.copy(approvedCulture = entry.copy(publicationStatus = "approved_for_publication",speciesKey = "scientific:another plant")))
    }
    @Test fun futureExplicitApprovalRequiresEvidence() {
        val entry = CultureEntry(c.speciesKey,c.scientificName,"银杏","","",emptyList(),listOf(LiteraryWork("测试作者","测试作品","测试文字","https://example.org/source","test")),TaxonomySource("test","https://example.org/taxon"),null,"source_checked","approved_for_publication",true,null,null,null,true,true)
        assertNotNull(CardValidation.validate(c.copy(approvedCulture = entry),1,true).content.approvedCulture)
    }
}

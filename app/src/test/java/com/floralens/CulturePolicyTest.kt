package com.floralens

import com.floralens.data.culture.*
import org.junit.Assert.*
import org.junit.Test

class CulturePolicyTest {
    private val entry = CultureEntry("scientific:ginkgo biloba", "Ginkgo biloba", "银杏", "主题", "原文保持",
        listOf("赠答"), listOf(LiteraryWork("欧阳修", "作品", "短摘", "https://zh.wikisource.org/wiki/example", "historical")),
        TaxonomySource("Kew", "https://powo.science.kew.org/taxon/example"), "对应有限", "source_checked", "ready_for_editorial_review", true,
        "see_matchNote", "短摘", "supplied", true, true)
    @Test fun normalizedScientificKey_exactAndWhitespace() {
        assertEquals(entry.speciesKey, CulturePolicy.canonicalKey("scientific: GINKGO   biloba "))
        assertNotEquals(entry.speciesKey, CulturePolicy.canonicalKey("scientific:Ginkgo testensis"))
    }
    @Test fun unknownAndChineseNamesNeverMatch() {
        assertNull(CulturePolicy.canonicalKey("unknown:ginkgo")); assertNull(CulturePolicy.canonicalKey("银杏"))
        assertNull(CulturePolicy.canonicalKey("scientific:菊"))
    }
    @Test fun editorialSourceCheckedIsNotPublicationApproval() {
        assertTrue(CulturePolicy.eligible(entry, true)); assertFalse(CulturePolicy.eligible(entry, false))
        assertFalse(CulturePolicy.readable(entry))
    }
    @Test fun publicationDoesNotRewriteVerificationOrAuthorizeDraftQuotations() {
        listOf("source_checked", "literary_source_checked_taxon_pending", "model_draft_unverified").forEach { status ->
            val published = entry.copy(publicationStatus = "approved_for_publication", verificationStatus = status)
            assertTrue(CulturePolicy.readable(published)); assertEquals(status, published.verificationStatus)
            if (status != "source_checked") assertFalse(CulturePolicy.eligible(published, false))
            if (status == "model_draft_unverified") assertTrue(CulturePolicy.readingWorks(published).isEmpty())
            else assertEquals(entry.literaryWorks, CulturePolicy.readingWorks(published))
        }
    }
    @Test fun explicitApprovalStillNeedsAllEvidence() {
        val approved = entry.copy(publicationStatus = "approved_for_publication")
        assertTrue(CulturePolicy.eligible(approved, false))
        assertFalse(CulturePolicy.eligible(approved.copy(taxonomySource = null), false))
    }
    @Test fun taxonPendingNeverAutomaticallyAttached() {
        assertFalse(CulturePolicy.eligible(entry.copy(verificationStatus = "literary_source_checked_taxon_pending", safeForAutomaticSpeciesMatch = false), true))
    }
    @Test fun modelDraftNeverAutomaticallyAttached() {
        assertFalse(CulturePolicy.eligible(entry.copy(verificationStatus = "model_draft_unverified", safeForAutomaticSpeciesMatch = false), true))
    }
    @Test fun missingLiteraryEvidenceFailsClosed() {
        assertFalse(CulturePolicy.eligible(entry.copy(literaryWorks = emptyList()), true))
        assertFalse(CulturePolicy.eligible(entry.copy(poetrySourceChecked = false), true))
    }
    @Test fun unresolvedCitationIsNotMadeVerifiedByApprovalFlag() {
        assertFalse(CulturePolicy.eligible(entry.copy(speciesKey = "scientific:salix babylonica", publicationStatus = "approved_for_publication"), false))
    }
    @Test fun sourceUrlOnlyAcceptsWebSchemes() {
        assertFalse(CulturePolicy.validUrl(null)); assertFalse(CulturePolicy.validUrl("javascript:alert(1)"))
        assertFalse(CulturePolicy.validUrl("file:///private")); assertFalse(CulturePolicy.validUrl("https://name:secret@example.com"))
        assertTrue(CulturePolicy.validUrl("https://zh.wikisource.org/wiki/愛蓮說"))
    }
}

package com.floralens

import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.data.culture.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CultureCatalogTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun entries() = CultureCatalog.parse(context.assets.open("culture/culture_entries.json").bufferedReader().use { it.readText() })
    @Test fun originalFiftyEntriesAndStatusesPreserved() {
        val all = entries(); assertEquals(50, all.size); assertEquals(50, all.map { it.speciesKey }.distinct().size)
        assertEquals(mapOf("source_checked" to 17, "literary_source_checked_taxon_pending" to 23, "model_draft_unverified" to 10), all.groupingBy { it.verificationStatus }.eachCount())
        assertEquals("李清照不以鲜艳颜色评价桂花，而突出它含蓄的色泽与悠长香气。作品又将桂花置于中秋时节，表现出一种不靠浓妆夺目的审美。", all.first().culturalDescription)
    }
    @Test fun candidateCatalogAndReviewQueueAreSeparateAndComplete() {
        val root = JSONObject(context.assets.open("culture/species_catalog.json").bufferedReader().use { it.readText() })
        val species = root.getJSONArray("species"); assertEquals(50,species.length())
        assertEquals(entries().map { it.speciesKey }.toSet(), (0 until species.length()).map { species.getJSONObject(it).getString("speciesKey") }.toSet())
        val review = JSONObject(context.assets.open("culture/culture_review_queue.json").bufferedReader().use { it.readText() }).getJSONArray("reviewQueue")
        assertEquals(16,review.length())
    }
    @Test fun ownerApprovalPublishesAllReadingsButOnlySuitableQuotationsExport() = runBlocking {
        val repo = CultureRepository(CultureCatalog(context))
        val all = entries(); assertEquals(50,all.size)
        assertEquals(if (BuildConfig.INTERNAL_TOOLS) 50 else 0, repo.editorialEntries().size)
        if (!BuildConfig.INTERNAL_TOOLS) all.forEach { assertNull(repo.editorialEntry(it.speciesKey)) }
        assertEquals(50,all.count { repo.matchSpecies(it.speciesKey) != null })
        assertTrue(all.all { it.publicationStatus == "approved_for_publication" })
        assertEquals(50, all.count { repo.readingContent(it.speciesKey) != null })
        assertEquals(33,all.count { !it.safeForAutomaticSpeciesMatch })
        all.filter { !it.safeForAutomaticSpeciesMatch }.forEach {
            assertNotNull(repo.readingContent(it.speciesKey)); assertNull(repo.approvedContent(it.speciesKey))
        }
        assertEquals(17, all.count { repo.approvedContent(it.speciesKey) != null })
        listOf("osmanthus fragrans", "ginkgo biloba", "prunus mume", "nelumbo nucifera", "prunus persica").forEach {
            assertEquals(repo.readingContent("scientific:$it"), repo.approvedContent("scientific:$it"))
        }
        all.filter { it.verificationStatus == "model_draft_unverified" }.forEach { assertTrue(CulturePolicy.readingWorks(it).isEmpty()) }
        assertNull(repo.matchSpecies("unknown:銀杏")); assertNull(repo.matchSpecies("scientific:Ginkgo testensis"))
        assertNotNull(repo.matchSpecies("scientific: GINKGO  BILOBA "))
    }
    @Test fun worksAndNullEvidenceAreHonestAndExploreNotOverwritten() {
        val all = entries(); assertEquals(10,all.count { it.literaryWorks.isEmpty() })
        assertEquals(33,all.count { it.taxonomySource == null })
        all.flatMap { it.literaryWorks }.forEach { assertFalse(it.title.isNullOrBlank()); assertFalse(it.author.isNullOrBlank()); assertTrue(CulturePolicy.validUrl(it.sourceUrl)) }
        val explore = com.floralens.data.explore.ExploreCatalog(context)
        assertEquals(3,explore.species.size); assertEquals(3,explore.species.map { it.scientific }.distinct().size)
        assertEquals(setOf("osmanthus", "ginkgo", "camellia"), explore.species.map { it.id }.toSet())
    }
    @Test fun uncertainArchivedIdentificationNeverBecomesAConfirmedSpeciesFact() {
        assertFalse(CultureSnapshotPolicy.uncertain(null))
        listOf("{", "{}", "{\"kind\":\"low\"}", "{\"kind\":\"none\"}", "{\"kind\":\"high\"}").forEach { assertTrue(CultureSnapshotPolicy.uncertain(it)) }
        val high = JSONObject().put("kind", "high").put("selected", JSONObject().put("key", "scientific:ginkgo biloba"))
            .put("candidates", org.json.JSONArray().put(JSONObject().put("scientific", "Ginkgo biloba").put("confidence", .95)))
        assertFalse(CultureSnapshotPolicy.uncertain(high.toString()))
        high.getJSONObject("selected").put("key", "scientific:osmanthus fragrans")
        assertTrue(CultureSnapshotPolicy.uncertain(high.toString()))
        high.getJSONObject("selected").put("key", "scientific:salisburia biloba")
        assertFalse(CultureSnapshotPolicy.uncertain(high.toString()))
    }
}

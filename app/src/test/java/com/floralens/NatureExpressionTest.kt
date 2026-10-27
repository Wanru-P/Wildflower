package com.floralens

import com.floralens.domain.culture.*
import org.junit.Assert.*
import org.junit.Test

class NatureExpressionTest {
    @Test fun thirtyIdentityCases_areConservativeAndStable() {
        val canonical = listOf("Osmanthus fragrans", "Ginkgo biloba", "Prunus mume", "Nelumbo nucifera", "Prunus persica",
            "Camellia japonica", "Lagerstroemia indica", "Magnolia grandiflora", "Monstera deliciosa", "Welwitschia mirabilis", "Adansonia digitata")
        canonical.forEach { name -> val id = CultureIdentityResolver.resolve("scientific:${name.lowercase()}")
            assertTrue(name, id.canGenerate); assertEquals(name, id.scientific); assertEquals(id, CultureIdentityResolver.resolve(scientific = name)) }
        listOf("Ginkgo biloba L.", "Salisburia adiantifolia Sm.", "Salisburia biloba (L.) Hoffmanns.").forEach {
            assertEquals("scientific:ginkgo biloba", CultureIdentityResolver.resolve(scientific = it).key)
        }
        listOf("银杏", "白果").forEach { assertEquals("scientific:ginkgo biloba", CultureIdentityResolver.resolve(common = it).key) }
        listOf("兰", "竹", "柳", "菊花", "莲花", "桂", "未知植物").forEach { assertFalse(it, CultureIdentityResolver.resolve(common = it).canGenerate) }
        listOf("Ginkgo", "Ginkgo sp.", "Unknown plant", "Unknown species", "Chrysanthemum × morifolium", "Ginkgo biloba var. aurea", "Ginkgo biloba imaginary", "Ginkgo cf. biloba").forEach {
            assertFalse(it, CultureIdentityResolver.resolve(scientific = it).canGenerate)
        }
        assertFalse(CultureIdentityResolver.resolve("unknown:manual", common = "桂花").canGenerate)
        assertFalse(CultureIdentityResolver.resolve(scientific = "Ginkgo biloba", certain = false).canGenerate)
        assertEquals("Ginkgo biloba", CultureIdentityResolver.resolve("scientific: GINKGO  BILOBA ").scientific)
    }
    @Test fun localCoversUnknownAndColdSpecies_withoutInventingObservation() {
        listOf("", "unknown:one", "scientific:ginkgo biloba", "scientific:welwitschia mirabilis").forEach {
            val identity = CultureIdentityResolver.resolve(it)
            val expression = NatureTextPolicy.local(identity)
            assertTrue(NatureTextPolicy.valid(expression)); assertEquals(ExpressionSource.LOCAL, expression.source)
            assertEquals(expression, NatureTextPolicy.local(identity)); assertEquals(identity.key, expression.identityKey)
            assertFalse(Regex("开花|结果|秋季|校园|今天|我在|桂花").containsMatchIn(expression.text))
        }
    }
    @Test fun outputsRejectForgeryFactsAndFormatting() {
        assertTrue(NatureTextPolicy.validAI("把目光交给一段安静，\n让每次停留都有新的意义。"))
        listOf("", "一句话而已", "A quiet moment,\nLet memories remain.", "```json\n{}\n```", "作者：李白\n这是古诗文内容", "今天我在校园观察到花开，\n原产中国的花瓣如此芬芳。", "银杏叶片呈扇形，\n给一段时光留下念想。", "风".repeat(121), "你好\n世界").forEach {
            assertFalse(it, NatureTextPolicy.validAI(it))
        }
    }
    @Test fun cacheIsIdentityStyleAndPromptVersionScoped() {
        val a = CultureIdentityResolver.resolve(scientific = "Ginkgo biloba")
        val b = CultureIdentityResolver.resolve(scientific = "Osmanthus fragrans")
        assertNotEquals(NatureTextPolicy.cacheKey(a, "quiet"), NatureTextPolicy.cacheKey(b, "quiet"))
        assertNotEquals(NatureTextPolicy.cacheKey(a, "quiet"), NatureTextPolicy.cacheKey(a, "journal"))
        assertEquals(NatureTextPolicy.cacheKey(a, "quiet"), NatureTextPolicy.cacheKey(CultureIdentityResolver.resolve(scientific = "Salisburia biloba"), "quiet"))
    }
    @Test fun genericCreationNeverPromotesIdentityAndPreservesLegacySpeciesCache() {
        val known = CultureIdentityResolver.resolve(scientific = "Ginkgo biloba")
        val uncertain = known.copy(certain = false)
        val manual = CultureIdentityResolver.resolve("unknown:personal-nickname", common = "桂花")
        assertEquals("species", known.natureMode)
        assertEquals("general", uncertain.natureMode)
        assertEquals("general", manual.natureMode)
        assertFalse(manual.certain)
        assertNull(manual.scientific)
        assertNotEquals(NatureTextPolicy.cacheKey(known, "quiet"), NatureTextPolicy.cacheKey(uncertain, "quiet"))
        assertNotEquals(NatureTextPolicy.cacheKey(manual, "quiet"), NatureTextPolicy.cacheKey(manual, "journal"))
        val legacy = java.security.MessageDigest.getInstance("SHA-256").digest("1|${known.key}|quiet".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(legacy, NatureTextPolicy.cacheKey(known, "quiet"))
    }
    @Test fun proxyUrlPolicyHasNoReleaseCleartextExceptionOrProviderEndpoint() {
        val policy = com.floralens.data.culture.NatureServiceConfig.Companion
        assertEquals(BuildConfig.DEBUG, policy.validUrl("http://127.0.0.1:8787/v1/nature"))
        assertFalse(policy.validUrl("http://example.org/v1/nature"))
        assertTrue(policy.validUrl("https://example.org/v1/nature"))
        assertFalse(policy.validUrl("https://api.deepseek.com/chat/completions"))
        assertFalse(policy.validUrl("https://token@example.org/v1/nature"))
        assertFalse(policy.validUrl("https://example.org/v1/nature?token=private"))
    }
    @Test fun aHighResultDoesNotMakeItsLowAlternateOrUnknownIdentityCertain() {
        val best = com.floralens.domain.model.IdCandidate("Ginkgo biloba", "银杏", .95f)
        val alternate = com.floralens.domain.model.IdCandidate("Osmanthus fragrans", "桂花", .15f)
        val high = com.floralens.domain.model.IdResult.HighConfidence(best, listOf(alternate))
        assertTrue(CultureIdentificationPolicy.certain(high, "scientific:ginkgo biloba"))
        assertTrue(CultureIdentificationPolicy.certain(high, "scientific:salisburia biloba"))
        assertFalse(CultureIdentificationPolicy.certain(high, "scientific:osmanthus fragrans"))
        assertFalse(CultureIdentificationPolicy.certain(high, "unknown:manual"))
        assertFalse(CultureIdentificationPolicy.certain(high.copy(best = best.copy(confidence = .1f)), "scientific:ginkgo biloba"))
        assertFalse(CultureIdentificationPolicy.certain(com.floralens.domain.model.IdResult.LowConfidence(listOf(best)), "scientific:ginkgo biloba"))
        assertFalse(CultureIdentificationPolicy.certain(null, "scientific:ginkgo biloba"))
    }
    @Test fun expiredCaptureAndMissingArchiveNeverRestoreFalseSpeciesCertainty() {
        val key = "scientific:ginkgo biloba"
        assertFalse(CultureIdentificationPolicy.contextCertain(true, null, key))
        assertFalse(CultureIdentificationPolicy.contextCertain(false, null, key, archiveAvailable = false))
        assertFalse(CultureIdentificationPolicy.contextCertain(false, null, key, uncertainArchive = true))
        assertTrue(CultureIdentificationPolicy.contextCertain(false, null, key))
        val high = com.floralens.domain.model.IdResult.HighConfidence(
            com.floralens.domain.model.IdCandidate("Ginkgo biloba", "银杏", .95f), emptyList())
        assertTrue(CultureIdentificationPolicy.contextCertain(true, high, key))
        assertFalse(CultureIdentificationPolicy.contextCertain(true, high, "scientific:osmanthus fragrans"))
    }
}

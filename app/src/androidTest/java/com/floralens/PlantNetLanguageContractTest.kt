package com.floralens

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.floralens.data.photos.CaptureCodec
import com.floralens.data.photos.PendingCapture
import com.floralens.domain.model.IdResult
import com.floralens.domain.model.IdentificationSource
import com.floralens.domain.usecase.IdentifyPlant
import com.floralens.ml.PlantIdentifier
import com.floralens.ml.PlantNetIdentifier
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Offline contract fixtures, not claims of a reproduced 40% live identification. No Room writes. */
@RunWith(AndroidJUnit4::class)
class PlantNetLanguageContractTest {
    private val identifier = PlantNetIdentifier()

    @Test fun requestExplicitlyUsesOfficialChineseLanguageCode() {
        val request = identifier.buildRequest("contract-test-key", byteArrayOf(1, 2, 3))
        assertEquals("POST", request.method)
        assertEquals("https", request.url.scheme)
        assertEquals("my-api.plantnet.org", request.url.host)
        assertEquals("/v2/identify/all", request.url.encodedPath)
        assertEquals("zh", request.url.queryParameter("lang"))
        assertEquals("5", request.url.queryParameter("nb-results"))
        assertEquals("true", request.url.queryParameter("include-related-images"))
        assertEquals("contract-test-key", request.url.queryParameter("api-key"))
        val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        assertTrue(body.contains("name=\"images\"; filename=\"capture.jpg\""))
        assertTrue(body.contains("image/jpeg"))
        assertTrue(body.contains("name=\"organs\""))
        assertTrue(body.contains("\r\n\r\nauto\r\n"))
    }

    @Test fun mixedCommonNamesKeepChineseAndFortyPercentAcrossDiskCodecAndUseCase() = runBlocking {
        val scientific = "Heptaple\u200burum arboricola"
        val candidates = identifier.parseResults("""{"results":[{"score":0.40,"species":{
          "scientificNameWithoutAuthor":"$scientific",
          "commonNames":[null,42," ","Dwarf umbrella tree","鹅掌藤","七叶莲"]},
          "images":[{"url":{"m":"https://example.org/plant.jpg"}}]}]}""")
        val candidate = candidates.single()
        assertEquals(scientific, candidate.scientificName)
        assertEquals("鹅掌藤", candidate.commonName)
        assertEquals(.4f, candidate.confidence, 0f)
        assertEquals(IdentificationSource.PLANTNET, candidate.source)
        assertEquals(listOf("https://example.org/plant.jpg"), candidate.imageUrls)
        val result = IdentifyPlant(object : PlantIdentifier {
            override suspend fun classify(imageBytes: ByteArray) = candidates
        })(byteArrayOf(1))
        assertTrue(result is IdResult.LowConfidence)
        val capture = PendingCapture("contract-fixture", "file:///fixture.jpg", 1, result)
        val restored = CaptureCodec.decode(CaptureCodec.encode(capture))
        assertEquals(candidate, (restored.result as IdResult.LowConfidence).possibilities.single())
    }

    @Test fun missingEmptyAndForeignNamesDoNotDropValidCandidates() {
        val candidates = identifier.parseResults("""{"results":[
          {"score":0.4,"species":{"scientificNameWithoutAuthor":"Plant one","commonNames":[null,42," "]}},
          {"score":0.3,"species":{"scientificNameWithoutAuthor":"Plant two"}},
          {"score":0.2,"species":{"scientificNameWithoutAuthor":"Plant three","commonNames":[" "," English name "]}}
        ]}""")
        assertEquals(listOf("Plant one", "Plant two", "English name"), candidates.map { it.commonName })
        assertTrue(identifier.parseResults("{}").isEmpty())
    }
}

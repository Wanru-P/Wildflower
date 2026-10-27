package com.floralens

import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.data.species.SpeciesReference
import com.floralens.ml.TfLiteIdentifier
import com.floralens.domain.model.IdentificationSource
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Real bundled model, including the shared live/still concurrent-call regression. No candidates injected. */
class Phase6ARealModelTest {
    @Test fun actualModel_concurrentLiveAndStill_areStableAndBounded() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cameras = context.getSystemService(android.hardware.camera2.CameraManager::class.java)
        cameras.cameraIdList.forEach { id ->
            val metadata = cameras.getCameraCharacteristics(id)
            android.util.Log.i("Phase6AEvidence", "CAMERA_CAPABILITIES id=$id maxZoom=${metadata.get(android.hardware.camera2.CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)} afModes=${metadata.get(android.hardware.camera2.CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.contentToString()}")
        }
        val model = TfLiteIdentifier(context, SpeciesReference(context))
        val assets = listOf("ginkgo.jpg", "osmanthus_kenraiz.jpg", "camellia_pumpkinsky.jpg")
            .associateWith { context.assets.open("explore/$it").use { stream -> stream.readBytes() } }
        val baseline = assets.mapValues { (_, bytes) -> model.classify(bytes) }
        baseline.forEach { (name, candidates) ->
            android.util.Log.i("Phase6AEvidence", "REAL_MODEL $name $candidates")
            assertTrue(candidates.size <= 5)
            candidates.forEach { assertTrue(it.confidence in 0f..1f); assertEquals(IdentificationSource.ON_DEVICE, it.source) }
        }
        assertEquals("Real licensed ginkgo input must not be shifted to the preceding CSV row",
            "Ginkgo biloba", baseline.getValue("ginkgo.jpg").first().scientificName)
        assertTrue("At least one plant photograph must yield real model candidates", baseline.values.any { it.isNotEmpty() })
        coroutineScope {
            (0 until 12).map { index -> async(Dispatchers.Default) {
                val name = assets.keys.elementAt(index % assets.size)
                assertEquals(baseline[name], model.classify(assets.getValue(name)))
            } }.awaitAll()
        }
        assertTrue(model.classify(byteArrayOf(1, 2, 3)).isEmpty())
        android.util.Log.i("Phase6AEvidence", "KEY_CONFIG plantnet=${BuildConfig.PLANTNET_API_KEY.isNotBlank()} maps=${BuildConfig.BAIDU_MAP_CONFIGURED}")
        if (InstrumentationRegistry.getArguments().getString("verifyPlantNet") == "true") verifyRealPlantNet()
        Unit
    }
}

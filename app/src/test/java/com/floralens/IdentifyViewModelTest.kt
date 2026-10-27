package com.floralens
import androidx.lifecycle.SavedStateHandle
import com.floralens.data.photos.*
import com.floralens.data.settings.SettingsProvider
import com.floralens.domain.model.*
import com.floralens.domain.usecase.IdentifyPlant
import com.floralens.ml.PlantIdentifier
import com.floralens.ui.screens.identify.IdentifyViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class IdentifyViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val candidate = IdCandidate("Monstera deliciosa", "Swiss cheese plant", .92f)
    private class MemoryCaptures : CaptureStore {
        val records = mutableMapOf<String, PendingCapture>()
        var fail = false
        override suspend fun create(bytes: ByteArray): PendingCapture {
            if(fail) error("disk full")
            yield()
            return PendingCapture(UUID.randomUUID().toString(), "file:///pending/photo.jpg", 1000).also { records[it.id] = it }
        }
        override suspend fun result(id: String, result: IdResult) = records.getValue(id).copy(result = result).also { records[id] = it }
        override suspend fun select(id: String, species: Species) = records.getValue(id).copy(selected = species).also { records[id] = it }
        override suspend fun get(id: String) = records[id]
        override suspend fun latest() = records.values.firstOrNull { it.result != null }
        override suspend fun cancel(id: String) { records.remove(id) }
        override suspend fun cleanup() = Unit
    }
    private class Stub(val candidates: List<IdCandidate>, val failure: Boolean = false) : PlantIdentifier {
        var calls = 0
        override suspend fun classify(imageBytes: ByteArray): List<IdCandidate> { calls++; yield(); if(failure) error("model unavailable"); return candidates }
    }
    private lateinit var store: MemoryCaptures
    @Before fun before() { Dispatchers.setMain(dispatcher); store = MemoryCaptures() }
    @After fun after() { Dispatchers.resetMain() }
    private fun vm(candidates: List<IdCandidate> = listOf(candidate), mode: IdentificationMode = IdentificationMode.OFFLINE,
        identifier: Stub = Stub(candidates), real: Stub = Stub(candidates), handle: SavedStateHandle = SavedStateHandle()) =
        IdentifyViewModel(IdentifyPlant(identifier), store, object : SettingsProvider {
            override val identificationMode: Flow<IdentificationMode> = MutableStateFlow(mode)
        }, real, handle)

    @Test fun onCapture_routesHighConfidence_andRetainsPhoto() = runTest(dispatcher) {
        val vm = vm(); vm.onCapture(byteArrayOf(1)); assertTrue(vm.uiState.value.isProcessing)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.result is IdResult.HighConfidence)
        assertNotNull(vm.uiState.value.capturedImage); assertNotNull(vm.uiState.value.captureId)
        assertEquals(1, store.records.size)
    }
    /** Replaces the old direct-save assertion: selection can only navigate, never create a Plant. */
    @Test fun candidateSelection_persistsIdentityAndNavigates_withoutCollecting() = runTest(dispatcher) {
        val vm = vm(); vm.onCapture(byteArrayOf(1)); advanceUntilIdle()
        vm.openDetails(candidate); advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(s.captureId, s.navigationCaptureId)
        assertEquals("scientific:monstera deliciosa", store.records.getValue(s.captureId!!).selected!!.key)
        assertNotNull(s.result); assertEquals(1, store.records.size)
        vm.consumeNavigation(); assertNull(vm.uiState.value.navigationCaptureId)
    }
    @Test fun dismiss_clearsPhotoResult_andDeletesPending() = runTest(dispatcher) {
        val vm = vm(); vm.onCapture(byteArrayOf(1)); advanceUntilIdle()
        vm.dismissResult(); advanceUntilIdle()
        assertNull(vm.uiState.value.result); assertNull(vm.uiState.value.capturedImage)
        assertNull(vm.uiState.value.captureId); assertTrue(store.records.isEmpty())
    }
    @Test fun onlinePlantNet_reportsNoFallback() = runTest(dispatcher) {
        val vm = vm(listOf(candidate.copy(source = IdentificationSource.PLANTNET)), IdentificationMode.ONLINE)
        vm.onCapture(byteArrayOf(1)); advanceUntilIdle()
        assertEquals(IdentificationSource.PLANTNET, vm.uiState.value.identificationSource)
        assertFalse(vm.uiState.value.usedOfflineFallback)
    }
    @Test fun onlineOnDevice_reportsFallback() = runTest(dispatcher) {
        val vm = vm(mode = IdentificationMode.ONLINE); vm.onCapture(byteArrayOf(1)); advanceUntilIdle()
        assertTrue(vm.uiState.value.usedOfflineFallback); assertEquals(IdentificationSource.ON_DEVICE, vm.uiState.value.identificationSource)
        vm.consumeFallbackNotice(); assertFalse(vm.uiState.value.usedOfflineFallback)
    }
    @Test fun chinesePlantNetNamePreservesLowConfidenceSourceAndPendingSelection() = runTest(dispatcher) {
        val online = IdCandidate("Heptapleurum arboricola", "鹅掌藤", .40f, IdentificationSource.PLANTNET)
        val vm = vm(listOf(online), IdentificationMode.ONLINE)
        vm.onCapture(byteArrayOf(1)); advanceUntilIdle()
        assertEquals(listOf(online), (vm.uiState.value.result as IdResult.LowConfidence).possibilities)
        assertEquals(IdentificationSource.PLANTNET, vm.uiState.value.identificationSource)
        assertFalse(vm.uiState.value.usedOfflineFallback)
        assertNull(store.records.values.single().selected)
        vm.openDetails(online); advanceUntilIdle()
        val capture = store.records.values.single()
        assertEquals("鹅掌藤", capture.selected!!.commonName)
        assertEquals(online.scientificName, capture.selected!!.scientificName)
        assertEquals(online, (capture.result as IdResult.LowConfidence).possibilities.single())
    }
    @Test fun lowConfidence_preservesCandidates_andNoAutomaticSelection() = runTest(dispatcher) {
        val vm = vm(listOf(candidate.copy(confidence = .5f))); vm.onCapture(byteArrayOf(1)); advanceUntilIdle()
        assertTrue(vm.uiState.value.result is IdResult.LowConfidence); assertNull(store.records.values.first().selected)
    }
    @Test fun manualName_remainsUnknown_withoutScientificName() = runTest(dispatcher) {
        val vm = vm(); vm.onCapture(byteArrayOf(1)); advanceUntilIdle()
        vm.openManualDetails("图书馆的树"); advanceUntilIdle()
        val species = store.records.values.first().selected!!
        assertNull(species.scientificName); assertTrue(species.key.startsWith("unknown:"))
    }
    @Test fun saveFailure_doesNotInvokeIdentifier_andIsRetryable() = runTest(dispatcher) {
        store.fail = true; val identifier = Stub(listOf(candidate)); val vm = vm(identifier = identifier)
        vm.onCapture(byteArrayOf(1)); advanceUntilIdle()
        assertEquals(0, identifier.calls); assertEquals("存储空间不足，请腾出空间后重试。", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isProcessing); store.fail = false
        vm.retry(); advanceUntilIdle(); assertNotNull(vm.uiState.value.result)
    }
    @Test fun modelFailure_keepsPending_withoutCollection_andAllowsRetake() = runTest(dispatcher) {
        val vm = vm(identifier = Stub(listOf(candidate), failure = true)); vm.onCapture(byteArrayOf(1)); advanceUntilIdle()
        assertNotNull(vm.uiState.value.error); assertEquals(1, store.records.size)
        vm.dismissResult(); advanceUntilIdle(); assertTrue(store.records.isEmpty())
    }
    @Test fun duplicateShutter_isDropped_whileCaptureInFlight() = runTest(dispatcher) {
        val stub = Stub(listOf(candidate)); val vm = vm(identifier = stub)
        vm.onCapture(byteArrayOf(1)); vm.onCapture(byteArrayOf(2)); advanceUntilIdle()
        assertEquals(1, stub.calls); assertEquals(1, store.records.size)
    }
    @Test fun liveFrames_throttled_andAlwaysOnDevice_evenOnlineMode() = runTest(dispatcher) {
        val online = Stub(listOf(candidate)); val local = Stub(listOf(candidate))
        val vm = vm(mode = IdentificationMode.ONLINE, identifier = online, real = local)
        advanceUntilIdle(); repeat(20) { vm.onLiveFrame(byteArrayOf(1)) }; advanceUntilIdle()
        assertEquals(0, online.calls); assertEquals(1, local.calls)
    }
    @Test fun recreation_restoresDiskReference_notNavigationBytes() = runTest(dispatcher) {
        val first = vm(); first.onCapture(byteArrayOf(1)); advanceUntilIdle()
        val restored = vm(handle = SavedStateHandle(mapOf("captureId" to first.uiState.value.captureId)))
        advanceUntilIdle(); assertEquals(first.uiState.value.photoUri, restored.uiState.value.photoUri)
        assertNotNull(restored.uiState.value.result); assertNull(restored.uiState.value.capturedImage)
    }
    @Test fun missingRestoredCapture_reportsExpiry() = runTest(dispatcher) {
        val vm = vm(handle = SavedStateHandle(mapOf("captureId" to UUID.randomUUID().toString())))
        advanceUntilIdle(); assertNotNull(vm.uiState.value.error); assertNull(vm.uiState.value.result)
    }
}

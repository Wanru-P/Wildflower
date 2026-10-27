package com.floralens.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.floralens.data.species.SpeciesReference
import com.floralens.domain.model.IdCandidate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real on-device inference via TensorFlow Lite (LiteRT).
 *
 * Model: Google AIY "plants V1" classifier (~2,100 species), bundled at
 * assets/model/plants_v1.tflite with its label map alongside. Quantized
 * uint8, 224x224 RGB input. Chosen for size, not accuracy — the
 * [PlantIdentifier] interface makes swapping in a stronger model a
 * one-file change.
 *
 * The label map identifies the background output (AIY ID 2101): when it outscores every plant
 * class, we report no candidates rather than surfacing whichever species
 * happens to rank first among the noise.
 *
 * This identifier runs entirely on-device; the separate online identifier handles network calls.
 */
@Singleton
class TfLiteIdentifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val speciesReference: SpeciesReference,
) : PlantIdentifier {

    companion object {
        private const val MODEL_PATH = "model/plants_v1.tflite"
        private const val LABELS_PATH = "model/plants_v1_labels.csv"
        private const val INPUT_SIZE = 224
        private const val BACKGROUND_LABEL = "background"
        private const val TOP_K = 5
    }

    // Lazy so app start pays nothing; first classify() call pays model load once.
    private val interpreter: Interpreter by lazy { Interpreter(loadModelFile()) }
    private val labels: List<String> by lazy { loadLabels() }
    // Live frames and still captures share this singleton; Interpreter is not thread-safe.
    private val inferenceLock = Mutex()

    override suspend fun classify(imageBytes: ByteArray): List<IdCandidate> =
        withContext(Dispatchers.Default) { inferenceLock.withLock {
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: return@withLock emptyList()

            val input = try { preprocess(bitmap) } finally { bitmap.recycle() }
            val scores = runInference(input)

            // Background gate: if the model's best explanation of the frame
            // is "no plant here", say so instead of ranking the noise.
            val backgroundScore = labels
                .indexOfFirst { it.equals(BACKGROUND_LABEL, ignoreCase = true) }
                .takeIf { it >= 0 }
                ?.let { scores.getOrElse(it) { 0f } } ?: 0f
            val bestPlantScore = scores.indices
                .filter { labels.getOrNull(it)?.lowercase() != BACKGROUND_LABEL }
                .maxOfOrNull { scores[it] } ?: 0f
            if (backgroundScore >= bestPlantScore) return@withLock emptyList()

            scores.indices
                .asSequence()
                .filter { labels.getOrNull(it)?.lowercase() != BACKGROUND_LABEL }
                .sortedByDescending { scores[it] }
                .take(TOP_K)
                .filter { scores[it] > 0f }
                .map { idx -> toCandidate(labels.getOrElse(idx) { "Unknown" }, scores[idx]) }
                .toList()
        } }

    // ── Preprocessing: center-crop → 224x224 → RGB buffer ──────────
    private fun preprocess(source: Bitmap): ByteBuffer {
        val side = minOf(source.width, source.height)
        val cropped = Bitmap.createBitmap(
            source,
            (source.width - side) / 2,
            (source.height - side) / 2,
            side,
            side,
        )
        val scaled = Bitmap.createScaledBitmap(cropped, INPUT_SIZE, INPUT_SIZE, true)

        val floatInput = interpreter.getInputTensor(0).dataType() == DataType.FLOAT32
        val bytesPerChannel = if (floatInput) 4 else 1
        val buffer = ByteBuffer
            .allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * bytesPerChannel)
            .order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            if (floatInput) {
                buffer.putFloat(r / 255f)
                buffer.putFloat(g / 255f)
                buffer.putFloat(b / 255f)
            } else {
                buffer.put(r.toByte())
                buffer.put(g.toByte())
                buffer.put(b.toByte())
            }
        }
        buffer.rewind()
        if (scaled !== cropped) scaled.recycle()
        if (cropped !== source) cropped.recycle()
        return buffer
    }

    // ── Inference + dequantization → FloatArray of probabilities ───
    private fun runInference(input: ByteBuffer): FloatArray {
        val outputTensor = interpreter.getOutputTensor(0)
        val numClasses = outputTensor.shape().last()

        return if (outputTensor.dataType() == DataType.UINT8) {
            val raw = Array(1) { ByteArray(numClasses) }
            interpreter.run(input, raw)
            val q = outputTensor.quantizationParams()
            FloatArray(numClasses) { i ->
                q.scale * ((raw[0][i].toInt() and 0xFF) - q.zeroPoint)
            }
        } else {
            val raw = Array(1) { FloatArray(numClasses) }
            interpreter.run(input, raw)
            raw[0]
        }
    }

    // ── Label → IdCandidate, common name via bundled species reference ──
    private fun toCandidate(scientificName: String, confidence: Float): IdCandidate {
        val known = speciesReference.find(scientificName)
        return IdCandidate(
            scientificName = scientificName,
            commonName = known?.commonName ?: scientificName,
            confidence = confidence.coerceIn(0f, 1f),
        )
    }

    // ── Asset loading ───────────────────────────────────────────────
    private fun loadModelFile(): MappedByteBuffer =
        context.assets.openFd(MODEL_PATH).use { fd ->
            FileInputStream(fd.fileDescriptor).channel.map(
                FileChannel.MapMode.READ_ONLY,
                fd.startOffset,
                fd.declaredLength,
            )
        }

    /**
     * Label map parser — tolerant of the AIY CSV format ("id,name" with
     * optional header and quoted names) and plain one-label-per-line txt.
     */
    private fun loadLabels(): List<String> =
        context.assets.open(LABELS_PATH).bufferedReader().useLines { lines ->
            parsePlantLabels(lines)
        }
}

/** AIY's background row comes first in the CSV but is output ID 2101, not ID 0. */
internal fun parsePlantLabels(lines: Sequence<String>): List<String> {
    val rows = lines.map(String::trim).filter { it.isNotEmpty() && !it.startsWith("id,", ignoreCase = true) }.toList()
    val indexed = rows.map { line -> line.substringBefore(',').toIntOrNull() }
    if (indexed.all { it == null }) return rows.map { it.removeSurrounding("\"") }
    require(indexed.all { it != null }) { "Model labels mix indexed and unindexed rows" }
    require(indexed.toSet().size == rows.size && indexed.toSet() == rows.indices.toSet()) { "Model label IDs must be unique and contiguous" }
    return rows.sortedBy { it.substringBefore(',').toInt() }
        .map { it.substringAfter(',').trim().removeSurrounding("\"") }
}

package com.floralens.data.insights

import android.util.Base64
import android.util.Log
import com.floralens.BuildConfig
import com.floralens.domain.model.CareLevel
import com.floralens.domain.model.CareLevels
import com.floralens.domain.model.PlantInsights
import com.floralens.domain.model.RegionalName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI-generated plant insights via the Gemini API. Online feature only —
 * called from the details screen after an online (Pl@ntNet) identification.
 * Interface + impl (PlantIdentifier / NetworkStatus pattern) so it's fakeable.
 *
 * Structured output (responseMimeType=application/json + responseSchema) so
 * Gemini returns strict, parseable JSON. Throws IOException on any failure;
 * the screen shows a graceful error+retry (no fallback source).
 */
interface PlantInfoService {
    suspend fun fetch(scientificName: String, commonName: String?): PlantInsights

    /**
     * Short, hedged AI note about a leaf photo — the online companion to the
     * on-device HSV heuristic. [heuristicSummary] tells Gemini what the local
     * check concluded so its note complements rather than contradicts.
     * Plain text (2-4 sentences), never a diagnosis.
     */
    suspend fun leafInsight(imageBytes: ByteArray, heuristicSummary: String): String
}

@Singleton
class GeminiPlantInfoService @Inject constructor() : PlantInfoService {

    companion object {
        private const val TAG = "GeminiPlantInfo"
        // Auto-updating alias — survives Google's model rotations (a pinned
        // version like gemini-2.5-flash got retired and 404'd).
        private const val MODEL = "gemini-flash-latest"
        private const val ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
        private val JSON = "application/json".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun fetch(scientificName: String, commonName: String?): PlantInsights =
        withContext(Dispatchers.IO) {
            val apiKey = requireApiKey()

            val request = Request.Builder()
                .url(ENDPOINT)
                .header("x-goog-api-key", apiKey)
                .post(buildRequestBody(scientificName, commonName).toString().toRequestBody(JSON))
                .build()

            Log.d(TAG, "Requesting insights for '$scientificName'")

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty()
                    Log.e(TAG, "Gemini HTTP ${response.code}: $err")
                    throw IOException("Gemini request failed: HTTP ${response.code}")
                }
                val payload = response.body?.string()
                    ?: throw IOException("Gemini returned an empty response")
                parseResponse(payload, scientificName)
            }
        }

    override suspend fun leafInsight(imageBytes: ByteArray, heuristicSummary: String): String =
        withContext(Dispatchers.IO) {
            val apiKey = requireApiKey()

            val prompt = """
                You are looking at a photo of a single plant leaf from a plant-care app.
                The app's simple on-device color check concluded: "$heuristicSummary".
                In 2-4 short sentences of plain, friendly language, describe what you can
                see about the leaf's condition (color, spots, edges, texture) and, if
                anything looks off, ONE OR TWO gentle care suggestions (watering, light,
                humidity). Rules:
                - This is a friendly observation, NOT a diagnosis — never name a specific
                  disease with certainty, and never prescribe chemicals or treatments.
                - If the leaf looks fine, say so briefly and encourage the current routine.
                - If the photo is too unclear to comment on, say exactly that.
                - No markdown, no lists, no headings — plain sentences only.
            """.trimIndent()

            val body = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray()
                        .put(JSONObject().put("inline_data", JSONObject().apply {
                            put("mime_type", "image/jpeg")
                            put("data", Base64.encodeToString(imageBytes, Base64.NO_WRAP))
                        }))
                        .put(JSONObject().put("text", prompt)),
                    ),
                ))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.4)
                    put("maxOutputTokens", 512)
                })
            }

            val request = Request.Builder()
                .url(ENDPOINT)
                .header("x-goog-api-key", apiKey)
                .post(body.toString().toRequestBody(JSON))
                .build()

            Log.d(TAG, "Requesting leaf insight (${imageBytes.size} bytes)")

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty()
                    Log.e(TAG, "Gemini leaf HTTP ${response.code}: $err")
                    throw IOException("Gemini request failed: HTTP ${response.code}")
                }
                val payload = response.body?.string()
                    ?: throw IOException("Gemini returned an empty response")
                extractText(payload)
            }
        }

    private fun requireApiKey(): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            Log.e(TAG, "GEMINI_API_KEY is blank — check local.properties, then Clean + Rebuild")
            throw IOException("Gemini API key not configured")
        }
        return apiKey
    }

    /** Pulls the first candidate's text out of a Gemini response. */
    private fun extractText(payload: String): String {
        val root = JSONObject(payload)
        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            throw IOException("Gemini returned no usable content")
        }
        return candidates.getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw IOException("Gemini response missing content")
    }

    private fun buildRequestBody(scientificName: String, commonName: String?): JSONObject {
        val plantRef = if (!commonName.isNullOrBlank()) "$scientificName (commonly \"$commonName\")" else scientificName

        val prompt = """
            You are a botanical assistant for an Indian audience. For the plant $plantRef, provide detailed, accurate information.
            Rules:
            - description: 3-4 sentences covering what the plant is, its appearance, and where it's typically kept/grown.
            - regionalNames: common names used in India with their language (Hindi, Marathi, Tamil, Bengali, etc.). Only include names you're confident about; empty array if unsure. Never invent names.
            - growthHabit: one short phrase (e.g. "Evergreen climbing vine").
            - bloomSeason: brief note on flowering (e.g. "Rarely flowers indoors").
            - nativeRegions: list 2-5 specific geographic regions or countries where this plant is native or most commonly found in the wild (e.g. "Western Ghats, India", "Southeast Asia", "Central America"). Use recognizable place names that could be found on a map. Empty array if unsure.
            - careLevels: rate each of water, light, humidity, difficulty as exactly one of "Low", "Medium", or "High". Use your best judgement for a typical household setting.
            - careNotes: 2-3 sentences of practical guidance.
            - propagation: 2-4 short bullet points on how to propagate it.
            - commonProblems: 2-4 short bullet points on typical issues (pests, yellowing, etc.).
            - funFacts: 2-3 short, genuinely interesting facts.
            - toxicity: describe potential toxicity to humans/pets cautiously, and ALWAYS advise consulting a veterinarian or medical professional rather than a definitive verdict.
            - Leave uncertain fields empty rather than guessing. Keep every bullet concise.
        """.trimIndent()

        val careLevelEnum = JSONArray().put("Low").put("Medium").put("High")

        val schema = JSONObject().apply {
            put("type", "OBJECT")
            put("properties", JSONObject().apply {
                put("description", strType())
                put("origin", strType())
                put("growthHabit", strType())
                put("bloomSeason", strType())
                put("careNotes", strType())
                put("toxicity", strType())
                put("careLevels", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("water", enumType(careLevelEnum))
                        put("light", enumType(careLevelEnum))
                        put("humidity", enumType(careLevelEnum))
                        put("difficulty", enumType(careLevelEnum))
                    })
                })
                put("nativeRegions", strArrayType())
                put("propagation", strArrayType())
                put("commonProblems", strArrayType())
                put("funFacts", strArrayType())
                put("regionalNames", JSONObject().apply {
                    put("type", "ARRAY")
                    put("items", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("language", strType())
                            put("name", strType())
                        })
                        put("required", JSONArray().put("language").put("name"))
                    })
                })
            })
            put("required", JSONArray().put("description"))
        }

        return JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", prompt),
                )),
            ))
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("responseSchema", schema)
                put("temperature", 0.4)
            })
        }
    }

    private fun strType() = JSONObject().put("type", "STRING")
    private fun strArrayType() = JSONObject().apply {
        put("type", "ARRAY")
        put("items", JSONObject().put("type", "STRING"))
    }
    private fun enumType(values: JSONArray) = JSONObject().apply {
        put("type", "STRING")
        put("enum", values)
    }

    private fun parseResponse(payload: String, scientificName: String): PlantInsights {
        val root = JSONObject(payload)
        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            Log.w(TAG, "Gemini returned no candidates")
            throw IOException("Gemini returned no usable content")
        }

        val text = candidates.getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text")
            ?: throw IOException("Gemini response missing content")

        val inner = runCatching { JSONObject(text) }.getOrElse {
            Log.e(TAG, "Inner JSON parse failed: $text")
            throw IOException("Gemini returned malformed JSON")
        }

        return PlantInsights(
            description = inner.optString("description").takeIf { it.isNotBlank() },
            regionalNames = parseRegionalNames(inner.optJSONArray("regionalNames")),
            origin = inner.optString("origin").takeIf { it.isNotBlank() },
            nativeRegions = parseStringList(inner.optJSONArray("nativeRegions")),
            growthHabit = inner.optString("growthHabit").takeIf { it.isNotBlank() },
            bloomSeason = inner.optString("bloomSeason").takeIf { it.isNotBlank() },
            careLevels = parseCareLevels(inner.optJSONObject("careLevels")),
            careNotes = inner.optString("careNotes").takeIf { it.isNotBlank() },
            propagation = parseStringList(inner.optJSONArray("propagation")),
            commonProblems = parseStringList(inner.optJSONArray("commonProblems")),
            funFacts = parseStringList(inner.optJSONArray("funFacts")),
            toxicityNote = inner.optString("toxicity").takeIf { it.isNotBlank() },
            scientificName = scientificName,
        )
    }

    private fun parseRegionalNames(arr: JSONArray?): List<RegionalName> {
        if (arr == null) return emptyList()
        val out = mutableListOf<RegionalName>()
        for (i in 0 until arr.length()) {
            runCatching {
                val obj = arr.getJSONObject(i)
                val lang = obj.optString("language").takeIf { it.isNotBlank() } ?: return@runCatching
                val name = obj.optString("name").takeIf { it.isNotBlank() } ?: return@runCatching
                out += RegionalName(lang, name)
            }
        }
        return out
    }

    private fun parseCareLevels(obj: JSONObject?): CareLevels {
        if (obj == null) return CareLevels()
        return CareLevels(
            water = CareLevel.from(obj.optString("water")),
            light = CareLevel.from(obj.optString("light")),
            humidity = CareLevel.from(obj.optString("humidity")),
            difficulty = CareLevel.from(obj.optString("difficulty")),
        )
    }

    private fun parseStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            arr.optString(i).takeIf { it.isNotBlank() }?.let(out::add)
        }
        return out
    }
}
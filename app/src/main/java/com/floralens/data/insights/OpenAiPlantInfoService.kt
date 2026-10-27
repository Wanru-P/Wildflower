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
 * OpenAI implementation of [PlantInfoService] — the drop-in replacement for
 * [GeminiPlantInfoService], swapped via one @Binds line in RepositoryModule.
 * Nothing above the interface (ViewModels, screens, offline cache) changed:
 * this is the PlantIdentifier pattern paying rent.
 *
 * Structured output via response_format=json_schema (strict mode) so the
 * model returns exactly the shape [PlantInsights] parsing expects. Strict
 * mode requires every property listed in `required` — the prompt tells the
 * model to use empty strings/arrays when unsure, and blank values are
 * normalized to null/empty downstream, matching the Gemini behavior.
 */
@Singleton
class OpenAiPlantInfoService @Inject constructor() : PlantInfoService {

    companion object {
        private const val TAG = "OpenAiPlantInfo"
        /** Bump here when a newer/cheaper model fits. */
        private const val MODEL = "gpt-5-mini"
        private const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
        private val JSON = "application/json".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun fetch(scientificName: String, commonName: String?): PlantInsights =
        withContext(Dispatchers.IO) {
            val payload = execute(buildInsightsRequest(scientificName, commonName))
            val text = extractText(payload)
            val inner = runCatching { JSONObject(text) }.getOrElse {
                Log.e(TAG, "Inner JSON parse failed: $text")
                throw IOException("OpenAI returned malformed JSON")
            }
            parseInsights(inner, scientificName)
        }

    override suspend fun leafInsight(imageBytes: ByteArray, heuristicSummary: String): String =
        withContext(Dispatchers.IO) {
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

            val dataUrl = "data:image/jpeg;base64," +
                    Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            val body = JSONObject().apply {
                put("model", MODEL)
                put("max_completion_tokens", 800)
                put("messages", JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray()
                            .put(JSONObject().put("type", "text").put("text", prompt))
                            .put(JSONObject().put("type", "image_url").put(
                                "image_url", JSONObject().put("url", dataUrl),
                            )),
                        )
                    },
                ))
            }

            Log.d(TAG, "Requesting leaf insight (${imageBytes.size} bytes)")
            extractText(execute(body)).trim()
                .takeIf { it.isNotBlank() }
                ?: throw IOException("OpenAI returned empty leaf insight")
        }

    // ── Request plumbing ────────────────────────────────────────────

    private fun execute(body: JSONObject): String {
        val apiKey = BuildConfig.OPENAI_API_KEY
        if (apiKey.isBlank()) {
            Log.e(TAG, "OPENAI_API_KEY is blank — check local.properties, then Clean + Rebuild")
            throw IOException("OpenAI API key not configured")
        }
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string().orEmpty()
                Log.e(TAG, "OpenAI HTTP ${response.code}: $err")
                throw IOException("OpenAI request failed: HTTP ${response.code}")
            }
            return response.body?.string()
                ?: throw IOException("OpenAI returned an empty response")
        }
    }

    /** choices[0].message.content — OpenAI's answer text. */
    private fun extractText(payload: String): String {
        val root = JSONObject(payload)
        val choices = root.optJSONArray("choices")
        if (choices == null || choices.length() == 0) {
            throw IOException("OpenAI returned no choices")
        }
        return choices.getJSONObject(0)
            .optJSONObject("message")
            ?.optString("content")
            ?.takeIf { it.isNotBlank() }
            ?: throw IOException("OpenAI response missing content")
    }

    // ── Insights request (structured output) ────────────────────────

    private fun buildInsightsRequest(scientificName: String, commonName: String?): JSONObject {
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
            - The schema requires every field: use an empty string or empty array for anything you're unsure of, rather than guessing. Keep every bullet concise.
        """.trimIndent()

        // OpenAI strict json_schema: lowercase types, every property required,
        // additionalProperties=false on every object.
        val careLevelEnum = JSONArray().put("Low").put("Medium").put("High")
        fun str() = JSONObject().put("type", "string")
        fun strArray() = JSONObject().apply {
            put("type", "array")
            put("items", JSONObject().put("type", "string"))
        }
        fun enumStr() = JSONObject().apply {
            put("type", "string")
            put("enum", careLevelEnum)
        }

        val careLevelsSchema = JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject().apply {
                put("water", enumStr())
                put("light", enumStr())
                put("humidity", enumStr())
                put("difficulty", enumStr())
            })
            put("required", JSONArray().put("water").put("light").put("humidity").put("difficulty"))
            put("additionalProperties", false)
        }

        val regionalNameSchema = JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject().apply {
                put("language", str())
                put("name", str())
            })
            put("required", JSONArray().put("language").put("name"))
            put("additionalProperties", false)
        }

        val schema = JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject().apply {
                put("description", str())
                put("origin", str())
                put("growthHabit", str())
                put("bloomSeason", str())
                put("careNotes", str())
                put("toxicity", str())
                put("careLevels", careLevelsSchema)
                put("nativeRegions", strArray())
                put("propagation", strArray())
                put("commonProblems", strArray())
                put("funFacts", strArray())
                put("regionalNames", JSONObject().apply {
                    put("type", "array")
                    put("items", regionalNameSchema)
                })
            })
            put("required", JSONArray()
                .put("description").put("origin").put("growthHabit").put("bloomSeason")
                .put("careNotes").put("toxicity").put("careLevels").put("nativeRegions")
                .put("propagation").put("commonProblems").put("funFacts").put("regionalNames"))
            put("additionalProperties", false)
        }

        return JSONObject().apply {
            put("model", MODEL)
            put("max_completion_tokens", 3000)
            put("messages", JSONArray().put(
                JSONObject().put("role", "user").put("content", prompt),
            ))
            put("response_format", JSONObject().apply {
                put("type", "json_schema")
                put("json_schema", JSONObject().apply {
                    put("name", "plant_insights")
                    put("strict", true)
                    put("schema", schema)
                })
            })
        }
    }

    // ── Parsing (blank → null, identical semantics to the Gemini impl) ──

    private fun parseInsights(inner: JSONObject, scientificName: String) = PlantInsights(
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
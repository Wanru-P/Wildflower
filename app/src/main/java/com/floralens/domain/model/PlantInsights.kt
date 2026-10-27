package com.floralens.domain.model

/**
 * AI-generated plant information from Gemini (online mode only). Every field
 * is best-effort and may be inaccurate — the UI labels the whole thing as
 * AI-generated and, for anything safety-adjacent (toxicity), defers to
 * professionals. Nullable/emptyable so a partial response still renders.
 */
data class PlantInsights(
    val description: String?,              // richer, multi-sentence overview
    val regionalNames: List<RegionalName>, // e.g. Hindi: तुलसी, Marathi: तुळस
    val origin: String?,
    val nativeRegions: List<String>,       // named regions for the map (geocoded client-side)// native region / origin
    val growthHabit: String?,              // e.g. "Evergreen climbing vine"
    val bloomSeason: String?,              // e.g. "Rarely flowers indoors"
    val careLevels: CareLevels,            // water / light / humidity / difficulty
    val careNotes: String?,                // short prose guidance — not authoritative
    val propagation: List<String>,         // step/method bullets
    val commonProblems: List<String>,      // typical issues as bullets
    val funFacts: List<String>,            // short tidbits
    val toxicityNote: String?,             // cautious; defers to a vet/professional
    val scientificName: String,
)

/** One regional/common name in a given language. */
data class RegionalName(
    val language: String,
    val name: String,
)

/**
 * Discrete care levels — the honest "chart" data. Gemini returns one of
 * three buckets per axis; the UI renders them as dot/bar meters. UNKNOWN
 * when Gemini couldn't say, so the meter can be omitted rather than faked.
 */
enum class CareLevel { LOW, MEDIUM, HIGH, UNKNOWN;
    companion object {
        fun from(raw: String?): CareLevel = when (raw?.trim()?.uppercase()) {
            "LOW" -> LOW
            "MEDIUM", "MODERATE", "MED" -> MEDIUM
            "HIGH" -> HIGH
            else -> UNKNOWN
        }
    }
}

data class CareLevels(
    val water: CareLevel = CareLevel.UNKNOWN,
    val light: CareLevel = CareLevel.UNKNOWN,
    val humidity: CareLevel = CareLevel.UNKNOWN,
    val difficulty: CareLevel = CareLevel.UNKNOWN,
) {
    /** True when at least one axis is known — lets the UI skip the whole section if all UNKNOWN. */
    val hasAny: Boolean
        get() = listOf(water, light, humidity, difficulty).any { it != CareLevel.UNKNOWN }
}
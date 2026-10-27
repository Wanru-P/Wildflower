package com.floralens.domain.model

/**
 * Real occurrence distribution from GBIF (Global Biodiversity Information
 * Facility) — counts of recorded occurrences per country for a species.
 * Unlike the AI-generated content, these are actual scientific records, so
 * the chart built from them is trustworthy (not invented percentages).
 */
data class OccurrenceData(
    val totalCount: Long,
    val slices: List<CountrySlice>, // top countries + optional "Other", desc by count
)

data class CountrySlice(
    val countryCode: String,  // ISO-2, or "OTHER" for the grouped tail
    val displayName: String,
    val count: Long,
) {
    /** Fraction of the charted total (slices sum), 0f..1f. */
    fun fraction(chartTotal: Long): Float =
        if (chartTotal <= 0) 0f else (count.toDouble() / chartTotal).toFloat()
}
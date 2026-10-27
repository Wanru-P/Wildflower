package com.floralens.domain.model

/**
 * Rich details for an identified species, fetched from Wikipedia in online
 * mode only (see data/details/PlantDetailsService.kt). Everything is
 * nullable because Wikipedia coverage is uneven — the UI shows what's
 * present and omits what isn't, rather than inventing fields.
 *
 * [regionalNames] is best-effort: when a species' Wikipedia intro mentions
 * regional/Indian common names they surface in [extract], but there's no
 * authoritative structured source for folk taxonomy, so this list is only
 * populated when we can extract it confidently — often empty, and that's
 * honest rather than fabricated.
 */
data class PlantDetails(
    val title: String,
    val extract: String?,          // plain-text summary/description
    val thumbnailUrl: String?,     // representative image, if any
    val wikipediaUrl: String?,     // canonical article link
    val scientificName: String,    // what we queried with
)
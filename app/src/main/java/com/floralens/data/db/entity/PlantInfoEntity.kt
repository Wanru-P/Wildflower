package com.floralens.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Offline-saved "Learn more" details for a species — everything the details
 * screen shows except YouTube videos (excluded by design: they're not
 * viewable offline anyway, and caching titles for videos you can't play
 * would just be a wall of dead links).
 *
 * Keyed by scientific name (not plant id) so two plants of the same species
 * share one saved copy. The JSON columns are serialized/deserialized by
 * PlantInfoRepository — this entity is deliberately dumb strings, matching
 * the convention of keeping mapping logic at the edges.
 */
@Entity(tableName = "plant_info")
data class PlantInfoEntity(
    @PrimaryKey val scientificName: String,
    val commonName: String?,
    /** PlantInsights serialized as JSON. */
    val insightsJson: String,
    /** List<GeoPoint> (geocoded native regions) as a JSON array. Empty array if none. */
    val geoPointsJson: String,
    /** OccurrenceData (GBIF) as JSON, or null if it never loaded. */
    val occurrenceJson: String?,
    /** When this copy was saved — shown as "Saved for offline • 12 Jul". */
    val savedAtEpochMs: Long,
)
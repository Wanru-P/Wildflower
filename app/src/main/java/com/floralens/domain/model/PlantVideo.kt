package com.floralens.domain.model

/**
 * A YouTube video result for the Videos tab (online mode only). Tapping a
 * card launches the YouTube app / browser — we don't embed playback, which
 * keeps the feature robust (no broken embeds, no ToS issues).
 */
data class PlantVideo(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
) {
    /** Canonical watch URL — used for the launch intent. */
    val watchUrl: String get() = "https://www.youtube.com/watch?v=$videoId"
}

/** A titled row of videos (e.g. "Care & watering") from one search query. */
data class VideoSection(
    val title: String,
    val videos: List<PlantVideo>,
)
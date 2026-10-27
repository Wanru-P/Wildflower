package com.floralens.ui.presentation

/**
 * Separates a user's observation prose from an attribution line already stored
 * in the legacy note field. This is presentation-only: the original note stays
 * byte-for-byte unchanged and no missing photo metadata is invented.
 */
data class ObservationNotePresentation(
    val observation: String?,
    val photoSource: String?,
)

object ObservationNotePresenter {
    private val sourcePrefixes = listOf(
        "摄影：", "摄影:", "摄影者：", "摄影者:",
        "照片来源：", "照片来源:", "图片来源：", "图片来源:",
        "来源：", "来源:", "许可：", "许可:",
        "photo credit:", "credit:", "license:",
    )

    fun split(note: String?): ObservationNotePresentation {
        val lines = note.orEmpty().lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        if (lines.isEmpty()) return ObservationNotePresentation(null, null)
        val (source, observation) = lines.partition { line ->
            sourcePrefixes.any { prefix -> line.startsWith(prefix, ignoreCase = true) }
        }
        return ObservationNotePresentation(
            observation = observation.joinToString("\n").takeIf(String::isNotBlank),
            photoSource = source.joinToString("\n").takeIf(String::isNotBlank),
        )
    }
}

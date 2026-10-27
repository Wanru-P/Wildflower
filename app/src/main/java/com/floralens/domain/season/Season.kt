package com.floralens.domain.season

import com.floralens.domain.model.Observation
import com.floralens.domain.model.ObservationType
import com.floralens.domain.model.Plant
import java.time.Instant
import java.time.ZoneId

enum class Season(val label: String, val english: String) {
    SPRING("春", "Spring"), SUMMER("夏", "Summer"), AUTUMN("秋", "Autumn"), WINTER("冬", "Winter")
}
data class SeasonPeriod(val year: Int, val season: Season)
object SeasonResolver {
    fun month(month: Int): Season {
        require(month in 1..12)
        return when (month) { in 3..5 -> Season.SPRING; in 6..8 -> Season.SUMMER; in 9..11 -> Season.AUTUMN; else -> Season.WINTER }
    }
    /** Device local zone at query time; December and January remain in their calendar years. */
    fun period(time: Instant, zone: ZoneId): SeasonPeriod = time.atZone(zone).let { SeasonPeriod(it.year, month(it.monthValue)) }
    fun natural(observation: Observation): Boolean = observation.type in setOf(ObservationType.IDENTIFIED, ObservationType.PHOTO_OBSERVATION)
}
data class SeasonalObservation(val plant: Plant, val observation: Observation, val period: SeasonPeriod)
data class SeasonArchive(val plants: List<Plant> = emptyList(), val records: List<SeasonalObservation> = emptyList(),
    val zone: ZoneId = ZoneId.systemDefault(), val loaded: Boolean = false) {
    val years: List<Int> get() = records.map { it.period.year }.distinct().sortedDescending()
    fun observations(year: Int, season: Season) = records.filter { it.period == SeasonPeriod(year, season) }
    fun garden(season: Season?): List<Plant> {
        if (season == null) return plants
        val matching = records.filter { it.period.season == season }.groupBy { it.plant.id }
        return plants.mapNotNull { plant -> matching[plant.id]?.let { records ->
            // All records retained, including missing photos; only the seasonal cover uses latest valid photo.
            plant.copy(photoUri = records.firstOrNull { validPhoto(it.observation.photoUri) }?.observation?.photoUri)
        } }
    }
    companion object {
        fun build(plants: List<Plant>, observations: List<Observation>, zone: ZoneId): SeasonArchive {
            val owners = plants.associateBy { it.id }
            val records = observations.filter(SeasonResolver::natural).sortedWith(compareByDescending<Observation> { it.timestamp }.thenByDescending { it.id })
                .mapNotNull { o -> owners[o.plantId]?.let { SeasonalObservation(it, o, SeasonResolver.period(o.timestamp, zone)) } }
            return SeasonArchive(plants, records, zone, true)
        }
        private fun validPhoto(uri: String?): Boolean = runCatching {
            uri != null && java.net.URI(uri).scheme == "file" && java.io.File(java.net.URI(uri)).isFile
        }.getOrDefault(false)
    }
}

package com.floralens.data.species

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Care defaults for a freshly identified plant, keyed by scientific name.
 * Extracted as an interface so callers (e.g. IdentifyViewModel) don't need
 * a real Android Context in unit tests — swap in a fake in tests the same
 * way PlantIdentifier swaps Fake/TfLite.
 */
interface CareDefaults {
    fun defaultWaterInterval(scientificName: String): Int
}

/**
 * Bundled read-only species reference (assets/species_reference.json) →
 * care defaults (water interval, light) for a freshly identified plant.
 */
@Singleton
class SpeciesReference @Inject constructor(
    @ApplicationContext private val context: Context,
) : CareDefaults {
    data class SpeciesInfo(
        val scientificName: String,
        val commonName: String,
        val defaultWaterIntervalDays: Int,
        val light: String,
    )

    private val entries: List<SpeciesInfo> by lazy { load() }

    fun find(scientificName: String): SpeciesInfo? =
        entries.firstOrNull { it.scientificName.equals(scientificName, ignoreCase = true) }

    override fun defaultWaterInterval(scientificName: String): Int =
        find(scientificName)?.defaultWaterIntervalDays ?: DEFAULT_INTERVAL_DAYS

    private fun load(): List<SpeciesInfo> {
        val json = context.assets.open("species_reference.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    SpeciesInfo(
                        scientificName = o.getString("scientificName"),
                        commonName = o.getString("commonName"),
                        defaultWaterIntervalDays = o.getInt("defaultWaterIntervalDays"),
                        light = o.getString("light"),
                    )
                )
            }
        }
    }

    companion object { const val DEFAULT_INTERVAL_DAYS = 7 }
}
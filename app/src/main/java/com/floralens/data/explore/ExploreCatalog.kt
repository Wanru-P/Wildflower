package com.floralens.data.explore

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class FeaturedSpecies(
    val id: String, val name: String, val scientific: String, val common: String,
    val photo: String, val season: String, val summary: String, val habit: String,
    val family: String, val bloom: String, val source: String, val credit: String,
)

/** Read-only public references: never writes a Plant or Observation. */
@Singleton
class ExploreCatalog @Inject constructor(@ApplicationContext context: Context) {
    val species: List<FeaturedSpecies> = context.assets.open("explore/catalog.json").bufferedReader().use {
        val entries = JSONArray(it.readText())
        List(entries.length()) { index ->
            val o = entries.getJSONObject(index)
            FeaturedSpecies(o.getString("id"), o.getString("name"), o.getString("scientific"), o.getString("common"),
                "file:///android_asset/explore/${o.getString("image")}", o.getString("season"), o.getString("summary"),
                o.getString("habit"), o.getString("family"), o.getString("bloom"), o.getString("source"), o.getString("credit"))
        }
    }
    fun find(scientific: String): FeaturedSpecies? = species.firstOrNull {
        it.scientific.lowercase(Locale.ROOT) == scientific.trim().lowercase(Locale.ROOT)
    }
    fun seasonal(date: LocalDate): FeaturedSpecies = species.firstOrNull { it.season == seasonKey(date.monthValue) } ?: species.first()
}

fun seasonKey(month: Int): String = when (month) {
    in 3..5 -> "spring"
    in 6..8 -> "summer"
    in 9..11 -> "autumn"
    else -> "winter"
}
fun seasonLabel(month: Int): String = when (seasonKey(month)) {
    "spring" -> "春日"
    "summer" -> "夏日"
    "autumn" -> "秋日"
    else -> "冬日"
}

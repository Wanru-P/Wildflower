package com.floralens.data.culture

import android.content.Context
import com.floralens.BuildConfig
import com.floralens.domain.model.SpeciesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

data class LiteraryWork(val author: String?, val title: String?, val excerpt: String?, val sourceUrl: String?, val sourceType: String?)
data class TaxonomySource(val organization: String?, val url: String?)
data class CultureEntry(
    val speciesKey: String, val scientificName: String, val chineseName: String,
    val themeTitle: String, val culturalDescription: String, val culturalSymbolism: List<String>,
    val literaryWorks: List<LiteraryWork>, val taxonomySource: TaxonomySource?, val matchNote: String?,
    val verificationStatus: String, val publicationStatus: String, val safeForAutomaticSpeciesMatch: Boolean,
    val citationScope: String?, val contentAttribution: String?, val contentOrigin: String?,
    val poetrySourceChecked: Boolean, val taxonSourceChecked: Boolean,
)

/** Interpret existing capture snapshots read-only; malformed/alternate results fail closed. */
object CultureSnapshotPolicy {
    fun uncertain(json: String?): Boolean {
        if (json == null) return false // A curated/manual archive may have no identification event.
        return runCatching {
            val snapshot = JSONObject(json)
            if (snapshot.optString("kind") != "high") return@runCatching true
            val best = snapshot.optJSONArray("candidates")?.optJSONObject(0) ?: return@runCatching true
            val selected = com.floralens.domain.culture.CultureIdentityResolver.resolve(snapshot.optJSONObject("selected")?.optString("key").orEmpty())
            val canonical = com.floralens.domain.culture.CultureIdentityResolver.resolve(scientific = best.optString("scientific"))
            val score = best.optDouble("confidence", Double.NaN)
            !selected.canGenerate || !canonical.canGenerate || selected.key != canonical.key || !score.isFinite() ||
                score !in com.floralens.domain.usecase.IdentifyPlant.HIGH_CONFIDENCE_THRESHOLD.toDouble()..1.0
        }.getOrDefault(true)
    }
}

/** Separate editorial restrictions; never mutates the supplied 17/23/10 statuses or prose. */
object CulturePolicy {
    /** Publishing a reading note does not verify its citations or botanical identity. */
    fun readable(entry: CultureEntry): Boolean = entry.publicationStatus == "approved_for_publication"
    fun readingWorks(entry: CultureEntry): List<LiteraryWork> =
        if (entry.verificationStatus in setOf("source_checked", "literary_source_checked_taxon_pending")) entry.literaryWorks
        else emptyList()
    val citationPending = setOf("scientific:salix babylonica", "scientific:citrus reticulata",
        "scientific:narcissus tazetta", "scientific:metasequoia glyptostroboides", "scientific:cercis chinensis")
    fun validUrl(url: String?): Boolean = runCatching {
        val uri = URI(url ?: return false)
        uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
    fun canonicalKey(key: String): String? {
        if (!key.startsWith("scientific:")) return null
        val name = key.removePrefix("scientific:")
        return SpeciesKey.fromLegacy(name, "").takeIf { it.startsWith("scientific:") }
    }
    fun eligible(entry: CultureEntry, development: Boolean): Boolean =
        entry.safeForAutomaticSpeciesMatch && entry.verificationStatus == "source_checked" &&
            entry.speciesKey !in citationPending && canonicalKey(entry.speciesKey) == entry.speciesKey &&
            validUrl(entry.taxonomySource?.url) && entry.taxonSourceChecked && entry.poetrySourceChecked &&
            entry.literaryWorks.isNotEmpty() && entry.literaryWorks.all {
                !it.author.isNullOrBlank() && !it.title.isNullOrBlank() && !it.excerpt.isNullOrBlank() && validUrl(it.sourceUrl)
            } && (if (development) entry.publicationStatus in setOf("ready_for_editorial_review", "approved_for_publication")
                else entry.publicationStatus == "approved_for_publication")
    fun notice(entry: CultureEntry): String = when (entry.verificationStatus) {
        "source_checked" -> if (readable(entry)) "已批准发布 · 来源检查资料" else "来源审查稿 · 待编辑批准，非发布级资料"
        "literary_source_checked_taxon_pending" -> "文化名称与现代物种对应待核实"
        else -> "编辑草稿／待核实 · 未经独立来源核验"
    }
}

@Singleton
class CultureCatalog @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val mutex = Mutex()
    private var cached: List<CultureEntry>? = null
    suspend fun entries(): List<CultureEntry> = withContext(Dispatchers.IO) {
        mutex.withLock {
            cached ?: parse(context.assets.open("culture/culture_entries.json").bufferedReader().use { it.readText() })
                .also { cached = it }
        }
    }
    companion object {
        fun parse(text: String): List<CultureEntry> {
            val root = JSONObject(text)
            val array = root.getJSONArray("entries")
            val result = List(array.length()) { i ->
                val o = array.getJSONObject(i)
                val works = o.optJSONArray("literaryWorks")
                val taxon = o.optJSONObject("taxonomySource")
                val symbols = o.optJSONArray("culturalSymbolism")
                CultureEntry(o.getString("speciesKey"), o.getString("scientificName"), o.getString("chineseName"),
                    o.getString("themeTitle"), o.getString("culturalDescription"),
                    List(symbols?.length() ?: 0) { symbols!!.getString(it) },
                    List(works?.length() ?: 0) { j -> works!!.getJSONObject(j).let {
                        LiteraryWork(it.nullString("author"), it.nullString("title"), it.nullString("excerpt"), it.nullString("sourceUrl"), it.nullString("sourceType"))
                    } }, taxon?.let { TaxonomySource(it.nullString("organization"), it.nullString("url")) },
                    o.nullString("matchNote"), o.getString("verificationStatus"), o.getString("publicationStatus"),
                    o.optBoolean("safeForAutomaticSpeciesMatch", false), o.nullString("citationScope"),
                    o.nullString("contentAttribution"), o.nullString("contentOrigin"), o.optBoolean("poetrySourceChecked"), o.optBoolean("taxonSourceChecked"))
            }
            require(result.size == root.getInt("entryCount")) { "文化资料数量不一致" }
            require(result.map { it.speciesKey }.distinct().size == result.size) { "文化资料标识重复" }
            return result
        }
        private fun JSONObject.nullString(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    }
}

@Singleton
class CultureRepository @Inject constructor(private val catalog: CultureCatalog) {
    private val mutex = Mutex()
    private var index: Map<String, CultureEntry>? = null
    private suspend fun indexed(): Map<String, CultureEntry> = mutex.withLock {
        index ?: catalog.entries().associateBy { it.speciesKey }.also { index = it }
    }
    /** Exact catalog identity only; the UI must retain each note's literary/taxon scope. */
    suspend fun readingContent(key: String): CultureEntry? {
        if (!key.startsWith("scientific:")) return null
        val identity = com.floralens.domain.culture.CultureIdentityResolver.resolve(key)
        val lookup = if (identity.canGenerate) identity.key else key.trim().replace(Regex("\\s+"), " ").lowercase(java.util.Locale.ROOT)
        return indexed()[lookup]?.takeIf(CulturePolicy::readable)
    }
    suspend fun matchSpecies(key: String): CultureEntry? = readingContent(key)
    /** Guarded at repository AND route: release never exposes the review catalog or draft detail. */
    suspend fun editorialEntries(): List<CultureEntry> = if (BuildConfig.DEBUG && BuildConfig.INTERNAL_TOOLS) indexed().values.toList() else emptyList()
    suspend fun editorialEntry(key: String): CultureEntry? = if (BuildConfig.DEBUG && BuildConfig.INTERNAL_TOOLS) indexed()[key] else null
    /** Export/chat quotations require evidence and taxon suitability in addition to reading approval. */
    suspend fun approvedContent(key: String): CultureEntry? =
        com.floralens.domain.culture.CultureIdentityResolver.resolve(key).takeIf { it.canGenerate }?.key
            ?.let { indexed()[it] }?.takeIf { CulturePolicy.eligible(it, false) }
}

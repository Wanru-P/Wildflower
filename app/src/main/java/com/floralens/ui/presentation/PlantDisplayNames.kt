package com.floralens.ui.presentation

import java.util.Locale

/** Presentation only: never changes model labels, species keys, confidence or saved records. */
object PlantDisplayNames {
    data class Entry(val chinese: String, val family: String? = null, val genus: String? = null) {
        val classification: String? get() = listOfNotNull(family, genus).takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    // Verified exact botanical names. The first culture-catalog group carries a
    // taxonomy source; the original five-name provenance remains documented in
    // docs/wildflower/phase6b-copy.md. These entries affect presentation only.
    // Exact binomials only. No fuzzy Chinese matching or invented translations.
    private val entries = mapOf(
        "osmanthus fragrans" to Entry("桂花", "木犀科", "木犀属"),
        "ginkgo biloba" to Entry("银杏", "银杏科", "银杏属"),
        "camellia japonica" to Entry("山茶", "山茶科", "山茶属"),
        "lagerstroemia indica" to Entry("紫薇", "千屈菜科", "紫薇属"),
        "magnolia grandiflora" to Entry("广玉兰", "木兰科", "木兰属"),
        "prunus mume" to Entry("梅花"),
        "nelumbo nucifera" to Entry("荷花"),
        "prunus persica" to Entry("桃花"),
        "punica granatum" to Entry("石榴"),
        "gardenia jasminoides" to Entry("栀子"),
        "chimonanthus praecox" to Entry("蜡梅"),
        "prunus armeniaca" to Entry("杏花"),
        "camellia sinensis" to Entry("茶树"),
        "litchi chinensis" to Entry("荔枝"),
        "ziziphus jujuba" to Entry("枣树"),
        "jasminum sambac" to Entry("茉莉"),
        "hibiscus syriacus" to Entry("木槿"),
        "hibiscus mutabilis" to Entry("木芙蓉"),
        "eriobotrya japonica" to Entry("枇杷"),
    )
    private val whitespace = Regex("\\s+")
    private val chinese = Regex("[\\u3400-\\u9fff]")

    fun find(scientificName: String?): Entry? = scientificName?.trim()
        ?.replace(whitespace, " ")?.lowercase(Locale.ROOT)?.let(entries::get)

    fun chinese(scientificName: String?, commonName: String?): String =
        find(scientificName)?.chinese ?: commonName?.trim()?.takeIf { chinese.containsMatchIn(it) } ?: "未知植物"

    fun confidence(value: Float): String = if (value.isFinite())
        "识别可信度 ${(value.coerceIn(0f, 1f) * 100).toInt()}%" else "识别可信度暂不可用"
}

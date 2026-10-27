package com.floralens.ml

/** Pl@ntNet commonNames are untagged strings. Requests use lang=zh; never guess a translation. */
internal object PlantNetCommonNames {
    private val han = Regex("[\\u3400-\\u9fff]")

    fun preferred(names: List<String>, scientificName: String): String {
        val available = names.map(String::trim).filter(String::isNotEmpty)
        return available.firstOrNull { han.containsMatchIn(it) }
            ?: available.firstOrNull()
            ?: scientificName
    }
}

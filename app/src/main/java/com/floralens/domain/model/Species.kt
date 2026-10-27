package com.floralens.domain.model

import java.security.MessageDigest
import java.util.Locale

/** Public identity, never an owner record and never a confidence score. */
data class Species(val key: String, val scientificName: String?, val commonName: String) {
    companion object {
        fun identified(scientific: String, common: String) = Species(
            SpeciesKey.fromLegacy(scientific, common), scientific.takeIf { SpeciesKey.isScientific(it) }, common)
        fun manual(name: String) = Species(SpeciesKey.unknown(name), null, name.trim())
    }
}

object SpeciesKey {
    private fun normalized(value: String) = value.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
    // A syntactically plausible name is an identifier, NOT a taxonomic validation claim.
    fun isScientific(value: String) = Regex("[a-z][a-z-]+ [a-z][a-z-]+(?: .*|)").matches(normalized(value))
    fun fromLegacy(scientific: String, common: String): String = if (isScientific(scientific))
        "scientific:${normalized(scientific)}" else unknown(common.ifBlank { scientific })
    fun unknown(label: String): String = "unknown:" + MessageDigest.getInstance("SHA-256")
        .digest(normalized(label).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

package com.floralens.domain.card

import com.floralens.data.culture.CultureEntry
import java.time.LocalDate
import com.floralens.domain.culture.NatureExpression

/** Immutable snapshot of one owned observation, not a database entity or UI state. */
data class CardContent(
    val plantId: Long, val observationId: Long, val nickname: String,
    val chineseSpeciesName: String, val scientificName: String, val photoUri: String,
    val observedDate: LocalDate, val note: String?, val season: String,
    val speciesKey: String, val approvedCulture: CultureEntry? = null,
    val natureExpression: NatureExpression? = null, val useNatureInJournal: Boolean = false, val expressionOverridden: Boolean = false,
)

/** Only CardValidation may create the rendering capability. No public copy constructor. */
class ValidatedCardContent internal constructor(val content: CardContent)

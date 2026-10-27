package com.floralens.card.render

import android.graphics.Bitmap
import android.graphics.RectF
import com.floralens.domain.card.CardContent

internal object ModernJournalRenderer {
    fun draw(p: CardPage, c: CardContent, photo: Bitmap) = with(p) {
        // Remove all printed sample data before drawing the owned observation.
        blank(RectF(135f,250f,970f,520f))
        text(c.observedDate.dayOfMonth.toString().padStart(2,'0'), RectF(145f,270f,480f,505f), 208f, min = 150f)
        text("${c.observedDate.year} / ${c.observedDate.monthValue.toString().padStart(2,'0')}\n${c.season}日观察",
            RectF(560f,340f,970f,515f), 42f, color = brown)
        photo(photo, RectF(145f,580f,1455f,1430f))
        blank(RectF(128f,1560f,1320f,1730f))
        val height = text(c.nickname, RectF(140f,1550f,1310f,1685f), 76f, min = 36f)
        val latinTop = 1550f + height + 22f
        val latinHeight = text(c.scientificName.ifBlank { "学名未核实" }, RectF(140f,latinTop,1300f,1850f), 42f, italic = true, color = brown)
        val journalText = listOfNotNull(c.note?.takeIf(String::isNotBlank),
            c.natureExpression?.takeIf { c.useNatureInJournal }?.let { "${it.source.label}\n${it.text}" }).joinToString("\n").takeIf(String::isNotBlank)
        journalText?.let {
            text(it, RectF(140f,latinTop + latinHeight + 35f,1210f,2140f), 32f, serif = false)
        }
        blank(RectF(1280f,2270f,1510f,2350f))
        text("${c.plantId} / ${c.observationId}", RectF(1250f,2285f,1490f,2350f), 27f, color = brown, center = true)
    }
}

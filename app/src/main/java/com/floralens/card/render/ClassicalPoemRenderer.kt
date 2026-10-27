package com.floralens.card.render

import android.graphics.Bitmap
import android.graphics.RectF
import com.floralens.domain.card.CardContent

internal object ClassicalPoemRenderer {
    fun draw(p: CardPage, c: CardContent, photo: Bitmap) = with(p) {
        blank(RectF(100f,88f,990f,142f))
        text("WILDFLOWER LENS / CULTURAL HERBARIUM", RectF(105f,94f,1030f,148f), 30f, color = brown)
        val height = text(c.chineseSpeciesName.ifBlank { c.nickname }, RectF(130f,300f,1050f,430f), 80f, min = 36f)
        text(c.scientificName.ifBlank { "学名未核实" }, RectF(130f,300f + height + 20f,1040f,585f), 40f, italic = true, color = brown)
        photo(photo, RectF(350f,635f,1250f,1540f))
        blank(RectF(595f,1600f,900f,1670f))
        text(date(c), RectF(545f,1615f,1040f,1690f), 36f, center = true, color = brown)
        val work = c.approvedCulture?.literaryWorks?.firstOrNull()?.takeUnless { c.expressionOverridden }
        if (work != null) {
            text(work.excerpt.orEmpty(), RectF(195f,1780f,1270f,1950f), 48f, min = 28f, center = true)
            text("${work.author} · ${work.title}\n${work.sourceUrl}", RectF(195f,1970f,1270f,2170f), 27f, min = 22f, color = brown, center = true)
        } else if (c.natureExpression != null) {
            // Four modern lines need a slightly lower text minimum in the same approved
            // text box. Never scale the artwork, move the photo, or drop a verse.
            text(c.natureExpression.text, RectF(195f,1780f,1270f,1950f), 48f, min = 24f, center = true)
            text(listOfNotNull(c.natureExpression.source.label, c.note?.takeIf(String::isNotBlank)).joinToString("\n"),
                RectF(195f,1970f,1270f,2170f), 27f, min = 22f, color = brown, center = true)
        } else {
            text("${c.season}日观察 · 一页自然", RectF(195f,1725f,1260f,1790f), 42f, color = brown, center = true)
            c.note?.takeIf(String::isNotBlank)?.let { text(it, RectF(195f,1900f,1240f,2170f), 32f, serif = false) }
        }
        blank(RectF(725f,2240f,910f,2315f))
        text("${c.plantId} / ${c.observationId}", RectF(720f,2260f,970f,2325f), 27f, color = brown)
    }
}

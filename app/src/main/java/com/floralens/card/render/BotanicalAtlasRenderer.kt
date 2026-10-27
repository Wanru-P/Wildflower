package com.floralens.card.render

import android.graphics.Bitmap
import android.graphics.RectF
import com.floralens.domain.card.CardContent

internal object BotanicalAtlasRenderer {
    fun draw(p: CardPage, c: CardContent, photo: Bitmap) = with(p) {
        text(c.chineseSpeciesName.ifBlank { c.nickname }, RectF(140f,320f,620f,475f), 80f, min = 36f)
        text(c.scientificName.ifBlank { "学名未核实" }, RectF(730f,325f,1220f,550f), 40f, italic = true, color = brown)
        photo(photo, RectF(145f,600f,1455f,1480f))
        // The base's writing rules must not run through a dynamic, possibly multi-line nickname.
        blank(RectF(130f,1550f,1230f,1810f))
        val height = text(c.nickname, RectF(140f,1580f,1190f,1720f), 58f, min = 36f)
        text("${c.season}日观察 · ${date(c)}", RectF(140f,1580f + height + 24f,1190f,1825f), 36f, color = brown)
        c.note?.takeIf(String::isNotBlank)?.let { text(it, RectF(140f,1840f,1180f,2150f), 32f, serif = false) }
        blank(RectF(635f,2235f,865f,2310f))
        text("${c.plantId} / ${c.observationId}", RectF(645f,2245f,910f,2320f), 27f, color = brown)
    }
}

package com.floralens.card.render

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.floralens.domain.card.*
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import kotlin.math.max
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.floralens.R
import javax.inject.Singleton

/** Only bundled visual resources; no repository, user URI, permissions or screen density. */
@Singleton
class CardRenderer @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val chinese by lazy { context.resources.getFont(R.font.noto_serif_sc) }
    private val latin by lazy { context.resources.getFont(R.font.lora_regular) }
    private val branch by lazy { BitmapFactory.decodeResource(context.resources, R.drawable.wf_ornament_osmanthus_branch) }
    fun render(content: ValidatedCardContent, template: CardTemplateId, photo: Bitmap, width: Int = CardTemplate.WIDTH): Bitmap {
        require(width in 160..CardTemplate.WIDTH)
        require(!photo.isRecycled)
        // Defense in depth even for internal callers: a mutated/forged culture snapshot fails closed.
        CardValidation.validate(content.content, content.content.plantId, true)
        val result = createBitmap(width, width * 3 / 2)
        try {
            val canvas = Canvas(result).apply { scale(width / 1600f, width / 1600f) }
            // Only the active base is decoded; previews sample down and exports use full pixels.
            val sample = when { width <= 200 -> 8; width <= 400 -> 4; width <= 800 -> 2; else -> 1 }
            val baseId = PoetryTemplateVisuals.base(template)
            fun drawResource(resource: Int, rect: RectF, alpha: Int) {
                val layer = BitmapFactory.decodeResource(context.resources, resource, BitmapFactory.Options().apply { inSampleSize = sample; inScaled = false })
                    ?: error("诗笺素材无法读取")
                try { canvas.drawBitmap(layer, null, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha }) } finally { layer.recycle() }
            }
            drawResource(baseId, RectF(0f, 0f, 1600f, 2400f), 255)
            val page = CardPage(canvas, chinese, latin, branch, ::drawResource)
            when(template) {
                CardTemplateId.BOTANICAL_ATLAS -> BotanicalAtlasRenderer.draw(page, content.content, photo)
                CardTemplateId.CLASSICAL_POETRY -> ClassicalPoemRenderer.draw(page, content.content, photo)
                CardTemplateId.MODERN_JOURNAL -> ModernJournalRenderer.draw(page, content.content, photo)
            }
            return result
        } catch(e: Throwable) { result.recycle(); throw e }
    }
}

internal class CardPage(val canvas: Canvas, private val chinese: Typeface? = null, private val latin: Typeface? = null, private val branch: Bitmap? = null, private val drawResource: ((Int, RectF, Int) -> Unit)? = null) {
    val ink = Color.rgb(35,79,54)
    val brown = Color.rgb(155,128,104)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    fun layer(resource: Int, rect: RectF, alpha: Int = 255) { drawResource?.invoke(resource, rect, alpha) }
    /** Replace printed sample fields, not the template's ornamental composition. */
    fun blank(rect: RectF) { layer(R.drawable.wf_poetry_paper, rect) }
    fun background(color: Int) { canvas.drawColor(color) }
    fun paper(color: Int) {
        background(color)
        paint.color = Color.argb(9, 115, 93, 66)
        for(y in 40..2370 step 31) for(x in 40..1570 step 37) {
            val dx = ((x * 17 + y * 7) % 19).toFloat()
            canvas.drawCircle(x + dx, y.toFloat(), 1.2f, paint)
        }
    }
    fun flourish(rect: RectF, alpha: Int = 65) {
        branch?.let { paint.alpha = alpha; canvas.drawBitmap(it, null, rect, paint); paint.alpha = 255 }
    }
    fun stamp(x: Float, y: Float, radius: Float) {
        paint.color = brown; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
        canvas.drawCircle(x,y,radius,paint); canvas.drawCircle(x,y,radius-9f,paint); paint.style = Paint.Style.FILL
        text("BOTANICAL\nNOTES", RectF(x-radius+12f,y-44f,x+radius-12f,y+50f), 24f, min = 22f, color = brown, center = true)
    }
    fun line(x: Float, y: Float, right: Float, color: Int = brown) {
        paint.color = color; paint.strokeWidth = 2f; canvas.drawLine(x,y,right,y,paint)
    }
    fun border(rect: RectF, color: Int = brown) {
        paint.color = color; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
        canvas.drawRect(rect,paint); paint.style = Paint.Style.FILL
    }
    fun photo(photo: Bitmap, box: RectF, focusX: Float = .5f) {
        require(focusX in 0f..1f)
        val scale = max(box.width()/photo.width, box.height()/photo.height)
        val w = box.width()/scale; val h = box.height()/scale
        val left = ((photo.width-w)*focusX).toInt()
        val crop = Rect(left, ((photo.height-h)/2).toInt(), (left+w).toInt(), ((photo.height+h)/2).toInt())
        canvas.drawBitmap(photo, crop, box, paint)
    }
    /** Measure whole text; shrink only within readable limits, otherwise fail visibly before export. */
    fun text(text: String, box: RectF, size: Float, min: Float = 26f, italic: Boolean = false,
        color: Int = ink, center: Boolean = false, serif: Boolean = true): Int {
        if(text.isBlank()) return 0
        require(box.left >= 0 && box.top >= 0 && box.right <= 1600 && box.bottom <= 2400)
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = if(serif) Typeface.create(if(text.any { it.code >= 0x3000 }) chinese ?: Typeface.SERIF else latin ?: Typeface.SERIF, if(italic) Typeface.ITALIC else Typeface.NORMAL)
                else Typeface.create("sans-serif", Typeface.NORMAL)
        }
        var current = size
        while(current >= min) {
            p.textSize = current
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, p, box.width().toInt())
                .setAlignment(if(center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false).setLineSpacing(current * .18f, 1f).build()
            if(layout.height <= box.height() && (0 until layout.lineCount).all { layout.getLineWidth(it) <= box.width()+1 }) {
                canvas.withTranslation(box.left, box.top) { layout.draw(this) }
                return layout.height
            }
            current -= 2f
        }
        error("文字超出模板容量，请选择其他模板或整理观察备注；原文未截断")
    }
    fun date(c: CardContent) = c.observedDate.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
    fun record(c: CardContent) = "个人观察  ${c.plantId} / ${c.observationId}"
}

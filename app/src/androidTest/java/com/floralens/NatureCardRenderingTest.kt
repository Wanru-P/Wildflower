package com.floralens

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.card.render.CardRenderer
import com.floralens.data.culture.*
import com.floralens.domain.card.*
import com.floralens.domain.culture.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** Renderer contract fixtures only. Does not publish literature or insert an observation. */
class NatureCardRenderingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val identity = CultureIdentityResolver.resolve(scientific = "Ginkgo biloba")
    private val text = "试着为一次相遇留下疑问，\n让每次停留都成为理解的开始。\n愿记忆不急于给出答案，\n把目光交还给下一次认真。"
    private val expression get() = NatureExpression(text, ExpressionSource.AI, identity.key)
    private fun content() = CardContent(1, 2, "银杏 · 参考影集", "银杏", "Ginkgo biloba", "file:/renderer-fixture.jpg",
        LocalDate.of(2026, 10, 14), "摄影：CC0 参考素材；日期仅为版式测试，不代表拍摄时间。", "秋", identity.key, natureExpression = expression)
    private fun render(c: CardContent, type: CardTemplateId, width: Int = 1600): android.graphics.Bitmap {
        val photo = context.assets.open("explore/ginkgo.jpg").use { BitmapFactory.decodeStream(it)!! }
        return try { CardRenderer(context).render(CardValidation.validate(c, 1, true), type, photo, width) } finally { photo.recycle() }
    }
    @Test fun fourModernLinesFitAllThreeOriginalTemplatesAndPreview() {
        assertTrue(NatureTextPolicy.valid(expression))
        CardTemplateId.entries.forEach { type ->
            val c = content().copy(useNatureInJournal = true)
            val full = render(c, type); val preview = render(c, type, 640)
            try { assertEquals(1600, full.width); assertEquals(2400, full.height); assertEquals(960, preview.height)
                assertEquals(text, c.natureExpression!!.text)
            } finally { full.recycle(); preview.recycle() }
        }
    }
    @Test fun journalHonorsNoteUntilCreativeMaterialIsExplicitlySelected() {
        val c = content()
        val plain = render(c.copy(natureExpression = null), CardTemplateId.MODERN_JOURNAL)
        val automatic = render(c, CardTemplateId.MODERN_JOURNAL)
        val opted = render(c.copy(useNatureInJournal = true), CardTemplateId.MODERN_JOURNAL)
        try { assertTrue(plain.sameAs(automatic)); assertFalse(plain.sameAs(opted)); assertEquals(content().note, c.note) }
        finally { plain.recycle(); automatic.recycle(); opted.recycle() }
    }
    @Test fun publishedClassicWinsUntilUserExplicitlyOverrides() = runBlocking {
        val catalog = CultureCatalog(context)
        val approved = requireNotNull(CultureRepository(catalog).approvedContent(identity.key))
        val c = content().copy(approvedCulture = approved)
        val classic = render(c.copy(natureExpression = null), CardTemplateId.CLASSICAL_POETRY)
        val withCreative = render(c, CardTemplateId.CLASSICAL_POETRY)
        val user = render(c.copy(expressionOverridden = true,
            natureExpression = expression.copy(source = ExpressionSource.USER)), CardTemplateId.CLASSICAL_POETRY)
        try { assertTrue(classic.sameAs(withCreative)); assertFalse(classic.sameAs(user)) }
        finally { classic.recycle(); withCreative.recycle(); user.recycle() }
        assertTrue(runCatching { CardValidation.validate(c.copy(natureExpression = expression.copy(identityKey = "scientific:osmanthus fragrans")), 1, true) }.isFailure)
        assertEquals(approved, CultureRepository(catalog).approvedContent(identity.key))
    }
}

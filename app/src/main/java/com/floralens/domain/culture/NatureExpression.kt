package com.floralens.domain.culture

import java.security.MessageDigest
import java.util.Locale

enum class ExpressionSource(val label: String) {
    LOCAL("自然观察文字 · 草木知时"), AI("AI 原创 · DeepSeek · 非古典文献"), USER("用户自写 · 非已核实古典文献")
}
data class NatureExpression(val text: String, val source: ExpressionSource, val identityKey: String)
data class CultureIdentity(val key: String, val scientific: String?, val chinese: String?, val certain: Boolean) {
    // Identity certainty still gates literature matching; it must not gate general creative writing.
    val canGenerate: Boolean get() = certain && scientific != null
    val natureMode: String get() = if (canGenerate) "species" else "general"
}

/** Conservative identity normalization. A name is not proof of an ancient plant's identity. */
object CultureIdentityResolver {
    // Sources: POWO Ginkgo biloba / Osmanthus fragrans, recorded in phase6b5/RESULTS.md.
    private val synonyms = mapOf("salisburia adiantifolia" to "ginkgo biloba", "salisburia biloba" to "ginkgo biloba")
    private val chinese = mapOf("银杏" to "ginkgo biloba", "白果" to "ginkgo biloba", "桂花" to "osmanthus fragrans",
        "梅花" to "prunus mume", "荷花" to "nelumbo nucifera", "桃花" to "prunus persica",
        "山茶" to "camellia japonica", "山茶花" to "camellia japonica", "紫薇" to "lagerstroemia indica", "广玉兰" to "magnolia grandiflora")
    private val display = mapOf("ginkgo biloba" to "银杏", "osmanthus fragrans" to "桂花", "prunus mume" to "梅花",
        "nelumbo nucifera" to "荷花", "prunus persica" to "桃花", "camellia japonica" to "山茶",
        "lagerstroemia indica" to "紫薇", "magnolia grandiflora" to "广玉兰")
    fun resolve(key: String = "", scientific: String? = null, common: String? = null, certain: Boolean = true): CultureIdentity {
        val raw = scientific?.takeIf(String::isNotBlank) ?: key.takeIf { it.startsWith("scientific:") }?.removePrefix("scientific:")
        val normalized = raw?.trim()?.replace(Regex("\\s+"), " ")?.lowercase(Locale.ROOT)
        // Authorities can be removed, but never silently collapse varieties/hybrids to species.
        val words = normalized?.split(' ').orEmpty()
        val binomial = if (words.size >= 2 && words.take(2).all { it.matches(Regex("[a-z][a-z-]+")) } &&
            words.take(2).none { it in setOf("sp", "spp", "unknown", "unidentified", "species", "genus", "null") } &&
            (words.size == 2 || words.drop(2).all { it.matches(Regex("[a-z.()&]+")) } &&
                words.drop(2).all { it in setOf("l.", "lour.", "gaertn.", "(l.)", "batsch", "siebold", "(siebold)", "&", "zucc.", "sm.", "hoffmanns.") })) words.take(2).joinToString(" ") else null
        // Chinese lookup only when explicitly requested without a stored unknown/manual identity.
        val name = binomial?.let { synonyms[it] ?: it } ?: if (key.isBlank() && raw.isNullOrBlank()) chinese[common] else null
        val canonical = name?.let { "scientific:$it" } ?: key.ifBlank { "unknown:unresolved" }
        return CultureIdentity(canonical, name?.replaceFirstChar(Char::titlecase), display[name], certain && name != null)
    }
}

/** Certainty belongs to the selected identification, never to a species globally. */
object CultureIdentificationPolicy {
    /** Missing metadata on a capture-backed route is uncertainty, not a public species browse. */
    fun contextCertain(captureRequested: Boolean, result: com.floralens.domain.model.IdResult?, selectedKey: String?,
        archiveAvailable: Boolean = true, uncertainArchive: Boolean = false): Boolean =
        archiveAvailable && !uncertainArchive && (!captureRequested || certain(result, selectedKey))

    fun certain(result: com.floralens.domain.model.IdResult?, selectedKey: String?): Boolean {
        val high = result as? com.floralens.domain.model.IdResult.HighConfidence ?: return false
        val selected = CultureIdentityResolver.resolve(selectedKey.orEmpty())
        val best = CultureIdentityResolver.resolve(scientific = high.best.scientificName)
        return high.best.confidence.isFinite() && high.best.confidence in com.floralens.domain.usecase.IdentifyPlant.HIGH_CONFIDENCE_THRESHOLD..1f &&
            selected.canGenerate && best.canGenerate && selected.key == best.key
    }
}

object NatureTextPolicy {
    const val VERSION = 1
    const val MAX = 120
    fun validAI(text: String): Boolean {
        val lines = text.trim().lines()
        return text == text.trim() && text.length in 12..MAX && lines.size in 2..4 && lines.all { it.length in 3..30 && it.count { char -> char in '\u3400'..'\u9fff' } >= 3 } &&
            text.none { it.isISOControl() && it != '\n' } &&
            !Regex("```|[{}<>#*]|https?://|《|》|作者|出处|出自|李白|杜甫|苏轼|李清照|古诗|古籍|传说|据史|公元|\\d|原产|花期|常绿|落叶植物|药用|功效|治疗|我在|今天|昨天|昨日|拍摄|观察到|开花|结出|花瓣|香|芬芳|叶片|叶子|叶形|扇形|针形|叶色|叶缘|叶脉|花色|树皮|树高|果实|种子|多年生|一年生|灌木|乔木|耐旱|耐寒|喜光|有毒|无毒|气味|繁殖|分布|生长于|生长在|校园|雨后|金黄|金色|黄色|绿色|红色|白色|紫色|银白|粉红|窗前|窗外|路旁|树下|枝头|枝间|叶间|叶腋|庭院|微风|阳光|暮色|晨光|清晨|黄昏|傍晚|春天|夏日|秋日|冬日").containsMatchIn(text)
    }
    fun valid(expression: NatureExpression): Boolean = expression.identityKey.isNotBlank() && expression.text.isNotBlank() &&
        expression.text.length <= MAX && (expression.source != ExpressionSource.AI || validAI(expression.text))
    fun local(identity: CultureIdentity): NatureExpression {
        val choices = listOf("先记下眼前的轮廓与颜色，\n为下一次相遇留一个线索。", "不急着为自然下结论，\n让一次记录成为下一次观察的起点。", "将目光放慢一些，\n试着留意这株植物与周围环境的关系。")
        val index = (identity.key.hashCode().toLong() and 0x7fffffff) % choices.size
        return NatureExpression(choices[index.toInt()], ExpressionSource.LOCAL, identity.key)
    }
    fun cacheKey(identity: CultureIdentity, style: String) = MessageDigest.getInstance("SHA-256")
        .digest("$VERSION|${if (identity.canGenerate) identity.key else "general|${identity.key}"}|$style".toByteArray())
        .joinToString("") { "%02x".format(it) }
}

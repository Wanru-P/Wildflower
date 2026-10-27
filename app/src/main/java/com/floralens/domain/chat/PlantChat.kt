package com.floralens.domain.chat

import com.floralens.domain.culture.CultureIdentity

enum class PlantChatSource(val routeValue: String, val label: String) {
    CAPTURE("capture", "本次识别"),
    SPECIES("species", "植物详情"),
    PLANT("plant", "我的档案");

    companion object {
        fun fromRoute(value: String?): PlantChatSource? = entries.firstOrNull { it.routeValue == value }
    }
}

data class ApprovedCultureReference(
    val title: String,
    val author: String,
    val excerpt: String,
    val sourceUrl: String,
)

/** Read-only context. [imageUri] is display-only and is never part of a network request. */
data class PlantChatContext(
    val source: PlantChatSource,
    val identity: CultureIdentity,
    val displayName: String,
    val scientificName: String?,
    val imageUri: String?,
    val confidence: Float? = null,
    val approvedReference: ApprovedCultureReference? = null,
) {
    val stableKey: String get() = "${source.routeValue}|${identity.key}"
    val identityNotice: String? get() = if (identity.certain) null else
        "识别仍有不确定性，以下回答只提供一般观察建议，不代表已确定物种。"
}

enum class PlantChatRole { USER, ASSISTANT }

data class PlantChatMessage(
    val id: Long,
    val role: PlantChatRole,
    val text: String,
)

data class PlantChatTurn(val role: PlantChatRole, val text: String)

object PlantChatPolicy {
    const val MAX_QUESTION = 300
    const val MAX_ANSWER = 1200
    const val MAX_HISTORY_MESSAGES = 10
    const val MAX_HISTORY_CHARS = 6000

    fun validQuestion(value: String): Boolean {
        val text = value.trim()
        return text.isNotEmpty() && text.length <= MAX_QUESTION && text.none { it.isISOControl() && it != '\n' && it != '\t' }
    }

    fun validAnswer(value: String): Boolean {
        val text = value.trim()
        return text.length in 2..MAX_ANSWER && text.none { it.isISOControl() && it != '\n' && it != '\t' } &&
            !Regex("```|<script|https?://", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    /** Keep only recent complete turns, with a hard total-character ceiling. */
    fun trimmedHistory(history: List<PlantChatTurn>): List<PlantChatTurn> {
        val completePairs = history.chunked(2).mapNotNull { pair ->
            if (pair.size == 2 && pair[0].role == PlantChatRole.USER && pair[1].role == PlantChatRole.ASSISTANT) pair else null
        }
        val accepted = ArrayDeque<List<PlantChatTurn>>()
        var characters = 0
        for (pair in completePairs.asReversed()) {
            val cleaned = pair.map { it.copy(text = it.text.trim()) }
            val pairCharacters = cleaned.sumOf { it.text.length }
            if (cleaned.any { it.text.isEmpty() || it.text.length > MAX_ANSWER }) continue
            if (accepted.sumOf { it.size } + 2 > MAX_HISTORY_MESSAGES || characters + pairCharacters > MAX_HISTORY_CHARS) break
            accepted.addFirst(cleaned)
            characters += pairCharacters
        }
        return accepted.flatten()
    }

    fun suggestions(certain: Boolean): List<String> = if (certain) listOf(
        "下次观察可以重点记录什么？",
        "怎样辨认相近的植物？",
        "它有哪些常见文化意象？",
        "季节变化时可以留意什么？",
    ) else listOf(
        "我可以先观察哪些特征？",
        "怎样拍照更容易识别？",
        "下次记录可以写些什么？",
        "怎样避免误认植物？",
    )
}

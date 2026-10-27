package com.floralens

import com.floralens.domain.chat.PlantChatPolicy
import com.floralens.domain.chat.PlantChatRole
import com.floralens.domain.chat.PlantChatTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlantChatPolicyTest {
    @Test fun questionAndAnswerBoundsRejectControlMarkupAndUrls() {
        assertTrue(PlantChatPolicy.validQuestion("怎样观察银杏的季节变化？"))
        assertFalse(PlantChatPolicy.validQuestion(""))
        assertFalse(PlantChatPolicy.validQuestion("问".repeat(301)))
        assertFalse(PlantChatPolicy.validQuestion("你好\u0000世界"))
        assertTrue(PlantChatPolicy.validAnswer("可以先记录整体轮廓，再比较下一次观察。"))
        assertFalse(PlantChatPolicy.validAnswer("https://example.org"))
        assertFalse(PlantChatPolicy.validAnswer("```json\n{}\n```"))
        assertFalse(PlantChatPolicy.validAnswer("答".repeat(1201)))
    }

    @Test fun historyKeepsOnlyRecentCompletePairsWithinMessageAndCharacterLimits() {
        val history = buildList {
            repeat(8) { index ->
                add(PlantChatTurn(PlantChatRole.USER, "第${index}个问题"))
                add(PlantChatTurn(PlantChatRole.ASSISTANT, "第${index}个回答"))
            }
            add(PlantChatTurn(PlantChatRole.USER, "尚未回答的问题不应进入历史"))
        }
        val trimmed = PlantChatPolicy.trimmedHistory(history)
        assertEquals(10, trimmed.size)
        assertEquals(PlantChatRole.USER, trimmed.first().role)
        assertEquals(PlantChatRole.ASSISTANT, trimmed.last().role)
        assertFalse(trimmed.any { it.text.contains("尚未回答") })
        assertTrue(trimmed.sumOf { it.text.length } <= PlantChatPolicy.MAX_HISTORY_CHARS)
    }

    @Test fun suggestionsDoNotAssertUnknownIdentity() {
        val uncertain = PlantChatPolicy.suggestions(false).joinToString()
        assertTrue(uncertain.contains("观察"))
        assertFalse(uncertain.contains("它为什么"))
        assertEquals(4, PlantChatPolicy.suggestions(true).size)
    }
}

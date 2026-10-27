package com.floralens.domain.card

enum class CardTemplateId(val title: String, val caption: String) {
    BOTANICAL_ATLAS("植物图鉴", "自然档案 · 摄影标本"),
    CLASSICAL_POETRY("古典诗笺", "东方留白 · 温润纸色"),
    MODERN_JOURNAL("自然手账", "推荐 · 记录一次相遇"),
}

object CardTemplate {
    const val WIDTH = 1600
    const val HEIGHT = 2400
    const val PREVIEW_WIDTH = 640
}

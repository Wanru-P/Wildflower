package com.floralens.ui.screens.culture

import com.floralens.data.culture.CultureEntry

/** Reader-facing scope, not a replacement for editorial status or export approval. */
internal fun cultureReadingNotice(entry: CultureEntry): String =
    when (entry.verificationStatus) {
        "model_draft_unverified" -> "待核文化笔记 · 现代整理，尚未独立核实，不作为古典文献或历史事实。"
        "literary_source_checked_taxon_pending" -> "文学意象导读 · 古名为文化泛称，不等同于本物种的种级认定。"
        else -> if (entry.publicationStatus == "approved_for_publication") "探索植物背后的传统意象。"
            else "文献导读 · 探索植物背后的传统意象。古名不等同于现代物种，相关引文暂不用于诗笺。"
    }

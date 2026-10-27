package com.floralens.card.render

import androidx.annotation.DrawableRes
import com.floralens.R
import com.floralens.domain.card.CardTemplateId

/** All miniature previews and exports share this mapping through CardRenderer. */
object PoetryTemplateVisuals {
    @DrawableRes fun base(template: CardTemplateId): Int = when (template) {
        CardTemplateId.BOTANICAL_ATLAS -> R.drawable.wf_poetry_atlas
        // Ornamental journal base, with only approved cultural content drawn at runtime.
        CardTemplateId.CLASSICAL_POETRY -> R.drawable.wf_poetry_classical
        // Observation-log base matches the dated artwork in the target editor.
        CardTemplateId.MODERN_JOURNAL -> R.drawable.wf_poetry_journal
    }
}

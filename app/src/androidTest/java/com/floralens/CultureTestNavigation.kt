package com.floralens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Published readings are lazy lists; natural-text fallbacks use a scrollable column. */
internal fun ComposeTestRule.scrollCultureTo(tag: String) {
    if (onAllNodesWithTag("culture-list").fetchSemanticsNodes(false).isNotEmpty())
        onNodeWithTag("culture-list").performScrollToNode(hasTestTag(tag))
    else onNodeWithTag(tag).performScrollTo()
}

package com.floralens.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.floralens.domain.model.CareStatus
import com.floralens.ui.theme.FloraLensTheme

/**
 * Care-status chip — tone mapping from the design system:
 * water → tertiary-container, healthy → secondary-container,
 * overdue → error-container.
 */
@Composable
fun StatusChip(status: CareStatus, modifier: Modifier = Modifier) {
    val (bg, fg, label) = when (status) {
        CareStatus.DUE_TODAY -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "Water today",
        )
        CareStatus.OVERDUE -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Overdue",
        )
        CareStatus.THIS_WEEK -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "This week",
        )
        CareStatus.HEALTHY -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "Healthy",
        )
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Preview
@Composable
private fun StatusChipPreview() {
    FloraLensTheme {
        Row {
            StatusChip(CareStatus.DUE_TODAY, Modifier.padding(4.dp))
            StatusChip(CareStatus.HEALTHY, Modifier.padding(4.dp))
            StatusChip(CareStatus.OVERDUE, Modifier.padding(4.dp))
        }
    }
}

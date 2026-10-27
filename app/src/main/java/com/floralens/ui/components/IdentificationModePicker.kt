package com.floralens.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.floralens.domain.model.IdentificationMode
import com.floralens.ui.theme.Spacing
import com.floralens.ui.theme.Editorial

/**
 * Offline/online picker — used at onboarding and again in About & Help, so
 * the choice is never a one-time decision. Two tappable cards; selecting one
 * calls [onSelect] immediately. Callers decide when to persist it (onboarding
 * waits for "Continue"; About & Help persists on tap).
 *
 * Online carries the "recommended" tag: Pl@ntNet's coverage and accuracy
 * beat the compact bundled model, and identification is the app's whole
 * point. Offline remains the privacy option, pitched on its own merits.
 */
@Composable
fun IdentificationModePicker(
    selected: IdentificationMode,
    onSelect: (IdentificationMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ModeCard(
            icon = Icons.Filled.CloudQueue,
            title = "在线 · Pl@ntNet",
            body = "覆盖更多物种，需要网络并发送本次照片。离线时自动回退至本机模型。",
            selected = selected == IdentificationMode.ONLINE,
            onClick = { onSelect(IdentificationMode.ONLINE) },
        )
        ModeCard(
            icon = Icons.Filled.Lock,
            title = "离线 · 本机识别",
            body = "照片不上传，无需网络。使用随应用提供的轻量模型，物种覆盖较少。",
            selected = selected == IdentificationMode.OFFLINE,
            onClick = { onSelect(IdentificationMode.OFFLINE) },
        )
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) Editorial.sage.copy(alpha = .6f)
                else Editorial.paper,
            )
            .border(
                width = if (selected) 1.dp else .5.dp,
                color = if (selected) Editorial.forest.copy(alpha = .6f) else Editorial.hairline,
                shape = RoundedCornerShape(16.dp),
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Default),
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Icon(if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null, tint = if (selected) Editorial.forest else Editorial.hairline,
            modifier = Modifier.size(20.dp))
    }
}

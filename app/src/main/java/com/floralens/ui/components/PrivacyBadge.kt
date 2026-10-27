package com.floralens.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.floralens.R

/**
 * Status pill floating over the camera viewfinder. Defaults to
 * "Offline • On-device" — the icon/label are overridable so IdentifyScreen
 * can reflect the user's actual identification mode instead of a claim
 * that's only sometimes true.
 *
 * [online] switches the pill to a green tonal look (we're connected and
 * proud of it); offline keeps the dark privacy scrim. Colors animate on
 * mode change so the badge doesn't hard-snap when the user flips modes.
 */
@Composable
fun PrivacyBadge(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Lock,
    label: String = stringResource(R.string.privacy_badge),
    online: Boolean = false,
) {
    val background by animateColorAsState(
        targetValue = if (online) Color(0xFFC3E8CE).copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.38f),
        animationSpec = tween(250),
        label = "privacyBadgeBg",
    )
    val foreground by animateColorAsState(
        targetValue = if (online) Color(0xFF00210F) else Color.White,
        animationSpec = tween(250),
        label = "privacyBadgeFg",
    )
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(15.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
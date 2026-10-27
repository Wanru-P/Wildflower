package com.floralens.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.annotation.DrawableRes
import com.floralens.R
import com.floralens.ui.theme.Editorial

/** Independent decorative asset. Never a species photo or a saved observation. */
@Composable fun FloralDecoration(modifier: Modifier = Modifier, alpha: Float = .18f, @DrawableRes resource: Int = R.drawable.wf_watercolor_osmanthus_corner) {
    Image(painterResource(resource), null, modifier.clearAndSetSemantics {}, contentScale = ContentScale.Fit, alpha = alpha)
}

/** Public editorial illustration only. Never used by BotanicalPhoto's URI fallback. */
fun editorialSpeciesArtwork(scientificName: String, alternate: Boolean = false): Int? = when (scientificName.trim().lowercase()) {
    "osmanthus fragrans" -> if (alternate) R.drawable.wf_editorial_osmanthus_alt else R.drawable.wf_editorial_osmanthus_hero
    "ginkgo biloba" -> R.drawable.wf_editorial_ginkgo
    "camellia japonica" -> R.drawable.wf_editorial_camellia
    else -> null
}
const val EDITORIAL_ARTWORK_CREDIT = "植物主题艺术配图"

@Composable fun KitIcon(@DrawableRes resource: Int, modifier: Modifier = Modifier, description: String? = null, tint: androidx.compose.ui.graphics.Color = Editorial.forest) {
    Icon(painterResource(resource), description, modifier, tint)
}

@Composable fun BotanicalEmptyArtwork(@DrawableRes resource: Int, modifier: Modifier = Modifier) {
    Image(painterResource(resource), null, modifier.clearAndSetSemantics {}, contentScale = ContentScale.Fit)
}

@Composable fun BotanicalHeading(title: String, modifier: Modifier = Modifier, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit = {}) {
    Box(modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize()) { FloralDecoration(Modifier.align(Alignment.TopEnd).size(154.dp)) }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            subtitle?.let { Text(it, style = Editorial.eyebrow, color = Editorial.captionBrown) }
            Text(title, style = Editorial.chineseTitle, color = Editorial.ink)
            content()
        }
    }
}

@Composable fun JournalBanner(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes iconResource: Int? = null) {
    Surface(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick), color = Editorial.sage, shape = RoundedCornerShape(16.dp)) {
        Box {
            Box(Modifier.matchParentSize()) { FloralDecoration(Modifier.align(Alignment.CenterEnd).size(114.dp), .24f, R.drawable.wf_watercolor_leaf_spray) }
            Row(Modifier.heightIn(min = 80.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).background(Editorial.botanical.copy(alpha = .25f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { if (iconResource != null) KitIcon(iconResource, modifier = Modifier.size(26.dp)) else Icon(icon, null, tint = Editorial.ink) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = Editorial.sectionTitle.copy(fontSize = 18.sp, lineHeight = 25.sp), color = Editorial.ink)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown)
                }
                Box(Modifier.size(48.dp).background(Editorial.paper.copy(alpha = .9f), CircleShape), contentAlignment = Alignment.Center) { KitIcon(R.drawable.wf_icon_arrow_right, modifier = Modifier.size(22.dp)) }
            }
        }
    }
}

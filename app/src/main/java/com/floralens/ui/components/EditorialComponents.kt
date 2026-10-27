package com.floralens.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.border
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.floralens.ui.theme.Editorial
import com.floralens.ui.theme.FloraLensTheme
import com.floralens.R
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.BorderStroke

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun WildflowerTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = { Text(title, style = Editorial.sectionTitle.copy(fontSize = 21.sp, lineHeight = 28.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis, color = Editorial.ink) },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) {
            KitIcon(R.drawable.wf_icon_back, modifier = Modifier.size(24.dp), description = "返回")
        } },
        actions = actions,
        expandedHeight = 56.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}

/** Absent/unreadable private URI: explicit supplied illustration, never editorial photography. */
@Composable
fun PhotoPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier.background(Editorial.linen).testTag("photo-unavailable"), contentAlignment = Alignment.Center) {
        BotanicalEmptyArtwork(R.drawable.wf_empty_photo_album, Modifier.fillMaxSize().padding(12.dp))
    }
}

@Composable
fun BotanicalPhoto(model: Any?, description: String?, modifier: Modifier = Modifier, alignment: Alignment = Alignment.Center) {
    SubcomposeAsyncImage(model = model, contentDescription = description, modifier = modifier,
        contentScale = ContentScale.Crop, alignment = alignment,
        loading = { PhotoPlaceholder(Modifier.fillMaxSize()) },
        error = { PhotoPlaceholder(Modifier.fillMaxSize()) })
}

@Composable
fun ScientificNameLabel(name: String, modifier: Modifier = Modifier) {
    Text(name, modifier, style = Editorial.scientific, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun EditorialSectionHeader(title: String, eyebrow: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        eyebrow?.let { Text(it, style = Editorial.eyebrow, color = Editorial.captionBrown) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = Editorial.sectionTitle, color = Editorial.ink)
            HorizontalDivider(Modifier.weight(1f), thickness = .5.dp, color = Editorial.hairline)
        }
    }
}

/** Lightweight reading furniture shared by the editorial screens, not another theme. */
@Composable
fun EditorialMetadata(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(label, Modifier.width(64.dp), style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown)
            Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider(color = Editorial.hairline)
    }
}

@Composable
fun EditorialReadingNote(title: String, body: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(2.dp).height(36.dp).background(Editorial.botanical))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BotanicalHero(photo: Any?, title: String, subtitle: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, scientific: String = "") {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Editorial.paper).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.75f).clip(RoundedCornerShape(18.dp))) {
            BotanicalPhoto(photo, title, Modifier.fillMaxSize(), Alignment.CenterEnd)
            Text(label, Modifier.align(Alignment.TopStart).padding(14.dp).border(.5.dp, Editorial.paper.copy(alpha = .5f), CircleShape).background(Editorial.forest.copy(alpha = .72f), CircleShape).padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium, color = Editorial.paper)
        }
        Column(Modifier.fillMaxWidth().offset(y = (-10).dp).background(Editorial.paper, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(title, Modifier.weight(1f, fill = false), style = Editorial.chineseTitle.copy(fontSize = 27.sp, lineHeight = 35.sp), color = Editorial.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("当季选读", Modifier.background(Editorial.linen, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = Editorial.captionBrown)
                }
                if (scientific.isNotBlank()) Text(scientific, style = Editorial.scientific, color = Editorial.captionBrown)
            }
            Box(Modifier.size(48.dp).background(Editorial.forest, CircleShape), contentAlignment = Alignment.Center) {
                KitIcon(R.drawable.wf_icon_arrow_right, modifier = Modifier.size(24.dp), tint = Editorial.paper)
            }
        }
        HorizontalDivider(Modifier.width(16.dp), color = Editorial.captionBrown)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp), color = Editorial.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun CircularPlantCard(photo: Any?, title: String, scientific: String, date: String, onClick: () -> Unit, modifier: Modifier = Modifier, nameLines: Int = 1, scientificLines: Int = 1) {
    Column(modifier.clickable(onClick = onClick).padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.widthIn(max = 152.dp).fillMaxWidth().aspectRatio(1f).border(.7.dp, Editorial.hairline, CircleShape).padding(3.dp).border(.5.dp, Editorial.warmBrown, CircleShape).padding(3.dp)) {
            BotanicalPhoto(photo, title, Modifier.fillMaxSize().clip(CircleShape))
        }
        Text(title, Modifier.padding(top = 5.dp), style = Editorial.sectionTitle.copy(fontSize = 17.sp, lineHeight = 23.sp), color = Editorial.ink, textAlign = TextAlign.Center, minLines = nameLines, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(scientific, style = Editorial.scientific.copy(fontSize = 13.sp, lineHeight = 19.sp), color = Editorial.captionBrown, textAlign = TextAlign.Center, minLines = scientificLines, maxLines = 2, overflow = TextOverflow.Ellipsis)
        HorizontalDivider(Modifier.width(14.dp).padding(vertical = 4.dp), color = Editorial.captionBrown)
        Text(date, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp), color = Editorial.captionBrown)
    }
}

/** Independent decorative journal illustration, never a fabricated saved plant. */
@Composable
fun HerbariumIllustration(modifier: Modifier = Modifier) {
    BotanicalEmptyArtwork(R.drawable.wf_empty_journal_illustration, modifier)
}

/** Retained small native botanical mark for simple placeholder furniture. */
@Composable
fun BotanicalMark(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val stem = Path().apply { moveTo(size.width * .42f, size.height * .87f); cubicTo(size.width * .57f, size.height * .63f, size.width * .4f, size.height * .4f, size.width * .58f, size.height * .15f) }
        drawPath(stem, ink, style = Stroke(1.5.dp.toPx()))
        for (i in 0..4) {
            val y = size.height * (.28f + i * .105f)
            val x = size.width * .5f
            val side = if (i % 2 == 0) -1f else 1f
            val leaf = Path().apply { moveTo(x, y); quadraticTo(x + side * size.width * .32f, y - size.height * .22f, x + side * size.width * .25f, y - size.height * .015f); quadraticTo(x + side * size.width * .15f, y + size.height * .08f, x, y) }
            drawPath(leaf, Editorial.botanical.copy(alpha = .28f))
            drawPath(leaf, ink.copy(alpha = .75f), style = Stroke(1.dp.toPx()))
        }
        drawLine(Editorial.warmBrown, Offset(size.width * .68f, size.height * .82f), Offset(size.width * .88f, size.height * .82f), 1.dp.toPx())
        drawLine(Editorial.warmBrown, Offset(size.width * .68f, size.height * .86f), Offset(size.width * .82f, size.height * .86f), 1.dp.toPx())
    }
}

@Composable
fun EditorialInfoCard(title: String, body: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = Editorial.paper) {
        Column(Modifier.padding(Editorial.cardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** No invented poems. Verified data and its citation must be supplied together in Phase 3. */
@Composable
fun CulturePreviewCard(verifiedText: String? = null, citation: String? = null) {
    EditorialInfoCard("植物与文化", if (verifiedText != null && citation != null) "$verifiedText\n$citation" else "文化资料正在整理。这里仅展示核实过的文字与出处。")
}

@Composable
fun WildflowerPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, @DrawableRes iconResource: Int? = null) {
    Button(onClick, modifier.heightIn(min = 54.dp), shape = RoundedCornerShape(28.dp), contentPadding = PaddingValues(14.dp, 12.dp)) {
        if (iconResource != null) { KitIcon(iconResource, modifier = Modifier.size(22.dp), tint = Editorial.paper); Spacer(Modifier.width(6.dp)) }
        else icon?.let { Icon(it, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text)
    }
}

@Composable
fun WildflowerSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, @DrawableRes iconResource: Int? = null) {
    OutlinedButton(onClick, modifier.heightIn(min = 52.dp), shape = RoundedCornerShape(28.dp), border = BorderStroke(.75.dp, Editorial.forest), contentPadding = PaddingValues(12.dp, 10.dp)) {
        if (iconResource != null) { KitIcon(iconResource, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(6.dp)) }
        else icon?.let { Icon(it, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text)
    }
}

@Composable
fun EditorialLoading(message: String = "正在整理植物资料…") {
    Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun EditorialError(message: String, onRetry: (() -> Unit)? = null) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) WildflowerSecondaryButton("重新加载", onRetry)
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun EditorialComponentsPreview() = FloraLensTheme {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        EditorialSectionHeader("自然的细节", "BOTANICAL NOTES")
        ScientificNameLabel("Osmanthus fragrans")
        CulturePreviewCard()
        WildflowerPrimaryButton("拍照识别植物", {})
        PhotoPlaceholder(Modifier.size(128.dp).clip(CircleShape))
    }
}

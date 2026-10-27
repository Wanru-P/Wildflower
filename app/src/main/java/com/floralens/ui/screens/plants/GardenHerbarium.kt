package com.floralens.ui.screens.plants

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.floralens.R
import com.floralens.domain.season.Season
import com.floralens.ui.components.*
import com.floralens.ui.theme.Editorial
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Page-local overrides specified by MG-V1, built on the existing licensed Editorial fonts.
// They deliberately do not change the application theme or the approved empty page.
private val gardenBackground = Color(0xFFF8F8F1)
private val gardenBrown = Color(0xFF8C6B4F)
private val gardenRing = Color(0xFFB49B78)
private val gardenDivider = Color(0xFFE6DECD)

@Composable
internal fun PopulatedGardenContent(
    state: MyPlantsUiState, onPlantClick: (Long) -> Unit, onIdentifyClick: () -> Unit,
    onAboutClick: () -> Unit, onMapClick: () -> Unit, onCareClick: () -> Unit,
    onSeasons: (() -> Unit)?, onSeason: ((Season?) -> Unit)?,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(gardenBackground)) {
        // Scaffold already owns status/navigation insets and the fixed three-tab bar.
        // The grid receives only page spacing, never another navigation-bar height.
        val narrow = maxWidth < 350.dp
        val margin = if (narrow) 16.dp else 20.dp
        val columns = if (maxWidth < 600.dp) 2 else (maxWidth.value / 190f).toInt().coerceIn(3, 4)
        val columnWidth = (maxWidth - margin * 2 - 20.dp * (columns - 1)) / columns
        Image(painterResource(R.drawable.wf_garden_paper_texture), null, Modifier.matchParentSize(),
            contentScale = ContentScale.FillBounds, alpha = .035f)
        val grid = rememberLazyGridState()
        // Use actual resolved-font layouts, not an estimate, to align paired title slots.
        val titleLines = remember { mutableStateMapOf<Long, Int>() }
        LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxSize().testTag("garden-grid"), state = grid,
            contentPadding = PaddingValues(start = margin, top = 18.dp, end = margin, bottom = 24.dp),
            // The header-to-photograph gap is 14dp; card bottom padding makes row gaps 26dp.
            horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item(key = "garden-heading", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    GardenHeading(state.totalPlants, narrow, onCareClick, onAboutClick)
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        onSeasons?.let { GardenAction("四季手账 →", R.drawable.wf_icon_book, it, Modifier.weight(1f)) }
                        GardenAction("观察地图", R.drawable.wf_icon_map_pin, onMapClick, Modifier.weight(1f))
                    }
                    onSeason?.let { select ->
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth().testTag("garden-season-filter"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf<Season?>(null).plus(Season.entries).forEach { season ->
                                GardenSeasonChip(season?.let { "${it.label}季" } ?: "全部", state.selectedSeason == season,
                                    { select(season) }, Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    GardenSectionHeading(state, narrow)
                }
            }
            if (state.plants.isEmpty()) item(key = "garden-filter-empty", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    EditorialInfoCard("这一季，尚无植物观察", "筛选跨年份汇总实际观察；无观察档案仍在“全部”。")
                    WildflowerPrimaryButton("去识别", onIdentifyClick)
                }
            }
            itemsIndexed(state.plants, key = { _, item -> item.plant.id }) { index, item ->
                val rowStart = index / columns * columns
                val nameLines = (rowStart until minOf(rowStart + columns, state.plants.size))
                    .maxOf { titleLines[state.plants[it].plant.id] ?: 1 }
                val photoDiameter = minOf(if (narrow) 126.dp else 148.dp, columnWidth.coerceAtLeast(1.dp))
                val plant = item.plant
                val date = (if (item.latestObservationAt == null) "收藏 · " else "观察 · ") +
                    (item.latestObservationAt ?: plant.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM月dd日"))
                GardenPlantCard(item, date, nameLines, photoDiameter, { lines ->
                    if (titleLines[plant.id] != lines) titleLines[plant.id] = lines
                }, { onPlantClick(plant.id) })
            }
            if (state.plants.isNotEmpty()) item(key = "garden-discover", span = { GridItemSpan(maxLineSpan) }) {
                WildflowerPrimaryButton("去发现新的植物", onIdentifyClick, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun GardenHeading(count: Int, narrow: Boolean, onCareClick: () -> Unit, onAboutClick: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val brandStyle = Editorial.brand.copy(fontSize = if (narrow) 34.sp else 37.sp, lineHeight = 44.sp)
        val brandWidth = with(density) { rememberTextMeasurer().measure("My Garden", brandStyle).size.width.toDp() }
        val utilityWidth = if (density.fontScale > 1.2f) 76.dp else 48.dp
        val stacked = brandWidth + utilityWidth * 2 > maxWidth
        Box(Modifier.matchParentSize()) {
            FloralDecoration(Modifier.align(Alignment.TopEnd).width(155.dp).aspectRatio(1.5f), .16f, R.drawable.wf_garden_corner_leaves)
        }
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text("My Garden", Modifier.weight(1f), style = brandStyle,
                    color = Color(0xFF153D2E), maxLines = 1)
                if (!stacked) {
                    GardenUtility("养护提醒", Icons.Outlined.NotificationsNone, onCareClick, utilityWidth)
                    GardenUtility("设置", Icons.Outlined.Settings, onAboutClick, utilityWidth)
                }
            }
            if (stacked) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GardenUtility("养护提醒", Icons.Outlined.NotificationsNone, onCareClick, utilityWidth)
                GardenUtility("设置", Icons.Outlined.Settings, onAboutClick, utilityWidth)
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("我的数字植物标本馆", Modifier.alignByBaseline(), style = Editorial.sectionTitle.copy(fontSize = 15.sp, lineHeight = 22.sp), color = gardenBrown)
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = Editorial.brand.fontFamily, fontSize = 26.sp, color = Editorial.forest)) { append(count.toString()) }
                    withStyle(SpanStyle(fontSize = 12.sp, color = gardenBrown)) { append(" 株收藏") }
                }, Modifier.alignByBaseline(), style = Editorial.sectionTitle.copy(lineHeight = 26.sp))
            }
        }
    }
}

@Composable
private fun GardenUtility(label: String, icon: ImageVector, onClick: () -> Unit, width: androidx.compose.ui.unit.Dp) {
    // Reuse the existing bell/gear vectors: the kit's generic alert/settings files depict a triangle/sun.
    TextButton(onClick, Modifier.width(width).heightIn(min = 48.dp), contentPadding = PaddingValues(0.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(icon, null, Modifier.size(22.dp), tint = Editorial.forest)
            Text(label, style = Editorial.sectionTitle.copy(fontSize = 11.sp, lineHeight = 16.sp), color = Editorial.forest, maxLines = 1)
        }
    }
}

@Composable
private fun GardenAction(label: String, @DrawableRes icon: Int, onClick: () -> Unit, modifier: Modifier) {
    // 46dp visible pill inside a 48dp semantic/click target.
    Box(modifier.heightIn(min = 48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Surface(Modifier.fillMaxWidth().heightIn(min = 46.dp), shape = CircleShape,
            color = Color.Transparent, border = BorderStroke(1.dp, Editorial.forest)) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                if (LocalDensity.current.fontScale <= 1.2f) KitIcon(icon, Modifier.size(19.dp))
                Text(label, style = Editorial.sectionTitle.copy(fontSize = 14.sp, lineHeight = 22.sp), color = Editorial.forest, maxLines = 1)
            }
        }
    }
}

@Composable
private fun GardenSeasonChip(label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier.heightIn(min = 48.dp).semantics { selected = active }.clip(CircleShape)
        .clickable(role = Role.Tab, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().heightIn(min = 40.dp).background(if (active) Editorial.forest else Color(0xFFEAF0E5), CircleShape)
            .padding(horizontal = 2.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(label, style = Editorial.sectionTitle.copy(fontSize = 13.sp, lineHeight = 20.sp),
                color = if (active) Color(0xFFFEFDF7) else gardenBrown, maxLines = 1)
        }
    }
}

@Composable
private fun GardenSectionHeading(state: MyPlantsUiState, narrow: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (narrow) 6.dp else 8.dp)) {
        Text(if (state.selectedSeason == null) "HERBARIUM" else "${state.selectedSeason.label}季观察 · ${state.plants.size} 株",
            style = Editorial.eyebrow.copy(fontSize = if (narrow) 11.sp else 12.sp, letterSpacing = 1.2.sp), color = gardenBrown)
        if (state.selectedSeason == null && LocalDensity.current.fontScale <= 1.2f)
            Text("/ 每一次相遇都有迹可循", style = Editorial.sectionTitle.copy(fontSize = 11.sp, lineHeight = 16.sp), color = gardenBrown)
        HorizontalDivider(Modifier.weight(1f), thickness = .5.dp, color = gardenDivider)
        Image(painterResource(R.drawable.wf_garden_osmanthus_divider), null, Modifier.size(28.dp).clip(CircleShape),
            contentScale = ContentScale.Crop, alignment = Alignment.CenterEnd, alpha = .8f)
    }
}

@Composable
private fun GardenPlantCard(item: PlantWithStatus, date: String, nameLines: Int, diameter: androidx.compose.ui.unit.Dp,
    onNameLines: (Int) -> Unit, onClick: () -> Unit) {
    val plant = item.plant
    val titleHeight = with(LocalDensity.current) { (if (nameLines > 1) 48.sp else 24.sp).toDp() }
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("garden-plant-${plant.id}").clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(diameter).testTag("garden-photo-${plant.id}"), contentAlignment = Alignment.Center) {
            BotanicalPhoto(plant.photoUri, plant.nickname, Modifier.fillMaxSize().padding(9.dp).clip(CircleShape))
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2
                drawCircle(gardenRing, radius = (radius - .5.dp.toPx()).coerceAtLeast(0f), style = Stroke(1.dp.toPx()))
                drawCircle(gardenRing, radius = (radius - 4.5.dp.toPx()).coerceAtLeast(0f), style = Stroke(.8.dp.toPx()))
            }
        }
        Spacer(Modifier.height(10.dp))
        // Only rows with genuinely wrapped nicknames reserve a second line.
        Box(Modifier.fillMaxWidth().heightIn(min = titleHeight), contentAlignment = Alignment.TopCenter) {
            Text(plant.nickname, Modifier.testTag("garden-name-${plant.id}"), style = Editorial.sectionTitle.copy(fontSize = 17.sp, lineHeight = 23.sp),
                color = Editorial.forest, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
                onTextLayout = { onNameLines(it.lineCount) })
        }
        Spacer(Modifier.height(5.dp))
        Text(plant.scientificName, Modifier.testTag("garden-latin-${plant.id}"), style = Editorial.scientific.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = gardenBrown, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Spacer(Modifier.height(7.dp))
        HorizontalDivider(Modifier.width(14.dp), thickness = .7.dp, color = gardenBrown)
        Spacer(Modifier.height(7.dp))
        Text(date, Modifier.testTag("garden-date-${plant.id}").semantics { contentDescription = "${plant.nickname}，$date" },
            style = Editorial.sectionTitle.copy(fontSize = 12.sp, lineHeight = 16.sp), color = gardenBrown,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

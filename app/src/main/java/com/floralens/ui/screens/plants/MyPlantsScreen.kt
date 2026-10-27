package com.floralens.ui.screens.plants

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.floralens.ui.components.*
import com.floralens.ui.theme.Editorial
import com.floralens.ui.theme.FloraLensTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.floralens.domain.season.Season
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import com.floralens.R
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity

@Composable
fun MyPlantsScreen(onPlantClick: (Long) -> Unit, onIdentifyClick: () -> Unit, onAboutClick: () -> Unit,
    onMapClick: () -> Unit = {}, onCareClick: () -> Unit = {}, viewModel: MyPlantsViewModel = hiltViewModel(), onSeasons: (() -> Unit)? = null) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MyGardenContent(state, onPlantClick, onIdentifyClick, onAboutClick, onMapClick, onCareClick, onSeasons, viewModel::selectSeason)
}

@Composable
fun MyGardenContent(state: MyPlantsUiState, onPlantClick: (Long) -> Unit, onIdentifyClick: () -> Unit,
    onAboutClick: () -> Unit, onMapClick: () -> Unit, onCareClick: () -> Unit, onSeasons: (() -> Unit)? = null, onSeason: ((Season?) -> Unit)? = null) {
    // The approved empty/loading page and its shared components remain unchanged.
    if (state.totalPlants > 0) {
        PopulatedGardenContent(state, onPlantClick, onIdentifyClick, onAboutClick, onMapClick, onCareClick, onSeasons, onSeason)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth < 600.dp) 2 else (maxWidth.value / 190f).toInt().coerceIn(3, 4)
        val brandSize = if (maxWidth < 350.dp) 26.sp else 30.sp
        val compact = maxHeight < 650.dp
        val showEntryIcons = maxWidth >= 350.dp && androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.2f
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        // Animated navigation can measure the incoming page at zero width for a frame.
        // Only the text-measurement bound is clamped; normal grid proportions stay unchanged.
        val titleConstraints = Constraints(maxWidth = with(density) { ((maxWidth - (if (maxWidth < 350.dp) 28.dp else 32.dp) - 16.dp * (columns - 1)) / columns).roundToPx().coerceAtLeast(1) })
        val margin = if (maxWidth < 350.dp) 14.dp else Editorial.pageMargin
        LazyVerticalGrid(GridCells.Fixed(columns), Modifier.testTag("garden-grid"), contentPadding = PaddingValues(margin, 8.dp, margin, 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box {
                Box(Modifier.matchParentSize()) { FloralDecoration(Modifier.align(Alignment.TopEnd).width(150.dp).height(120.dp), .22f) }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("My Garden", Modifier.weight(1f), style = Editorial.brand.copy(fontSize = brandSize), color = MaterialTheme.colorScheme.primary)
                        TextButton(onCareClick, Modifier.width(if (density.fontScale > 1.2f) 76.dp else 56.dp), contentPadding = PaddingValues(2.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.NotificationsNone, null); Text("养护提醒", style = MaterialTheme.typography.labelSmall) }
                        }
                        TextButton(onAboutClick, Modifier.width(48.dp), contentPadding = PaddingValues(2.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.Settings, null); Text("设置", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("我的数字植物标本馆", style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown)
                        Text("${state.totalPlants} 株收藏", style = MaterialTheme.typography.labelSmall, color = Editorial.captionBrown)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        onSeasons?.let { WildflowerSecondaryButton("四季手账 →", it, Modifier.weight(1f), iconResource = if (showEntryIcons) R.drawable.wf_icon_book else null) }
                        WildflowerSecondaryButton("观察地图", onMapClick, Modifier.weight(1f), iconResource = if (showEntryIcons) R.drawable.wf_icon_map_pin else null)
                    }
                    if (state.totalPlants > 0) onSeason?.let { select -> Row(Modifier.fillMaxWidth().testTag("garden-season-filter"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf<Season?>(null).plus(Season.entries).forEach { season ->
                            TextButton({ select(season) }, Modifier.weight(1f).heightIn(min = 48.dp).semantics { selected = state.selectedSeason == season }, shape = RoundedCornerShape(24.dp), contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = if (state.selectedSeason == season) Editorial.paper else Editorial.captionBrown)) {
                                Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).clip(CircleShape)
                                    .background(if (state.selectedSeason == season) Editorial.forest else Editorial.linen),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                                    if (season == null && showEntryIcons) KitIcon(R.drawable.wf_icon_filter, modifier = Modifier.size(14.dp), tint = if (state.selectedSeason == null) Editorial.paper else Editorial.captionBrown)
                                    Text(season?.let { "${it.label}季" } ?: "全部", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    } }
                    if (state.plants.isNotEmpty() || state.selectedSeason != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (state.selectedSeason == null) "HERBARIUM / 每一次相遇都有迹可循" else "${state.selectedSeason.label}季观察 · ${state.plants.size} 株", style = Editorial.eyebrow.copy(letterSpacing = .3.sp), color = Editorial.captionBrown)
                        HorizontalDivider(Modifier.weight(1f), color = Editorial.hairline)
                        FloralDecoration(Modifier.size(22.dp), .42f, R.drawable.wf_ornament_sage_leaf)
                    }
                }
                }
            }
            when {
                state.isLoading -> item(span = { GridItemSpan(maxLineSpan) }) { EditorialLoading("正在翻开你的植物档案…") }
                state.plants.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    if (state.selectedSeason == null) EmptyGardenState(onIdentifyClick, compact)
                    else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        EditorialInfoCard("这一季，尚无植物观察", "筛选跨年份汇总实际观察；无观察档案仍在“全部”。")
                        WildflowerPrimaryButton("去识别", onIdentifyClick)
                    }
                }
                else -> itemsIndexed(state.plants, key = { _, item -> item.plant.id }) { index, item ->
                    val plant = item.plant
                    val row = state.plants.drop(index / columns * columns).take(columns)
                    val nameLines = row.maxOf { measurer.measure(it.plant.nickname, Editorial.sectionTitle.copy(fontSize = 17.sp, lineHeight = 23.sp), maxLines = 2, constraints = titleConstraints).lineCount }.coerceIn(1, 2)
                    val latinLines = row.maxOf { measurer.measure(it.plant.scientificName, Editorial.scientific.copy(fontSize = 13.sp, lineHeight = 19.sp), maxLines = 2, constraints = titleConstraints).lineCount }.coerceIn(1, 2)
                    CircularPlantCard(plant.photoUri, plant.nickname, plant.scientificName,
                        (if (item.latestObservationAt == null) "收藏 · " else "观察 · ") + (item.latestObservationAt ?: plant.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM月dd日")),
                        { onPlantClick(plant.id) }, Modifier.testTag("garden-plant-${plant.id}"), nameLines, latinLines)
                }
            }
            if (state.plants.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                WildflowerPrimaryButton("去发现新的植物", onIdentifyClick, Modifier.fillMaxWidth().padding(horizontal = 8.dp))
            }
        }
    }
}

@Composable
fun EmptyGardenState(onIdentifyClick: () -> Unit, compact: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp)) {
        HerbariumIllustration(Modifier.widthIn(max = 343.dp).fillMaxWidth().height(if (compact) 210.dp else 260.dp))
        Text("你的植物故事，\n从第一次发现开始。", style = Editorial.sectionTitle, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        HorizontalDivider(Modifier.width(18.dp), color = Editorial.captionBrown)
        Text("留下一张照片，让日常的相遇有迹可循。", style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown, textAlign = TextAlign.Center)
        WildflowerPrimaryButton("去识别", onIdentifyClick, Modifier.widthIn(min = 172.dp).padding(top = 4.dp), iconResource = R.drawable.wf_icon_camera)
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun EmptyGardenPreview() = FloraLensTheme { MyGardenContent(MyPlantsUiState(isLoading = false), {}, {}, {}, {}, {}) }

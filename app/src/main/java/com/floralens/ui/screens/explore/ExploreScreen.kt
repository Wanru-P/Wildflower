package com.floralens.ui.screens.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.floralens.data.explore.*
import com.floralens.ui.components.*
import com.floralens.ui.theme.Editorial
import com.floralens.ui.theme.FloraLensTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.floralens.data.culture.CultureRepository
import com.floralens.domain.model.SpeciesKey
import com.floralens.BuildConfig
import com.floralens.R

@HiltViewModel
class ExploreViewModel @Inject constructor(val catalog: ExploreCatalog, culture: CultureRepository) : ViewModel() {
    val culturalSpecies = MutableStateFlow<List<FeaturedSpecies>>(emptyList())
    init { viewModelScope.launch { culturalSpecies.value = catalog.species.distinctBy { SpeciesKey.fromLegacy(it.scientific, it.name) }
        .filter { runCatching { culture.matchSpecies(SpeciesKey.fromLegacy(it.scientific, it.name)) != null }.getOrDefault(false) } } }
}

@Composable
fun ExploreScreen(onSpecies: (FeaturedSpecies) -> Unit, onScan: () -> Unit, onSettings: () -> Unit, viewModel: ExploreViewModel = hiltViewModel(),
    onCulture: ((String) -> Unit)? = null, onReview: (() -> Unit)? = null) {
    val today = LocalDate.now()
    val culturalSpecies by viewModel.culturalSpecies.collectAsStateWithLifecycle()
    ExploreContent(viewModel.catalog.species, viewModel.catalog.seasonal(today), seasonLabel(today.monthValue), onSpecies, onScan, onSettings, culturalSpecies, onCulture, onReview)
}

@Composable
fun ExploreContent(species: List<FeaturedSpecies>, hero: FeaturedSpecies, season: String,
    onSpecies: (FeaturedSpecies) -> Unit, onScan: () -> Unit, onSettings: () -> Unit, culturalSpecies: List<FeaturedSpecies> = emptyList(),
    onCulture: ((String) -> Unit)? = null, onReview: (() -> Unit)? = null) {
    val featured = species.filter { it.id != hero.id }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    val margin = if (maxWidth < 350.dp) 14.dp else Editorial.pageMargin
    val brandSize = if (maxWidth < 350.dp) 24.sp else 28.sp
    LazyColumn(Modifier.widthIn(max = 600.dp).fillMaxWidth().testTag("explore-list"), contentPadding = PaddingValues(horizontal = margin, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Box {
            Box(Modifier.matchParentSize()) { FloralDecoration(Modifier.align(Alignment.TopEnd).width(98.dp).height(110.dp), .18f, R.drawable.wf_watercolor_leaf_spray) }
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Wildflower Lens", style = Editorial.brand.copy(fontSize = brandSize, lineHeight = 34.sp), color = Editorial.ink)
                    Text("草木知时 / 自然观察笔记", style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown)
                }
                IconButton(onSettings) { KitIcon(R.drawable.wf_icon_settings, description = "设置") }
            }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("发现自然，收藏四季。", style = Editorial.sectionTitle.copy(fontSize = 18.sp, lineHeight = 26.sp), color = Editorial.captionBrown)
                HorizontalDivider(Modifier.weight(1f).padding(horizontal = 12.dp), thickness = .5.dp, color = Editorial.hairline)
                Text(season, style = MaterialTheme.typography.labelSmall, color = Editorial.captionBrown)
            }
        }
        item { BotanicalHero(editorialSpeciesArtwork(hero.scientific) ?: hero.photo, hero.name, hero.summary, "$season / 植物发现", { onSpecies(hero) }, Modifier.testTag("explore-hero"), scientific = hero.scientific) }
        item {
            JournalBanner("拍照识别植物", "记录校园中的自然变化", Icons.Outlined.PhotoCamera, onScan, iconResource = R.drawable.wf_icon_camera)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("值得驻足的植物", style = Editorial.sectionTitle)
                    Text("精选 / ${featured.size.toString().padStart(2, '0')}", style = MaterialTheme.typography.labelSmall, color = Editorial.captionBrown)
                }
            }
        }
        items(featured.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { plant -> Box(Modifier.weight(1f)) {
                    FeaturedPlantCard(plant, photoAspect = 1.33f) { onSpecies(plant) }
                } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (culturalSpecies.isNotEmpty() && onCulture != null) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EditorialSectionHeader("植物与文字", "CULTURE / 文献导读")
                culturalSpecies.forEach { plant ->
                    Surface(onClick = { onCulture(SpeciesKey.fromLegacy(plant.scientific, plant.name)) },
                        modifier = Modifier.fillMaxWidth(), color = Editorial.linen.copy(alpha = .55f),
                        shape = RoundedCornerShape(16.dp), border = BorderStroke(.5.dp, Editorial.hairline)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BotanicalPhoto(editorialSpeciesArtwork(plant.scientific) ?: plant.photo, null,
                                Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${plant.name} · 阅读文化", style = MaterialTheme.typography.titleMedium, color = Editorial.forest)
                                Text("文化意象与阅读", style = MaterialTheme.typography.labelSmall, color = Editorial.muted)
                            }
                            KitIcon(R.drawable.wf_icon_arrow_right, Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        if (BuildConfig.DEBUG && BuildConfig.INTERNAL_TOOLS && onReview != null) item { WildflowerSecondaryButton("资料审阅 · Debug", onReview, Modifier.fillMaxWidth()) }
        item {
            HorizontalDivider(color = Editorial.hairline)
            Text("$EDITORIAL_ARTWORK_CREDIT\n发现自然，收藏四季。手账按观察日期归档。", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    }
}

@Composable
fun FeaturedPlantCard(plant: FeaturedSpecies, photoAspect: Float = .95f, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Editorial.paper).clickable(role = Role.Button, onClickLabel = "认识${plant.name}", onClick = onClick), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BotanicalPhoto(editorialSpeciesArtwork(plant.scientific) ?: plant.photo, plant.name, Modifier.fillMaxWidth().aspectRatio(photoAspect).clip(RoundedCornerShape(8.dp)))
            Column(Modifier.padding(horizontal = 10.dp).padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(plant.name, style = Editorial.sectionTitle.copy(fontSize = 17.sp, lineHeight = 24.sp), color = MaterialTheme.colorScheme.primary)
                Text(plant.scientific, style = Editorial.scientific.copy(fontSize = 13.sp, lineHeight = 19.sp), color = Editorial.captionBrown)
                Text(plant.habit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ExplorePreview() = FloraLensTheme {
    val p = FeaturedSpecies("preview", "桂花", "Osmanthus fragrans", "Sweet Osmanthus", "", "autumn", "细小花朵，浓郁香气。", "常绿灌木", "木犀科", "秋季", "", "")
    ExploreContent(listOf(p), p, "秋日", {}, {}, {})
}

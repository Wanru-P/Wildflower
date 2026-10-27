package com.floralens.ui.screens.seasons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.floralens.data.season.SeasonRepository
import com.floralens.domain.season.*
import com.floralens.ui.components.*
import com.floralens.ui.theme.Editorial
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import com.floralens.R

data class SeasonsUiState(val archive: SeasonArchive = SeasonArchive(), val year: Int? = null, val season: Season? = null, val error: String? = null)
@HiltViewModel
class SeasonsViewModel @Inject constructor(repository: SeasonRepository, private val saved: SavedStateHandle) : ViewModel() {
    private val year = saved.getStateFlow<Int?>("selectedYear", null)
    private val season = saved.getStateFlow<String?>("selectedSeason", null)
    val state = combine(repository.observe(), year, season) { archive, y, s ->
        SeasonsUiState(archive, y ?: archive.years.firstOrNull(), s?.let { Season.valueOf(it) })
    }.catch { emit(SeasonsUiState(error = "观察手账暂时无法读取，请返回后重试。")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeasonsUiState())
    fun year(value: Int) { saved["selectedYear"] = value }
    fun season(value: Season?) { saved["selectedSeason"] = value?.name }
}

@Composable
fun SeasonsScreen(onBack: () -> Unit, onScan: () -> Unit, onObservation: (Long, Long) -> Unit, viewModel: SeasonsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SeasonsContent(state, onBack, onScan, onObservation, viewModel::year, viewModel::season)
}
private fun seasonTint(season: Season): Color = when (season) {
    Season.SPRING -> Color(0xFFEDF1E4); Season.SUMMER -> Color(0xFFE3EBD8)
    Season.AUTUMN -> Color(0xFFF7E9D1); Season.WINTER -> Color(0xFFE5EAE9)
}
private fun seasonOrnament(season: Season) = when (season) {
    Season.SPRING -> R.drawable.wf_ornament_cherry
    Season.SUMMER -> R.drawable.wf_ornament_lotus
    Season.AUTUMN -> R.drawable.wf_ornament_ginkgo
    Season.WINTER -> R.drawable.wf_ornament_winter_berries
}
private fun emptySeasonArtwork(season: Season) = when (season) {
    Season.SPRING -> R.drawable.wf_editorial_spring
    Season.SUMMER -> R.drawable.wf_editorial_summer
    Season.AUTUMN -> R.drawable.wf_editorial_autumn
    Season.WINTER -> R.drawable.wf_editorial_winter_decorative_only
}
@Composable
fun SeasonsContent(state: SeasonsUiState, onBack: () -> Unit, onScan: () -> Unit, onObservation: (Long, Long) -> Unit,
    onYear: (Int) -> Unit, onSeason: (Season?) -> Unit) {
    val archive = state.archive
    var showRules by remember { mutableStateOf(false) }
    var yearMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        WildflowerTopBar("四季手账", onBack)
        LazyColumn(Modifier.align(Alignment.CenterHorizontally).widthIn(max = 600.dp).fillMaxWidth().weight(1f).testTag("seasons-list"), contentPadding = PaddingValues(Editorial.pageMargin), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Box(Modifier.fillMaxWidth()) {
                Box(Modifier.matchParentSize()) { FloralDecoration(Modifier.align(Alignment.TopEnd).size(150.dp), .26f) }
                Column {
                Text("SEASONAL JOURNAL  /  真实观察", style = Editorial.eyebrow, color = Editorial.captionBrown)
                Text("把相遇留在四季里。", Modifier.padding(top = 8.dp), style = Editorial.chineseTitle.copy(fontSize = 27.sp), color = Editorial.ink)
                Text("以每一次真实观察，记录四季的变化。", style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown)
                TextButton({ showRules = !showRules }, contentPadding = PaddingValues(0.dp)) { Text(if(showRules) "收起归档说明" else "了解四季归档方式", style = MaterialTheme.typography.labelSmall) }
                if(showRules) Text("按观察日期 · ${archive.zone.id}\n春 3–5 月 / 夏 6–8 月\n秋 9–11 月 / 冬 12、1、2 月\n冬季按各自公历年份归档；不以花期推测。", style = MaterialTheme.typography.bodySmall)
                if (archive.years.isNotEmpty()) Box {
                    OutlinedButton({ yearMenu = true }, Modifier.heightIn(min = 48.dp).testTag("year-${state.year}").semantics { selected = true },
                        shape = androidx.compose.foundation.shape.CircleShape,
                        border = BorderStroke(.7.dp, Editorial.hairline), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("${state.year} 年", style = MaterialTheme.typography.bodyMedium)
                        KitIcon(R.drawable.wf_icon_chevron_down, modifier = Modifier.padding(start = 10.dp).size(20.dp))
                    }
                    DropdownMenu(yearMenu, { yearMenu = false }) {
                        archive.years.forEach { year -> DropdownMenuItem(text = { Text("$year 年") }, onClick = { onYear(year); yearMenu = false }, modifier = Modifier.testTag("year-option-$year")) }
                    }
                }
                }
                }
            }
            when {
                state.error != null -> item { EditorialError(state.error) }
                !archive.loaded -> item { EditorialLoading("正在整理你的四季…") }
                state.year == null || state.year !in archive.years -> item {
                    BotanicalEmptyArtwork(R.drawable.wf_empty_season, Modifier.fillMaxWidth().height(220.dp))
                    EditorialInfoCard("这一年，还没有自然观察", "年份只来自实际记录。没有照片的观察会显示占位；浇水、笔记和叶片检查不计入摄影手账。")
                    WildflowerPrimaryButton("去发现植物", onScan, Modifier.fillMaxWidth())
                }
                state.season == null -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Season.entries.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEach { season ->
                                    val records = archive.observations(state.year, season)
                                    Surface(Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable { onSeason(season) }.testTag("season-${season.name}"),
                                        shape = MaterialTheme.shapes.medium, color = seasonTint(season)) {
                                        Column {
                                            Box(Modifier.fillMaxWidth().height(136.dp)) {
                                                if (records.isEmpty()) {
                                                    BotanicalPhoto(emptySeasonArtwork(season), "${season.label}季编辑意象 · 非观察照片", Modifier.fillMaxSize())
                                                    Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = Editorial.ink.copy(alpha = .8f)) {
                                                        Text("季节意象 · 非观察照片", Modifier.padding(horizontal = 6.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = Editorial.paper)
                                                    }
                                                } else BotanicalPhoto(records.firstOrNull { it.observation.photoUri != null }?.observation?.photoUri, "${season.label}季观察封面", Modifier.fillMaxSize())
                                                Surface(Modifier.align(Alignment.TopEnd).padding(8.dp).size(30.dp), shape = androidx.compose.foundation.shape.CircleShape, color = seasonTint(season)) {
                                                    Box(contentAlignment = Alignment.Center) { Text(season.label, style = Editorial.sectionTitle) }
                                                }
                                            }
                                            Box {
                                            Box(Modifier.matchParentSize()) { FloralDecoration(Modifier.align(Alignment.BottomEnd).size(100.dp), .2f, seasonOrnament(season)) }
                                            Column(Modifier.heightIn(min = 108.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                                Text("${season.label} · ${season.english}", style = Editorial.sectionTitle.copy(fontSize = 18.sp, lineHeight = 24.sp), color = MaterialTheme.colorScheme.primary)
                                                Text("${records.size} 次观察 · ${records.map { it.plant.id }.distinct().size} 株植物", style = MaterialTheme.typography.labelSmall)
                                                Text(if (records.isEmpty()) "等待相遇 →" else "翻开这一季 →", style = MaterialTheme.typography.labelSmall, color = Editorial.captionBrown)
                                            }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    val records = archive.observations(state.year, state.season)
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Season.entries.forEach { season -> FilterChip(state.season == season, { onSeason(season) }, { Text("${season.label}季") }) }
                        }
                        TextButton({ onSeason(null) }, contentPadding = PaddingValues(0.dp)) { Text("返回四季总览") }
                        EditorialSectionHeader("${state.year} · ${state.season.label}季", "${records.size} 次观察 / ${records.map { it.plant.id }.distinct().size} 株植物")
                    }
                    if (records.isEmpty()) item {
                        BotanicalEmptyArtwork(R.drawable.wf_empty_season, Modifier.fillMaxWidth().height(220.dp))
                        EditorialInfoCard("这一季，尚无观察", "记录下一次真实相遇，它会按观察日期归入手账。")
                        WildflowerPrimaryButton("去发现植物", onScan)
                    }
                    items(records, key = { it.observation.id }) { record ->
                        val leading = record == records.firstOrNull()
                        Column(Modifier.fillMaxWidth().clickable { onObservation(record.plant.id, record.observation.id) }.testTag("season-observation-${record.observation.id}"),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (leading) BotanicalPhoto(record.observation.photoUri, record.plant.nickname, Modifier.fillMaxWidth().aspectRatio(1.65f).clip(MaterialTheme.shapes.medium))
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (!leading) BotanicalPhoto(record.observation.photoUri, record.plant.nickname, Modifier.size(88.dp).clip(MaterialTheme.shapes.small))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(record.observation.timestamp.atZone(archive.zone).format(DateTimeFormatter.ofPattern("yyyy.MM.dd · HH:mm")), style = MaterialTheme.typography.labelSmall, color = Editorial.captionBrown)
                                    Text(record.plant.nickname, style = if (leading) Editorial.sectionTitle else MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    ScientificNameLabel(record.plant.scientificName.ifBlank { "学名未知" })
                                }
                                Text("↗", color = MaterialTheme.colorScheme.primary)
                            }
                            record.observation.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                            Text("查看植物档案与本次观察 →", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

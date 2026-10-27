package com.floralens.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.floralens.BuildConfig
import com.floralens.domain.model.CareLevel
import com.floralens.domain.model.GeoPoint
import com.floralens.domain.model.PlantInsights
import com.floralens.domain.model.RegionalName
import com.floralens.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.floralens.data.explore.FeaturedSpecies
import com.floralens.ui.components.BotanicalPhoto
import com.floralens.ui.components.ScientificNameLabel
import com.floralens.ui.components.EditorialSectionHeader
import com.floralens.ui.components.EditorialInfoCard
import com.floralens.ui.components.CulturePreviewCard
import com.floralens.ui.components.WildflowerTopBar
import com.floralens.ui.components.WildflowerSecondaryButton
import com.floralens.ui.components.EditorialLoading
import com.floralens.ui.components.EditorialError
import com.floralens.ui.theme.Editorial
import com.floralens.ui.components.WildflowerPrimaryButton
import com.floralens.ui.components.EditorialMetadata
import com.floralens.ui.components.EditorialReadingNote
import com.floralens.ui.components.FloralDecoration
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.unit.sp
import com.floralens.R
import com.floralens.ui.components.KitIcon
import com.floralens.ui.components.editorialSpeciesArtwork
import com.floralens.ui.components.EDITORIAL_ARTWORK_CREDIT

/**
 * Species detail — opened via "Learn more" on an online (Pl@ntNet) result.
 * Tabbed: Overview · Care · Explore (map + occurrence chart) · Videos.
 * All data is fetched up front when the screen opens (see ViewModel).
 * Honest by design: AI-generated banner, toxicity defers to a professional.
 *
 * Offline behavior: viewing details online auto-saves them; when offline
 * (by mode or by connectivity) the saved copy renders with a banner, and
 * the top-bar cloud icon shows whether this species is saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantDetailsScreen(
    onBack: () -> Unit,
    onCollected: (Long) -> Unit = {},
    viewModel: PlantDetailsViewModel = hiltViewModel(),
    onCulture: ((String, String?) -> Unit)? = null,
    onChat: ((String) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()
    val collection by viewModel.collection.collectAsStateWithLifecycle()
    var confirming by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(collection.savedPlantId) { collection.savedPlantId?.let(onCollected) }

    SpeciesDetailContent(uiState, viewModel.scientificName, viewModel.commonName, viewModel.reference,
        isSaved, viewModel.canRefresh, onBack, viewModel::load,
        collectArea = {
            viewModel.captureError?.let { EditorialError(it) }
            if (viewModel.collectionReady) WildflowerPrimaryButton("收藏这株植物", { confirming = true; viewModel.existingPlants() }, Modifier.fillMaxWidth())
            Text(if (viewModel.capture != null) "收藏这次相遇，留下照片与观察。" else "先收藏喜欢的植物，遇见时再添上自己的照片。", style = MaterialTheme.typography.bodySmall)
        }, cultureArea = onCulture?.let { open -> { com.floralens.ui.screens.culture.CultureLink(viewModel.species.key,
            { key -> open(key, viewModel.capture?.id) }) } },
        chatArea = onChat?.let { open -> { com.floralens.ui.screens.chat.PlantChatEntryCard(
            onClick = { open(viewModel.species.key) }, modifier = Modifier.testTag("species-chat-entry")) } })
    if(confirming) CollectionConfirmation(viewModel.reference?.name ?: com.floralens.ui.presentation.PlantDisplayNames.chinese(viewModel.scientificName, viewModel.commonName), collection,
        viewModel.capture != null, viewModel.species.scientificName != null, { if(!collection.saving) confirming = false }, viewModel::collect)
}

@Composable
internal fun CollectionConfirmation(initialName: String, state: CollectionState, hasPhoto: Boolean, knownSpecies: Boolean,
    onDismiss: () -> Unit, onConfirm: (String, Long?) -> Unit) {
    var nickname by rememberSaveable { mutableStateOf(initialName) }
    var existingId by rememberSaveable { mutableStateOf<Long?>(null) }
    androidx.compose.material3.AlertDialog(onDismissRequest = onDismiss, title = { Text("收藏确认") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.OutlinedTextField(nickname, { nickname = it }, label = { Text("植物昵称") }, enabled = !state.saving && existingId == null, singleLine = true)
            Text(if(hasPhoto) "保存本次实拍照片与观察。" else "没有实拍照片，不会创建首次观察。")
            if(state.loading) EditorialLoading("正在查找同物种档案…")
            if(state.existing.isNotEmpty()) Text(if(knownSpecies) "已有同物种档案，请自主选择；不会自动合并。" else "已有同名未知物种档案；名称相同不代表同一物种，请自主选择。")
            androidx.compose.material3.TextButton({ existingId = null }, enabled = !state.saving) { Text(if(existingId == null) "新建一株植物档案 ✓" else "新建一株植物档案") }
            state.existing.forEach { plant ->
                androidx.compose.material3.TextButton({ existingId = plant.id }, enabled = !state.saving) { Text("添加到：${plant.nickname}" + if(existingId == plant.id) " ✓" else "") }
            }
            if(existingId != null && !hasPhoto) Text("关联已有档案不会生成新观察。")
            if(existingId != null) Text("保留已有档案昵称；可在个人植物详情中编辑。")
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { androidx.compose.material3.Button({ onConfirm(nickname, existingId) }, enabled = !state.loading && !state.saving && (existingId != null || (nickname.isNotBlank() && nickname.length <= 100))) { Text(if(state.saving) "正在收藏…" else "确认收藏") } },
        dismissButton = { androidx.compose.material3.TextButton(onDismiss, enabled = !state.saving) { Text("取消") } })
}

/** Main editorial reading surface. Legacy maps/videos/cached science remain in a full-screen reader. */
@Composable
fun SpeciesDetailContent(state: DetailsUiState, scientificName: String, commonName: String?, reference: FeaturedSpecies?,
    isSaved: Boolean, canRefresh: Boolean, onBack: () -> Unit, onRetry: () -> Unit, collectArea: (@Composable () -> Unit)? = null,
    cultureArea: (@Composable () -> Unit)? = null, chatArea: (@Composable () -> Unit)? = null) {
    val uriHandler = LocalUriHandler.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var sourceError by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        WildflowerTopBar("植物笔记", onBack, actions = {
                if (isSaved) {
                    Icon(
                        Icons.Filled.CloudDone,
                        contentDescription = "科学资料已缓存（非个人收藏）",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = Spacing.sm).size(20.dp),
                    )
                }
                if (canRefresh) {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新科学资料")
                    }
                }
            })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            val publicArtwork = reference?.let { editorialSpeciesArtwork(it.scientific, alternate = true) }
            val usesArtwork = state.imageUrls.isEmpty() && publicArtwork != null
            Box(Modifier.widthIn(max = 600.dp).fillMaxWidth().height(212.dp).clipToBounds()) {
                // The supplied editorial crop contains a remnant at its left edge. Zoom only
                // that public asset; pending captures and personal photographs are unchanged.
                BotanicalPhoto(state.imageUrls.firstOrNull() ?: publicArtwork ?: reference?.photo,
                    reference?.name ?: commonName ?: scientificName,
                    Modifier.fillMaxSize().graphicsLayer {
                        if (usesArtwork) { scaleX = 1.08f; scaleY = 1.08f }
                    })
            }
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().offset(y = (-14).dp).background(Editorial.paper, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).padding(horizontal = Editorial.pageMargin, vertical = 16.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Box {
                Box(Modifier.matchParentSize()) { FloralDecoration(Modifier.align(Alignment.TopEnd).size(150.dp), .26f) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SPECIES NOTES  /  公共物种资料", style = Editorial.eyebrow, color = Editorial.captionBrown)
                    Text(reference?.name ?: com.floralens.ui.presentation.PlantDisplayNames.chinese(scientificName, commonName),
                        style = Editorial.chineseTitle.copy(fontSize = 32.sp, lineHeight = 39.sp), color = Editorial.ink)
                    ScientificNameLabel(scientificName.ifBlank { "学名待补充" })
                    reference?.let {
                        Text(it.common, style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown)
                        Text(it.summary, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp), modifier = Modifier.padding(top = 6.dp))
                    }
                }
                }
                reference?.let {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { EditorialSectionHeader("科学速览") }
                            FloralDecoration(Modifier.padding(start = 8.dp).size(28.dp), .42f, R.drawable.wf_ornament_sage_leaf)
                        }
                        Surface(shape = RoundedCornerShape(16.dp), color = Editorial.paper, border = androidx.compose.foundation.BorderStroke(.5.dp, Editorial.hairline)) {
                            Column(Modifier.padding(14.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        KitIcon(R.drawable.wf_icon_leaf, modifier = Modifier.size(30.dp))
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("科属", style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown)
                                            Text(it.family, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        KitIcon(R.drawable.wf_icon_flower, modifier = Modifier.size(30.dp))
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("生长形态", style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown)
                                            Text(it.habit, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                                EditorialMetadata("典型花期", it.bloom)
                            }
                        }
                    }
                }
                HorizontalDivider(color = Editorial.hairline)
                Surface(color = Editorial.linen, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { if (cultureArea != null) cultureArea() else CulturePreviewCard() }
                        val culturePhoto = if (scientificName.trim().equals("Osmanthus fragrans", ignoreCase = true)) R.drawable.wf_editorial_culture_banner else state.imageUrls.firstOrNull() ?: reference?.photo
                        if (culturePhoto != null) BotanicalPhoto(culturePhoto, "公共植物资料摄影", Modifier.width(94.dp).height(126.dp).clip(RoundedCornerShape(12.dp)))
                    }
                }
                chatArea?.invoke()
                collectArea?.invoke()
                reference?.let {
                    Text("资料：NC State Extension Gardener Plant Toolbox\n" + if (usesArtwork) EDITORIAL_ARTWORK_CREDIT else "参考摄影：${it.credit}", style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown)
                    androidx.compose.material3.TextButton({
                        sourceError = try { uriHandler.openUri(it.source); null } catch (_: Exception) { "来源暂时无法打开，请检查网络或浏览器。" }
                    }) { Text("查看科学资料出处 ↗") }
                    sourceError?.let { message -> Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
                when (state) {
                    is DetailsUiState.Loading -> EditorialLoading()
                    is DetailsUiState.Error -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                      EditorialReadingNote("扩展资料",
                        if (state.offlineAndNotSaved) "当前离线，尚无此物种的扩展资料缓存。" + if (reference != null) "本地精选资料仍可阅读。" else "联网后可再试。"
                        else "资料暂时无法加载，请检查网络后重试。")
                      WildflowerSecondaryButton("重新加载", onRetry)
                    }
                    is DetailsUiState.Loaded -> {
                        if (state.fromOfflineCache) OfflineCopyBanner(state.savedAt)
                        EditorialSectionHeader("延伸阅读", "SCIENCE & CARE")
                        EditorialReadingNote("AI 辅助资料 · 请核实", state.insights.description ?: "暂无概述，可阅读其他科学与养护资料。")
                        WildflowerSecondaryButton("浏览科学、养护与分布资料", { expanded = true }, Modifier.fillMaxWidth())
                    }
                }
                Text("浏览物种笔记不会创建个人植物档案。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (expanded && state is DetailsUiState.Loaded) {
        Dialog(onDismissRequest = { expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                Column {
                    WildflowerTopBar("扩展科学资料", { expanded = false })
                    TabbedContent(state, commonName, reference)
                }
            }
        }
    }
}

/** "You're looking at the saved copy" strip, with the save date. */
@Composable
private fun OfflineCopyBanner(savedAt: Instant?) {
    val dateLabel = remember(savedAt) {
        savedAt?.let {
            DateTimeFormatter.ofPattern("yyyy年M月d日").withZone(ZoneId.systemDefault()).format(it)
        }
    }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm),
        ) {
            Icon(
                Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                if (dateLabel != null) "离线科学资料 · 缓存于 $dateLabel" else "离线科学资料",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private enum class DetailTab(val label: String) {
    OVERVIEW("科学"), CARE("养护"), EXPLORE("分布"), VIDEOS("视频")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabbedContent(state: DetailsUiState.Loaded, commonName: String?, reference: FeaturedSpecies?) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = DetailTab.entries

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = Spacing.lg) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t.label) })
            }
        }
        when (tabs[tab]) {
            DetailTab.OVERVIEW -> OverviewTab(state, commonName, reference)
            DetailTab.CARE -> CareTab(state.insights)
            DetailTab.EXPLORE -> ExploreTab(state)
            DetailTab.VIDEOS -> VideosTab(state)
        }
    }
}

@Composable
private fun PlaceholderTab(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.sm))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ImageGallery(imageUrls: List<String>) {
    when {
        imageUrls.isEmpty() -> Unit
        imageUrls.size == 1 -> AsyncImage(
            model = imageUrls.first(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(20.dp)),
        )
        else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            items(imageUrls) { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.height(200.dp).aspectRatio(3f / 4f).clip(RoundedCornerShape(20.dp)),
                )
            }
        }
    }
}

@Composable
private fun OverviewTab(state: DetailsUiState.Loaded, commonName: String?, reference: FeaturedSpecies?) {
    val insights = state.insights
    val imageUrls = state.imageUrls
    val localReference = reference?.takeIf { it.scientific.trim().equals(insights.scientificName.trim(), ignoreCase = true) }
    val uriHandler = LocalUriHandler.current
    var sourceError by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Spacer(Modifier.height(Spacing.lg))
        ImageGallery(imageUrls)
        Text(com.floralens.ui.presentation.PlantDisplayNames.chinese(insights.scientificName, commonName),
            style = Editorial.sectionTitle, color = MaterialTheme.colorScheme.primary)
        ScientificNameLabel(insights.scientificName)
        com.floralens.ui.presentation.PlantDisplayNames.find(insights.scientificName)?.classification?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.fromOfflineCache) OfflineCopyBanner(state.savedAt)

        // Reuse the existing sourced catalog only for this exact species. These
        // bundled facts stay distinct from optional AI material below.
        localReference?.let { local ->
            EditorialReadingNote("本地精选资料", local.summary)
            EditorialMetadata("生长形态", local.habit)
            EditorialMetadata("观察特征", local.bloom)
            Text("资料来源：NC State Extension Gardener Plant Toolbox", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.TextButton({
                sourceError = try { uriHandler.openUri(local.source); null }
                catch (_: Exception) { "来源暂时无法打开，请检查网络或浏览器。" }
            }) { Text("打开精选科学资料来源 ↗") }
            sourceError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(
                "AI 辅助生成，可能不准确。养护与安全建议需另行核实。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        insights.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) }

        DetailSection(icon = Icons.Filled.AutoAwesome, title = "资料说明") {
            Text(
                if (state.fromOfflineCache)
                    "当前可离线阅读。AI 辅助扩展内容仅供一般参考，不替代专业植物学资料；本地精选内容另附来源。"
                else
                    "本页扩展内容由联网服务辅助整理；它不是个人植物档案，重要分类、养护与安全信息仍需结合可靠来源核实。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (insights.careLevels.hasAny) {
            DetailSection(icon = Icons.Filled.LocalFlorist, title = "养护速览") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CareMeter("浇水", insights.careLevels.water)
                    CareMeter("光照", insights.careLevels.light)
                    CareMeter("湿度", insights.careLevels.humidity)
                    CareMeter("养护难度", insights.careLevels.difficulty)
                }
            }
        }

        val quickFacts = buildList {
            insights.growthHabit?.let { add("生长形态" to it) }
            insights.bloomSeason?.let { add("花期" to it) }
            insights.origin?.let { add("原产地" to it) }
        }
        if (quickFacts.isNotEmpty()) {
            DetailSection(icon = Icons.Filled.Public, title = "植物概览") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    quickFacts.forEach { (label, value) ->
                        Row {
                            Text("$label:  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        if (insights.regionalNames.isNotEmpty()) {
            DetailSection(icon = Icons.Filled.Translate, title = "其他名称") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    insights.regionalNames.forEach { RegionalNameChip(it) }
                }
            }
        }

        if (insights.funFacts.isNotEmpty()) {
            DetailSection(icon = Icons.Filled.AutoAwesome, title = "自然小知识") { BulletList(insights.funFacts) }
        }

        val onlyBasicDescription = !insights.careLevels.hasAny && quickFacts.isEmpty() &&
                insights.regionalNames.isEmpty() && insights.funFacts.isEmpty() && insights.toxicityNote == null
        if (onlyBasicDescription) {
            Text(
                if (insights.description.isNullOrBlank() && localReference != null) "更多辅助资料暂未收录，联网后可尝试刷新。"
                else if (insights.description.isNullOrBlank()) "当前暂无科学介绍与更多字段，联网后可尝试刷新。"
                else "当前仅有基础介绍，其他科学字段尚无可显示内容。联网后可尝试刷新。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        insights.toxicityNote?.let { ToxicityCard(it) }
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun CareTab(insights: PlantInsights) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Spacer(Modifier.height(Spacing.lg))
        insights.careNotes?.let {
            DetailSection(icon = Icons.Filled.LocalFlorist, title = "养护笔记") {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (insights.propagation.isNotEmpty()) {
            DetailSection(icon = Icons.Filled.ContentCut, title = "繁殖方式") { BulletList(insights.propagation) }
        }
        if (insights.commonProblems.isNotEmpty()) {
            DetailSection(icon = Icons.Filled.BugReport, title = "常见问题") { BulletList(insights.commonProblems) }
        }
        val nothing = insights.careNotes == null && insights.propagation.isEmpty() && insights.commonProblems.isEmpty()
        if (nothing) {
            Text("养护资料暂未收录。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "本页是物种层面的 AI 辅助一般参考，不会改变个人档案中的浇水计划。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun ExploreTab(state: DetailsUiState.Loaded) {
    // While a finger is on the region map, the page scroll is disabled so
    // the map owns every gesture — otherwise Compose's scrollable steals
    // the second pointer and pinch-zoom never reaches the map.
    var mapTouched by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState(), enabled = !mapTouched)
            .padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Spacer(Modifier.height(Spacing.lg))

        val regions = state.insights.nativeRegions
        if (regions.isEmpty()) {
            Text(
                "这株植物的原生分布资料暂未收录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            DetailSection(icon = Icons.Filled.Public, title = "分布区域") {
                when {
                    state.geoPoints.isNotEmpty() -> {
                        // Live view with a Maps key → real world map. Offline
                        // copies keep the canvas: no tiles without a network.
                        if (!state.fromOfflineCache && BuildConfig.BAIDU_MAP_CONFIGURED) {
                            NativeRegionsMap(
                                points = state.geoPoints,
                                onTouchChanged = { mapTouched = it },
                            )
                        } else {
                            WorldMapCanvas(points = state.geoPoints, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    state.fromOfflineCache -> Text(
                        // Cached copy without pins — don't spin forever on data that will never arrive.
                        "缓存中暂无分布地图，可在联网后刷新。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            "正在整理分布区域…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                "由辅助资料推测的常见分布区域，仅供阅读参考。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // GBIF occurrence distribution — real records, not AI estimates.
        Spacer(Modifier.height(Spacing.md))
        DetailSection(icon = Icons.Filled.Public, title = "按国家查看分布记录") {
            when {
                state.occurrenceLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("正在读取分布记录…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.occurrence == null -> Text(
                    if (state.fromOfflineCache)
                        "缓存中暂无分布记录，可在联网后刷新。"
                    else
                        "GBIF 暂未找到此物种的分布记录。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Column {
                    OccurrenceDonut(data = state.occurrence, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "来源：GBIF 物种记录，非推测分布。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.xl))
    }
}

/** Catalog-region map; separate from personal observations and gated by SDK consent. */
@Composable
private fun NativeRegionsMap(
    points: List<GeoPoint>,
    onTouchChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val privacy = remember(context) { com.floralens.data.settings.BaiduMapPrivacy(context) }
    val choice by privacy.choice.collectAsStateWithLifecycle(initialValue = privacy.current())
    var selectedRegion by remember(points) { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { onTouchChanged(false) } }
    Column {
        if (choice == com.floralens.data.settings.MapPrivacyChoice.GRANTED) {
          key(points) { // A different species reframes; ordinary personal-map updates do not.
            Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)).pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false); onTouchChanged(true)
                    do { val event = awaitPointerEvent(PointerEventPass.Initial) } while (event.changes.any { it.pressed })
                    onTouchChanged(false)
                }
            }) {
                com.floralens.ui.screens.map.BaiduMapSurface(points.mapIndexed { index, p ->
                    // This separate catalog map uses known WGS84 Nominatim geocodes, not personal observations.
                    com.floralens.ui.screens.map.BaiduMapPoint(index.toLong(), p.latitude, p.longitude,
                        com.floralens.domain.model.CoordinateSystem.WGS84)
                }, { id -> selectedRegion = points.getOrNull(id.toInt())?.label }, Modifier.fillMaxSize(), agreed = true)
                selectedRegion?.let { label ->
                    Surface(Modifier.align(Alignment.TopStart).padding(8.dp), shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = .94f)) {
                        Text(label, Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall, maxLines = 2)
                    }
                }
            }
          }
        } else WorldMapCanvas(points, Modifier.fillMaxWidth())
        com.floralens.ui.screens.map.BaiduMapPrivacyControl(choice, privacy::choose)
    }
}

@Composable
private fun VideosTab(state: DetailsUiState.Loaded) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Spacer(Modifier.height(Spacing.lg))
        when {
            state.videoAvailability.message != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    state.videoAvailability.message.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.videosLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(Spacing.sm))
                Text("正在查找相关视频…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.videoSections.isEmpty() -> Text(
                "暂无该植物的视频资料。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                VideoSectionList(sections = state.videoSections, modifier = Modifier.fillMaxWidth())
                Text(
                    "视频来自 YouTube，点击后在外部应用中打开。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun ToxicityCard(note: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(Spacing.lg)) {
            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.md))
            Column {
                Text("安全提示", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.height(2.dp))
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}

@Composable
private fun BulletList(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items.forEach { item ->
            Row {
                Text("•  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(item, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CareMeter(label: String, level: CareLevel) {
    val filled = when (level) {
        CareLevel.LOW -> 1; CareLevel.MEDIUM -> 2; CareLevel.HIGH -> 3; CareLevel.UNKNOWN -> 0
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
        if (level == CareLevel.UNKNOWN) {
            Text("—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    Box(
                        Modifier.size(if (i < filled) 12.dp else 10.dp).clip(CircleShape)
                            .background(if (i < filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                }
            }
            Spacer(Modifier.width(Spacing.md))
            Text(when(level) { CareLevel.LOW -> "低"; CareLevel.MEDIUM -> "中"; CareLevel.HIGH -> "高"; CareLevel.UNKNOWN -> "暂无" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailSection(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        content()
    }
}

@Composable
private fun RegionalNameChip(regional: RegionalName) {
    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(modifier = Modifier.padding(horizontal = Spacing.md, vertical = 6.dp)) {
            Text("${regional.language}: ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
            Text(regional.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun LoadingContent(imageUrls: List<String>) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Spacer(Modifier.height(Spacing.lg))
        ImageGallery(imageUrls)
        Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.x10), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(Spacing.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("正在整理植物资料…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ErrorContent(
    scientificName: String,
    offlineAndNotSaved: Boolean,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (offlineAndNotSaved) Icons.Filled.CloudOff else Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(Spacing.lg))
        Text(
            if (offlineAndNotSaved) "暂无离线资料" else "资料暂时无法加载",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            if (offlineAndNotSaved)
                "此物种尚无离线资料。请在联网时打开详情，" +
                        "可用资料会自动保存在本机。"
            else
                "资料暂时无法加载，请检查网络后重试。" +
                        "浏览详情不会自动收藏植物。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        Button(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text("重新加载")
        }
    }
}

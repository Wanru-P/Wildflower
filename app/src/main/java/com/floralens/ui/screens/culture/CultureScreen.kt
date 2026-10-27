package com.floralens.ui.screens.culture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.floralens.BuildConfig
import com.floralens.data.culture.*
import com.floralens.data.explore.ExploreCatalog
import com.floralens.data.photos.PendingCaptureStore
import com.floralens.data.repo.PlantRepository
import com.floralens.domain.model.SpeciesKey
import com.floralens.ui.components.*
import com.floralens.ui.theme.Editorial
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.floralens.R
import com.floralens.domain.culture.*
import com.floralens.data.repo.ObservationRepository
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape

data class CultureUiState(val loading: Boolean = true, val entry: CultureEntry? = null, val photo: String? = null,
    val photoCaption: String = "植物影像待补充", val editorial: Boolean = false, val error: String? = null,
    val chineseName: String? = null, val scientificName: String? = null,
    val identity: CultureIdentity = CultureIdentityResolver.resolve(), val expression: NatureExpression? = null,
    val generating: Boolean = false, val generationMessage: String? = null, val style: String = "quiet")
@HiltViewModel
class CultureViewModel @Inject constructor(saved: SavedStateHandle, repository: CultureRepository,
    explore: ExploreCatalog, captures: PendingCaptureStore, plants: PlantRepository,
    observations: ObservationRepository, private val expressions: NatureExpressionRepository) : ViewModel() {
    val state = MutableStateFlow(CultureUiState())
    private var generation: kotlinx.coroutines.Job? = null
    init { viewModelScope.launch {
        val key = saved.get<String>("speciesKey").orEmpty()
        val editorial = saved.get<Boolean>("review") == true && BuildConfig.DEBUG && BuildConfig.INTERNAL_TOOLS
        try {
            val candidate = if (editorial) repository.editorialEntry(key) else repository.readingContent(key)
            val reference = explore.species.firstOrNull { SpeciesKey.fromLegacy(it.scientific, it.name) == key }
            val plantId = saved.get<Long>("plantId")?.takeIf { it > 0 }
            val plant = plantId?.let { plants.observeById(it).first() }?.takeIf { it.speciesKey == key }
            val captureId = saved.get<String>("captureId")?.takeIf { it.isNotBlank() }
            val capture = captureId?.let { captures.get(it) }
                ?.takeIf { it.selected?.key == key }
            val uncertainArchive = plant?.let { observations.observeForPlant(it.id).first().any { observation ->
                CultureSnapshotPolicy.uncertain(observation.identificationJson)
            } } ?: false
            val contextCertain = CultureIdentificationPolicy.contextCertain(captureId != null, capture?.result, key,
                archiveAvailable = plantId == null || plant != null, uncertainArchive = uncertainArchive)
            val identity = CultureIdentityResolver.resolve(key, capture?.selected?.scientificName ?: plant?.scientificName ?: reference?.scientific,
                certain = contextCertain)
            val entry = candidate.takeIf { editorial || contextCertain }
            state.value = CultureUiState(false, entry, capture?.photoUri ?: plant?.photoUri ?: reference?.photo,
                when { capture != null -> "本次实拍 · 尚未收藏"; plant?.photoUri != null -> "你的植物观察照片";
                    reference != null -> "参考摄影 · ${reference.credit}"; else -> "植物影像待补充" }, editorial,
                chineseName = reference?.name ?: plant?.commonName ?: capture?.selected?.commonName,
                scientificName = reference?.scientific ?: plant?.scientificName ?: capture?.selected?.scientificName ?: identity.scientific,
                identity = identity, expression = expressions.current(identity), style = expressions.selectedStyle(identity))
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { val identity = CultureIdentityResolver.resolve(key, certain = false)
            state.value = CultureUiState(loading = false, identity = identity, expression = NatureTextPolicy.local(identity), scientificName = identity.scientific,
                generationMessage = "文献暂时无法读取，仍可阅读自然观察文字。") }
    } }
    fun style(value: String) {
        if (state.value.loading || state.value.generating || value !in setOf("quiet", "journal")) return
        expressions.selectStyle(state.value.identity, value)
        state.value = state.value.copy(style = value, expression = NatureTextPolicy.local(state.value.identity), generationMessage = null)
        viewModelScope.launch { val before = state.value; val text = expressions.current(before.identity, value)
            // A delayed cache read must not replace an in-flight/newly generated expression.
            if (state.value.style == value && !state.value.generating && state.value.expression?.source != ExpressionSource.AI)
                state.value = state.value.copy(expression = text) }
    }
    fun generate() {
        if (state.value.loading || generation?.isActive == true) return
        val before = state.value
        state.value = before.copy(generating = true, generationMessage = null)
        generation = viewModelScope.launch {
            try { val result = expressions.generate(before.identity, before.style, force = before.expression?.source == ExpressionSource.AI)
                state.value = state.value.copy(expression = result) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { state.value = state.value.copy(generationMessage = NatureExpressionRepository.message((e as? NatureServiceException)?.reason ?: "network")) }
            finally { state.value = state.value.copy(generating = false) }
        }
    }
    fun configure(url: String, token: String) = expressions.config.save(url, token)
}

@HiltViewModel
class CultureLinkViewModel @Inject constructor(private val repository: CultureRepository, private val saved: SavedStateHandle,
    private val captures: PendingCaptureStore, private val observations: ObservationRepository) : ViewModel() {
    val entry = MutableStateFlow<CultureEntry?>(null)
    private var job: kotlinx.coroutines.Job? = null
    fun load(key: String) {
        job?.cancel(); entry.value = null
        job = viewModelScope.launch { entry.value = runCatching {
            val captureId = saved.get<String>("captureId")?.takeIf(String::isNotBlank)
            val capture = captureId?.let { captures.get(it) }?.takeIf { it.selected?.key == key }
            val uncertain = saved.get<Long>("plantId")?.takeIf { it > 0 }?.let { id ->
                observations.observeForPlant(id).first().any { CultureSnapshotPolicy.uncertain(it.identificationJson) } } ?: false
            if (!CultureIdentificationPolicy.contextCertain(captureId != null, capture?.result, key, uncertainArchive = uncertain)) null else repository.readingContent(key)
        }.getOrNull() }
    }
}

@Composable
fun CultureLink(speciesKey: String, onCulture: (String) -> Unit, viewModel: CultureLinkViewModel = hiltViewModel()) {
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    LaunchedEffect(speciesKey) { viewModel.load(speciesKey) }
    entry?.let {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EditorialSectionHeader("植物与文化 · ${it.themeTitle}", "CULTURE NOTES")
            Text(cultureReadingNotice(it), style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown)
            WildflowerSecondaryButton("阅读植物文化 →", { onCulture(it.speciesKey) }, Modifier.fillMaxWidth(), iconResource = R.drawable.wf_icon_book)
        }
    } ?: Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EditorialReadingNote("植物与自然文字", "从可靠的文献或一次细心观察，寻找与植物相遇的表达。")
        WildflowerSecondaryButton("阅读植物文化 →", { onCulture(speciesKey) }, Modifier.fillMaxWidth(), iconResource = R.drawable.wf_icon_book)
    }
}

@Composable
fun CultureScreen(onBack: () -> Unit, viewModel: CultureViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    var consent by rememberSaveable { mutableStateOf(false) }
    var configuration by rememberSaveable { mutableStateOf(false) }
    CultureContentWithActions(state, onBack, { uri.openUri(it) }, { consent = true }, { configuration = true }, viewModel::style)
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text("生成 AI 原创自然短诗") },
        text = { Text(if (state.identity.canGenerate) "仅向创作代理和 DeepSeek 发送已确认的植物名称与所选文风，不发送照片、位置、日期或私人观察备注。内容是现代原创，不是古典文献或观察事实。"
            else "本次使用通用自然创作模式，仅发送文风与通用模式标识，不发送昵称、候选学名、照片、位置、日期或私人观察备注。内容是现代原创，不确认植物身份，也不是古典文献。") },
        confirmButton = { TextButton({ consent = false; viewModel.generate() }) { Text("同意并生成") } },
        dismissButton = { TextButton({ consent = false }) { Text("取消") } })
    if (configuration) NatureServiceDialog({ configuration = false }, viewModel::configure)
}

@Composable
fun CultureContent(state: CultureUiState, onBack: () -> Unit, openSource: (String) -> Unit) =
    CultureContentWithActions(state, onBack, openSource)

@Composable
fun CultureContentWithActions(state: CultureUiState, onBack: () -> Unit, openSource: (String) -> Unit,
    onGenerate: () -> Unit = {}, onConfigure: () -> Unit = {}, onStyle: (String) -> Unit = {}) {
    val artwork = if (state.photoCaption.startsWith("参考摄影") || state.photo == null) editorialSpeciesArtwork(state.scientificName ?: state.entry?.scientificName.orEmpty(), alternate = true) else null
    val photo: Any? = artwork ?: state.photo
    val photoCaption = if (artwork != null) EDITORIAL_ARTWORK_CREDIT else state.photoCaption
    var linkError by rememberSaveable { mutableStateOf<String?>(null) }
    val open: (String?) -> Unit = { url ->
        if (!CulturePolicy.validUrl(url)) linkError = "此项没有可打开的来源链接，仍待核实。"
        else try { openSource(url!!); linkError = null } catch (_: Exception) {
            linkError = "来源暂时无法打开，请检查网络或浏览器后重试。"
        }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        WildflowerTopBar(if (state.editorial) "文化资料 · 编辑预览" else "植物文化", onBack)
        linkError?.let { Text(it, Modifier.fillMaxWidth().padding(horizontal = Editorial.pageMargin, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        when {
            state.loading -> EditorialLoading("正在翻阅本地文化资料…")
            state.error != null -> EditorialError(state.error)
            else -> {
                val entry = state.entry
                LazyColumn(Modifier.widthIn(max = 660.dp).fillMaxWidth().weight(1f).testTag("culture-list"), contentPadding = PaddingValues(bottom = 32.dp)) {
                    item { CultureIntroduction(state, photo, photoCaption) }
                    item { Column(Modifier.padding(horizontal = Editorial.pageMargin).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HorizontalDivider(color = Editorial.hairline)
                        EditorialSectionHeader("自然观察", "NATURE NOTES")
                        Text("从轮廓、颜色与环境关系开始记录，\n为下一次相遇留下可比较的线索。", style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 25.sp), color = Editorial.ink)
                    } }
                    if (entry != null) {
                    item { Column(Modifier.padding(horizontal = Editorial.pageMargin).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        EditorialSectionHeader("植物文化", "CULTURE NOTES")
                        Surface(color = Editorial.linen, shape = MaterialTheme.shapes.medium) {
                            Box {
                            Box(Modifier.matchParentSize()) { FloralDecoration(Modifier.align(Alignment.CenterEnd).size(108.dp)) }
                            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                KitIcon(R.drawable.wf_icon_book, modifier = Modifier.size(36.dp), tint = Editorial.captionBrown)
                                Text(if (state.editorial && BuildConfig.INTERNAL_TOOLS) "资料审阅 · 非种级认定\n" + CulturePolicy.notice(entry) else cultureReadingNotice(entry),
                                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            }
                        }
                        if (entry.speciesKey in CulturePolicy.citationPending) EditorialInfoCard("阅读提示", "相关引文暂不作为可引用内容，请以原作与可靠版本为准。")
                        EditorialSectionHeader("文化导读", "READING NOTES")
                        Text(entry.culturalDescription, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 27.sp))
                        Text(if (entry.verificationStatus == "model_draft_unverified") "CULTURE NOTES · 待核文化笔记" else "CULTURAL IMAGERY", style = Editorial.eyebrow, color = Editorial.captionBrown)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(entry.themeTitle, Modifier.weight(1f), style = Editorial.sectionTitle, color = Editorial.ink)
                            HorizontalDivider(Modifier.width(16.dp), color = Editorial.hairline)
                            if (entry.culturalSymbolism.isNotEmpty()) Text(entry.culturalSymbolism.joinToString(" · "),
                                Modifier.widthIn(max = 116.dp), style = MaterialTheme.typography.labelSmall, color = Editorial.captionBrown)
                        }
                    } }
                    // No works means no poetry block; prose/modern research is not labelled a poem.
                    items(CulturePolicy.readingWorks(entry)) { work ->
                        Column(Modifier.padding(horizontal = Editorial.pageMargin, vertical = 8.dp)) {
                            Surface(color = Editorial.paper, shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(.5.dp, Editorial.hairline)) {
                                Box {
                                Box(Modifier.matchParentSize()) { Image(painterResource(R.drawable.wf_texture_translucent_vellum), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = .04f) }
                                Box(Modifier.matchParentSize()) { FloralDecoration(Modifier.align(Alignment.BottomEnd).size(110.dp), .22f) }
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(if (entry.speciesKey in CulturePolicy.citationPending) "文献短摘 · 引文待核" else "作品短摘", style = MaterialTheme.typography.labelSmall, color = Editorial.captionBrown)
                                    work.excerpt?.let { Text(it, style = Editorial.chineseTitle.copy(fontSize = 22.sp, lineHeight = 34.sp),
                                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("culture-excerpt")) }
                                    Text(listOfNotNull(work.author, work.title?.let { "《$it》" }).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                                    }
                                    val readingArtwork: Any? = if (entry.scientificName.equals("Osmanthus fragrans", true)) R.drawable.wf_editorial_culture_banner else photo
                                    readingArtwork?.let { BotanicalPhoto(it, "文化阅读编辑配图（非鉴定证据）", Modifier.width(94.dp).height(126.dp).clip(MaterialTheme.shapes.small)) }
                                    }
                                    work.sourceType?.let { Text("文献类型 · ${sourceTypeLabel(it)}", style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown) }
                                    if (CulturePolicy.validUrl(work.sourceUrl)) WildflowerSecondaryButton("查看作品出处 ↗", { open(work.sourceUrl) })
                                }
                                }
                            }
                        }
                    }
                    item { Column(Modifier.padding(horizontal = Editorial.pageMargin, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        EditorialSectionHeader("阅读与出处", "SOURCE LEDGER")
                        entry.matchNote?.let { EditorialReadingNote("古名与现代物种 · 对应边界", it) }
                        entry.taxonomySource?.let { taxon ->
                            EditorialSectionHeader("文献与分类学来源", "SOURCE LEDGER")
                            taxon.organization?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            Text("古典文献中的植物名称可能与现代分类不同，请结合原作语境阅读。", style = MaterialTheme.typography.bodySmall)
                            if (CulturePolicy.validUrl(taxon.url)) WildflowerSecondaryButton("查看分类学来源 ↗", { open(taxon.url) })
                        } ?: EditorialInfoCard("植物名称的边界", "古名与现代物种不一定一一对应，请结合原作语境阅读。")
                        entry.citationScope?.let { Text(if (BuildConfig.INTERNAL_TOOLS) "引用范围 · $it" else "阅读范围 · 结合原作语境，理解植物意象。", style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown) }
                        entry.contentAttribution?.let { Text("内容署名 · $it", style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown) }
                        Text(if (BuildConfig.INTERNAL_TOOLS) "原始核验：${entry.verificationStatus}\n发布状态：${entry.publicationStatus}" else cultureReadingNotice(entry), style = MaterialTheme.typography.bodySmall)
                        Text("资料用于自然与文学探索，不替代植物鉴定。", style = MaterialTheme.typography.bodySmall)
                    } }
                    }
                    if (!state.editorial) item {
                        Column(Modifier.padding(horizontal = Editorial.pageMargin), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            HorizontalDivider(color = Editorial.hairline)
                            NatureExpressionPanel(state, onGenerate, onConfigure, onStyle)
                            WildflowerSecondaryButton("返回植物笔记", onBack, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

/** Both entry routes share a compact identity/photo block; no full-height empty illustration. */
@Composable
private fun CultureIntroduction(state: CultureUiState, photo: Any?, caption: String) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(Editorial.pageMargin).testTag("culture-introduction")) {
        val narrow = maxWidth < 330.dp
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 18.dp)) {
            Box(Modifier.width(if (narrow) 88.dp else 110.dp).height(if (narrow) 106.dp else 128.dp).clip(RoundedCornerShape(14.dp))) {
                if (photo != null) BotanicalPhoto(photo, caption, Modifier.fillMaxSize())
                else BotanicalEmptyArtwork(R.drawable.wf_empty_culture, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("BOTANICAL CULTURE", style = Editorial.eyebrow, color = Editorial.captionBrown)
                Text(state.entry?.chineseName ?: state.chineseName?.takeIf(String::isNotBlank) ?: "一次自然相遇",
                    style = Editorial.chineseTitle.copy(fontSize = if (narrow) 24.sp else 28.sp, lineHeight = 34.sp),
                    color = MaterialTheme.colorScheme.primary)
                (state.scientificName ?: state.entry?.scientificName)?.takeIf(String::isNotBlank)?.let { ScientificNameLabel(it) }
                if (!state.identity.certain) Text("身份待确认 · 自然观察档案", style = MaterialTheme.typography.labelSmall, color = Editorial.captionBrown)
                Text(caption, style = MaterialTheme.typography.labelSmall.copy(lineHeight = 17.sp), color = Editorial.captionBrown)
            }
        }
    }
}

/** Display labels only; the supplied sourceType and all original content remain unchanged in DTO/assets. */
private fun sourceTypeLabel(type: String): String = when (type) {
    "primary_text_or_historical_anthology" -> "古典作品 / 历史文集"
    "historical_text_index" -> "历史文献索引"
    "historical_anthology" -> "历史文集"
    "historical_plant_text" -> "历史植物文献"
    "historical_prose" -> "古典散文"
    "historical_text_or_modern_research_page" -> "历史文献 / 现代研究"
    else -> "文献资料"
}

@HiltViewModel
class CultureReviewViewModel @Inject constructor(repository: CultureRepository) : ViewModel() {
    val entries = MutableStateFlow<List<CultureEntry>?>(null)
    val error = MutableStateFlow<String?>(null)
    init { viewModelScope.launch { try { entries.value = repository.editorialEntries() } catch (_: Exception) { error.value = "资料审阅目录读取失败。" } } }
}

@Composable
fun CultureReviewScreen(onBack: () -> Unit, onEntry: (String) -> Unit, viewModel: CultureReviewViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("all") }
    if (!BuildConfig.DEBUG || !BuildConfig.INTERNAL_TOOLS) { EditorialError("此页面暂不可用，请返回植物文化。"); return }
    Column(Modifier.fillMaxSize()) {
        WildflowerTopBar("资料审阅", onBack)
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("culture-review-list"), contentPadding = PaddingValues(Editorial.pageMargin), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Text("${entries?.size ?: "…"} 份文化阅读笔记", style = Editorial.chineseTitle, color = MaterialTheme.colorScheme.primary)
                val counts = entries.orEmpty().groupingBy { it.verificationStatus }.eachCount()
                Text("${counts["source_checked"] ?: 0} 来源审查稿 / ${counts["literary_source_checked_taxon_pending"] ?: 0} 物种待核 / ${counts["model_draft_unverified"] ?: 0} 未核草稿\n${entries.orEmpty().count(CulturePolicy::readable)} 条已获发布批准；候选学名不等于古名鉴定。", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "全部", "source_checked" to "来源审查稿", "literary_source_checked_taxon_pending" to "物种待核", "model_draft_unverified" to "未核草稿").forEach { (key, label) ->
                        FilterChip(filter == key, { filter = key }, { Text(label) })
                    }
                }
            }
            error?.let { item { EditorialError(it) } }
            if (entries == null && error == null) item { EditorialLoading() }
            items(entries.orEmpty().filter { filter == "all" || it.verificationStatus == filter }, key = { it.speciesKey }) { entry ->
                Column(Modifier.fillMaxWidth().clickable { onEntry(entry.speciesKey) }.testTag("review-${entry.speciesKey}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.chineseName, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    ScientificNameLabel(entry.scientificName)
                    Text(entry.themeTitle, style = MaterialTheme.typography.bodyMedium)
                    Text(CulturePolicy.notice(entry), style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown)
                    HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

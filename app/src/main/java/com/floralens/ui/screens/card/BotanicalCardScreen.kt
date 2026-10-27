package com.floralens.ui.screens.card

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.floralens.domain.card.CardTemplateId
import com.floralens.ui.components.*
import com.floralens.ui.theme.Editorial
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.floralens.R

@Composable
fun BotanicalCardScreen(onBack: () -> Unit, onAddObservation: () -> Unit, vm: BotanicalCardViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var picker by rememberSaveable { mutableStateOf(false) }
    var writing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    // The initial ViewModel collection already renders the selected observation.
    // Refresh only after a real pause/resume (for example returning from DocumentsUI),
    // otherwise first composition can clear an already-ready preview for one frame.
    var resumedAfterPause by remember { mutableStateOf(false) }
    val document = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png"), vm::documentResult)
    LifecycleResumeEffect(Unit) {
        if (resumedAfterPause) vm.refresh()
        onPauseOrDispose { resumedAfterPause = true }
    }
    LaunchedEffect(vm) {
        vm.event.collect { event -> when(event) {
            is CardEvent.CreateDocument -> runCatching { document.launch(event.name) }.onFailure { vm.documentResult(null); vm.feedback("系统文件选择器不可用，请重试") }
            is CardEvent.Share -> runCatching {
                context.startActivity(Intent.createChooser(vm.shareIntent(event.uri), "分享植物诗笺"))
                vm.feedback("已打开系统分享，请选择接收应用")
            }.onFailure { vm.feedback("无法打开系统分享，请重试") }
        } }
    }
    Box(Modifier.fillMaxSize().background(PoetryTokens.paper)) {
    Image(painterResource(R.drawable.wf_poetry_paper), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop, alpha = .38f)
    Column(Modifier.fillMaxSize().testTag("card-editor")) {
        PoetryTopBar(onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("card-editor-scroll")
            .padding(horizontal = PoetryTokens.margin).padding(top = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                !state.loaded -> EditorialLoading("正在读取观察照片…")
                !state.plantExists -> EditorialError("植物档案已删除，请返回花园。")
                state.photos.isEmpty() -> {
                    BotanicalEmptyArtwork(R.drawable.wf_empty_observations, Modifier.size(210.dp))
                    EditorialInfoCard("让下一次相遇成为作品", "这株植物还没有可读取的观察照片。添加一次真实观察后，就能制作诗笺。")
                    WildflowerPrimaryButton("添加观察", onAddObservation)
                }
                else -> {
                    PoetryHeaderBlock(state.plantName, state.scientificName)
                    Spacer(Modifier.height(20.dp))
                    CardPreview(state)
                    Spacer(Modifier.height(20.dp))
                    state.photos.firstOrNull { it.id == state.selectedId }?.let { observation ->
                        Text("本次观察 · ${observation.timestamp.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))}",
                            Modifier.testTag("card-selected-date"), style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), color = PoetryTokens.brown)
                    }
                    Spacer(Modifier.height(28.dp))
                    TemplatePicker(state.template, vm::template, !busy, state.templatePreviews)
                    Spacer(Modifier.height(24.dp))
                    state.error?.let { EditorialError(it); TextButton(vm::refresh) { Text("重新读取") } }
                    Surface(Modifier.widthIn(max = 520.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(enabled = !busy) { picker = true }.testTag("card-photo-picker"), color = PoetryTokens.card, shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            BotanicalPhoto(state.photos.firstOrNull { it.id == state.selectedId }?.photoUri, null, Modifier.width(72.dp).height(64.dp).clip(MaterialTheme.shapes.small))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("选择观察照片 · ${state.photos.size} 张", style = MaterialTheme.typography.titleSmall)
                                Text(state.photos.firstOrNull { it.id == state.selectedId }?.note?.takeIf(String::isNotBlank) ?: "选择一张照片，记录一次相遇", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            KitIcon(R.drawable.wf_icon_arrow_right, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(state.expressionSource, style = MaterialTheme.typography.labelSmall, color = Editorial.captionBrown, modifier = Modifier.testTag("card-expression-source"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton({ draft = vm.creativeTextForEditing(); writing = true }, enabled = !busy && state.preview != null, modifier = Modifier.testTag("card-edit-expression")) { Text("编辑诗笺文字") }
                        TextButton(vm::automaticText, enabled = !busy) { Text("自动选择文字") }
                    }
                    if (state.template == CardTemplateId.MODERN_JOURNAL) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(state.useNatureInJournal, vm::useNatureInJournal, enabled = !busy)
                        Text("附加创作文字，不覆盖观察备注", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                    if(state.template == CardTemplateId.CLASSICAL_POETRY && !state.hasCulture)
                        Text("用照片与留白，写下这次自然相遇。", style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown)
                    Text("1600 × 2400 · 高清 PNG", style = MaterialTheme.typography.labelSmall, color = Editorial.captionBrown)
                    Text("选择适合本次观察的表达方式，让照片与文字成为一份记忆。", style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown)
                    TextButton(vm::requestDocument, enabled = !busy && state.preview != null) { Text("另存到所选位置…") }
                }
            }
            message?.let { Text(it, Modifier.testTag("card-message"), color = MaterialTheme.colorScheme.primary) }
        }
        if(state.photos.isNotEmpty() && state.plantExists) BottomActionBar(!busy && state.preview != null, busy,
            { if(Build.VERSION.SDK_INT >= 29) vm.saveGallery() else vm.requestDocument() }, vm::share)
    }
    }
    if (writing) AlertDialog(onDismissRequest = { writing = false }, title = { Text("诗笺文字 · 用户自写") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("最多 120 字。仅修改当前作品，不写回观察记录。点击“自动选择文字”可恢复自动内容。")
            OutlinedTextField(draft, { draft = it }, modifier = Modifier.testTag("card-expression-input"), minLines = 3, label = { Text("创作文字") })
            if (draft.length > 120) Text("文字超过 120 字，请自行整理，原文不会截断。", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton({ vm.creativeText(draft); writing = false }, enabled = draft.length <= 120) { Text("应用到作品") } },
        dismissButton = { TextButton({ writing = false }) { Text("取消") } })
    if(picker) androidx.compose.ui.window.Dialog({ if(!busy) picker = false }) {
        Surface(color = Editorial.paper, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).padding(20.dp)) {
                Text("选择一次真实观察", style = MaterialTheme.typography.titleLarge)
                LazyColumn(Modifier.weight(1f, fill = false).testTag("card-photo-list"), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 20.dp)) {
                    items(state.photos, key = { it.id }) { observation ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { vm.select(observation.id); picker = false }.testTag("card-photo-${observation.id}"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BotanicalPhoto(observation.photoUri, "观察 ${observation.id}", Modifier.size(80.dp))
                            Column(Modifier.weight(1f)) {
                                Text(observation.timestamp.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy.MM.dd · HH:mm")))
                                observation.note?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                if(state.selectedId == observation.id) Text("已选择", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                TextButton({ picker = false }, enabled = !busy) { Text("取消") }
            }
        }
    }
}

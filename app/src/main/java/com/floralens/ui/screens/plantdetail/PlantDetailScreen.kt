package com.floralens.ui.screens.plantdetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.floralens.domain.model.ObservationType
import com.floralens.ui.components.*
import com.floralens.ui.theme.Editorial
import com.floralens.ui.theme.FloraLensTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PlantDetailScreen(onBack: () -> Unit, onLeafCheck: () -> Unit,
    onLearnMore: (String, String) -> Unit = { _, _ -> }, onAddObservation: (() -> Unit)? = null,
    onGallery: (() -> Unit)? = null, viewModel: PlantDetailViewModel = hiltViewModel(), onCulture: ((String, Long) -> Unit)? = null,
    onCard: (() -> Unit)? = null, onChat: (() -> Unit)? = null) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val error by viewModel.editError.collectAsStateWithLifecycle()
    var editingName by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    PersonalPlantContent(state, onBack, onLeafCheck, onLearnMore, viewModel::onMarkWatered, viewModel::onToggleReminders,
        onAddObservation, onGallery, { name = state.plant?.nickname.orEmpty(); editingName = true }, error,
        selectedObservationId = viewModel.selectedObservationId, onCard = onCard, onChat = onChat,
        cultureArea = onCulture?.let { open -> { state.plant?.let { plant ->
            com.floralens.ui.screens.culture.CultureLink(plant.speciesKey, { open(it, plant.id) })
        } } })
    if(editingName) AlertDialog(onDismissRequest = { editingName = false }, title = { Text("编辑植物昵称") },
        text = { OutlinedTextField(name, { name = it }, label = { Text("植物昵称") }, singleLine = true) },
        confirmButton = { TextButton({ viewModel.rename(name); editingName = false }, enabled = name.isNotBlank() && name.length <= 100) { Text("保存昵称") } },
        dismissButton = { TextButton({ editingName = false }) { Text("取消") } })
}

@Composable
fun PersonalPlantContent(state: PlantDetailUiState, onBack: () -> Unit, onLeafCheck: () -> Unit,
    onLearnMore: (String, String) -> Unit, onWatered: () -> Unit, onReminders: (Boolean) -> Unit,
    onAddObservation: (() -> Unit)? = null, onGallery: (() -> Unit)? = null, onRename: (() -> Unit)? = null, error: String? = null,
    selectedObservationId: Long = 0, cultureArea: (@Composable () -> Unit)? = null, onCard: (() -> Unit)? = null,
    onChat: (() -> Unit)? = null) {
    var previewId by rememberSaveable(state.plant?.id) { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFFF8F8F1))) {
        PersonalDetailTopBar(onBack)
        when {
            state.isLoading -> EditorialLoading("正在读取个人档案…")
            state.plant == null -> EditorialError("这株植物的档案已不存在。请返回花园。")
            else -> {
                val plant = state.plant
                // Saved relationship first; legacy Plant URI is the fallback, never the newest observation.
                val cover = state.history.firstOrNull { it.id == plant.coverObservationId }?.photoUri
                    ?.takeIf { it.isNotBlank() } ?: plant.photoUri
                val photographs = state.history.filter { !it.photoUri.isNullOrBlank() }
                BoxWithConstraints(Modifier.align(Alignment.CenterHorizontally).widthIn(max = 640.dp).fillMaxWidth().weight(1f)) {
                    val margin = if(maxWidth < 360.dp) 14.dp else 16.dp
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("personal-journal")
                        .padding(horizontal = margin).padding(top = 10.dp, bottom = 32.dp)) {
                        PersonalDetailCover(cover, plant.nickname)
                        Spacer(Modifier.height(20.dp))
                        PersonalDetailIdentity(plant)
                        error?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error) }
                        state.history.firstOrNull { it.id == selectedObservationId }?.let { observation ->
                            Spacer(Modifier.height(12.dp))
                            EditorialInfoCard("来自四季的本次观察", observation.timestamp.atZone(ZoneId.systemDefault()).format(historyFormat) + "\n" + observation.note.orEmpty())
                            if(!observation.photoUri.isNullOrBlank()) BotanicalPhoto(observation.photoUri, "四季中选中的观察照片", Modifier.fillMaxWidth().height(180.dp))
                        }
                        Spacer(Modifier.height(20.dp))
                        PersonalDetailActions(onAddObservation, onGallery)
                        Spacer(Modifier.height(20.dp))
                        PersonalDetailSlices(photographs, plant.coverObservationId, { previewId = it }, onAddObservation)
                        onChat?.let { open ->
                            Spacer(Modifier.height(16.dp))
                            com.floralens.ui.screens.chat.PlantChatEntryCard(
                                onClick = open,
                                modifier = Modifier.testTag("plant-chat-entry"),
                                subtitle = "从这株植物的档案出发，继续追问自然",
                            )
                        }
                        onCard?.let { create ->
                            Spacer(Modifier.height(16.dp))
                            PersonalDetailCreationEntry(create)
                        }
                        Spacer(Modifier.height(24.dp))
                        PersonalDetailHistoryHeading()
                        Spacer(Modifier.height(14.dp))
                        if(state.history.isEmpty()) EditorialInfoCard("静待下一次相遇", "这株植物还没有观察记录。")
                        val firstId = state.history.filter { it.type == ObservationType.IDENTIFIED }.minOfOrNull { it.id }
                        state.history.forEachIndexed { index, observation ->
                            key(observation.id) {
                                PersonalDetailObservation(observation, observation.id == firstId, index == 0,
                                    if(!observation.photoUri.isNullOrBlank()) ({ previewId = observation.id }) else null)
                                Spacer(Modifier.height(20.dp))
                            }
                        }
                        onRename?.let { TextButton(it) { Text("编辑昵称") } }
                        WildflowerSecondaryButton("认识这个物种  →", { onLearnMore(plant.scientificName, plant.commonName) }, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        cultureArea?.invoke()
                        Spacer(Modifier.height(24.dp))
                        EditorialSectionHeader("日常养护", "CARE  /  次级工具")
                        Spacer(Modifier.height(12.dp))
                        Surface(color = Editorial.paper, shape = MaterialTheme.shapes.large) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                state.status?.let { StatusChip(it) }
                                Text("浇水间隔：${plant.waterIntervalDays} 天", style = MaterialTheme.typography.bodyMedium)
                                Text("下次计划：${state.nextDue?.format(dateFormat) ?: "尚未设置"}", style = MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("养护提醒", Modifier.weight(1f))
                                    Switch(plant.remindersEnabled, onReminders)
                                }
                                WildflowerPrimaryButton("记录本次浇水", onWatered, Modifier.fillMaxWidth())
                                WildflowerSecondaryButton("叶片健康检查", onLeafCheck, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
    state.history.firstOrNull { it.id == previewId && !it.photoUri.isNullOrBlank() }?.let { observation ->
        PersonalDetailPhotoPreview(observation, { previewId = null }, onGallery?.let { { previewId = null; it() } })
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd")
private val historyFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd · HH:mm")

@Preview(showBackground = true, widthDp = 393)
@Composable
private fun MissingPlantPreview() = FloraLensTheme { PersonalPlantContent(PlantDetailUiState(isLoading = false), {}, {}, { _, _ -> }, {}, {}) }

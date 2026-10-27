package com.floralens.ui.screens.identify
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.floralens.domain.model.*
import com.floralens.ui.components.*
import com.floralens.ui.theme.FloraLensTheme
import com.floralens.ui.theme.Editorial
import com.floralens.R
import com.floralens.ui.presentation.PlantDisplayNames

/** Candidate selection is reading only, never a database write. */
@Composable
fun IdentifyResultSheet(result: IdResult, capturedImage: ByteArray?, isSaving: Boolean,
    onAdd: (IdCandidate) -> Unit, onAddManually: (String) -> Unit, onRetake: () -> Unit,
    modifier: Modifier = Modifier, photoUri: String? = null, onChat: (IdCandidate) -> Unit = {}) {
    val candidates = when(result) { is IdResult.HighConfidence -> listOf(result.best) + result.alternates; is IdResult.LowConfidence -> result.possibilities; else -> emptyList() }
    var selected by remember(result) { mutableIntStateOf(0) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    FloraLensTheme {
        BottomSheet(true, { if(!isSaving) onRetake() }, modifier) {
            Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BotanicalPhoto(photoUri ?: capturedImage, "本次拍摄照片", Modifier.fillMaxWidth().height(152.dp).clip(MaterialTheme.shapes.medium))
                Text("IDENTIFICATION / 一次发现", style = Editorial.eyebrow, color = Editorial.captionBrown)
                Text(if(result is IdResult.LowConfidence) "还不确定，可能是这些植物" else "本次识别结果", style = Editorial.sectionTitle.copy(fontSize = 22.sp))
                Text("认识眼前的植物，从一次发现开始。识别结果仅供参考。", style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown)
                candidates.forEachIndexed { index, candidate ->
                    Surface(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(enabled = !isSaving) { selected = index },
                        color = if (selected == index) Editorial.sage else Editorial.paper, shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, if (selected == index) MaterialTheme.colorScheme.primary else Editorial.hairline)) {
                      Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(PlantDisplayNames.chinese(candidate.scientificName, candidate.commonName), style = MaterialTheme.typography.titleMedium)
                            ScientificNameLabel(candidate.scientificName)
                        }
                        Text(PlantDisplayNames.confidence(candidate.confidence) + if(selected == index) " ✓" else "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                      }
                    }
                }
                Text("来源：" + if(candidates.firstOrNull()?.source == IdentificationSource.PLANTNET) "Pl@ntNet 在线识别" else "本机模型", style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown)
                Text(candidates.getOrNull(selected)?.let { PlantDisplayNames.find(it.scientificName)?.classification } ?: "可信度仅反映本次照片的识别结果", style = MaterialTheme.typography.bodySmall)
                Button({ candidates.getOrNull(selected)?.let(onAdd) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(28.dp), enabled = !isSaving && candidates.isNotEmpty()) { Text("查看植物详情") }
                candidates.getOrNull(selected)?.let { candidate ->
                    com.floralens.ui.screens.chat.PlantChatEntryCard(
                        onClick = { if (!isSaving) onChat(candidate) },
                        modifier = Modifier.fillMaxWidth().testTag("identification-chat-entry"),
                        subtitle = "从这次发现出发，聊聊自然与文化",
                    )
                }
                WildflowerSecondaryButton("重新拍摄", onRetake, Modifier.fillMaxWidth(), iconResource = R.drawable.wf_icon_refresh)
                if(!manual) TextButton({ manual = true }, Modifier.fillMaxWidth()) { Text("我认识它，手动填写名称") }
                else {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("植物名称") }, singleLine = true, enabled = !isSaving)
                    Text("手动名称不会被当作已核实的学名，也不附加模型置信度。", style = MaterialTheme.typography.bodySmall)
                    Button({ onAddManually(name.trim()) }, Modifier.fillMaxWidth(), enabled = name.isNotBlank() && name.length <= 100 && !isSaving) { Text("按手动名称查看详情") }
                }
            }
        }
    }
}

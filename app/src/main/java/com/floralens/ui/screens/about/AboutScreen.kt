package com.floralens.ui.screens.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.floralens.BuildConfig
import com.floralens.data.settings.SettingsRepository
import com.floralens.domain.model.IdentificationMode
import com.floralens.ui.components.IdentificationModePicker
import com.floralens.ui.theme.Spacing
import com.floralens.ui.theme.Editorial
import com.floralens.ui.components.EditorialSectionHeader
import com.floralens.ui.components.KitIcon
import com.floralens.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AboutUiState(val identificationMode: IdentificationMode = IdentificationMode.DEFAULT,
    val mapPrivacy: com.floralens.data.settings.MapPrivacyChoice = com.floralens.data.settings.MapPrivacyChoice.PENDING)

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val mapPrivacy: com.floralens.data.settings.BaiduMapPrivacy,
) : ViewModel() {
    val uiState: StateFlow<AboutUiState> = kotlinx.coroutines.flow.combine(settings.identificationMode, mapPrivacy.choice) { mode, choice -> AboutUiState(mode, choice) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AboutUiState())

    fun setIdentificationMode(mode: IdentificationMode) =
        viewModelScope.launch { settings.setIdentificationMode(mode) }
    fun setMapPrivacy(choice: com.floralens.data.settings.MapPrivacyChoice) = mapPrivacy.choose(choice)
}

/** App information, usage guidance, identification mode and map privacy settings. */
@Composable
fun AboutScreen(onBack: () -> Unit, viewModel: AboutViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Editorial.readingWidth).fillMaxSize()) {
            com.floralens.ui.components.WildflowerTopBar("设置与帮助", onBack)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Surface(color = Editorial.linen, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            KitIcon(R.drawable.wf_icon_leaf, Modifier.size(32.dp))
                            Column(Modifier.weight(1f)) {
                                Text("草木知时", style = Editorial.sectionTitle, color = Editorial.forest)
                                Text("Wildflower Lens", style = Editorial.scientific, color = Editorial.captionBrown)
                            }
                        }
                        Text("认识身边的植物，记录四季的变化。\n用照片与文字，留住每一次自然相遇。",
                            style = MaterialTheme.typography.bodyMedium, color = Editorial.bodyInk)
                        Text("版本 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = Editorial.muted)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EditorialSectionHeader("识别模式", "RECOGNITION")
                    Text("随时切换，下次识别时生效。", style = MaterialTheme.typography.bodySmall, color = Editorial.muted)
                    IdentificationModePicker(uiState.identificationMode, viewModel::setIdentificationMode)
                }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    EditorialSectionHeader("隐私与授权", "YOUR CHOICES")
                    HelpRow(Icons.Filled.Lock, "隐私与识别模式",
                        "默认使用 Pl@ntNet 在线识别，会发送本次识别照片；可切换为不上传照片的本机离线模式。AI 原创与花间问答分别征求同意；问答会发送植物名称、你的问题和必要的近期对话，但不会主动附加照片、位置、日期或私人观察备注。")
                    HorizontalDivider(color = Editorial.hairline, thickness = .5.dp)
                    com.floralens.ui.screens.map.BaiduMapPrivacyControl(uiState.mapPrivacy, viewModel::setMapPrivacy)
                }
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    EditorialSectionHeader("使用指南", "FIELD GUIDE")
                    HelpRow(Icons.Filled.CameraAlt, "拍照识别", "将镜头对准植物，点击快门查看结果。不确定时会展示候选项，也可以手动填写名称。")
                    HelpRow(Icons.Filled.Spa, "我的花园", "保存的植物、照片和历史记录都在这里。点击圆形照片打开个人档案。")
                    HelpRow(Icons.Filled.WaterDrop, "养护提醒", "从花园进入养护页面，查看浇水计划、记录本次浇水。提醒状态按每株植物的间隔计算。")
                    HelpRow(Icons.Filled.Eco, "叶片检查", "在个人档案里拍摄叶片，获得颜色变化提示。这不是植物病害诊断。")
                    HelpRow(Icons.Filled.Map, "观察地图", "允许位置权限后，可记录识别时的坐标。位置权限可选，拒绝不会影响其他功能。")
                }
                com.floralens.ui.components.EditorialReadingNote("结果与内容边界",
                    "识别可信度只反映本次照片的候选结果，并非百分之百准确。叶片检查依据照片颜色提供观察提示，不是病害诊断；AI 辅助内容会明确标注，重要信息仍需结合可靠资料核实。")
                Text("个人植物档案与观察照片保存在本机。", Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelSmall, color = Editorial.muted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
@Composable
private fun HelpRow(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).background(Editorial.linen, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Editorial.forest, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

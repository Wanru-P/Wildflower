package com.floralens.ui.screens.culture

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.floralens.domain.culture.*
import com.floralens.ui.components.*
import com.floralens.ui.theme.Editorial
import com.floralens.BuildConfig

@Composable
fun NatureExpressionPanel(state: CultureUiState, generate: () -> Unit, configure: () -> Unit, style: (String) -> Unit) {
    val text = state.expression ?: NatureTextPolicy.local(state.identity)
    var reading by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("nature-expression"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EditorialSectionHeader("AI 原创", "NATURE EXPRESSION")
        Text(text.source.label, style = MaterialTheme.typography.labelSmall, color = Editorial.captionBrown, modifier = Modifier.testTag("nature-source"))
        if (text.source != ExpressionSource.AI) Text("尚未生成 · 选择一种文风，为这次相遇写一段现代原创。", Modifier.testTag("nature-guidance"), style = MaterialTheme.typography.bodyMedium, color = Editorial.captionBrown)
        Text(text.text, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 27.sp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("nature-text"))
        WildflowerSecondaryButton("阅读自然文字 →", { reading = true })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.style == "quiet", { style("quiet") }, { Text("静谧短诗") }, enabled = !state.generating)
            FilterChip(state.style == "journal", { style("journal") }, { Text("自然感悟") }, enabled = !state.generating)
        }
        Text(if (state.identity.canGenerate) "仅发送已确认的植物名称与文风，不上传照片、位置或观察备注。"
            else "通用自然创作 · 不发送昵称或候选学名，不确认植物身份。", style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown)
        Button(generate, enabled = !state.loading && !state.generating, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("nature-generate"),
            colors = ButtonDefaults.buttonColors(containerColor = Editorial.sage, contentColor = MaterialTheme.colorScheme.primary)) {
            if (state.generating) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
            Text(if (state.generating) "正在创作…" else if (state.generationMessage != null) "重试 AI 创作" else if (text.source == ExpressionSource.AI) "重新生成 AI 原创" else "生成 AI 原创短诗")
        }
        if (BuildConfig.DEBUG) TextButton(configure, Modifier.testTag("nature-configure")) { Text("创作服务设置") }
        state.generationMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("nature-message")) }
    }
    if (reading) AlertDialog(onDismissRequest = { reading = false }, title = { Text("自然文字") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text.source.label, style = MaterialTheme.typography.labelMedium)
            Text(text.text, style = MaterialTheme.typography.bodyLarge)
            Text(if (text.source == ExpressionSource.AI) "现代 AI 原创，不是古人作品，不作为植物特征或观测经历的证据。" else "自然观察引导，不是古典文献，也不描述你已经完成的观察。", style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { TextButton({ reading = false }) { Text("返回植物文化") } })
}

@Composable
fun NatureServiceDialog(close: () -> Unit, save: (String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") } // Never SavedState, never logs, never provider key.
    var message by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = close, title = { Text("安全创作服务") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("填写已部署代理的 HTTPS 地址及专用访问凭证。不要填写 DeepSeek API Key；供应商密钥只能存放在服务端。")
            OutlinedTextField(url, { url = it }, label = { Text("代理地址") }, modifier = Modifier.testTag("nature-proxy-url"), singleLine = true)
            OutlinedTextField(token, { token = it }, label = { Text("代理访问凭证") }, modifier = Modifier.testTag("nature-proxy-token"), visualTransformation = PasswordVisualTransformation(), singleLine = true)
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton({
        try { save(url.trim(), token.trim()); token = ""; close() }
        catch (_: Exception) { message = "请检查代理 HTTPS 地址和访问凭证。不可填写 DeepSeek 官方地址或 API Key。" }
    }) { Text("保存配置") } }, dismissButton = { TextButton(close) { Text("取消") } })
}

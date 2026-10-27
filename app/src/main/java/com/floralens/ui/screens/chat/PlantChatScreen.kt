package com.floralens.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.floralens.R
import com.floralens.domain.chat.PlantChatContext
import com.floralens.domain.chat.PlantChatMessage
import com.floralens.domain.chat.PlantChatPolicy
import com.floralens.domain.chat.PlantChatRole
import com.floralens.ui.components.BotanicalPhoto
import com.floralens.ui.components.EditorialError
import com.floralens.ui.components.EditorialLoading
import com.floralens.ui.components.KitIcon
import com.floralens.ui.components.ScientificNameLabel
import com.floralens.ui.components.WildflowerTopBar
import com.floralens.ui.presentation.PlantDisplayNames
import com.floralens.ui.theme.Editorial
import com.floralens.BuildConfig
import androidx.compose.foundation.text.selection.SelectionContainer

@Composable
fun PlantChatScreen(onBack: () -> Unit, viewModel: PlantChatViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draft by rememberSaveable(state.context?.stableKey) { mutableStateOf("") }
    PlantChatContent(
        state = state,
        draft = draft,
        onDraft = { if (it.length <= PlantChatPolicy.MAX_QUESTION) draft = it },
        onBack = onBack,
        onAsk = { question -> viewModel.ask(question); if (PlantChatPolicy.validQuestion(question)) draft = "" },
        onSuggestion = { question -> viewModel.ask(question) },
        onRetry = viewModel::retry,
        onCancel = viewModel::cancelReply,
        onClear = viewModel::clearSession,
        onPrivacy = viewModel::showPrivacy,
        onConfigure = viewModel::showConfiguration,
    )
    if (state.showConsent) ConsentDialog(viewModel::acceptAndAsk, viewModel::dismissConsent)
    if (state.showPrivacy) PrivacyDialog(viewModel::revokeConsent, viewModel::dismissPrivacy)
    if (state.showConfiguration) ConfigurationDialog(viewModel::saveConfiguration, viewModel::dismissConfiguration)
}

@Composable
fun PlantChatContent(
    state: PlantChatUiState,
    draft: String,
    onDraft: (String) -> Unit,
    onBack: () -> Unit,
    onAsk: (String) -> Unit,
    onSuggestion: (String) -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    onPrivacy: () -> Unit,
    onConfigure: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.isReplying, state.error) {
        if (state.messages.isNotEmpty()) {
            delay(40)
            val last = listState.layoutInfo.totalItemsCount - 1
            if (last >= 0) listState.animateScrollToItem(last)
        }
    }
    Box(Modifier.fillMaxSize().background(Editorial.paper), contentAlignment = Alignment.TopCenter) {
    Scaffold(
        // Resize the conversation and composer together, without padding the
        // Surface/bottom-bar measurement a second time when the IME opens.
        modifier = Modifier.widthIn(max = Editorial.readingWidth).fillMaxSize().imePadding(),
        containerColor = Editorial.paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            WildflowerTopBar("花间问答", onBack, actions = {
                Box {
                    IconButton({ menu = true }, Modifier.size(48.dp).semantics { contentDescription = "问答设置" }) {
                        Icon(Icons.Default.MoreVert, null, tint = Editorial.forest)
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("清空本次对话") }, { menu = false; onClear() })
                        DropdownMenuItem({ Text("隐私与授权") }, { menu = false; onPrivacy() })
                        if (BuildConfig.DEBUG) DropdownMenuItem({ Text("安全服务设置") }, { menu = false; onConfigure() })
                    }
                }
            })
        },
        bottomBar = {
            ChatComposer(draft, onDraft, { onAsk(draft) }, state.isReplying, onCancel,
                enabled = state.context != null && !state.loadingContext,
                Modifier)
        },
    ) { padding ->
        when {
            state.loadingContext -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { EditorialLoading("正在读取植物上下文…") }
            state.context == null -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                EditorialError(state.error ?: "植物上下文已经失效。")
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).testTag("plant-chat-list"),
                contentPadding = PaddingValues(horizontal = if (LocalDensity.current.fontScale > 1.2f) 14.dp else 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item("context") { ChatContextCard(state.context) }
                state.context.identityNotice?.let { notice -> item("identity-notice") { IdentityNotice(notice) } }
                state.context.approvedReference?.let { reference -> item("approved-reference") {
                    Surface(modifier = Modifier.fillMaxWidth(), color = Editorial.linen.copy(alpha = .55f), shape = RoundedCornerShape(16.dp), border = BorderStroke(.5.dp, Editorial.hairline)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("已审核文献摘录", style = MaterialTheme.typography.labelMedium, color = Editorial.forest)
                            Text("${reference.author}《${reference.title}》", style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown)
                            SelectionContainer { Text(reference.excerpt, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                } }
                if (state.messages.isEmpty()) item("empty") { ChatEmptyState(state.context.identity.certain, onSuggestion) }
                items(state.messages, key = { it.id }) { message -> ChatBubble(message) }
                if (state.isReplying) item("loading") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Editorial.forest)
                        Text("正在整理回答…", style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown)
                    }
                }
                state.error?.let { message -> item("error") {
                    Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .55f), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(message, style = MaterialTheme.typography.bodySmall)
                            Row {
                                if (state.retryQuestion != null) TextButton(onRetry, Modifier.heightIn(min = 48.dp)) { Text("重试") }
                                if (BuildConfig.DEBUG && !state.configured) TextButton(onConfigure, Modifier.heightIn(min = 48.dp)) { Text("配置安全服务") }
                            }
                        }
                    }
                } }
                item("bottom-space") { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
    }
}

@Composable
private fun ChatContextCard(context: PlantChatContext) {
    val displayName = PlantDisplayNames.chinese(context.scientificName, context.displayName)
    Surface(color = Editorial.sage.copy(alpha = .46f), shape = RoundedCornerShape(18.dp), border = BorderStroke(.5.dp, Editorial.hairline)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotanicalPhoto(context.imageUri, "${displayName}的上下文图片", Modifier.size(66.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(displayName, style = MaterialTheme.typography.titleMedium, color = Editorial.forest,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                context.scientificName?.let { ScientificNameLabel(it) }
                val details = buildList {
                    add(context.source.label)
                    context.confidence?.let { add(PlantDisplayNames.confidence(it)) }
                }.joinToString(" · ")
                Text(details, style = MaterialTheme.typography.labelSmall, color = Editorial.captionBrown)
            }
            KitIcon(R.drawable.ic_chat_sprout, Modifier.size(28.dp), "植物上下文")
        }
    }
}

@Composable private fun IdentityNotice(text: String) {
    Surface(color = Editorial.linen, shape = RoundedCornerShape(14.dp)) {
        Text(text, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall, color = Editorial.captionBrown)
    }
}

@Composable private fun ChatEmptyState(certain: Boolean, onSuggestion: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("从这株植物出发", style = Editorial.sectionTitle.copy(fontSize = 20.sp, lineHeight = 28.sp), color = Editorial.forest)
                Text(if (certain) "聊聊观察方法、自然变化与文化。" else "身份还不确定，可以先聊观察与辨认方法。",
                    style = MaterialTheme.typography.bodySmall, color = Editorial.muted)
            }
            KitIcon(R.drawable.il_chat_empty_botanical, Modifier.size(width = 48.dp, height = 58.dp), tint = Editorial.botanical)
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlantChatPolicy.suggestions(certain).chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { suggestion -> SuggestionChip(suggestion, { onSuggestion(suggestion) }, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable private fun SuggestionChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = "提问：$text", onClick = onClick),
        color = Editorial.sage.copy(alpha = .22f), shape = RoundedCornerShape(14.dp), border = BorderStroke(.5.dp, Editorial.hairline)) {
        Box(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Default), color = Editorial.forest, textAlign = TextAlign.Center)
        }
    }
}

@Composable private fun ChatBubble(message: PlantChatMessage) {
    val user = message.role == PlantChatRole.USER
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).testTag(if (user) "chat-user-message" else "chat-ai-message"),
            color = if (user) Editorial.sage else Editorial.paper,
            border = if (user) null else BorderStroke(.7.dp, Editorial.hairline),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (user) 20.dp else 6.dp, bottomEnd = if (user) 6.dp else 20.dp),
        ) {
            SelectionContainer { Text(message.text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Default, lineHeight = 24.sp), color = Editorial.bodyInk) }
        }
        if (!user) Text("AI 生成 · 仅供参考", Modifier.padding(start = 8.dp, top = 4.dp), style = MaterialTheme.typography.labelSmall, color = Editorial.captionBrown)
    }
}

@Composable private fun ChatComposer(value: String, onValue: (String) -> Unit, onSend: () -> Unit, replying: Boolean, onCancel: () -> Unit,
    enabled: Boolean, modifier: Modifier = Modifier) {
    val canSend = enabled && (replying || value.isNotBlank())
    Surface(modifier = modifier.fillMaxWidth(), color = Editorial.paper) {
        Column {
        HorizontalDivider(thickness = .5.dp, color = Editorial.hairline)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value, onValue, Modifier.weight(1f).heightIn(min = 52.dp, max = 124.dp).testTag("chat-input"),
                placeholder = { Text("继续问这株植物……") }, enabled = enabled && !replying, maxLines = 4,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Editorial.forest,
                    unfocusedBorderColor = Editorial.hairline, focusedContainerColor = Editorial.paper,
                    unfocusedContainerColor = Editorial.linen.copy(alpha = .4f)),
                shape = RoundedCornerShape(26.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (value.isNotBlank()) onSend() }))
            IconButton(onClick = if (replying) onCancel else onSend,
                enabled = canSend,
                modifier = Modifier.size(52.dp).background(if (canSend) Editorial.forest else Editorial.linen, CircleShape).testTag("chat-send")
                    .semantics { contentDescription = if (replying) "停止回答" else "发送问题" }) {
                if (replying) Icon(Icons.Default.Close, null, tint = Editorial.paper)
                else Icon(painterResource(R.drawable.ic_send_leaf), null, tint = if (canSend) Editorial.paper else Editorial.muted, modifier = Modifier.size(25.dp))
            }
        }
        }
    }
}

@Composable private fun ConsentDialog(accept: () -> Unit, cancel: () -> Unit) {
    AlertDialog(onDismissRequest = cancel, icon = { KitIcon(R.drawable.ic_chat_privacy, Modifier.size(34.dp)) }, title = { Text("使用花间问答") },
        text = { Text("发送当前植物名称、你输入的问题和必要的本次对话内容，以生成回答。不会主动上传照片、观察地点、日期和私人备注。请勿在问题中填写敏感信息。AI 回答可能不准确，古典引文以标明出处且经过审核的资料为准。") },
        confirmButton = { Button(accept, Modifier.heightIn(min = 48.dp)) { Text("同意并提问") } },
        dismissButton = { TextButton(cancel, Modifier.heightIn(min = 48.dp)) { Text("取消") } })
}

@Composable private fun PrivacyDialog(revoke: () -> Unit, close: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text("隐私与授权") },
        text = { Text("问答只在当前页面保留；退出后不会写入植物档案或诗意表达缓存。请求仅包含受控植物名称、你的问题和最近有限轮对话，不会自动附加照片、位置、日期或观察备注。") },
        confirmButton = { TextButton(revoke, Modifier.heightIn(min = 48.dp)) { Text("撤回问答授权") } },
        dismissButton = { TextButton(close, Modifier.heightIn(min = 48.dp)) { Text("关闭") } })
}

@Composable private fun ConfigurationDialog(save: (String, String) -> Unit, close: () -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("安全问答服务") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("仅填写项目部署的 HTTPS 代理地址和可撤销访问凭证，不要填写供应商 API Key。", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("代理地址") }, singleLine = true)
            OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("服务访问凭证") }, singleLine = true)
        } },
        confirmButton = { Button({ save(url, token) }, enabled = url.isNotBlank() && token.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) { Text("保存") } },
        dismissButton = { TextButton(close, Modifier.heightIn(min = 48.dp)) { Text("取消") } })
}

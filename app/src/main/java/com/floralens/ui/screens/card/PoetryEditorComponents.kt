package com.floralens.ui.screens.card

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.floralens.R
import com.floralens.domain.card.CardTemplateId
import com.floralens.ui.components.KitIcon
import com.floralens.ui.theme.BotanicalSerif
import com.floralens.ui.theme.Lora

internal object PoetryTokens {
    val paper = Color(0xFFF7F4EE)
    val card = Color(0xFFFBFAF6)
    val ink = Color(0xFF234F36)
    val brown = Color(0xFF9B8068)
    val line = Color(0xFFD8CFBF)
    val border = Color(0xFFCBBFAE)
    val selected = Color(0xFFE9F0E5)
    val action = Color(0xFF1F5A37)
    val margin = 24.dp
    val title = TextStyle(fontFamily = BotanicalSerif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 35.sp)
}

@Composable
internal fun PoetryTopBar(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 56.dp), contentAlignment = Alignment.Center) {
        IconButton(onBack, Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) {
            KitIcon(R.drawable.wf_icon_back, description = "返回", modifier = Modifier.size(24.dp), tint = PoetryTokens.ink)
        }
        Text("植物诗笺", style = PoetryTokens.title.copy(fontSize = 28.sp), color = PoetryTokens.ink,
            modifier = Modifier.padding(horizontal = 64.dp, vertical = 8.dp))
    }
}

@Composable
internal fun PoetryHeaderBlock(name: String, scientific: String) {
    Box(Modifier.fillMaxWidth()) {
        // Decoration must not make the header 145dp tall or insert a false gap before the artwork.
        Box(Modifier.matchParentSize()) {
            Image(painterResource(R.drawable.wf_poetry_sprig), null,
                Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = 32.dp).width(105.dp).height(130.dp),
                alpha = .55f, contentScale = ContentScale.Crop, alignment = Alignment.CenterEnd)
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = PoetryTokens.title, color = PoetryTokens.ink)
            Text(scientific.ifBlank { "学名未知" }, style = TextStyle(fontFamily = Lora, fontStyle = FontStyle.Italic,
                fontSize = 19.sp, lineHeight = 25.sp), color = Color(0xFF4E5A4D))
        }
    }
}

@Composable
fun CardPreview(state: CardUiState) {
    Surface(Modifier.widthIn(max = 260.dp).fillMaxWidth().aspectRatio(2f/3f).testTag("card-preview"),
        color = PoetryTokens.card, border = BorderStroke(1.dp, Color(0xFFD9D0C4)),
        shadowElevation = 8.dp) {
        state.preview?.let { Image(it.asImageBitmap(), "${state.template.title}预览", contentScale = ContentScale.Fit) }
            ?: Box(contentAlignment = Alignment.Center) {
                Text(if (state.error == null) "正在排版…" else "请重新选择观察", color = PoetryTokens.brown)
            }
    }
}

@Composable
private fun TemplateSectionHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("THREE WAYS TO REMEMBER", style = TextStyle(fontSize = 12.sp, letterSpacing = 1.sp),
            color = Color(0xFF8F725B))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("选择版式", style = PoetryTokens.title, color = PoetryTokens.ink)
            HorizontalDivider(Modifier.weight(1f), color = PoetryTokens.line)
        }
    }
}

@Composable
fun TemplatePicker(selected: CardTemplateId, onSelected: (CardTemplateId) -> Unit, enabled: Boolean,
    previews: Map<CardTemplateId, Bitmap> = emptyMap()) {
    BoxWithConstraints(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
    val narrow = maxWidth < 300.dp
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TemplateSectionHeader()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTemplateId.entries.forEach { type ->
                TemplateOptionCard(type, type == selected, enabled, previews[type], { onSelected(type) }, Modifier.weight(1f), narrow)
            }
        }
    }
    }
}

@Composable
private fun TemplateOptionCard(type: CardTemplateId, selected: Boolean, enabled: Boolean, bitmap: Bitmap?,
    onClick: () -> Unit, modifier: Modifier, narrow: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    Surface(modifier, color = if (selected) PoetryTokens.selected else PoetryTokens.card,
        shape = shape, border = BorderStroke(if(selected) 2.dp else 1.dp,
            if(selected) Color(0xFF2E6142) else PoetryTokens.border)) {
        Column(Modifier.testTag("template-${type.name}").selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f/3f), contentAlignment = Alignment.Center) {
                bitmap?.let { Image(it.asImageBitmap(), "${type.title}缩略预览", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                    ?: Text("预览待生成", fontSize = 11.sp, color = PoetryTokens.brown)
            }
            Text(type.title, Modifier.heightIn(min = if(narrow) 40.dp else 20.dp), style = TextStyle(fontFamily = BotanicalSerif,
                fontSize = if(narrow) 13.sp else 15.sp, lineHeight = 20.sp), color = PoetryTokens.ink)
            Text(if(selected) "已选择" else "点击选择", fontSize = 12.sp, lineHeight = 17.sp, color = PoetryTokens.brown)
        }
    }
}

@Composable
internal fun BottomActionBar(enabled: Boolean, busy: Boolean, onSave: () -> Unit, onShare: () -> Unit) {
    Surface(color = PoetryTokens.card) {
        Row(Modifier.fillMaxWidth().padding(horizontal = PoetryTokens.margin, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onSave, Modifier.weight(1f).heightIn(min = 58.dp).testTag("card-save"), enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = PoetryTokens.action, contentColor = Color.White),
                shape = RoundedCornerShape(30.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                KitIcon(R.drawable.wf_icon_download, modifier = Modifier.size(20.dp), tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text(if(busy) "处理中…" else "保存 PNG", fontSize = 15.sp)
            }
            OutlinedButton(onShare, Modifier.weight(1f).heightIn(min = 58.dp).testTag("card-share"), enabled = enabled,
                border = BorderStroke(1.5.dp, PoetryTokens.action), colors = ButtonDefaults.outlinedButtonColors(contentColor = PoetryTokens.action),
                shape = RoundedCornerShape(30.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                KitIcon(R.drawable.wf_poetry_icon_share, modifier = Modifier.size(20.dp), tint = PoetryTokens.action)
                Spacer(Modifier.width(6.dp))
                Text("系统分享", fontSize = 15.sp)
            }
        }
    }
}

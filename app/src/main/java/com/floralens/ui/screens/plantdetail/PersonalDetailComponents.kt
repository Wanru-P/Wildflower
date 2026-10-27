package com.floralens.ui.screens.plantdetail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.floralens.R
import com.floralens.domain.model.Observation
import com.floralens.domain.model.ObservationType
import com.floralens.domain.model.Plant
import com.floralens.ui.components.*
import com.floralens.ui.theme.Editorial
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Adapted from the supplied compose_starters, using existing Editorial families/tokens.
// Local furniture only: no sample records, global theme changes or duplicate data models.
private val DetailSurface = Color(0xFFFEFDF8)
private val DetailBrown = Color(0xFF92785E)
private val DetailHairline = Color(0xFFDDD5C7)
private val DetailBody = Color(0xFF343C35)
private val dateLabel = DateTimeFormatter.ofPattern("yyyy.MM.dd")
private val fullDateLabel = DateTimeFormatter.ofPattern("yyyy.MM.dd · HH:mm")
private fun Observation.localTime() = timestamp.atZone(ZoneId.systemDefault())

@Composable internal fun PersonalDetailTopBar(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack, Modifier.size(48.dp)) { KitIcon(R.drawable.wf_pd_icon_back, Modifier.size(22.dp), "返回") }
        Text("我的植物档案", Modifier.weight(1f), style = Editorial.sectionTitle.copy(fontSize = 23.sp, lineHeight = 30.sp),
            textAlign = TextAlign.Center, color = Editorial.forest)
        Spacer(Modifier.width(48.dp))
    }
}

@Composable internal fun PersonalDetailCover(uri: String?, nickname: String) {
    Box(Modifier.fillMaxWidth().aspectRatio(1.9f).clip(RoundedCornerShape(18.dp)).testTag("personal-cover")) {
        BotanicalPhoto(uri, "$nickname 的植物封面", Modifier.fillMaxSize())
        Text("PERSONAL HERBARIUM", Modifier.align(Alignment.BottomStart).padding(12.dp)
            .background(Editorial.forest.copy(alpha = .78f), CircleShape)
            .border(.5.dp, Color.White.copy(alpha = .4f), CircleShape).padding(horizontal = 10.dp, vertical = 6.dp),
            style = Editorial.eyebrow.copy(fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.sp), color = Color.White)
    }
}

@Composable internal fun PersonalDetailIdentity(plant: Plant) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if(maxWidth >= 330.dp && LocalDensity.current.fontScale <= 1.2f && plant.scientificName.length <= 28) {
            Box(Modifier.matchParentSize()) {
                FloralDecoration(Modifier.align(Alignment.CenterEnd).size(94.dp), .20f, R.drawable.wf_pd_osmanthus_sprig)
            }
        }
        Column(Modifier.fillMaxWidth().padding(end = 24.dp)) {
            Text(plant.nickname.ifBlank { "未命名植物" }, style = Editorial.chineseTitle.copy(fontSize = 28.sp, lineHeight = 35.sp),
                maxLines = 3, overflow = TextOverflow.Ellipsis, color = Editorial.forest)
            Spacer(Modifier.height(6.dp))
            Text(plant.scientificName.ifBlank { "学名未知 · 未核实" }, style = if(plant.scientificName.isBlank()) MaterialTheme.typography.bodyMedium else Editorial.scientific.copy(fontSize = 17.sp, lineHeight = 23.sp), color = DetailBody)
            if(plant.commonName.isNotBlank() && plant.commonName != plant.nickname) {
                Spacer(Modifier.height(5.dp)); Text(com.floralens.ui.presentation.PlantDisplayNames.chinese(plant.scientificName, plant.commonName), style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 21.sp), color = DetailBody)
            }
            Spacer(Modifier.height(8.dp))
            Text("首次收藏 · ${plant.createdAt.atZone(ZoneId.systemDefault()).format(dateLabel)}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp), color = DetailBrown)
        }
    }
}

@Composable internal fun PersonalDetailActions(add: (() -> Unit)?, gallery: (() -> Unit)?) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 280.dp || LocalDensity.current.fontScale > 1.2f
        if(stacked) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            add?.let { DetailAction("添加观察", R.drawable.wf_pd_icon_add, true, it, Modifier.fillMaxWidth()) }
            gallery?.let { DetailAction("查看全部照片", R.drawable.wf_pd_icon_gallery, false, it, Modifier.fillMaxWidth()) }
        } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            add?.let { DetailAction("添加观察", R.drawable.wf_pd_icon_add, true, it, Modifier.weight(1f)) }
            gallery?.let { DetailAction("查看全部照片", R.drawable.wf_pd_icon_gallery, false, it, Modifier.weight(1f)) }
        }
    }
}

@Composable private fun DetailAction(title: String, icon: Int, primary: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Row(modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp))
        .background(if(primary) Editorial.forest else Color.Transparent)
        .border(if(primary) 0.dp else 1.2.dp, Editorial.forest, RoundedCornerShape(28.dp))
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        KitIcon(icon, Modifier.size(21.dp), tint = if(primary) Color.White else Editorial.forest)
        Spacer(Modifier.width(7.dp))
        Text(title, style = MaterialTheme.typography.bodySmall.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
            color = if(primary) Color.White else Editorial.forest)
    }
}

@Composable internal fun PersonalDetailSlices(photos: List<Observation>, coverId: Long?, onPhoto: (Long) -> Unit, add: (() -> Unit)?) {
    Column(Modifier.testTag("personal-slices")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("相遇的切片", style = Editorial.sectionTitle.copy(fontSize = 22.sp, lineHeight = 28.sp), color = Editorial.forest)
            HorizontalDivider(Modifier.weight(1f), thickness = .5.dp, color = DetailHairline)
            Text("${photos.size} 张归档照片", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp), color = DetailBrown)
        }
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DetailSurface)) {
            Image(painterResource(R.drawable.wf_pd_vellum), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop, alpha = .04f)
            if(photos.isEmpty()) Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("还没有观察照片", style = MaterialTheme.typography.bodyMedium, color = Editorial.forest)
                Text("添加一次观察，保存这株植物的变化。", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = DetailBrown)
                add?.let { TextButton(it, Modifier.padding(top = 4.dp)) { Text("添加第一张照片") } }
            } else {
                val photoWidth = ((maxWidth - 36.dp) / 3).coerceAtLeast(1.dp)
                LazyRow(Modifier.fillMaxWidth().testTag("personal-photo-strip"), contentPadding = PaddingValues(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(photos, key = { it.id }) { observation ->
                        Column(Modifier.width(photoWidth).clip(RoundedCornerShape(10.dp)).testTag("personal-slice-${observation.id}")
                            .clickable(role = Role.Button) { onPhoto(observation.id) }) {
                            Box {
                                BotanicalPhoto(observation.photoUri, "${observation.localTime().format(dateLabel)} 观察照片${if(observation.id == coverId) "，当前封面" else ""}",
                                    Modifier.fillMaxWidth().height(98.dp).clip(RoundedCornerShape(10.dp)))
                                if(observation.id == coverId) Text("封面", Modifier.align(Alignment.TopStart).padding(5.dp)
                                    .background(Editorial.forest.copy(alpha = .8f), CircleShape).padding(horizontal = 6.dp, vertical = 2.dp).testTag("personal-slice-cover-${observation.id}"),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color.White)
                            }
                            Text(observation.localTime().format(dateLabel), Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.Center,
                                style = Editorial.scientific.copy(fontSize = 11.sp, lineHeight = 16.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Normal), color = DetailBrown)
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun PersonalDetailCreationEntry(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Editorial.sage)
        .border(if(pressed) 1.2.dp else .5.dp, if(pressed) Editorial.forest else Color(0xFFBCC8B7).copy(alpha = .4f), RoundedCornerShape(18.dp))
        .testTag("botanical-creation-entry").clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)) {
        // The decoration must not participate in measurement: its 106dp artwork previously
        // inflated the 76dp action card. Keep its dimensions purely in the background layer.
        Box(Modifier.matchParentSize()) {
            FloralDecoration(Modifier.align(Alignment.CenterEnd).size(106.dp), .3f, R.drawable.wf_pd_osmanthus_ghost)
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(if(pressed) Editorial.botanical else Color(0xFFC9D9C6), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                KitIcon(R.drawable.wf_pd_icon_edit, Modifier.size(24.dp), tint = if(pressed) Color.White else Editorial.forest)
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.width(.5.dp).height(30.dp).background(Editorial.forest.copy(alpha = .35f)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("制作植物诗笺", style = Editorial.sectionTitle.copy(fontSize = 18.sp, lineHeight = 25.sp), color = Editorial.forest)
                Text("选一张照片，留下一页自然", Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp), color = DetailBrown)
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(44.dp).background(DetailSurface, CircleShape), contentAlignment = Alignment.Center) {
                KitIcon(R.drawable.wf_pd_icon_arrow_right, Modifier.size(22.dp))
            }
        }
    }
}

@Composable internal fun PersonalDetailHistoryHeading() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("OBSERVATIONS", style = Editorial.eyebrow, color = DetailBrown)
            HorizontalDivider(Modifier.weight(1f), thickness = .5.dp, color = DetailHairline)
        }
        Text("我的观察", style = Editorial.sectionTitle.copy(fontSize = 23.sp, lineHeight = 30.sp), color = Editorial.forest)
    }
}

@Composable internal fun PersonalDetailObservation(observation: Observation, firstIdentification: Boolean, decorate: Boolean, onPhoto: (() -> Unit)?) {
    val time = observation.localTime()
    Row(Modifier.fillMaxWidth().testTag("journal-observation-${observation.id}"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(66.dp)) {
            if(decorate) FloralDecoration(Modifier.size(74.dp).padding(top = 16.dp), .28f, R.drawable.wf_pd_leaf_ghost)
            Column {
                Text(time.format(DateTimeFormatter.ofPattern("MM.dd")), style = Editorial.brand.copy(fontSize = 22.sp, lineHeight = 29.sp), color = Editorial.forest)
                Text(time.format(DateTimeFormatter.ofPattern("yyyy")), style = Editorial.eyebrow.copy(letterSpacing = .4.sp), color = DetailBrown)
                Box(Modifier.padding(start = 22.dp, top = 12.dp).width(1.dp).height(if(onPhoto != null) 130.dp else 36.dp).background(DetailHairline))
            }
        }
        Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(DetailSurface)) {
            if(!observation.photoUri.isNullOrBlank()) {
                BotanicalPhoto(observation.photoUri, "${time.format(fullDateLabel)} 观察照片", Modifier.padding(8.dp).fillMaxWidth().height(140.dp)
                    .clip(RoundedCornerShape(10.dp)).then(if(onPhoto != null) Modifier.clickable(role = Role.Button, onClick = onPhoto) else Modifier))
            }
            Column(Modifier.padding(horizontal = 12.dp).padding(top = if(onPhoto != null) 0.dp else 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(when(observation.type) {
                    ObservationType.IDENTIFIED -> if(firstIdentification) "首次识别" else "识别记录"
                    ObservationType.WATERED -> "浇水记录"
                    ObservationType.NOTE -> "观察笔记"
                    ObservationType.LEAF_CHECK -> "叶片健康检查"
                    ObservationType.PHOTO_OBSERVATION -> "照片观察（未重新识别）"
                }, style = Editorial.sectionTitle.copy(fontSize = 16.sp, lineHeight = 23.sp), color = Editorial.forest)
                observation.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 21.sp), color = DetailBody) }
                Text(time.format(fullDateLabel), style = MaterialTheme.typography.labelSmall, color = DetailBrown)
            }
        }
    }
}

/** Read-only preview; cover/edit/delete remain owned by the existing gallery/use cases. */
@Composable internal fun PersonalDetailPhotoPreview(observation: Observation, close: () -> Unit, gallery: (() -> Unit)?) {
    Dialog(close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 620.dp).fillMaxWidth().fillMaxHeight(.92f), color = DetailSurface, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BotanicalPhoto(observation.photoUri, "${observation.localTime().format(fullDateLabel)} 观察照片", Modifier.fillMaxWidth().aspectRatio(1.2f).clip(RoundedCornerShape(12.dp)))
                Text(observation.localTime().format(fullDateLabel), style = Editorial.sectionTitle.copy(fontSize = 20.sp), color = Editorial.forest)
                Text(if(observation.type == ObservationType.IDENTIFIED) "识别记录 · 置信度仅属于此次事件" else "照片观察 · 未重新识别",
                    style = MaterialTheme.typography.bodySmall, color = DetailBrown)
                observation.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                gallery?.let { WildflowerSecondaryButton("在照片集中管理", it, Modifier.fillMaxWidth()) }
                TextButton(close, Modifier.fillMaxWidth()) { Text("关闭照片") }
            }
        }
    }
}

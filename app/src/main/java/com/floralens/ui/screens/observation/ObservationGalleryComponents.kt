package com.floralens.ui.screens.observation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.floralens.R
import com.floralens.domain.model.Observation
import com.floralens.ui.components.BotanicalPhoto
import com.floralens.ui.theme.BotanicalSerif
import com.floralens.ui.theme.Editorial
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Local kit tokens: do not restyle the other accepted screens. */
internal object GalleryVisuals {
    val cream = Color(0xFFFBF9F2)
    val forest = Color(0xFF214D33)
    val brown = Color(0xFF8D7B6B)
    val metadata = Color(0xFF8D6F55)
    val divider = Color(0xFFE6DFD3)
    val body = Color(0xFF6F675E)
    val card = Color(0xFFFFFEFA)
    val date = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    val time = DateTimeFormatter.ofPattern("HH:mm")
}

@Composable
fun ObservationGalleryContent(
    state: GalleryState,
    onBack: () -> Unit,
    onAddObservation: () -> Unit,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    Box(modifier.fillMaxSize().background(GalleryVisuals.cream)) {
        Image(painterResource(R.drawable.wf_paper_ivory), null,
            Modifier.matchParentSize(), contentScale = ContentScale.Crop, alpha = .12f)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxWidth < 360.dp
            val margin = if (compact) 12.dp else 16.dp
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(top = 12.dp).height(56.dp)) {
                    IconButton(onBack, Modifier.align(Alignment.CenterStart).padding(start = 4.dp).size(48.dp)) {
                        Icon(painterResource(R.drawable.ic_wf_back), "返回", Modifier.size(24.dp), tint = GalleryVisuals.forest)
                    }
                    Text("观察照片集", Modifier.align(Alignment.Center),
                        style = Editorial.sectionTitle.copy(fontSize = if (compact) 24.sp else 26.sp, lineHeight = 34.sp),
                        color = GalleryVisuals.forest)
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().weight(1f).testTag("observation-gallery-grid"),
                    contentPadding = PaddingValues(start = margin, end = margin, top = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "heading") {
                        GalleryHeader(state, compact)
                    }
                    message?.let { text -> item(span = { GridItemSpan(maxLineSpan) }, key = "message") {
                        Text(text, color = GalleryVisuals.forest, style = MaterialTheme.typography.bodySmall)
                    } }
                    if (state.photos.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                            Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Image(painterResource(R.drawable.wf_osmanthus_divider), null,
                                    Modifier.width(220.dp).height(90.dp), contentScale = ContentScale.Fit, alpha = .55f)
                                Text("还没有观察照片", style = Editorial.sectionTitle, color = GalleryVisuals.forest)
                                Text("添加一次观察，把植物的变化留在这里。", style = MaterialTheme.typography.bodySmall,
                                    color = GalleryVisuals.body)
                                Button(onAddObservation, Modifier.heightIn(min = 52.dp).testTag("gallery-add-observation"),
                                    shape = RoundedCornerShape(28.dp), colors = ButtonDefaults.buttonColors(containerColor = GalleryVisuals.forest)) {
                                    Text("添加观察", Modifier.padding(horizontal = 20.dp), color = Color.White)
                                }
                            }
                        }
                    }
                    items(state.photos, key = { it.id }) { observation ->
                        ObservationPhotoCard(observation, state.plant?.coverObservationId == observation.id,
                            compact, { onSelect(observation.id) }, { onSelect(observation.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryHeader(state: GalleryState, compact: Boolean) {
    Box(Modifier.fillMaxWidth().padding(bottom = 4.dp).testTag("gallery-heading")) {
        // Decoration must follow the measured heading, never create empty space under it.
        Box(Modifier.matchParentSize()) {
            Image(painterResource(R.drawable.wf_osmanthus_watermark_corner), null,
                Modifier.align(Alignment.TopEnd).width(if (compact) 120.dp else 146.dp).aspectRatio(1.5f),
                contentScale = ContentScale.Fit, alpha = .16f)
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PHOTO ARCHIVE / 观察切片", style = Editorial.eyebrow.copy(fontSize = 11.sp, letterSpacing = 1.1.sp), color = GalleryVisuals.brown)
            state.plant?.let {
                Text(it.nickname, style = Editorial.chineseTitle.copy(fontSize = if (compact) 28.sp else 30.sp,
                    lineHeight = if (compact) 36.sp else 39.sp, fontWeight = FontWeight.SemiBold),
                    color = GalleryVisuals.forest, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("${state.photos.size} 张照片 · 按观察时间排序", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = Color(0xFF9A8068))
        }
    }
}

/** Adapted from the supplied compose_starters; controlled by actual Observation IDs. */
@Composable
internal fun ObservationPhotoCard(
    observation: Observation,
    isCover: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    val at = observation.timestamp.atZone(ZoneId.systemDefault())
    val fontScale = LocalDensity.current.fontScale
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("gallery-card-${observation.id}")
        .semantics { contentDescription = "查看观察 ${observation.id}" },
        shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, GalleryVisuals.divider),
        colors = CardDefaults.cardColors(containerColor = GalleryVisuals.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.padding(8.dp)) {
            Box {
                BotanicalPhoto(observation.photoUri, null,
                    Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(14.dp)).testTag("gallery-photo-${observation.id}"))
                if (isCover) Surface(Modifier.align(Alignment.TopStart).padding(8.dp).testTag("gallery-cover-${observation.id}"),
                    color = GalleryVisuals.forest, shape = RoundedCornerShape(999.dp)) {
                    Text("当前封面", Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color.White, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Column(Modifier.padding(end = 48.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(at.format(GalleryVisuals.date), style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = if (compact) 13.sp else 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
                        color = GalleryVisuals.metadata)
                    Text(at.format(GalleryVisuals.time), style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = if (compact) 12.sp else 13.sp, lineHeight = 18.sp), color = GalleryVisuals.metadata)
                }
                IconButton(onMore, Modifier.align(Alignment.BottomEnd).size(48.dp)) {
                    Icon(painterResource(R.drawable.ic_wf_more), "更多观察操作 ${observation.id}",
                        Modifier.size(24.dp).offset(y = 7.dp), tint = GalleryVisuals.brown)
                }
            }
            HorizontalDivider(color = GalleryVisuals.divider)
            // Keep a three-line reading slot, not unlimited notes or a stretched paragraph.
            Box(Modifier.fillMaxWidth().heightIn(min = (if (compact) 57 else 60).dp * fontScale + 8.dp).padding(top = 8.dp)) {
                observation.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, Modifier.testTag("gallery-note-${observation.id}"),
                        fontFamily = BotanicalSerif, fontSize = 13.sp,
                        lineHeight = if (compact) 19.sp else 20.sp, maxLines = 3,
                        overflow = TextOverflow.Ellipsis, color = GalleryVisuals.body)
                }
            }
        }
    }
}

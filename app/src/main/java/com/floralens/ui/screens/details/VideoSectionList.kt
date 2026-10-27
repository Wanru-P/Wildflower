package com.floralens.ui.screens.details

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.floralens.domain.model.PlantVideo
import com.floralens.domain.model.VideoSection
import com.floralens.ui.theme.Spacing

/**
 * Renders the YouTube video sections. Each is a titled horizontal row of
 * cards; tapping a card launches the YouTube app (falling back to a browser),
 * rather than embedding playback — robust, and avoids broken-embed states.
 */
@Composable
fun VideoSectionList(
    sections: List<VideoSection>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var launchError by rememberSaveable { mutableStateOf<String?>(null) }

    fun openVideo(video: PlantVideo) {
        launchError = null
        // Try the YouTube app first (vnd.youtube), fall back to the watch URL.
        val appIntent = Intent(Intent.ACTION_VIEW, "vnd.youtube:${video.videoId}".toUri())
        val webIntent = Intent(Intent.ACTION_VIEW, video.watchUrl.toUri())
        val launched = runCatching { context.startActivity(appIntent) }.isSuccess
        if (!launched) runCatching { context.startActivity(webIntent) }
            .onFailure { launchError = "暂时无法打开视频，请确认已安装浏览器或视频应用。" }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        launchError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(section.title, style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    items(section.videos) { video ->
                        VideoCard(video = video, onClick = { openVideo(video) })
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCard(video: PlantVideo, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(240.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            // Play glyph over the thumbnail.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayCircle,
                    contentDescription = "在 YouTube 中播放",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Text(
            video.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            video.channelTitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

package com.antigravity.videoplayer.tv.ui.screen.browse

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import com.antigravity.videoplayer.core.model.VideoMediaItem
import com.antigravity.videoplayer.tv.viewmodel.TvBrowseViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvBrowseScreen(
    viewModel: TvBrowseViewModel,
    onVideoClick: (VideoMediaItem) -> Unit
) {
    val videos by viewModel.videos.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp)
    ) {
        Text(
            text = "Video Library",
            style = MaterialTheme.typography.displayMedium,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        TvLazyRow(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(end = 48.dp)
        ) {
            items(videos) { video ->
                VideoCard(video = video, onClick = { onVideoClick(video) })
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoCard(video: VideoMediaItem, onClick: () -> Unit) {
    StandardCardContainer(
        imageCard = {
            Card(
                onClick = onClick,
                modifier = Modifier.size(width = 300.dp, height = 180.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(text = "▶", style = MaterialTheme.typography.displaySmall)
                }
            }
        },
        title = {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        },
        subtitle = {
            Text(
                text = "1080p • MKV",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    )
}

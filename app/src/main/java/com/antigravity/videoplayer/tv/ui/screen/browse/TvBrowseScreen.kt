package com.antigravity.videoplayer.tv.ui.screen.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import com.antigravity.videoplayer.core.model.VideoMediaItem
import com.antigravity.videoplayer.tv.viewmodel.TvBrowseViewModel

@Composable
fun TvBrowseScreen(
    viewModel: TvBrowseViewModel,
    onVideoClick: (VideoMediaItem) -> Unit
) {
    val videos by viewModel.videos.collectAsState()
    val rowFocusRequester = remember { FocusRequester() }

    LaunchedEffect(videos.isNotEmpty()) {
        if (videos.isNotEmpty()) {
            rowFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
            .padding(horizontal = 56.dp, vertical = 48.dp)
    ) {
        Text(
            text = "Video Library",
            style = MaterialTheme.typography.displayMedium,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(36.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            contentPadding = PaddingValues(end = 56.dp)
        ) {
            itemsIndexed(videos) { index, video ->
                VideoCard(
                    video = video,
                    focusRequester = rowFocusRequester.takeIf { index == 0 },
                    onClick = { onVideoClick(video) }
                )
            }
        }
    }
}

@Composable
private fun VideoCard(
    video: VideoMediaItem,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val focusModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }

    Column(
        modifier = Modifier.size(width = 320.dp, height = 280.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF242424)),
            shape = shape,
            modifier = Modifier
                .size(width = 320.dp, height = 190.dp)
                .then(focusModifier)
                .clip(shape)
                .border(
                    width = if (isFocused) 4.dp else 1.dp,
                    color = if (isFocused) Color.White else Color(0xFF424242),
                    shape = shape
                )
                .onFocusChanged { isFocused = it.isFocused }
                .focusable()
                .clickable(onClick = onClick)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = video.thumbnailUri ?: video.uri,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Text(
            text = video.title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp)
        )

        if (video.description.isNotBlank() || video.folderName.isNotBlank()) {
            Text(
                text = video.description.ifBlank { video.folderName },
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFBDBDBD)
            )
        }
    }
}

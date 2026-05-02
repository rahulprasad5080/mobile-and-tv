package com.mplayer.videoplayer.tv.ui.screen.browse

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mplayer.videoplayer.R
import com.mplayer.videoplayer.core.model.VideoMediaItem
import com.mplayer.videoplayer.tv.viewmodel.TvBrowseViewModel

private val TvBackground = Color(0xFF080A0F)
private val ShelfTitle = Color(0xFFF4F6FA)
private val MutedText = Color(0xFFAFB6C4)
private val FocusBlue = Color(0xFF4DA3FF)

@Composable
fun TvBrowseScreen(
    viewModel: TvBrowseViewModel,
    onVideoClick: (VideoMediaItem) -> Unit
) {
    val videos by viewModel.videos.collectAsState()
    val firstItemFocusRequester = remember { FocusRequester() }
    val shelves = remember(videos) { buildShelves(videos) }

    LaunchedEffect(videos.isNotEmpty()) {
        if (videos.isNotEmpty()) {
            firstItemFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF17223B), Color.Transparent)
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 72.dp, top = 56.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(34.dp)
        ) {
            item {
                TvHomeHeader(videoCount = videos.size)
            }

            if (videos.isEmpty()) {
                item {
                    EmptyLibraryMessage()
                }
            } else {
                items(
                    items = shelves,
                    key = { it.title }
                ) { shelf ->
                    VideoShelf(
                        title = shelf.title,
                        videos = shelf.videos,
                        firstItemFocusRequester = firstItemFocusRequester.takeIf { shelf.isFirstShelf },
                        onVideoClick = onVideoClick
                    )
                }
            }
        }
    }
}

@Composable
private fun TvHomeHeader(videoCount: Int) {
    Column {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (videoCount == 1) "1 video ready to play" else "$videoCount videos ready to play",
            style = MaterialTheme.typography.titleLarge,
            color = MutedText
        )
    }
}

@Composable
private fun EmptyLibraryMessage() {
    Surface(
        color = Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        modifier = Modifier
            .width(620.dp)
            .padding(top = 28.dp)
    ) {
        Text(
            text = "No videos found",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            modifier = Modifier.padding(32.dp)
        )
    }
}

@Composable
private fun VideoShelf(
    title: String,
    videos: List<VideoMediaItem>,
    firstItemFocusRequester: FocusRequester?,
    onVideoClick: (VideoMediaItem) -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = ShelfTitle,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 18.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(end = 72.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            itemsIndexed(
                items = videos,
                key = { _, video -> video.id }
            ) { index, video ->
                TvVideoCard(
                    video = video,
                    focusRequester = firstItemFocusRequester.takeIf { index == 0 },
                    onClick = { onVideoClick(video) }
                )
            }
        }
    }
}

@Composable
private fun TvVideoCard(
    video: VideoMediaItem,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "tv-card-scale"
    )
    val shape = RoundedCornerShape(14.dp)
    val requesterModifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier

    Column(
        modifier = Modifier
            .width(300.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151923)),
            shape = shape,
            modifier = Modifier
                .size(width = 300.dp, height = 170.dp)
                .then(requesterModifier)
                .shadow(
                    elevation = if (isFocused) 22.dp else 0.dp,
                    shape = shape,
                    ambientColor = FocusBlue,
                    spotColor = FocusBlue
                )
                .clip(shape)
                .border(
                    width = if (isFocused) 3.dp else 1.dp,
                    color = if (isFocused) FocusBlue else Color.White.copy(alpha = 0.16f),
                    shape = shape
                )
                .onFocusChanged { isFocused = it.isFocused }
                .focusable()
                .clickable(onClick = onClick)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = video.thumbnailUri ?: video.uri,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f))
                            )
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = video.title,
            style = MaterialTheme.typography.titleLarge,
            color = if (isFocused) Color.White else Color(0xFFE2E6EF),
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = video.folderName.ifBlank { "Videos" },
            style = MaterialTheme.typography.bodyLarge,
            color = MutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class VideoShelfModel(
    val title: String,
    val videos: List<VideoMediaItem>,
    val isFirstShelf: Boolean = false
)

private fun buildShelves(videos: List<VideoMediaItem>): List<VideoShelfModel> {
    if (videos.isEmpty()) return emptyList()

    val shelves = mutableListOf<VideoShelfModel>()
    shelves += VideoShelfModel(title = "All Videos", videos = videos, isFirstShelf = true)

    videos
        .groupBy { it.folderName.ifBlank { "Videos" } }
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)
        .forEach { (folderName, folderVideos) ->
            if (folderVideos.isNotEmpty() && folderVideos.size != videos.size) {
                shelves += VideoShelfModel(title = folderName, videos = folderVideos)
            }
        }

    return shelves
}

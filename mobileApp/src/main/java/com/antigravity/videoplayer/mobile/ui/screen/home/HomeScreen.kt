package com.antigravity.videoplayer.mobile.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.videoplayer.core.model.VideoMediaItem
import com.antigravity.videoplayer.mobile.viewmodel.HomeViewModel

private val DarkBackground = Color(0xFF0F1517)
private val SurfaceColor = Color(0xFF1B2428)
private val PrimaryBlue = Color(0xFF2196F3)
private val FolderGrey = Color(0xFF455A64)
private val BadgeRed = Color(0xFFF44336)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onVideoClick: (VideoMediaItem) -> Unit,
    onFolderClick: (String, List<VideoMediaItem>) -> Unit
) {
    val groupedVideos by viewModel.groupedVideos.collectAsState()
    val lastPlayedVideo by viewModel.lastPlayedVideo.collectAsState()

    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Folders",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                },

                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        floatingActionButton = {
            if (lastPlayedVideo != null) {
                FloatingActionButton(
                    onClick = { onVideoClick(lastPlayedVideo!!) },
                    containerColor = PrimaryBlue,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Resume Last Play",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            

            Spacer(modifier = Modifier.height(8.dp))

            // Folder List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                groupedVideos.forEach { (folderName, videos) ->
                    item {
                        FolderItem(
                            name = folderName,
                            videoCount = videos.size,
                            badgeCount = if (folderName == "Download") 2 else if (folderName == "Twitter") 1 else 0,
                            isTrash = folderName == "Trash",
                            onClick = { onFolderClick(folderName, videos) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryChip(category: CategoryItem) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)),
        color = SurfaceColor,
        onClick = { /* Select category */ }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                category.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = category.name,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun FolderItem(
    name: String,
    videoCount: Int,
    badgeCount: Int = 0,
    isTrash: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = FolderGrey,
                modifier = Modifier.size(56.dp)
            )
            if (badgeCount > 0) {
                Surface(
                    color = BadgeRed,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .offset(x = 4.dp, y = (-4).dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = badgeCount.toString(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(20.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = if (isTrash) Color(0xFF4FC3F7) else Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                text = "$videoCount videos",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

data class CategoryItem(val name: String, val icon: ImageVector)


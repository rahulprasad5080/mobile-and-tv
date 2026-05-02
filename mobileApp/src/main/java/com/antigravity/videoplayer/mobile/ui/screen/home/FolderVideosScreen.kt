package com.antigravity.videoplayer.mobile.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.antigravity.videoplayer.core.model.VideoMediaItem

private val DarkBackground = Color(0xFF0F1517)
private val SurfaceColor = Color(0xFF1B2428)
private val PrimaryBlue = Color(0xFF2196F3)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderVideosScreen(
    folderName: String,
    videos: List<VideoMediaItem>,
    onVideoClick: (VideoMediaItem) -> Unit,
    onRename: (VideoMediaItem, String) -> Unit,
    onDelete: (VideoMediaItem) -> Unit,
    onCopy: (VideoMediaItem, String) -> Unit,
    onBackPressed: () -> Unit
) {
    var videoToRename by remember { mutableStateOf<VideoMediaItem?>(null) }
    var videoToDelete by remember { mutableStateOf<VideoMediaItem?>(null) }
    var newName by remember { mutableStateOf("") }
    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        folderName,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(videos) { video ->
                VideoListItem(
                    video = video,
                    onClick = { onVideoClick(video) },
                    onRename = { videoToRename = video; newName = video.title },
                    onDelete = { videoToDelete = video },
                    onCopy = { onCopy(video, "Copy of ${video.title}") }
                )
            }
        }
    }

    // Rename Dialog
    if (videoToRename != null) {
        AlertDialog(
            onDismissRequest = { videoToRename = null },
            title = { Text("Rename Video") },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(videoToRename!!, newName)
                    videoToRename = null
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (videoToDelete != null) {
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            title = { Text("Delete Video") },
            text = { Text("Are you sure you want to delete '${videoToDelete!!.title}'?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(videoToDelete!!)
                    videoToDelete = null
                }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun VideoListItem(
    video: VideoMediaItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = Color.Transparent
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(120.dp, 70.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceColor)
            ) {
                AsyncImage(
                    model = video.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                Text(
                    text = video.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = Color.White)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(SurfaceColor)
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename", color = Color.White) },
                        onClick = { showMenu = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color.White) },
                        onClick = { showMenu = false; onDelete() }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy", color = Color.White) },
                        onClick = { showMenu = false; onCopy() }
                    )
                }
            }
        }
    }
}

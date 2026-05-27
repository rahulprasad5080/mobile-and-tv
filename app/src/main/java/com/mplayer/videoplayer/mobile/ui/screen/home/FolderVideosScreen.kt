package com.mplayer.videoplayer.mobile.ui.screen.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mplayer.videoplayer.core.model.VideoMediaItem

enum class FileAction {
    Copy,
    Move
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderVideosScreen(
    folderName: String,
    videos: List<VideoMediaItem>,
    folderDestinations: List<Pair<String, String?>>,
    onVideoClick: (VideoMediaItem) -> Unit,
    onRename: (VideoMediaItem, String) -> Unit,
    onDelete: (VideoMediaItem) -> Unit,
    onDeleteSelected: (List<VideoMediaItem>) -> Unit,
    onCopy: (VideoMediaItem) -> Unit,
    onCopySelected: (List<VideoMediaItem>, String?) -> Unit,
    onMoveSelected: (List<VideoMediaItem>, String?) -> Unit,
    onPaste: () -> Unit,
    copiedVideo: VideoMediaItem?,
    onBackPressed: () -> Unit
) {
    var videoToRename by remember { mutableStateOf<VideoMediaItem?>(null) }
    var videoToDelete by remember { mutableStateOf<VideoMediaItem?>(null) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    // Show name without extension so user doesn't accidentally change/remove it
    var newName by remember { mutableStateOf("") }
    var selectedIds by remember(videos) { mutableStateOf(emptySet<String>()) }
    var pendingAction by remember { mutableStateOf<FileAction?>(null) }

    val selectedVideos = remember(selectedIds, videos) { videos.filter { it.id in selectedIds } }
    val selectionMode = selectedIds.isNotEmpty()
    val targetFolders = remember(folderDestinations, folderName) {
        folderDestinations
            .filter { (name, path) -> name != folderName && path != null }
            .distinctBy { it.second }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectionMode) "${selectedIds.size} selected" else folderName,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectionMode) {
                                selectedIds = emptySet()
                            } else {
                                onBackPressed()
                            }
                        }
                    ) {
                        Icon(
                            if (selectionMode) Icons.Default.Close else Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(
                            onClick = {
                                selectedIds = if (selectedIds.size == videos.size) {
                                    emptySet()
                                } else {
                                    videos.map { it.id }.toSet()
                                }
                            }
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select all", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (selectionMode) {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { pendingAction = FileAction.Move },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DriveFileMove, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Move")
                        }
                        Button(
                            onClick = { showBulkDeleteDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (copiedVideo != null && !selectionMode) {
                ExtendedFloatingActionButton(
                    text = { Text("Paste") },
                    icon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                    onClick = onPaste,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(
                items = videos,
                key = { it.id }
            ) { video ->
                val selected = video.id in selectedIds
                VideoListItem(
                    video = video,
                    selected = selected,
                    selectionMode = selectionMode,
                    onClick = {
                        if (selectionMode) {
                            selectedIds = selectedIds.toggle(video.id)
                        } else {
                            onVideoClick(video)
                        }
                    },
                    onLongClick = {
                        selectedIds = selectedIds.toggle(video.id)
                    },
                    onSelect = {
                        selectedIds = selectedIds.toggle(video.id)
                    },
                    onRename = {
                        videoToRename = video
                        // Show name without extension — repository adds it back on save
                        newName = video.title.substringBeforeLast('.').ifBlank { video.title }
                    },
                    onDelete = { videoToDelete = video },
                    onCopy = { onCopy(video) }
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
                    // Repository adds extension back — pass name without extension
                    val nameWithoutExt = newName.substringBeforeLast('.').ifBlank { newName }
                    onRename(videoToRename!!, nameWithoutExt)
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

    // Single Delete Dialog
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

    // Bulk Delete Dialog
    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text("Delete ${selectedIds.size} file(s)?") },
            text = { Text("Are you sure you want to permanently delete ${selectedIds.size} selected file(s)? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSelected(selectedVideos)
                    selectedIds = emptySet()
                    showBulkDeleteDialog = false
                }) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Copy/Move folder picker dialog
    if (pendingAction != null) {
        FolderDestinationDialog(
            action = pendingAction!!,
            folders = targetFolders,
            onFolderSelect = { targetPath ->
                when (pendingAction) {
                    FileAction.Copy -> onCopySelected(selectedVideos, targetPath)
                    FileAction.Move -> onMoveSelected(selectedVideos, targetPath)
                    null -> Unit
                }
                pendingAction = null
                selectedIds = emptySet()
            },
            onDismiss = { pendingAction = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListItem(
    video: VideoMediaItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(if (selected) 8.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onSelect() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .size(120.dp, 70.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                AsyncImage(
                    model = video.filePath ?: video.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                // Bug 7 fix: show duration in H:MM:SS
                if (video.duration > 0) {
                    Text(
                        text = formatVideoDuration(video.duration),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    )
                }
            }

            if (!selectionMode) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Actions",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            },
                            onClick = { showMenu = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = { showMenu = false; onDelete() }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            },
                            onClick = { showMenu = false; onCopy() }
                        )
                        DropdownMenuItem(
                            text = { Text("Select", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            },
                            onClick = { showMenu = false; onSelect() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FolderDestinationDialog(
    action: FileAction,
    folders: List<Pair<String, String?>>,
    onFolderSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${action.name} to folder") },
        text = {
            if (folders.isEmpty()) {
                Text("No other folder available")
            } else {
                Column {
                    folders.forEach { (name, path) ->
                        TextButton(
                            onClick = { onFolderSelect(path) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun Set<String>.toggle(id: String): Set<String> {
    return if (id in this) this - id else this + id
}

/**
 * Bug 7 fix: Format duration from milliseconds to H:MM:SS or MM:SS.
 */
fun formatVideoDuration(durationMs: Long): String {
    val safeMs = durationMs.coerceAtLeast(0)
    val totalSeconds = safeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

package com.mplayer.videoplayer.tv.ui.screen.browse

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.aspectRatio
import coil.compose.rememberAsyncImagePainter
import com.mplayer.videoplayer.core.model.VideoMediaItem
import com.mplayer.videoplayer.tv.viewmodel.TvBrowseViewModel
import java.io.File
import kotlinx.coroutines.delay

private val FileScreenBackground = Color(0xFFF6F7FB)
private val FilePanelLine = Color(0xFFE2E5EC)
private val FileText = Color(0xFF222836)
private val FileMuted = Color(0xFF747C8E)
private val FileAccent = Color(0xFF156CFF)
private val FileFocus = Color(0xFFE8F1FF)

@Composable
fun TvBrowseScreen(
    viewModel: TvBrowseViewModel,
    onVideoClick: (VideoMediaItem) -> Unit
) {
    val videos by viewModel.videos.collectAsState()
    var selectedFolder by remember { mutableStateOf<TvFolderModel?>(null) }
    var videoToRename by remember { mutableStateOf<VideoMediaItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var videoToDelete by remember { mutableStateOf<VideoMediaItem?>(null) }
    var isGridView by remember { mutableStateOf(false) }
    val folders = remember(videos) { buildFolders(videos) }
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(videos, selectedFolder?.name, videoToRename, videoToDelete) {
        if (videoToRename != null || videoToDelete != null) return@LaunchedEffect
        delay(120)
        runCatching { firstItemFocusRequester.requestFocus() }
    }

    BackHandler(enabled = selectedFolder != null) {
        selectedFolder = null
    }

    Surface(color = FileScreenBackground, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            FileToolbar(
                title = selectedFolder?.name ?: "Folders",
                subtitle = selectedFolder
                    ?.let { "${it.videos.size} videos" }
                    ?: "${folders.size} folders, ${videos.size} videos",
                showBack = selectedFolder != null,
                isGridView = isGridView,
                onBack = { selectedFolder = null },
                onToggleView = { isGridView = !isGridView },
                onRefresh = { viewModel.loadVideos() }
            )

            if (videos.isEmpty()) {
                EmptyLibraryMessage()
            } else if (selectedFolder == null) {
                if (isGridView) {
                    FolderGrid(
                        folders = folders,
                        firstItemFocusRequester = firstItemFocusRequester,
                        onFolderClick = { selectedFolder = it }
                    )
                } else {
                    FolderList(
                        folders = folders,
                        firstItemFocusRequester = firstItemFocusRequester,
                        onFolderClick = { selectedFolder = it }
                    )
                }
            } else {
                if (isGridView) {
                    VideoGrid(
                        videos = selectedFolder?.videos.orEmpty(),
                        firstItemFocusRequester = firstItemFocusRequester,
                        onVideoClick = onVideoClick,
                        onRenameClick = { video ->
                            videoToRename = video
                            renameText = video.title
                        },
                        onDeleteClick = { videoToDelete = it }
                    )
                } else {
                    VideoList(
                        videos = selectedFolder?.videos.orEmpty(),
                        firstItemFocusRequester = firstItemFocusRequester,
                        onVideoClick = onVideoClick,
                        onRenameClick = { video ->
                            videoToRename = video
                            renameText = video.title
                        },
                        onDeleteClick = { videoToDelete = it }
                    )
                }
            }
        }
    }

    videoToRename?.let { video ->
        AlertDialog(
            onDismissRequest = { videoToRename = null },
            title = { Text("Rename Video") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Video name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameVideo(video, renameText)
                        videoToRename = null
                    }
                ) {
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

    videoToDelete?.let { video ->
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            title = { Text("Delete Video") },
            text = { Text("Delete '${video.title}' from this device?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteVideo(video)
                        videoToDelete = null
                    }
                ) {
                    Text("Delete", color = Color(0xFFC62828))
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
private fun FileToolbar(
    title: String,
    subtitle: String,
    showBack: Boolean,
    isGridView: Boolean,
    onBack: () -> Unit,
    onToggleView: () -> Unit,
    onRefresh: () -> Unit
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .background(Color.White)
            .border(1.dp, FilePanelLine)
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = FileText)
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = FileText,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = FileMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = {}, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = FileText)
        }
        IconButton(onClick = {}, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Search, contentDescription = "Search", tint = FileText)
        }
        IconButton(onClick = onToggleView, modifier = Modifier.size(48.dp)) {
            Icon(
                if (isGridView) Icons.Rounded.ViewList else Icons.Rounded.GridView, 
                contentDescription = "Toggle View", 
                tint = FileText
            )
        }
        Box {
            IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = FileText)
            }
            DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Sort By") },
                    onClick = { showMoreMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Refresh") },
                    onClick = {
                        showMoreMenu = false
                        onRefresh()
                    }
                )
            }
        }
    }
}

@Composable
private fun FolderList(
    folders: List<TvFolderModel>,
    firstItemFocusRequester: FocusRequester,
    onFolderClick: (TvFolderModel) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 34.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(folders, key = { _, folder -> folder.path ?: folder.name }) { index, folder ->
            FolderRow(
                folder = folder,
                focusRequester = firstItemFocusRequester.takeIf { index == 0 },
                onClick = { onFolderClick(folder) }
            )
        }
    }
}

@Composable
private fun VideoList(
    videos: List<VideoMediaItem>,
    firstItemFocusRequester: FocusRequester,
    onVideoClick: (VideoMediaItem) -> Unit,
    onRenameClick: (VideoMediaItem) -> Unit,
    onDeleteClick: (VideoMediaItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 34.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(videos, key = { _, video -> video.id }) { index, video ->
            VideoRow(
                video = video,
                focusRequester = firstItemFocusRequester.takeIf { index == 0 },
                onClick = { onVideoClick(video) },
                onRenameClick = { onRenameClick(video) },
                onDeleteClick = { onDeleteClick(video) }
            )
        }
    }
}

@Composable
private fun FolderGrid(
    folders: List<TvFolderModel>,
    firstItemFocusRequester: FocusRequester,
    onFolderClick: (TvFolderModel) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 34.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        gridItemsIndexed(folders, key = { _, folder -> folder.path ?: folder.name }) { index, folder ->
            FolderGridItem(
                folder = folder,
                focusRequester = firstItemFocusRequester.takeIf { index == 0 },
                onClick = { onFolderClick(folder) }
            )
        }
    }
}

@Composable
private fun VideoGrid(
    videos: List<VideoMediaItem>,
    firstItemFocusRequester: FocusRequester,
    onVideoClick: (VideoMediaItem) -> Unit,
    onRenameClick: (VideoMediaItem) -> Unit,
    onDeleteClick: (VideoMediaItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 34.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        gridItemsIndexed(videos, key = { _, video -> video.id }) { index, video ->
            VideoGridItem(
                video = video,
                focusRequester = firstItemFocusRequester.takeIf { index == 0 },
                onClick = { onVideoClick(video) },
                onRenameClick = { onRenameClick(video) },
                onDeleteClick = { onDeleteClick(video) }
            )
        }
    }
}

@Composable
private fun FolderRow(
    folder: TvFolderModel,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    FileListRow(
        focusRequester = focusRequester,
        onClick = onClick,
        leading = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFDDEBFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Folder, contentDescription = null, tint = FileAccent, modifier = Modifier.size(34.dp))
            }
        },
        title = folder.name,
        subtitle = "",
        trailing = "${folder.videos.size} videos"
    )
}

@Composable
private fun VideoRow(
    video: VideoMediaItem,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    FileListRow(
        focusRequester = focusRequester,
        onClick = onClick,
        leading = {
            Box(
                modifier = Modifier
                    .size(width = 76.dp, height = 46.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFD6DAE4)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = rememberAsyncImagePainter(video.thumbnailUri ?: video.uri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Icon(Icons.Rounded.Movie, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        },
        title = video.title,
        subtitle = "",
        trailing = video.mimeType?.substringAfterLast('/')?.uppercase().orEmpty(),
        actions = {
            IconButton(onClick = onRenameClick, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Rounded.Edit, contentDescription = "Rename", tint = FileText)
            }
            IconButton(onClick = onDeleteClick, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color(0xFFC62828))
            }
        }
    )
}

@Composable
private fun FolderGridItem(
    folder: TvFolderModel,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    FileGridCard(
        focusRequester = focusRequester,
        onClick = onClick,
        imageContent = {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFFDDEBFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Folder, contentDescription = null, tint = FileAccent, modifier = Modifier.size(48.dp))
            }
        },
        title = folder.name,
        subtitle = "${folder.videos.size} videos"
    )
}

@Composable
private fun VideoGridItem(
    video: VideoMediaItem,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    FileGridCard(
        focusRequester = focusRequester,
        onClick = onClick,
        imageContent = {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFD6DAE4)), contentAlignment = Alignment.Center) {
                Image(
                    painter = rememberAsyncImagePainter(video.thumbnailUri ?: video.uri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Icon(Icons.Rounded.Movie, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        },
        title = video.title,
        subtitle = video.mimeType?.substringAfterLast('/')?.uppercase().orEmpty(),
        actions = {
            IconButton(onClick = onRenameClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Edit, contentDescription = "Rename", tint = FileText, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDeleteClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
            }
        }
    )
}

@Composable
private fun FileListRow(
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    trailing: String,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.015f else 1f,
        animationSpec = tween(100),
        label = "file-row-scale"
    )
    val requesterModifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier

    Surface(
        color = if (isFocused) FileFocus else Color.Transparent,
        shape = RoundedCornerShape(3.dp),
        border = if (isFocused) BorderStroke(2.dp, FileAccent) else null,
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(requesterModifier)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading()
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = FileText,
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = FileMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailing.isNotBlank()) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FileMuted,
                    maxLines = 1
                )
            }
            actions?.let {
                Spacer(modifier = Modifier.width(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = it
                )
            }
        }
    }
}

@Composable
private fun FileGridCard(
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    imageContent: @Composable () -> Unit,
    title: String,
    subtitle: String,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(100),
        label = "file-card-scale"
    )
    val requesterModifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier

    Surface(
        color = if (isFocused) FileFocus else Color.White,
        shape = RoundedCornerShape(8.dp),
        border = if (isFocused) BorderStroke(3.dp, FileAccent) else BorderStroke(1.dp, FilePanelLine),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(requesterModifier)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                imageContent()
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = FileText,
                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = FileMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                actions?.let {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = it
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryMessage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No videos found",
            style = MaterialTheme.typography.headlineSmall,
            color = FileMuted
        )
    }
}

private data class TvFolderModel(
    val name: String,
    val path: String?,
    val videos: List<VideoMediaItem>
)

private fun buildFolders(videos: List<VideoMediaItem>): List<TvFolderModel> {
    return videos
        .groupBy { video ->
            video.filePath
                ?.let { File(it).parent }
                ?: video.folderName.ifBlank { "Videos" }
        }
        .map { (pathOrName, folderVideos) ->
            val path = folderVideos.firstOrNull()?.filePath?.let { File(it).parent }
            TvFolderModel(
                name = path?.let { File(it).name }?.takeIf { it.isNotBlank() } ?: pathOrName,
                path = path,
                videos = folderVideos.sortedBy { it.title.lowercase() }
            )
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

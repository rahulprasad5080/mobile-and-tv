package com.mplayer.videoplayer.tv.ui.screen.browse

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import coil.compose.rememberAsyncImagePainter
import com.mplayer.videoplayer.core.model.VideoMediaItem
import com.mplayer.videoplayer.tv.util.cleanTvTitle
import com.mplayer.videoplayer.tv.viewmodel.TvBrowseViewModel
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private val FileScreenBackground = Color(0xFFF6F7FB)
private val FilePanelLine = Color(0xFFE2E5EC)
private val FileText = Color(0xFF222836)
private val FileMuted = Color(0xFF747C8E)
private val FileAccent = Color(0xFF156CFF)
private val FileFocus = Color(0xFFE8F1FF)

private enum class SortOrder(val label: String) {
    NAME_AZ("Name (A → Z)"),
    NAME_ZA("Name (Z → A)"),
    DURATION_LONGEST("Duration (Longest first)"),
    DURATION_SHORTEST("Duration (Shortest first)")
}

@Composable
fun TvBrowseScreen(
    viewModel: TvBrowseViewModel,
    onVideoClick: (VideoMediaItem) -> Unit,
    onPlayAllClick: (List<VideoMediaItem>) -> Unit
) {
    val videos by viewModel.videos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var selectedFolderPath by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFolderName by rememberSaveable { mutableStateOf<String?>(null) }
    var videoToRename by remember { mutableStateOf<VideoMediaItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var videoToDelete by remember { mutableStateOf<VideoMediaItem?>(null) }
    var isGridView by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var sortOrder by rememberSaveable { mutableStateOf(SortOrder.NAME_AZ) }
    var showSortDialog by rememberSaveable { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun List<VideoMediaItem>.applySortOrder(order: SortOrder): List<VideoMediaItem> = when (order) {
        SortOrder.NAME_AZ           -> sortedBy { it.cleanTvTitle().lowercase() }
        SortOrder.NAME_ZA           -> sortedByDescending { it.cleanTvTitle().lowercase() }
        SortOrder.DURATION_LONGEST  -> sortedByDescending { it.duration }
        SortOrder.DURATION_SHORTEST -> sortedBy { it.duration }
    }

    val filteredVideos = remember(videos, searchQuery, sortOrder) {
        val base = if (searchQuery.isBlank()) videos
                   else videos.filter { it.cleanTvTitle().contains(searchQuery, ignoreCase = true) }
        base.applySortOrder(sortOrder)
    }
    val folders = remember(filteredVideos) { buildFolders(filteredVideos) }
    val currentFolder = remember(folders, selectedFolderPath, selectedFolderName) {
        if (selectedFolderPath != null || selectedFolderName != null) {
            folders.firstOrNull { folder ->
                folder.path == selectedFolderPath && folder.name == selectedFolderName
            }
        } else {
            null
        }
    }

    val filteredFolderVideos = remember(currentFolder, searchQuery, sortOrder) {
        val folderVideos = currentFolder?.videos.orEmpty()
        val base = if (searchQuery.isBlank()) folderVideos
                   else folderVideos.filter { it.cleanTvTitle().contains(searchQuery, ignoreCase = true) }
        base.applySortOrder(sortOrder)
    }

    val firstItemFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(showSearch) {
        if (showSearch) {
            delay(150)
            runCatching { searchFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(selectedFolderPath, selectedFolderName, currentFolder) {
        if ((selectedFolderPath != null || selectedFolderName != null) && currentFolder == null) {
            selectedFolderPath = null
            selectedFolderName = null
        }
    }

    LaunchedEffect(filteredVideos, filteredFolderVideos, currentFolder?.path, currentFolder?.name, isGridView, videoToRename, videoToDelete, showSearch, showSortDialog) {
        if (videoToRename != null || videoToDelete != null || showSearch || showSortDialog) return@LaunchedEffect
        delay(120)
        runCatching { firstItemFocusRequester.requestFocus() }
    }

    BackHandler(enabled = (selectedFolderPath != null || selectedFolderName != null) || searchQuery.isNotBlank() || showSearch || showSortDialog) {
        when {
            showSortDialog -> showSortDialog = false
            showSearch -> { showSearch = false; searchQuery = "" }
            selectedFolderPath != null || selectedFolderName != null -> {
                selectedFolderPath = null
                selectedFolderName = null
            }
        }
    }

    Surface(color = FileScreenBackground, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showSearch) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp)
                        .background(Color.White)
                        .border(1.dp, FilePanelLine)
                        .padding(horizontal = 28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvBrowseIconButton(
                        onClick = {
                            showSearch = false
                            searchQuery = ""
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Close Search", tint = FileText)
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search videos by title...", color = FileMuted) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocusRequester),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = FileFocus,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = FileAccent,
                            unfocusedBorderColor = FilePanelLine,
                            cursorColor = FileAccent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = FileMuted)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = FileMuted)
                                }
                            }
                        }
                    )
                }
            } else {
                FileToolbar(
                    title = currentFolder?.name ?: "Folders",
                    subtitle = currentFolder
                        ?.let { "${filteredFolderVideos.size} videos" }
                        ?: "${folders.size} folders, ${filteredVideos.size} videos",
                    showBack = currentFolder != null,
                    isGridView = isGridView,
                    isRefreshing = isRefreshing,
                    sortOrder = sortOrder,
                    topBarFocusRequester = if ((currentFolder != null && filteredFolderVideos.isEmpty()) || (currentFolder == null && filteredVideos.isEmpty())) firstItemFocusRequester else null,
                    onBack = {
                        selectedFolderPath = null
                        selectedFolderName = null
                    },
                    onToggleView = { isGridView = !isGridView },
                    onRefresh = {
                        coroutineScope.launch {
                            isRefreshing = true
                            viewModel.loadVideos()
                            delay(600)
                            isRefreshing = false
                        }
                    },
                    onPlayAll = {
                        val listToPlay = if (currentFolder != null) filteredFolderVideos else filteredVideos
                        if (listToPlay.isNotEmpty()) onPlayAllClick(listToPlay)
                    },
                    onSearchClick = { showSearch = true },
                    onSortClick = { showSortDialog = true }
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(color = FileAccent)
                }
            } else if (filteredVideos.isEmpty()) {
                EmptyLibraryMessage()
            } else if (currentFolder == null) {
                if (isGridView) {
                    FolderGrid(
                        folders = folders,
                        firstItemFocusRequester = firstItemFocusRequester,
                        onFolderClick = { folder ->
                            selectedFolderPath = folder.path
                            selectedFolderName = folder.name
                        }
                    )
                } else {
                    FolderList(
                        folders = folders,
                        firstItemFocusRequester = firstItemFocusRequester,
                        onFolderClick = { folder ->
                            selectedFolderPath = folder.path
                            selectedFolderName = folder.name
                        }
                    )
                }
            } else {
                if (isGridView) {
                    VideoGrid(
                        videos = filteredFolderVideos,
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
                        videos = filteredFolderVideos,
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
        val renameFocusRequester = remember { FocusRequester() }
        LaunchedEffect(video) {
            delay(150)
            runCatching { renameFocusRequester.requestFocus() }
        }
        AlertDialog(
            onDismissRequest = { videoToRename = null },
            title = { Text("Rename Video") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Video name") },
                    modifier = Modifier.focusRequester(renameFocusRequester)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Strip extension from the display name before passing — repository adds it back
                        val nameWithoutExt = renameText.substringBeforeLast('.').ifBlank { renameText }
                        viewModel.renameVideo(video, nameWithoutExt)
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
            text = { Text("Delete '${video.cleanTvTitle()}' from this device?") },
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

    // Sort By Dialog
    if (showSortDialog) {
        val sortFocusRequesters = remember { SortOrder.entries.map { FocusRequester() } }
        LaunchedEffect(Unit) {
            delay(150)
            val currentIdx = SortOrder.entries.indexOf(sortOrder)
            runCatching { sortFocusRequesters[currentIdx].requestFocus() }
        }
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("Sort By", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    SortOrder.entries.forEachIndexed { idx, order ->
                        val isSelected = sortOrder == order
                        var isFocused by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        isFocused -> FileFocus
                                        isSelected -> Color(0xFFE8F1FF)
                                        else -> Color.Transparent
                                    }
                                )
                                .border(
                                    width = if (isFocused) 2.dp else if (isSelected) 1.5.dp else 0.dp,
                                    color = if (isFocused || isSelected) FileAccent else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .focusRequester(sortFocusRequesters[idx])
                                .onFocusChanged { isFocused = it.isFocused }
                                .onPreviewKeyEvent { event ->
                                    when {
                                        event.key.isTvSelectKey() -> {
                                            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                                                sortOrder = order
                                                showSortDialog = false
                                            }
                                            true
                                        }
                                        event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp -> {
                                            if (idx > 0) runCatching { sortFocusRequesters[idx - 1].requestFocus() }
                                            true
                                        }
                                        event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown -> {
                                            if (idx < sortFocusRequesters.lastIndex) runCatching { sortFocusRequesters[idx + 1].requestFocus() }
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                .focusable()
                                .clickable {
                                    sortOrder = order
                                    showSortDialog = false
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = order.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) FileAccent else FileText,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = FileAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSortDialog = false }) { Text("Cancel") }
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
    isRefreshing: Boolean = false,
    sortOrder: SortOrder = SortOrder.NAME_AZ,
    onBack: () -> Unit,
    onToggleView: () -> Unit,
    onRefresh: () -> Unit,
    onPlayAll: () -> Unit,
    onSearchClick: () -> Unit,
    onSortClick: () -> Unit = {},
    topBarFocusRequester: FocusRequester? = null
) {
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
            val backRequesterModifier = if (topBarFocusRequester != null) Modifier.focusRequester(topBarFocusRequester) else Modifier
            TvBrowseIconButton(
                onClick = onBack,
                modifier = backRequesterModifier.size(48.dp)
            ) {
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

        val playAllRequesterModifier = if (!showBack && topBarFocusRequester != null) Modifier.focusRequester(topBarFocusRequester) else Modifier

        TvBrowseIconButton(
            onClick = onPlayAll,
            modifier = playAllRequesterModifier.size(48.dp)
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = FileText)
        }
        TvBrowseIconButton(onClick = onSearchClick, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Search, contentDescription = "Search", tint = FileText)
        }
        // Sort button — shows current sort as a small tinted icon
        TvBrowseIconButton(onClick = onSortClick, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Sort, contentDescription = "Sort By: ${sortOrder.label}", tint = FileAccent)
        }
        // Refresh button — shows spinner when isRefreshing
        TvBrowseIconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
            if (isRefreshing) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                    color = FileAccent
                )
            } else {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = FileText)
            }
        }
        TvBrowseIconButton(onClick = onToggleView, modifier = Modifier.size(48.dp)) {
            Icon(
                if (isGridView) Icons.Rounded.ViewList else Icons.Rounded.GridView, 
                contentDescription = "Toggle View", 
                tint = FileText
            )
        }
    }
}

@Composable
private fun TvBrowseIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    IconButton(
        onClick = onClick,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) FileFocus else Color.Transparent)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) FileAccent else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.key.isTvSelectKey()) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        onClick()
                    }
                    true
                } else if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionLeft -> focusManager.moveFocus(FocusDirection.Left)
                        Key.DirectionRight -> focusManager.moveFocus(FocusDirection.Right)
                        Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                        Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                        else -> false
                    }
                }
            }
    ) {
        content()
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
        itemsIndexed(
            items = folders,
            key = { _, folder -> folder.path ?: folder.name },
            contentType = { _, _ -> "folder-row" }
        ) { index, folder ->
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
        itemsIndexed(
            items = videos,
            key = { _, video -> video.id },
            contentType = { _, _ -> "video-row" }
        ) { index, video ->
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
        gridItemsIndexed(
            items = folders,
            key = { _, folder -> folder.path ?: folder.name },
            contentType = { _, _ -> "folder-grid-card" }
        ) { index, folder ->
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
        gridItemsIndexed(
            items = videos,
            key = { _, video -> video.id },
            contentType = { _, _ -> "video-grid-card" }
        ) { index, video ->
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
@OptIn(ExperimentalFoundationApi::class)
private fun VideoRow(
    video: VideoMediaItem,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val rowBodyFocusRequester = remember { FocusRequester() }
    val editFocusRequester   = remember { FocusRequester() }
    val deleteFocusRequester = remember { FocusRequester() }

    var rowBodyFocused by remember { mutableStateOf(false) }
    var editFocused    by remember { mutableStateOf(false) }
    var deleteFocused  by remember { mutableStateOf(false) }
    val anyFocused = rowBodyFocused || editFocused || deleteFocused

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    val scale by animateFloatAsState(
        targetValue = if (anyFocused) 1.015f else 1f,
        animationSpec = tween(100), label = "row-scale"
    )
    val actionsWidth by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (anyFocused) 108.dp else 0.dp,
        animationSpec = tween(180), label = "actions-width"
    )

    Surface(
        color = when {
            editFocused || deleteFocused -> FileFocus
            anyFocused -> FileFocus
            else -> Color.Transparent
        },
        shape = RoundedCornerShape(6.dp),
        border = if (anyFocused) BorderStroke(2.dp, FileAccent) else null,
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { state ->
                if (state.hasFocus) {
                    coroutineScope.launch { delay(40); bringIntoViewRequester.bringIntoView() }
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(width = 76.dp, height = 46.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFD6DAE4)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = rememberAsyncImagePainter(video.filePath ?: video.uri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Icon(Icons.Rounded.Movie, contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))

            // Row body — focusable, plays on OK
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                    .focusRequester(rowBodyFocusRequester)
                    .onFocusChanged { rowBodyFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        when {
                            event.key.isTvSelectKey() &&
                            event.type == KeyEventType.KeyDown &&
                            event.nativeKeyEvent.repeatCount == 0 -> { onClick(); true }
                            event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionRight -> {
                                runCatching { editFocusRequester.requestFocus() }; true
                            }
                            else -> false
                        }
                    }
                    .focusable()
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.CenterStart
            ) {
                Column {
                    Text(
                        text = video.cleanTvTitle(),
                        style = MaterialTheme.typography.titleMedium,
                        color = FileText,
                        fontWeight = if (anyFocused) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (video.duration > 0) {
                        Text(
                            text = formatVideoDuration(video.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = FileMuted, maxLines = 1
                        )
                    }
                }
            }

            // Animated action buttons: slide in when focused
            if (actionsWidth > 0.dp) {
                Row(
                    modifier = Modifier.width(actionsWidth),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Edit button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (editFocused) FileAccent else FileFocus)
                            .border(1.5.dp, if (editFocused) FileAccent else FilePanelLine, RoundedCornerShape(8.dp))
                            .focusRequester(editFocusRequester)
                            .onFocusChanged { editFocused = it.isFocused }
                            .onPreviewKeyEvent { event ->
                                when {
                                    event.key.isTvSelectKey() &&
                                    event.type == KeyEventType.KeyDown &&
                                    event.nativeKeyEvent.repeatCount == 0 -> { onRenameClick(); true }
                                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft -> {
                                        runCatching { rowBodyFocusRequester.requestFocus() }; true
                                    }
                                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight -> {
                                        runCatching { deleteFocusRequester.requestFocus() }; true
                                    }
                                    else -> false
                                }
                            }
                            .focusable()
                            .clickable(onClick = onRenameClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Rename",
                            tint = if (editFocused) Color.White else FileAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // Delete button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (deleteFocused) Color(0xFFC62828) else Color(0xFFFFECEC))
                            .border(1.5.dp, Color(0xFFC62828).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .focusRequester(deleteFocusRequester)
                            .onFocusChanged { deleteFocused = it.isFocused }
                            .onPreviewKeyEvent { event ->
                                when {
                                    event.key.isTvSelectKey() &&
                                    event.type == KeyEventType.KeyDown &&
                                    event.nativeKeyEvent.repeatCount == 0 -> { onDeleteClick(); true }
                                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft -> {
                                        runCatching { editFocusRequester.requestFocus() }; true
                                    }
                                    else -> false
                                }
                            }
                            .focusable()
                            .clickable(onClick = onDeleteClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete",
                            tint = if (deleteFocused) Color.White else Color(0xFFC62828),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
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
        onRenameClick = onRenameClick,
        onDeleteClick = onDeleteClick,
        imageContent = {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFD6DAE4)), contentAlignment = Alignment.Center) {
                Image(
                    painter = rememberAsyncImagePainter(video.filePath ?: video.uri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Icon(Icons.Rounded.Movie, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        },
        title = video.cleanTvTitle(),
        subtitle = if (video.duration > 0) formatVideoDuration(video.duration) else ""
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
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFDDEBFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = FileAccent,
                    modifier = Modifier.size(54.dp)
                )
            }
        },
        title = folder.name,
        subtitle = "${folder.videos.size} videos"
    )
}


@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FileListRow(
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    trailing: String
) {
    var isFocused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
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
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    coroutineScope.launch {
                        delay(40)
                        bringIntoViewRequester.bringIntoView()
                    }
                }
            }
            .onPreviewKeyEvent { event ->
                when {
                    event.key.isTvSelectKey() -> {
                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                            onClick()
                        }
                        true
                    }
                    // Menu key or long-held OK triggers context menu
                    onLongPress != null && event.type == KeyEventType.KeyDown &&
                    event.key == Key.Menu -> {
                        onLongPress()
                        true
                    }
                    else -> false
                }
            }
            .focusProperties { canFocus = true }
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
            // Hint: show MoreVert icon when focused to indicate Menu key opens actions
            if (isFocused && onLongPress != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Press Menu for options",
                    tint = FileAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FileGridCard(
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onRenameClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    imageContent: @Composable () -> Unit,
    title: String,
    subtitle: String
) {
    val cardFocusRequester   = remember { FocusRequester() }
    val editFocusRequester   = remember { if (onRenameClick != null) FocusRequester() else FocusRequester() }
    val deleteFocusRequester = remember { if (onDeleteClick != null) FocusRequester() else FocusRequester() }

    var cardFocused   by remember { mutableStateOf(false) }
    var editFocused   by remember { mutableStateOf(false) }
    var deleteFocused by remember { mutableStateOf(false) }
    val anyFocused = cardFocused || editFocused || deleteFocused

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val scale by animateFloatAsState(
        targetValue = if (anyFocused) 1.05f else 1f,
        animationSpec = tween(100), label = "card-scale"
    )
    val overlayAlpha by animateFloatAsState(
        targetValue = if (anyFocused && onRenameClick != null) 1f else 0f,
        animationSpec = tween(200), label = "overlay-alpha"
    )
    val requesterModifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier

    Surface(
        color = if (anyFocused) FileFocus else Color.White,
        shape = RoundedCornerShape(8.dp),
        border = if (anyFocused) BorderStroke(3.dp, FileAccent) else BorderStroke(1.dp, FilePanelLine),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(requesterModifier)
            .focusRequester(cardFocusRequester)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { state ->
                cardFocused = state.isFocused
                if (state.hasFocus) {
                    coroutineScope.launch { delay(40); bringIntoViewRequester.bringIntoView() }
                }
            }
            .onPreviewKeyEvent { event ->
                when {
                    event.key.isTvSelectKey() && event.type == KeyEventType.KeyDown &&
                    event.nativeKeyEvent.repeatCount == 0 -> { onClick(); true }
                    onRenameClick != null && event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionDown -> {
                        runCatching { editFocusRequester.requestFocus() }; true
                    }
                    else -> false
                }
            }
            .focusProperties { canFocus = true }
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                // Image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    imageContent()
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = FileText,
                    fontWeight = if (anyFocused) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = FileMuted,
                        maxLines = 1
                    )
                }
            }

            // MX Player style: action row slides in at the bottom overlay when focused
            if (onRenameClick != null && onDeleteClick != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 8.dp)
                        .graphicsLayer { alpha = overlayAlpha },
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Edit button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (editFocused) FileAccent else Color.White.copy(alpha = 0.9f))
                            .border(1.dp, if (editFocused) FileAccent else FilePanelLine, RoundedCornerShape(6.dp))
                            .focusRequester(editFocusRequester)
                            .onFocusChanged { editFocused = it.isFocused }
                            .onPreviewKeyEvent { event ->
                                when {
                                    event.key.isTvSelectKey() && event.type == KeyEventType.KeyDown &&
                                    event.nativeKeyEvent.repeatCount == 0 -> { onRenameClick(); true }
                                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp -> {
                                        runCatching { cardFocusRequester.requestFocus() }; true
                                    }
                                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight -> {
                                        runCatching { deleteFocusRequester.requestFocus() }; true
                                    }
                                    else -> false
                                }
                            }
                            .focusable(enabled = anyFocused)
                            .clickable(onClick = onRenameClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Rename",
                            tint = if (editFocused) Color.White else FileAccent,
                            modifier = Modifier.size(18.dp))
                    }
                    // Delete button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (deleteFocused) Color(0xFFC62828) else Color.White.copy(alpha = 0.9f))
                            .border(1.dp, Color(0xFFC62828).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .focusRequester(deleteFocusRequester)
                            .onFocusChanged { deleteFocused = it.isFocused }
                            .onPreviewKeyEvent { event ->
                                when {
                                    event.key.isTvSelectKey() && event.type == KeyEventType.KeyDown &&
                                    event.nativeKeyEvent.repeatCount == 0 -> { onDeleteClick(); true }
                                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp -> {
                                        runCatching { cardFocusRequester.requestFocus() }; true
                                    }
                                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft -> {
                                        runCatching { editFocusRequester.requestFocus() }; true
                                    }
                                    else -> false
                                }
                            }
                            .focusable(enabled = anyFocused)
                            .clickable(onClick = onDeleteClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete",
                            tint = if (deleteFocused) Color.White else Color(0xFFC62828),
                            modifier = Modifier.size(18.dp))
                    }
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
                videos = folderVideos.sortedBy { it.cleanTvTitle().lowercase() }
            )
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

/**
 * Bug 7 fix: Format duration from milliseconds to H:MM:SS or MM:SS.
 * Shows hours only when needed, e.g. "1:26:34" for 1hr videos.
 */
private fun formatVideoDuration(durationMs: Long): String {
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

private fun Key.isTvSelectKey(): Boolean {
    return this == Key.DirectionCenter ||
        this == Key.Enter ||
        this == Key.NumPadEnter
}

private fun androidx.compose.ui.input.key.KeyEvent.isTvSelectDown(): Boolean {
    return type == KeyEventType.KeyDown && key.isTvSelectKey()
}

private fun androidx.compose.ui.input.key.KeyEvent.isInitialTvSelectDown(): Boolean {
    return isTvSelectDown() && nativeKeyEvent.repeatCount == 0
}

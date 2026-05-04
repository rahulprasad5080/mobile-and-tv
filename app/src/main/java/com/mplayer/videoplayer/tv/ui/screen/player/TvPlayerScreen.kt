package com.mplayer.videoplayer.tv.ui.screen.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mplayer.videoplayer.common.ui.player.SubtitleSize
import com.mplayer.videoplayer.common.ui.player.applyNetflixSubtitleStyle
import com.mplayer.videoplayer.core.model.AudioTrackInfo
import com.mplayer.videoplayer.core.model.SubtitleTrackInfo
import com.mplayer.videoplayer.tv.viewmodel.TvPlayerViewModel
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

private val TvOverlayBlack = Color.Black.copy(alpha = 0.62f)
private val TvPanel = Color(0xFF111722)
private val TvPanelLight = Color(0xFF202A3A)
private val TvAccent = Color(0xFF4DA3FF)
private val TvMuted = Color(0xFFB8C0CD)

@Composable
fun TvPlayerScreen(
    viewModel: TvPlayerViewModel,
    onBackPressed: () -> Unit
) {
    val player = viewModel.playerManager.getPlayer()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val currentTitle by viewModel.currentTitle.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val resizeMode by viewModel.resizeMode.collectAsState()
    val audioTracks by viewModel.audioTracks.collectAsState()
    val subtitleTracks by viewModel.subtitleTracks.collectAsState()
    var showControls by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showAudioPopup by remember { mutableStateOf(false) }
    var showSubtitlePopup by remember { mutableStateOf(false) }
    var isNightMode by remember { mutableStateOf(false) }
    var subtitleSize by remember { mutableStateOf(SubtitleSize.Medium) }
    val rootFocusRequester = remember { FocusRequester() }
    val playFocusRequester = remember { FocusRequester() }

    val lifecycleOwner = LocalLifecycleOwner.current
    var wasPlayingBeforePause by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    wasPlayingBeforePause = isPlaying
                    viewModel.stopPlayback()
                }
                Lifecycle.Event.ON_START -> {
                    if (wasPlayingBeforePause) {
                        viewModel.playerManager.resume()
                        wasPlayingBeforePause = false
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler {
        if (showAudioPopup) {
            showAudioPopup = false
        } else if (showSubtitlePopup) {
            showSubtitlePopup = false
        } else if (showSettings) {
            showSettings = false
        } else if (showControls) {
            showControls = false
        } else {
            onBackPressed()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopPlayback()
        }
    }

    LaunchedEffect(showControls, showSettings, showAudioPopup, showSubtitlePopup, isPlaying, currentPosition) {
        if (showControls && !showSettings && !showAudioPopup && !showSubtitlePopup && isPlaying) {
            delay(4_000)
            showControls = false
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            runCatching { playFocusRequester.requestFocus() }
        } else {
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(rootFocusRequester)
            .focusProperties { canFocus = true }
            .focusable()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.MediaPlayPause,
                    Key.Spacebar -> {
                        viewModel.togglePlayPause()
                        showControls = true
                        true
                    }
                    Key.MediaPlay -> {
                        if (!isPlaying) viewModel.togglePlayPause()
                        showControls = true
                        true
                    }
                    Key.MediaPause -> {
                        if (isPlaying) viewModel.togglePlayPause()
                        showControls = true
                        true
                    }
                    Key.MediaRewind -> {
                        viewModel.seekBy(-10_000)
                        showControls = true
                        true
                    }
                    Key.MediaFastForward -> {
                        viewModel.seekBy(10_000)
                        showControls = true
                        true
                    }
                    Key.MediaPrevious -> {
                        viewModel.playPrevious()
                        showControls = true
                        true
                    }
                    Key.MediaNext -> {
                        viewModel.playNext()
                        showControls = true
                        true
                    }
                    Key.DirectionCenter,
                    Key.Enter -> {
                        if (!showControls) {
                            showControls = true
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionLeft -> {
                        if (!showControls) {
                            viewModel.seekBy(-10_000)
                            showControls = true
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionRight -> {
                        if (!showControls) {
                            viewModel.seekBy(10_000)
                            showControls = true
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionUp,
                    Key.DirectionDown,
                    Key.Menu -> {
                        showControls = true
                        false
                    }
                    else -> false
                }
            }
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = false
                    applyNetflixSubtitleStyle(subtitleSize, isTv = true)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = resizeMode
                view.applyNetflixSubtitleStyle(subtitleSize, isTv = true)
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(240))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                TvOverlayBlack,
                                Color.Black.copy(alpha = 0.18f),
                                TvOverlayBlack
                            )
                        )
                    )
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TvPlayerTopBar(
                title = currentTitle.ifBlank { "Now Playing" },
                volume = volume,
                isMuted = isMuted,
                onBackPressed = onBackPressed,
                onAudioClick = {
                    showAudioPopup = true
                    showControls = true
                },
                onSubtitleClick = {
                    showSubtitlePopup = true
                    showControls = true
                },
                onVolumeDown = {
                    viewModel.decreaseVolume()
                    showControls = true
                },
                onMuteToggle = {
                    viewModel.toggleMute()
                    showControls = true
                },
                onVolumeUp = {
                    viewModel.increaseVolume()
                    showControls = true
                },
                onSettingsClick = { showSettings = true }
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            TvCenterControls(
                isPlaying = isPlaying,
                playFocusRequester = playFocusRequester,
                onPrevious = {
                    viewModel.playPrevious()
                    showControls = true
                },
                onRewind = {
                    viewModel.seekBy(-10_000)
                    showControls = true
                },
                onPlayPause = {
                    viewModel.togglePlayPause()
                    showControls = true
                },
                onForward = {
                    viewModel.seekBy(10_000)
                    showControls = true
                },
                onNext = {
                    viewModel.playNext()
                    showControls = true
                }
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            TvProgressBar(
                currentPosition = currentPosition,
                duration = duration,
                onSeekTo = {
                    viewModel.seekTo(it)
                    showControls = true
                }
            )
        }

        if (isNightMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
            )
        }
    }

    if (showAudioPopup) {
        TvAudioTracksPopup(
            audioTracks = audioTracks,
            onDismiss = { showAudioPopup = false },
            onAudioSelect = {
                viewModel.selectAudioTrack(it)
                showAudioPopup = false
                showControls = true
            }
        )
    }

    if (showSubtitlePopup) {
        TvSubtitleTracksPopup(
            subtitleTracks = subtitleTracks,
            selectedSubtitleSize = subtitleSize,
            onDismiss = { showSubtitlePopup = false },
            onSubtitleSelect = {
                viewModel.selectSubtitleTrack(it)
                showSubtitlePopup = false
                showControls = true
            },
            onSubtitleSizeChange = {
                subtitleSize = it
                showControls = true
            }
        )
    }

    if (showSettings) {
        TvPlayerSettingsDialog(
            onDismiss = { showSettings = false },
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
            selectedSubtitleSize = subtitleSize,
            playbackSpeed = playbackSpeed,
            resizeMode = resizeMode,
            volume = volume,
            isMuted = isMuted,
            isNightMode = isNightMode,
            onAudioSelect = {
                viewModel.selectAudioTrack(it)
                showControls = true
            },
            onSubtitleSelect = {
                viewModel.selectSubtitleTrack(it)
                showControls = true
            },
            onSubtitleSizeChange = {
                subtitleSize = it
                showControls = true
            },
            onSpeedChange = {
                viewModel.setPlaybackSpeed(it)
                showControls = true
            },
            onResizeModeChange = {
                viewModel.setResizeMode(it)
                showControls = true
            },
            onVolumeChange = {
                viewModel.setVolume(it)
                showControls = true
            },
            onMuteToggle = {
                viewModel.toggleMute()
                showControls = true
            },
            onNightToggle = {
                isNightMode = !isNightMode
                showControls = true
            }
        )
    }
}

@Composable
private fun TvPlayerTopBar(
    title: String,
    volume: Float,
    isMuted: Boolean,
    onBackPressed: () -> Unit,
    onAudioClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onVolumeDown: () -> Unit,
    onMuteToggle: () -> Unit,
    onVolumeUp: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 56.dp, vertical = 30.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBackPressed,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Text("Back", style = MaterialTheme.typography.titleMedium)
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 34.dp)
            )

            Button(
                onClick = onSettingsClick,
                colors = ButtonDefaults.buttonColors(containerColor = TvAccent),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Settings", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvQuickActionButton(Icons.Rounded.Audiotrack, "Audio", onAudioClick)
            TvQuickActionButton(Icons.Rounded.Subtitles, "Subtitles", onSubtitleClick)
            TvQuickActionButton(Icons.Rounded.VolumeOff, "Vol -", onVolumeDown)
            TvQuickActionButton(
                icon = if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                label = if (isMuted) "Unmute" else "Mute",
                onClick = onMuteToggle
            )
            TvQuickActionButton(Icons.Rounded.VolumeUp, "Vol +", onVolumeUp)
            Text(
                text = "${(volume * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.88f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun TvQuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) TvAccent else Color.White.copy(alpha = 0.12f)
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TvCenterControls(
    isPlaying: Boolean,
    playFocusRequester: FocusRequester,
    onPrevious: () -> Unit,
    onRewind: () -> Unit,
    onPlayPause: () -> Unit,
    onForward: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TvRoundControlButton(
            icon = Icons.Rounded.SkipPrevious,
            contentDescription = "Previous video",
            size = 74.dp,
            onClick = onPrevious
        )
        TvRoundControlButton(
            icon = Icons.Rounded.Replay10,
            contentDescription = "Rewind 10 seconds",
            size = 86.dp,
            onClick = onRewind
        )
        TvRoundControlButton(
            icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = "Play or pause",
            size = 112.dp,
            focusRequester = playFocusRequester,
            emphasized = true,
            onClick = onPlayPause
        )
        TvRoundControlButton(
            icon = Icons.Rounded.FastForward,
            contentDescription = "Forward 10 seconds",
            size = 86.dp,
            onClick = onForward
        )
        TvRoundControlButton(
            icon = Icons.Rounded.SkipNext,
            contentDescription = "Next video",
            size = 74.dp,
            onClick = onNext
        )
    }
}

@Composable
private fun TvRoundControlButton(
    icon: ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    focusRequester: FocusRequester? = null,
    emphasized: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.12f else 1f,
        animationSpec = tween(120),
        label = "tv-control-scale"
    )
    val requesterModifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .then(requesterModifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isFocused) 22.dp else 8.dp,
                shape = CircleShape,
                ambientColor = TvAccent,
                spotColor = TvAccent
            )
            .clip(CircleShape)
            .background(if (emphasized) TvAccent else Color.White.copy(alpha = 0.16f))
            .border(
                width = if (isFocused) 4.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.22f),
                shape = CircleShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(if (emphasized) 58.dp else 42.dp)
        )
    }
}

@Composable
private fun TvProgressBar(
    currentPosition: Long,
    duration: Long,
    onSeekTo: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 72.dp, end = 72.dp, bottom = 54.dp)
    ) {
        Slider(
            value = if (duration > 0) {
                (currentPosition.toFloat() / duration).coerceIn(0f, 1f)
            } else {
                0f
            },
            onValueChange = { progress ->
                if (duration > 0) {
                    onSeekTo((progress * duration).toLong())
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            colors = SliderDefaults.colors(
                thumbColor = TvAccent,
                activeTrackColor = TvAccent,
                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
            )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(currentPosition), color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(formatTime(duration), color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun TvAudioTracksPopup(
    audioTracks: List<AudioTrackInfo>,
    onDismiss: () -> Unit,
    onAudioSelect: (String) -> Unit
) {
    BackHandler(onBack = onDismiss)

    TvSidePopup(title = "Audio Tracks", icon = Icons.Rounded.Audiotrack, onDismiss = onDismiss) {
        if (audioTracks.isEmpty()) {
            item { DialogHint("No alternate audio tracks") }
        } else {
            items(audioTracks, key = { it.id }) { track ->
                TvTrackOption(
                    label = track.displayLabel,
                    selected = track.isSelected,
                    onClick = { onAudioSelect(track.id) }
                )
            }
        }
    }
}

@Composable
private fun TvSubtitleTracksPopup(
    subtitleTracks: List<SubtitleTrackInfo>,
    selectedSubtitleSize: SubtitleSize,
    onDismiss: () -> Unit,
    onSubtitleSelect: (String?) -> Unit,
    onSubtitleSizeChange: (SubtitleSize) -> Unit
) {
    BackHandler(onBack = onDismiss)

    TvSidePopup(title = "Subtitles", icon = Icons.Rounded.Subtitles, onDismiss = onDismiss) {
        item {
            DialogSectionHeader(icon = Icons.Rounded.Subtitles, title = "Language")
            Spacer(modifier = Modifier.height(10.dp))
            TvTrackOption(
                label = "Off",
                selected = subtitleTracks.none { it.isSelected },
                onClick = { onSubtitleSelect(null) }
            )
        }

        items(subtitleTracks, key = { it.id }) { track ->
            TvTrackOption(
                label = track.displayLabel,
                selected = track.isSelected,
                onClick = { onSubtitleSelect(track.id) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(18.dp))
            DialogSectionHeader(icon = Icons.Rounded.Subtitles, title = "Subtitle Size")
        }

        items(SubtitleSize.entries, key = { it.name }) { size ->
            TvTrackOption(
                label = size.name,
                selected = selectedSubtitleSize == size,
                onClick = { onSubtitleSizeChange(size) }
            )
        }
    }
}

@Composable
private fun TvSidePopup(
    title: String,
    icon: ImageVector,
    onDismiss: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.CenterEnd
    ) {
        Card(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(min = 460.dp, max = 560.dp)
                .padding(vertical = 50.dp, horizontal = 54.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = TvPanel),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    DialogSectionHeader(icon = icon, title = title)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                content()
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = TvAccent),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        Text("Close", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TvPlayerSettingsDialog(
    onDismiss: () -> Unit,
    audioTracks: List<AudioTrackInfo>,
    subtitleTracks: List<SubtitleTrackInfo>,
    selectedSubtitleSize: SubtitleSize,
    playbackSpeed: Float,
    resizeMode: Int,
    volume: Float,
    isMuted: Boolean,
    isNightMode: Boolean,
    onAudioSelect: (String) -> Unit,
    onSubtitleSelect: (String?) -> Unit,
    onSubtitleSizeChange: (SubtitleSize) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onResizeModeChange: (Int) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onNightToggle: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f)),
        contentAlignment = Alignment.CenterEnd
    ) {
        Card(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(min = 520.dp, max = 620.dp)
                .padding(vertical = 42.dp, horizontal = 48.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = TvPanel),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(30.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = "Player Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                item {
                    DialogSectionHeader(icon = Icons.Rounded.Speed, title = "Playback Speed")
                    Spacer(modifier = Modifier.height(10.dp))
                    TvOptionFlow {
                        playbackSpeedOptions.forEach { speed ->
                            TvSettingsChip(
                                label = if (speed == 1.0f) "Normal" else "${speed}x",
                                selected = playbackSpeed == speed,
                                onClick = { onSpeedChange(speed) }
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    DialogSectionHeader(icon = Icons.Rounded.AspectRatio, title = "Aspect Ratio")
                    Spacer(modifier = Modifier.height(10.dp))
                    TvOptionFlow {
                        resizeModeOptions.forEach { (mode, label) ->
                            TvSettingsChip(
                                label = label,
                                selected = resizeMode == mode,
                                onClick = { onResizeModeChange(mode) }
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    DialogSectionHeader(
                        icon = if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                        title = "Volume"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Slider(
                        value = volume,
                        onValueChange = onVolumeChange,
                        colors = SliderDefaults.colors(
                            thumbColor = TvAccent,
                            activeTrackColor = TvAccent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                        )
                    )
                    TvOptionFlow {
                        TvSettingsChip(
                            label = if (isMuted) "Unmute" else "Mute",
                            selected = isMuted,
                            onClick = onMuteToggle
                        )
                        TvSettingsChip(
                            label = "Night Mode",
                            selected = isNightMode,
                            onClick = onNightToggle
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    DialogSectionHeader(icon = Icons.Rounded.Audiotrack, title = "Audio Tracks")
                }

                if (audioTracks.isEmpty()) {
                    item { DialogHint("No alternate audio tracks") }
                } else {
                    items(audioTracks, key = { it.id }) { track ->
                        TvTrackOption(
                            label = track.displayLabel,
                            selected = track.isSelected,
                            onClick = { onAudioSelect(track.id) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(18.dp))
                    DialogSectionHeader(icon = Icons.Rounded.Subtitles, title = "Subtitles")
                }

                item {
                    TvTrackOption(
                        label = "Off",
                        selected = subtitleTracks.none { it.isSelected },
                        onClick = { onSubtitleSelect(null) }
                    )
                }

                items(subtitleTracks, key = { it.id }) { track ->
                    TvTrackOption(
                        label = track.displayLabel,
                        selected = track.isSelected,
                        onClick = { onSubtitleSelect(track.id) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(18.dp))
                    DialogSectionHeader(icon = Icons.Rounded.Subtitles, title = "Subtitle Size")
                }

                items(SubtitleSize.entries, key = { it.name }) { size ->
                    TvTrackOption(
                        label = size.name,
                        selected = selectedSubtitleSize == size,
                        onClick = { onSubtitleSizeChange(size) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = TvAccent),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        Text("Close", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogSectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = TvAccent, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DialogHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = TvMuted,
        modifier = Modifier.padding(vertical = 10.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TvOptionFlow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        content()
    }
}

@Composable
private fun TvSettingsChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = TvAccent,
            selectedLabelColor = Color.White,
            containerColor = if (isFocused) TvPanelLight else Color.White.copy(alpha = 0.06f),
            labelColor = Color.White
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
            selectedBorderColor = Color.White.copy(alpha = 0.22f),
            borderWidth = if (isFocused) 3.dp else 1.dp,
            selectedBorderWidth = if (isFocused) 3.dp else 1.dp
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .heightIn(min = 52.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    )
}

@Composable
private fun TvTrackOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = tween(120),
        label = "tv-option-scale"
    )

    Surface(
        color = when {
            selected -> TvAccent
            isFocused -> TvPanelLight
            else -> Color.White.copy(alpha = 0.06f)
        },
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isFocused) 3.dp else 1.dp,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.1f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Text(
                    text = "Selected",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(start = 18.dp)
                )
            }
        }
    }
}

private val playbackSpeedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)

private val resizeModeOptions: List<Pair<Int, String>> = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "Original",
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
    AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT to "Fill",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch"
)

private fun formatTime(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0)
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

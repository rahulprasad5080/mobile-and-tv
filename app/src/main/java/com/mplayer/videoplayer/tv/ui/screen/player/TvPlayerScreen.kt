@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.mplayer.videoplayer.tv.ui.screen.player

import android.app.Activity
import android.media.AudioManager
import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import com.mplayer.videoplayer.tv.util.FrameRateHelper
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mplayer.videoplayer.common.ui.player.SubtitleSize
import com.mplayer.videoplayer.common.ui.player.applyNetflixSubtitleStyle
import com.mplayer.videoplayer.common.ui.player.createCompatiblePlayerView
import com.mplayer.videoplayer.core.model.AudioTrackInfo
import com.mplayer.videoplayer.core.model.SubtitleTrackInfo
import com.mplayer.videoplayer.core.player.PlayerManager
import com.mplayer.videoplayer.tv.viewmodel.TvPlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.rememberCoroutineScope

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
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val resizeMode by viewModel.resizeMode.collectAsState()
    val audioTracks by viewModel.audioTracks.collectAsState()
    val subtitleTracks by viewModel.subtitleTracks.collectAsState()
    val playbackState by viewModel.playerManager.playbackState.collectAsState()
    val playbackErrorMessage = (playbackState as? PlayerManager.PlaybackState.Error)?.message
    
    val currentVideo = viewModel.currentVideoVal
    val autoFrameRate by viewModel.autoFrameRate.collectAsState()
    val volumeBoost by viewModel.volumeBoost.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val context = LocalContext.current
    var showControls by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showAudioPopup by remember { mutableStateOf(false) }
    var showSubtitlePopup by remember { mutableStateOf(false) }
    var subtitleSize by remember { mutableStateOf(SubtitleSize.Medium) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    val rootFocusRequester = remember { FocusRequester() }
    val playFocusRequester = remember { FocusRequester() }
    val audioFocusRequester = remember { FocusRequester() }
    val subtitleFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }
    var focusToRestore by remember { mutableStateOf<FocusRequester?>(null) }
    var controlsInteractionTrigger by remember { mutableStateOf(0) }
    var rootHasFocus by remember { mutableStateOf(false) }
    val focusScope = rememberCoroutineScope()
    val isModalOpen = showSettings || showAudioPopup || showSubtitlePopup

    // Seek overlay state — shows "HH:MM:SS [+/-MM:SS]" on screen during seeking
    var seekDeltaMs by remember { mutableStateOf(0L) }
    var showSeekOverlay by remember { mutableStateOf(false) }
    val seekOverlayScope = rememberCoroutineScope()
    var seekOverlayJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var wasPlayingBeforePause by remember { mutableStateOf(false) }

    // Keep screen on while playing — mirrors reference Player-master setKeepScreenOn(isPlaying)
    val activity = context as? Activity
    DisposableEffect(isPlaying) {
        if (isPlaying) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

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
        } else {
            onBackPressed()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            FrameRateHelper.abort()
            viewModel.stopPlayback()
        }
    }

    LaunchedEffect(currentVideo, autoFrameRate) {
        val video = currentVideo
        if (video != null && autoFrameRate) {
            val playerInstance = viewModel.playerManager.getPlayer()
            val wasPlaying = playerInstance?.playWhenReady ?: true
            playerInstance?.playWhenReady = false
            activity?.let { act ->
                FrameRateHelper.switchFrameRate(act, video.uri) {
                    if (wasPlaying) {
                        playerInstance?.playWhenReady = true
                    }
                }
            }
        }
    }


    LaunchedEffect(playerView, subtitleSize) {
        playerView?.applyNetflixSubtitleStyle(subtitleSize, isTv = true)
    }

    LaunchedEffect(showControls, isModalOpen, isPlaying, controlsInteractionTrigger) {
        if (showControls && !isModalOpen && isPlaying) {
            delay(4_000)
            showControls = false
        }
    }

    LaunchedEffect(showControls, isModalOpen) {
        if (!showControls && !isModalOpen) {
            runCatching { rootFocusRequester.requestFocus() }
        } else if (showControls && !isModalOpen) {
            delay(100)
            val requester = focusToRestore ?: playFocusRequester
            runCatching { requester.requestFocus() }
            focusToRestore = null
        }
    }

    fun restorePlayFocus() {
        showControls = true
        focusScope.launch {
            delay(80)
            runCatching { playFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(rootFocusRequester)
            .focusProperties { canFocus = true }
            .onFocusChanged { rootHasFocus = it.isFocused }
            .focusable()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (isModalOpen) return@onPreviewKeyEvent false
                val isRepeatedKey = event.nativeKeyEvent.repeatCount > 0
                val repeatCount = event.nativeKeyEvent.repeatCount
                val remoteSeekDirection = tvRemoteSeekDirection(
                    keyCode = event.nativeKeyEvent.keyCode,
                    showControls = showControls,
                    rootHasFocus = rootHasFocus
                )
                if (remoteSeekDirection != 0) {
                    // Progressive seek: hold longer = seek more
                    // 0-4 repeats: 10s, 5-14 repeats: 30s, 15+ repeats: 60s
                    val seekMs = when {
                        repeatCount >= 15 -> 60_000L
                        repeatCount >= 5  -> 30_000L
                        else             -> 10_000L
                    }
                    viewModel.seekBy(remoteSeekDirection * seekMs)
                    // Update seek overlay delta
                    seekDeltaMs += remoteSeekDirection * seekMs
                    showSeekOverlay = true
                    seekOverlayJob?.cancel()
                    seekOverlayJob = seekOverlayScope.launch {
                        delay(1_500)
                        showSeekOverlay = false
                        seekDeltaMs = 0L
                    }
                    restorePlayFocus()
                    controlsInteractionTrigger++
                    return@onPreviewKeyEvent true
                }
                when (event.key) {
                    Key.MediaPlayPause,
                    Key.Spacebar -> {
                        if (isRepeatedKey) return@onPreviewKeyEvent true
                        viewModel.togglePlayPause()
                        showControls = true
                        true
                    }
                    Key.MediaPlay -> {
                        if (isRepeatedKey) return@onPreviewKeyEvent true
                        if (!isPlaying) viewModel.togglePlayPause()
                        showControls = true
                        true
                    }
                    Key.MediaPause -> {
                        if (isRepeatedKey) return@onPreviewKeyEvent true
                        if (isPlaying) viewModel.togglePlayPause()
                        showControls = true
                        true
                    }
                    Key.MediaRewind,
                    Key.MediaSkipBackward -> {
                        // Handled above by tvRemoteSeekDirection (supports hold-to-seek)
                        true
                    }
                    Key.MediaFastForward,
                    Key.MediaSkipForward -> {
                        // Handled above by tvRemoteSeekDirection (supports hold-to-seek)
                        true
                    }
                    Key.MediaPrevious -> {
                        if (isRepeatedKey) return@onPreviewKeyEvent true
                        viewModel.playPrevious()
                        showControls = true
                        true
                    }
                    Key.MediaNext -> {
                        if (isRepeatedKey) return@onPreviewKeyEvent true
                        viewModel.playNext()
                        showControls = true
                        true
                    }
                    // Volume keys — mirrors reference Player-master KEYCODE_VOLUME_UP/DOWN handling
                    Key.VolumeUp -> {
                        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
                        audioManager.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_RAISE,
                            AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
                        )
                        true
                    }
                    Key.VolumeDown -> {
                        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
                        audioManager.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_LOWER,
                            AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
                        )
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
                        // Seek only when the video surface owns focus; otherwise let DPAD move between controls.
                        // Handled above by tvRemoteSeekDirection for hold-to-seek support.
                        false
                    }
                    Key.DirectionRight -> {
                        // Seek only when the video surface owns focus; otherwise let DPAD move between controls.
                        // Handled above by tvRemoteSeekDirection for hold-to-seek support.
                        false
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
                createCompatiblePlayerView(it).apply {
                    this.player = player
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    applyNetflixSubtitleStyle(subtitleSize, isTv = true)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    playerView = this
                }
            },
            update = { view ->
                if (view.player !== player) {
                    view.player = player
                }
                if (view.resizeMode != resizeMode) {
                    view.resizeMode = resizeMode
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Seek overlay — shows current position + accumulated delta like "20:28 [+04:20]"
        if (showSeekOverlay) {
            Box(
                modifier = androidx.compose.ui.Modifier
                    .align(Alignment.Center)
                    .background(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 32.dp, vertical = 18.dp)
            ) {
                val deltaSign = if (seekDeltaMs >= 0) "+" else "-"
                val absDelta = kotlin.math.abs(seekDeltaMs)
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        color = Color.White,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "[$deltaSign${formatTime(absDelta)}]",
                        color = TvAccent,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (playbackErrorMessage != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.64f)
                    .padding(24.dp),
                color = TvPanel,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Video format not supported",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = playbackErrorMessage,
                        style = MaterialTheme.typography.titleMedium,
                        color = TvMuted
                    )
                }
            }
        }

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
                onBackPressed = onBackPressed,
                audioFocusRequester = audioFocusRequester,
                subtitleFocusRequester = subtitleFocusRequester,
                settingsFocusRequester = settingsFocusRequester,
                onAudioClick = {
                    focusToRestore = audioFocusRequester
                    showAudioPopup = true
                    showControls = true
                },
                onSubtitleClick = {
                    focusToRestore = subtitleFocusRequester
                    showSubtitlePopup = true
                    showControls = true
                },
                onSettingsClick = {
                    focusToRestore = settingsFocusRequester
                    showSettings = true
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
                isPlaying = isPlaying,
                playFocusRequester = playFocusRequester,
                onSeekTo = {
                    viewModel.seekTo(it)
                    showControls = true
                    controlsInteractionTrigger++
                },
                onPrevious = {
                    viewModel.playPrevious()
                    showControls = true
                    controlsInteractionTrigger++
                },
                onRewind = {
                    viewModel.seekBy(-10_000)
                    showControls = true
                    controlsInteractionTrigger++
                },
                onPlayPause = {
                    viewModel.togglePlayPause()
                    showControls = true
                    controlsInteractionTrigger++
                },
                onForward = {
                    viewModel.seekBy(10_000)
                    showControls = true
                    controlsInteractionTrigger++
                },
                onNext = {
                    viewModel.playNext()
                    showControls = true
                    controlsInteractionTrigger++
                },
                onScreenSize = {
                    val nextMode = nextResizeMode(resizeMode)
                    viewModel.setResizeMode(nextMode)
                    val modeName = resizeModeOptions.find { it.first == nextMode }?.second ?: "Default"
                    android.widget.Toast.makeText(context, "Screen Size: $modeName", android.widget.Toast.LENGTH_SHORT).show()
                    showControls = true
                    controlsInteractionTrigger++
                }
            )
        }

    }

    if (showAudioPopup) {
        // Pause player and force-refresh track list when popup opens.
        // Bug 2 fix (Android TV 9): ExoPlayer on API 28 may fire onTracksChanged before all
        // tracks are parsed; refreshTracks() re-reads directly from the current player state.
        LaunchedEffect(Unit) {
            viewModel.playerManager.pause()
            viewModel.refreshTracks()
        }
        TvAudioTracksPopup(
            audioTracks = audioTracks,
            onRefreshTracks = { viewModel.refreshTracks() },
            onDismiss = {
                showAudioPopup = false
                viewModel.playerManager.resume()
            },
            onAudioSelect = {
                viewModel.selectAudioTrack(it)
                showAudioPopup = false
                viewModel.playerManager.resume()
            }
        )
    }

    if (showSubtitlePopup) {
        // Pause player and force-refresh track list when popup opens.
        // Bug 3 fix (Android TV 9): same issue as audio tracks — re-read from ExoPlayer state.
        LaunchedEffect(Unit) {
            viewModel.playerManager.pause()
            viewModel.refreshTracks()
        }
        TvSubtitleTracksPopup(
            subtitleTracks = subtitleTracks,
            onRefreshTracks = { viewModel.refreshTracks() },
            onDismiss = {
                showSubtitlePopup = false
                viewModel.playerManager.resume()
            },
            onSubtitleSelect = {
                viewModel.selectSubtitleTrack(it)
                showSubtitlePopup = false
                viewModel.playerManager.resume()
            }
        )
    }

    if (showSettings) {
        LaunchedEffect(Unit) { viewModel.playerManager.pause() }
        TvPlayerSettingsDialog(
            onDismiss = {
                showSettings = false
                viewModel.playerManager.resume()
            },
            playbackSpeed = playbackSpeed,
            onSpeedChange = {
                viewModel.setPlaybackSpeed(it)
                showControls = true
                controlsInteractionTrigger++
            },
            volumeBoost = volumeBoost,
            onVolumeBoostChange = {
                viewModel.setVolumeBoost(it)
                showControls = true
                controlsInteractionTrigger++
            },
            repeatMode = repeatMode,
            onRepeatModeChange = {
                viewModel.toggleRepeatMode()
                showControls = true
                controlsInteractionTrigger++
            }
        )
    }
}

@Composable
private fun TvPlayerTopBar(
    title: String,
    onBackPressed: () -> Unit,
    audioFocusRequester: FocusRequester,
    subtitleFocusRequester: FocusRequester,
    settingsFocusRequester: FocusRequester,
    onAudioClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 44.dp, vertical = 26.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvIconOnlyButton(icon = Icons.Rounded.ArrowBack, contentDescription = "Back", onClick = onBackPressed)
            Spacer(modifier = Modifier.width(18.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.padding(start = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvIconOnlyButton(icon = Icons.Rounded.Audiotrack, contentDescription = "Audio language", focusRequester = audioFocusRequester, onClick = onAudioClick)
            TvIconOnlyButton(icon = Icons.Rounded.Subtitles, contentDescription = "Subtitles", focusRequester = subtitleFocusRequester, onClick = onSubtitleClick)
            TvIconOnlyButton(icon = Icons.Rounded.Settings, contentDescription = "Settings", focusRequester = settingsFocusRequester, onClick = onSettingsClick)
        }
    }
}

@Composable
private fun TvIconOnlyButton(
    icon: ImageVector,
    contentDescription: String,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val requesterModifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(38.dp)
            .then(requesterModifier)
            .clip(CircleShape)
            .background(if (isFocused) TvAccent else Color.White.copy(alpha = 0.12f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.16f),
                shape = CircleShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.key.isTvSelectKey()) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        onClick()
                    }
                    true
                } else {
                    false
                }
            }
            .focusable()
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(20.dp))
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
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TvRoundControlButton(
            icon = Icons.Rounded.Replay10,
            contentDescription = "Rewind 10 seconds",
            size = 68.dp,
            onClick = onRewind
        )
        TvRoundControlButton(
            icon = Icons.Rounded.SkipPrevious,
            contentDescription = "Previous video",
            size = 62.dp,
            onClick = onPrevious
        )
        TvRoundControlButton(
            icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Resume",
            size = 88.dp,
            focusRequester = playFocusRequester,
            emphasized = true,
            onClick = onPlayPause
        )
        TvRoundControlButton(
            icon = Icons.Rounded.SkipNext,
            contentDescription = "Next video",
            size = 62.dp,
            onClick = onNext
        )
        TvRoundControlButton(
            icon = Icons.Rounded.Forward10,
            contentDescription = "Forward 10 seconds",
            size = 68.dp,
            onClick = onForward
        )
    }
}

@Composable
private fun TvRoundControlButton(
    icon: ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    emphasized: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val iconSize = when {
        emphasized -> 46.dp
        else -> 32.dp
    }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.25f else 1f,
        animationSpec = tween(150),
        label = "tv-control-scale"
    )
    val requesterModifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .then(modifier)
            .then(requesterModifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(
                when {
                    isFocused -> TvAccent
                    emphasized -> Color.White.copy(alpha = 0.16f)
                    else -> Color.White.copy(alpha = 0.08f)
                }
            )
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.16f),
                shape = CircleShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.key.isTvSelectKey()) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        onClick()
                    }
                    true
                } else {
                    false
                }
            }
            .focusable()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun TvProgressBar(
    currentPosition: Long,
    duration: Long,
    isPlaying: Boolean,
    playFocusRequester: FocusRequester,
    onSeekTo: (Long) -> Unit,
    onPrevious: () -> Unit,
    onRewind: () -> Unit,
    onPlayPause: () -> Unit,
    onForward: () -> Unit,
    onNext: () -> Unit,
    onScreenSize: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 58.dp, end = 58.dp, bottom = 48.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(currentPosition),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 16.dp)
            )
            
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
                    .weight(1f)
                    .focusProperties { canFocus = false },
                colors = SliderDefaults.colors(
                    thumbColor = TvAccent,
                    activeTrackColor = Color.White.copy(alpha = 0.5f),
                    inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                )
            )
            
            Text(
                text = formatTime(duration),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvRoundControlButton(
                    icon = Icons.Rounded.Replay10,
                    contentDescription = "Rewind 10 seconds",
                    size = 52.dp,
                    onClick = onRewind
                )
                TvRoundControlButton(
                    icon = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous video",
                    size = 52.dp,
                    onClick = onPrevious
                )
                TvRoundControlButton(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Resume",
                    size = 68.dp,
                    focusRequester = playFocusRequester,
                    emphasized = true,
                    onClick = onPlayPause
                )
                TvRoundControlButton(
                    icon = Icons.Rounded.SkipNext,
                    contentDescription = "Next video",
                    size = 52.dp,
                    onClick = onNext
                )
                TvRoundControlButton(
                    icon = Icons.Rounded.Forward10,
                    contentDescription = "Forward 10 seconds",
                    size = 52.dp,
                    onClick = onForward
                )
                Spacer(modifier = Modifier.width(16.dp))
                TvRoundControlButton(
                    icon = Icons.Rounded.AspectRatio,
                    contentDescription = "Screen size",
                    size = 52.dp,
                    onClick = onScreenSize
                )
            }
        }
    }
}

@Composable
private fun TvAudioTracksPopup(
    audioTracks: List<AudioTrackInfo>,
    onRefreshTracks: () -> Unit,
    onDismiss: () -> Unit,
    onAudioSelect: (String) -> Unit
) {
    BackHandler(onBack = onDismiss)
    val firstOptionFocusRequester = remember { FocusRequester() }

    // Bug 2 fix (Android TV 9): If tracks are empty when popup opens, retry fetching them.
    // ExoPlayer on API 28 sometimes fires onTracksChanged before all tracks are parsed.
    LaunchedEffect(Unit) {
        repeat(3) { attempt ->
            if (audioTracks.isEmpty()) {
                delay(if (attempt == 0) 300L else 600L)
                onRefreshTracks()
            } else {
                return@repeat
            }
        }
    }

    LaunchedEffect(audioTracks) {
        if (audioTracks.isNotEmpty()) {
            delay(100)
            runCatching { firstOptionFocusRequester.requestFocus() }
        }
    }

    TvSmallPopup(title = "Audio Language", icon = Icons.Rounded.Audiotrack, onDismiss = onDismiss) {
        if (audioTracks.isEmpty()) {
            item { DialogHint("No alternate audio tracks") }
        } else {
            items(audioTracks, key = { it.id }) { track ->
                TvTrackOption(
                    label = track.displayLabel,
                    selected = track.isSelected,
                    focusRequester = firstOptionFocusRequester.takeIf { track == audioTracks.first() },
                    onClick = { onAudioSelect(track.id) }
                )
            }
        }
    }
}

@Composable
private fun TvSubtitleTracksPopup(
    subtitleTracks: List<SubtitleTrackInfo>,
    onRefreshTracks: () -> Unit,
    onDismiss: () -> Unit,
    onSubtitleSelect: (String?) -> Unit
) {
    BackHandler(onBack = onDismiss)
    val firstOptionFocusRequester = remember { FocusRequester() }

    // Bug 3 fix (Android TV 9): Retry fetching subtitle tracks if empty when popup opens.
    LaunchedEffect(Unit) {
        repeat(3) { attempt ->
            if (subtitleTracks.isEmpty()) {
                delay(if (attempt == 0) 300L else 600L)
                onRefreshTracks()
            } else {
                return@repeat
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { firstOptionFocusRequester.requestFocus() }
    }

    TvSmallPopup(title = "Subtitles", icon = Icons.Rounded.Subtitles, onDismiss = onDismiss) {
        item {
            TvTrackOption(
                label = "Off",
                selected = subtitleTracks.none { it.isSelected },
                focusRequester = firstOptionFocusRequester,
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
    }
}

@Composable
private fun TvSmallPopup(
    title: String,
    icon: ImageVector,
    onDismiss: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    val popupFocusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(popupFocusRequester)
            .focusProperties { canFocus = true }
            .background(Color.Black.copy(alpha = 0.58f))
            .onPreviewKeyEvent { event ->
                when {
                    event.type != KeyEventType.KeyDown -> false
                    event.key == Key.Back || event.key == Key.Escape -> {
                        onDismiss()
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .clickable(onClick = { }),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(420.dp)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TvPanel),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    DialogSectionHeader(icon = icon, title = title)
                    Spacer(modifier = Modifier.height(8.dp))
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.15f))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                content()
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    TvCloseButton(onClick = onDismiss)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TvPlayerSettingsDialog(
    onDismiss: () -> Unit,
    playbackSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    volumeBoost: Int,
    onVolumeBoostChange: (Int) -> Unit,
    repeatMode: Int,
    onRepeatModeChange: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    val dialogFocusRequester = remember { FocusRequester() }
    val firstSettingFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { firstSettingFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(dialogFocusRequester)
            .focusProperties { canFocus = true }
            .background(Color.Black.copy(alpha = 0.78f))
            .onPreviewKeyEvent { event ->
                when {
                    event.type != KeyEventType.KeyDown -> false
                    event.key == Key.Back || event.key == Key.Escape -> {
                        onDismiss()
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .clickable(onClick = { }),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(460.dp)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TvPanel),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "Player Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.15f))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    DialogSectionHeader(icon = Icons.Rounded.Speed, title = "Playback Speed")
                    Spacer(modifier = Modifier.height(8.dp))
                    TvOptionFlow {
                        playbackSpeedOptions.forEachIndexed { index, speed ->
                            TvSettingsChip(
                                label = if (speed == 1.0f) "Normal" else "${speed}x",
                                selected = playbackSpeed == speed,
                                focusRequester = firstSettingFocusRequester.takeIf { index == 0 },
                                onClick = { onSpeedChange(speed) }
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    DialogSectionHeader(icon = Icons.Rounded.Repeat, title = "Repeat Mode")
                    Spacer(modifier = Modifier.height(8.dp))
                    TvOptionFlow {
                        TvSettingsChip(
                            label = "Off",
                            selected = repeatMode == 0,
                            onClick = { if (repeatMode != 0) onRepeatModeChange() }
                        )
                        TvSettingsChip(
                            label = "One",
                            selected = repeatMode == 1,
                            onClick = { if (repeatMode != 1) onRepeatModeChange() }
                        )
                        TvSettingsChip(
                            label = "All",
                            selected = repeatMode == 2,
                            onClick = { if (repeatMode != 2) onRepeatModeChange() }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    DialogSectionHeader(icon = Icons.Rounded.VolumeUp, title = "Volume Boost (Loudness)")
                    Spacer(modifier = Modifier.height(8.dp))
                    TvOptionFlow {
                        TvSettingsChip(
                            label = "Off",
                            selected = volumeBoost == 0,
                            onClick = { onVolumeBoostChange(0) }
                        )
                        TvSettingsChip(
                            label = "+2 dB",
                            selected = volumeBoost == 1,
                            onClick = { onVolumeBoostChange(1) }
                        )
                        TvSettingsChip(
                            label = "+6 dB",
                            selected = volumeBoost == 3,
                            onClick = { onVolumeBoostChange(3) }
                        )
                        TvSettingsChip(
                            label = "+12 dB",
                            selected = volumeBoost == 6,
                            onClick = { onVolumeBoostChange(6) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    TvCloseButton(onClick = onDismiss)
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

@Composable
private fun TvCloseButton(onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = tween(120),
        label = "tv-close-scale"
    )
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) TvAccent.copy(alpha = 0.9f) else TvAccent,
            contentColor = Color.White
        ),
        border = BorderStroke(
            width = if (isFocused) 3.dp else 1.dp,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.key.isTvSelectKey()) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        onClick()
                    }
                    true
                } else {
                    false
                }
            }
            .focusable(),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        Text("Close", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
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
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val requesterModifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1f,
        animationSpec = tween(120),
        label = "tv-chip-scale"
    )

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
            selectedBorderColor = if (isFocused) Color.White else Color.White.copy(alpha = 0.22f),
            borderWidth = if (isFocused) 3.dp else 1.dp,
            selectedBorderWidth = if (isFocused) 3.dp else 1.dp
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .heightIn(min = 52.dp)
            .then(requesterModifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.key.isTvSelectKey()) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        onClick()
                    }
                    true
                } else {
                    false
                }
            }
            .focusable()
    )
}

@Composable
private fun TvTrackOption(
    label: String,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val requesterModifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = tween(120),
        label = "tv-option-scale"
    )

    Surface(
        color = when {
            isFocused -> Color.White
            selected -> Color.Transparent
            else -> Color.White.copy(alpha = 0.06f)
        },
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isFocused) 3.dp else if (selected) 2.dp else 1.dp,
            color = if (isFocused) TvAccent else if (selected) TvAccent else Color.White.copy(alpha = 0.12f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(requesterModifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.key.isTvSelectKey()) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        onClick()
                    }
                    true
                } else {
                    false
                }
            }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (selected) "Selected" else "Not Selected",
                tint = if (isFocused) TvPanel else if (selected) TvAccent else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = if (isFocused) TvPanel else Color.White,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private val playbackSpeedOptions = listOf(0.75f, 1.0f, 1.25f, 1.5f)

private val resizeModeOptions: List<Pair<Int, String>> = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "Original",
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
    AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT to "Fill",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch"
)

private fun nextResizeMode(currentMode: Int): Int {
    val modes = resizeModeOptions.map { it.first }
    val currentIndex = modes.indexOf(currentMode).takeIf { it >= 0 } ?: 0
    return modes[(currentIndex + 1) % modes.size]
}

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

private fun Key.isTvSelectKey(): Boolean {
    return this == Key.DirectionCenter ||
        this == Key.Enter ||
        this == Key.NumPadEnter
}

internal fun shouldTvDpadSeek(showControls: Boolean, rootHasFocus: Boolean): Boolean {
    return !showControls || rootHasFocus
}

internal fun tvRemoteSeekDirection(
    keyCode: Int,
    showControls: Boolean,
    rootHasFocus: Boolean
): Int {
    return when (keyCode) {
        AndroidKeyEvent.KEYCODE_MEDIA_REWIND,
        AndroidKeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
        AndroidKeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
        AndroidKeyEvent.KEYCODE_BUTTON_L2 -> -1

        AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        AndroidKeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
        AndroidKeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
        AndroidKeyEvent.KEYCODE_BUTTON_R2 -> 1

        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
            if (shouldTvDpadSeek(showControls, rootHasFocus)) -1 else 0
        }

        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
            if (shouldTvDpadSeek(showControls, rootHasFocus)) 1 else 0
        }

        else -> 0
    }
}

private fun androidx.compose.ui.input.key.KeyEvent.isTvSelectDown(): Boolean {
    return type == KeyEventType.KeyDown && key.isTvSelectKey()
}

private fun androidx.compose.ui.input.key.KeyEvent.isInitialTvSelectDown(): Boolean {
    return isTvSelectDown() && nativeKeyEvent.repeatCount == 0
}

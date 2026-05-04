package com.mplayer.videoplayer.mobile.ui.screen.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import kotlin.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mplayer.videoplayer.core.model.AudioTrackInfo
import com.mplayer.videoplayer.core.model.SubtitleTrackInfo
import com.mplayer.videoplayer.common.ui.player.SubtitleSize
import com.mplayer.videoplayer.common.ui.player.applyNetflixSubtitleStyle
import com.mplayer.videoplayer.mobile.viewmodel.MobilePlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

private val TransparentBlack = Color.Black.copy(alpha = 0.5f)
private val DeepBlack = Color(0xFF0C0C0C)
private val PrimaryAccent = Color(0xFF3F51B5) // Indigo
private val PlaybackGreen = Color(0xFF20B33A)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    viewModel: MobilePlayerViewModel,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val player = viewModel.playerManager.getPlayer()
    val showControls by viewModel.showControls.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val currentTitle by viewModel.currentTitle.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val brightness by viewModel.brightness.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isLocked by viewModel.isLocked.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val resizeMode by viewModel.resizeMode.collectAsState()

    val orientationMode by viewModel.orientationMode.collectAsState()
    val isInPipMode by viewModel.isInPipMode.collectAsState()
    val audioTracks by viewModel.audioTracks.collectAsState()
    val subtitleTracks by viewModel.subtitleTracks.collectAsState()
    
    var showSettings by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showVolumeDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var isNightMode by remember { mutableStateOf(false) }
    var subtitleSize by remember { mutableStateOf(SubtitleSize.Medium) }
    var gestureInfo by remember { mutableStateOf<String?>(null) }
    var gestureIcon by remember { mutableStateOf<ImageVector?>(null) }
    var activeGesture by remember { mutableStateOf<GestureType?>(null) }
    
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBackPressed)

    LaunchedEffect(brightness) {
        val activity = context as? Activity
        activity?.window?.attributes?.let {
            it.screenBrightness = brightness
            activity.window.attributes = it
        }
    }

    LaunchedEffect(orientationMode) {
        (context as? Activity)?.requestedOrientation = orientationMode
    }

    LaunchedEffect(showControls) {
        if (!showControls) {
            showSettings = false
            showAudioDialog = false
            showVolumeDialog = false
            showSubtitleDialog = false
        }
    }

    DisposableEffect(context, showControls, isInPipMode) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (window != null && controller != null) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (!showControls && !isInPipMode) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(context, isPlaying, isInPipMode) {
        val window = (context as? Activity)?.window
        if (isPlaying && !isInPipMode) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

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

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopPlayback()
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .background(Color.Black)
            .pointerInput(isLocked, isInPipMode) {
                if (isLocked || isInPipMode) return@pointerInput
                
                var totalDragAmountVertical = 0f
                var totalDragAmountHorizontal = 0f
                var dragDirection: DragDirection? = null

                detectDragGestures(
                    onDragStart = {
                        dragDirection = null
                        totalDragAmountVertical = 0f
                        totalDragAmountHorizontal = 0f
                    },
                    onDragEnd = {
                        scope.launch {
                            delay(1000)
                            activeGesture = null
                            gestureInfo = null
                            gestureIcon = null
                        }
                    },
                    onDragCancel = {
                        activeGesture = null
                        gestureInfo = null
                        gestureIcon = null
                    },
                    onDrag = { change, dragAmount ->
                        if (dragDirection == null) {
                            totalDragAmountVertical += Math.abs(dragAmount.y)
                            totalDragAmountHorizontal += Math.abs(dragAmount.x)
                            
                            if (totalDragAmountVertical > 20f) {
                                dragDirection = DragDirection.VERTICAL
                                viewModel.resetHideTimer() // Keep controls visible if they are
                            } else if (totalDragAmountHorizontal > 20f) {
                                dragDirection = DragDirection.HORIZONTAL
                                viewModel.resetHideTimer()
                            }
                        }

                        if (dragDirection == DragDirection.VERTICAL) {
                            val isLeftSide = change.position.x < size.width / 2
                            val delta = -dragAmount.y / size.height
                            if (isLeftSide) {
                                activeGesture = GestureType.BRIGHTNESS
                                viewModel.adjustBrightness(delta)
                                gestureIcon = Icons.Rounded.LightMode
                            } else {
                                activeGesture = GestureType.VOLUME
                                viewModel.adjustVolume(delta)
                                gestureIcon = Icons.Rounded.VolumeUp
                            }
                        } else if (dragDirection == DragDirection.HORIZONTAL) {
                            activeGesture = null
                            val seekAmount = (dragAmount.x * 100).toLong()
                            viewModel.seekTo(currentPosition + seekAmount)
                            gestureInfo = formatTime(currentPosition)
                            gestureIcon = Icons.Rounded.FastForward
                        }
                    }
                )
            }
            .pointerInput(isLocked, isInPipMode) {
                if (isInPipMode) return@pointerInput
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (!isLocked) {
                            val isLeftSide = offset.x < size.width / 2
                            if (isLeftSide) {
                                viewModel.seekTo(currentPosition - 10000)
                                gestureInfo = "-10s"
                                gestureIcon = Icons.Rounded.Replay10
                            } else {
                                viewModel.seekTo(currentPosition + 10000)
                                gestureInfo = "+10s"
                                gestureIcon = Icons.Rounded.Forward10
                            }
                            scope.launch {
                                delay(800)
                                gestureInfo = null
                                gestureIcon = null
                            }
                        }
                    },
                    onTap = { viewModel.toggleControls() }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    keepScreenOn = true
                    useController = false
                    applyNetflixSubtitleStyle(subtitleSize, isTv = false)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.player = player
                view.keepScreenOn = isPlaying && !isInPipMode
                view.resizeMode = resizeMode
                view.applyNetflixSubtitleStyle(subtitleSize, isTv = false)
            },
        )
        
        if (!isInPipMode) {
            // Gesture Feedback UI
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = activeGesture == GestureType.BRIGHTNESS,
                enter = fadeIn() + slideInHorizontally(),
                exit = fadeOut() + slideOutHorizontally()
            ) {
                VerticalPillIndicator(
                    value = brightness,
                    icon = Icons.Rounded.LightMode
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Right Side: Volume
            AnimatedVisibility(
                visible = activeGesture == GestureType.VOLUME,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it })
            ) {
                VerticalPillIndicator(
                    value = volume,
                    icon = Icons.Rounded.VolumeUp,
                    fillBrush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFD500F9), Color(0xFF2979FF))
                    )
                )
            }
        }

        // Horizontal Gesture Feedback (Seek)
        AnimatedVisibility(
            visible = gestureInfo != null && activeGesture == null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                gestureIcon?.let {
                    Icon(
                        it, 
                        contentDescription = null, 
                        tint = Color.White, 
                        modifier = Modifier.size(64.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = gestureInfo ?: "",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Overlay Controls (Smooth Transitions)
        // 1. Background Gradient
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(400))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.7f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )
        }

        if (!isLocked) {
            // 2. Top Bar
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 10.dp, end = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBackPressed,
                            modifier = Modifier
                                .size(38.dp)
                                .background(TransparentBlack, CircleShape)
                        ) {
                            Icon(
                                Icons.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        
                        Text(
                            text = currentTitle.ifBlank { "Now Playing" },
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        
                        IconButton(
                            onClick = { 
                                showSettings = true
                                viewModel.resetHideTimer()
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .background(TransparentBlack, CircleShape)
                        ) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    QuickOptionsBar(
                        isMuted = isMuted,
                        isNightMode = isNightMode,
                        onSubtitleClick = {
                            showSubtitleDialog = true
                            viewModel.resetHideTimer()
                        },
                        onSpeedClick = {
                            showSettings = true
                            viewModel.resetHideTimer()
                        },
                        onAudioClick = {
                            showAudioDialog = true
                            viewModel.resetHideTimer()
                        },
                        onVolumeClick = {
                            showVolumeDialog = true
                            viewModel.resetHideTimer()
                        },
                        onMuteClick = {
                            viewModel.toggleMute()
                            viewModel.resetHideTimer()
                        },
                        onRotateClick = {
                            viewModel.toggleOrientation()
                            viewModel.resetHideTimer()
                        },
                        onPopupClick = {
                            enterPictureInPicture(context)
                            viewModel.resetHideTimer()
                        },
                        onNightClick = {
                            isNightMode = !isNightMode
                            viewModel.resetHideTimer()
                        }
                    )
                }
            }

            // 3. Bottom playback controls
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                BottomPlaybackControls(
                    isPlaying = isPlaying,
                    resizeMode = resizeMode,
                    currentPosition = currentPosition,
                    duration = duration,
                    onLockClick = {
                        viewModel.toggleLock()
                    },
                    onSeekTo = {
                        viewModel.seekTo(it)
                        viewModel.resetHideTimer()
                    },
                    onRewindClick = {
                        viewModel.seekTo((currentPosition - 10000).coerceAtLeast(0L))
                        viewModel.resetHideTimer()
                    },
                    onPreviousClick = {
                        viewModel.playPrevious()
                        viewModel.resetHideTimer()
                    },
                    onPlayPauseClick = {
                        viewModel.togglePlayPause()
                        viewModel.resetHideTimer()
                    },
                    onNextClick = {
                        viewModel.playNext()
                        viewModel.resetHideTimer()
                    },
                    onForwardClick = {
                        viewModel.seekTo((currentPosition + 10000).coerceAtMost(duration))
                        viewModel.resetHideTimer()
                    },
                    onAspectRatioClick = {
                        viewModel.setResizeMode(nextResizeMode(resizeMode))
                        viewModel.resetHideTimer()
                    }
                )
            }
        } else {
            // Locked State UI
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    IconButton(
                        onClick = { 
                            viewModel.toggleLock()
                            viewModel.resetHideTimer()
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(24.dp)
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = "Unlock", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }

        if (isNightMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
            )
        }
    }
}

    if (showVolumeDialog && !isInPipMode) {
        ValueSliderDialog(
            title = "Volume",
            icon = if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
            value = volume,
            onValueChange = { viewModel.setVolume(it) },
            onDismiss = { showVolumeDialog = false }
        )
    }

    if (showAudioDialog && !isInPipMode) {
        AudioLanguageDialog(
            audioTracks = audioTracks,
            onAudioTrackSelect = {
                viewModel.selectAudioTrack(it)
                showAudioDialog = false
            },
            onDismiss = { showAudioDialog = false }
        )
    }

    if (showSettings && !isInPipMode) {
        AdvancedSettingsDialog(
            currentSpeed = playbackSpeed,
            onSpeedChange = { viewModel.setPlaybackSpeed(it) },
            resizeMode = resizeMode,
            onResizeModeChange = { viewModel.setResizeMode(it) },
            audioTracks = audioTracks,
            onAudioTrackSelect = { viewModel.selectAudioTrack(it) },
            onSleepClick = { /* Sleep logic */ },
            onDismiss = { showSettings = false }
        )
    }

    if (showSubtitleDialog && !isInPipMode) {
        SubtitleSelectionDialog(
            subtitleTracks = subtitleTracks,
            selectedSize = subtitleSize,
            onSubtitleSizeChange = { subtitleSize = it },
            onSubtitleTrackSelect = { 
                viewModel.selectSubtitleTrack(it)
                showSubtitleDialog = false
            },
            onDismiss = { showSubtitleDialog = false }
        )
    }
}

@Composable
fun BottomPlaybackControls(
    isPlaying: Boolean,
    resizeMode: Int,
    currentPosition: Long,
    duration: Long,
    onLockClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onRewindClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onForwardClick: () -> Unit,
    onAspectRatioClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 10.dp, end = 10.dp, bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaybackTimeText(formatTime(currentPosition))
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
                    .height(24.dp)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = PlaybackGreen,
                    activeTrackColor = PlaybackGreen,
                    inactiveTrackColor = Color.White.copy(alpha = 0.48f)
                )
            )
            PlaybackTimeText(formatTime(duration))
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomControlButton(
                icon = Icons.Rounded.Lock,
                contentDescription = "Lock",
                onClick = onLockClick
            )
            BottomControlButton(
                icon = Icons.Rounded.Replay10,
                contentDescription = "Rewind 10 seconds",
                onClick = onRewindClick
            )
            BottomControlButton(
                icon = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                onClick = onPreviousClick
            )
            BottomPlayPauseButton(
                isPlaying = isPlaying,
                onClick = onPlayPauseClick
            )
            BottomControlButton(
                icon = Icons.Rounded.SkipNext,
                contentDescription = "Next",
                onClick = onNextClick
            )
            BottomControlButton(
                icon = Icons.Rounded.Forward10,
                contentDescription = "Forward 10 seconds",
                onClick = onForwardClick
            )
            BottomControlButton(
                icon = resizeModeIcon(resizeMode),
                contentDescription = resizeModeLabel(resizeMode),
                onClick = onAspectRatioClick
            )
        }
    }
}

@Composable
private fun PlaybackTimeText(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun BottomControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(25.dp)
        )
    }
}

@Composable
private fun BottomPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = "Play/Pause",
            tint = Color.White,
            modifier = Modifier.size(42.dp)
        )
    }
}

@Composable
fun QuickOptionsBar(
    isMuted: Boolean,
    isNightMode: Boolean,
    onSubtitleClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onAudioClick: () -> Unit,
    onVolumeClick: () -> Unit,
    onMuteClick: () -> Unit,
    onRotateClick: () -> Unit,
    onPopupClick: () -> Unit,
    onNightClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        QuickOptionItem(Icons.Rounded.Subtitles, "Subtitle", onSubtitleClick)
        QuickOptionItem(Icons.Rounded.Speed, "Speed", onSpeedClick)
        QuickOptionItem(Icons.Rounded.Audiotrack, "Audio", onAudioClick)
        QuickOptionItem(Icons.Rounded.VolumeUp, "Volume", onVolumeClick)
        QuickOptionItem(
            icon = if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeMute,
            label = "Mute",
            onClick = onMuteClick
        )
        QuickOptionItem(Icons.Rounded.ScreenRotation, "Rotate", onRotateClick)
        QuickOptionItem(Icons.Rounded.PictureInPicture, "Popup", onPopupClick)
        QuickOptionItem(
            icon = if (isNightMode) Icons.Rounded.Nightlight else Icons.Rounded.DarkMode,
            label = "Night",
            onClick = onNightClick
        )
    }
}

@Composable
fun QuickOptionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(52.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.62f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = Color.White,
            maxLines = 1,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ValueSliderDialog(
    title: String,
    icon: ImageVector,
    value: Float,
    onValueChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = DeepBlack,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, tint = PrimaryAccent, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${(value * 100).toInt()}%",
                        color = Color.White.copy(alpha = 0.76f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryAccent,
                        activeTrackColor = PrimaryAccent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                    )
                )
            }
        }
    }
}

@Composable
fun AdvancedControlOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(text = label, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvancedSettingsDialog(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    resizeMode: Int,
    onResizeModeChange: (Int) -> Unit,
    audioTracks: List<AudioTrackInfo>,
    onAudioTrackSelect: (String) -> Unit,
    onSleepClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.85f else 0.9f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = DeepBlack,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .padding(if (isLandscape) 16.dp else 24.dp)
                    .heightIn(max = if (isLandscape) 300.dp else 600.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Settings, 
                        contentDescription = null, 
                        tint = PrimaryAccent, 
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Playback Settings", 
                        color = Color.White, 
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                if (isLandscape) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            TracksSection(
                                audioTracks = audioTracks,
                                onAudioTrackSelect = onAudioTrackSelect
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            PlaybackSection(currentSpeed, onSpeedChange)
                            Spacer(modifier = Modifier.height(20.dp))
                            DisplaySection(resizeMode, onResizeModeChange)
                        }
                    }
                } else {
                    TracksSection(
                        audioTracks = audioTracks,
                        onAudioTrackSelect = onAudioTrackSelect
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 16.dp))
                    PlaybackSection(currentSpeed, onSpeedChange)
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 16.dp))
                    DisplaySection(resizeMode, onResizeModeChange)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TracksSection(
    audioTracks: List<AudioTrackInfo>,
    onAudioTrackSelect: (String) -> Unit
) {
    if (audioTracks.isNotEmpty()) {
        SettingsSectionHeader(Icons.Rounded.AudioFile, "Audio Tracks")
        
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            audioTracks.forEach { track ->
                SettingsChip(
                    selected = track.isSelected,
                    label = track.displayLabel,
                    onClick = { onAudioTrackSelect(track.id) }
                )
            }
        }
    }
}

@Composable
fun PlaybackSection(currentSpeed: Float, onSpeedChange: (Float) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically, 
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SettingsSectionHeader(Icons.Rounded.Speed, "Speed")
        SettingsChip(
            selected = currentSpeed == 1.0f,
            label = "Normal",
            onClick = { onSpeedChange(1.0f) }
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = currentSpeed,
            onValueChange = { onSpeedChange(it) },
            valueRange = 0.25f..3.0f,
            colors = SliderDefaults.colors(
                thumbColor = PrimaryAccent,
                activeTrackColor = PrimaryAccent,
                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
            ),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .background(PrimaryAccent.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .widthIn(min = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${"%.2f".format(currentSpeed)}x",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplaySection(resizeMode: Int, onResizeModeChange: (Int) -> Unit) {
    SettingsSectionHeader(Icons.Rounded.AspectRatio, "Aspect Ratio")
    Spacer(modifier = Modifier.height(12.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        resizeModeOptions.forEach { (mode, label) ->
            SettingsChip(
                selected = resizeMode == mode,
                label = label,
                onClick = { onResizeModeChange(mode) }
            )
        }
    }
}

private val resizeModeOptions: List<Pair<Int, String>> = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "Original",
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
    AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT to "Fill",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch"
)

private fun nextResizeMode(currentMode: Int): Int {
    val currentIndex = resizeModeOptions.indexOfFirst { it.first == currentMode }
    val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % resizeModeOptions.size
    return resizeModeOptions[nextIndex].first
}

private fun resizeModeLabel(mode: Int): String {
    return resizeModeOptions.firstOrNull { it.first == mode }?.second ?: "Aspect ratio"
}

private fun resizeModeIcon(mode: Int): ImageVector {
    return when (mode) {
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH -> Icons.Rounded.CropFree
        AspectRatioFrameLayout.RESIZE_MODE_FIT -> Icons.Rounded.FitScreen
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT -> Icons.Rounded.Fullscreen
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> Icons.Rounded.ZoomOutMap
        AspectRatioFrameLayout.RESIZE_MODE_FILL -> Icons.Rounded.OpenInFull
        else -> Icons.Rounded.AspectRatio
    }
}

@Composable
fun SettingsSectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = PrimaryAccent, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { 
            Text(
                label, 
                color = if (selected) Color.White else Color.Gray,
                style = MaterialTheme.typography.labelLarge
            ) 
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PrimaryAccent,
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        border = null,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.heightIn(min = 36.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioLanguageDialog(
    audioTracks: List<AudioTrackInfo>,
    onAudioTrackSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val dialogWidth = if (isLandscape) 0.52f else 0.9f
    val rowMinHeight = if (isLandscape) 42.dp else 50.dp

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(dialogWidth)
                .wrapContentHeight()
                .heightIn(max = if (isLandscape) 360.dp else 560.dp)
                .padding(if (isLandscape) 10.dp else 16.dp),
            shape = RoundedCornerShape(if (isLandscape) 22.dp else 28.dp),
            color = DeepBlack,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(if (isLandscape) 16.dp else 22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isLandscape) 36.dp else 42.dp)
                            .clip(CircleShape)
                            .background(PrimaryAccent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Audiotrack,
                            contentDescription = null,
                            tint = PrimaryAccent,
                            modifier = Modifier.size(if (isLandscape) 20.dp else 22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Audio Language",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (isLandscape) 18.sp else 20.sp
                        )
                        Text(
                            if (audioTracks.isEmpty()) "No alternate audio tracks found" else "${audioTracks.size} tracks available",
                            color = Color.White.copy(alpha = 0.58f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.82f))
                    }
                }

                Spacer(modifier = Modifier.height(if (isLandscape) 12.dp else 20.dp))

                if (audioTracks.isEmpty()) {
                    Surface(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Only default audio is available",
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                        )
                    }
                } else {
                    SubtitleSectionTitle(icon = Icons.Rounded.Translate, title = "Language")
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 8.dp)
                    ) {
                        audioTracks.forEach { track ->
                            val rowModifier = if (isLandscape) {
                                Modifier.fillMaxWidth(0.48f)
                            } else {
                                Modifier.fillMaxWidth()
                            }

                            SubtitleTrackRow(
                                title = track.displayLabel,
                                selected = track.isSelected,
                                minHeight = rowMinHeight,
                                modifier = rowModifier,
                                onClick = { onAudioTrackSelect(track.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubtitleSelectionDialog(
    subtitleTracks: List<SubtitleTrackInfo>,
    selectedSize: SubtitleSize,
    onSubtitleSizeChange: (SubtitleSize) -> Unit,
    onSubtitleTrackSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val dialogWidth = if (isLandscape) 0.58f else 0.9f
    val dialogMaxHeight = if (isLandscape) 360.dp else 620.dp
    val contentPadding = if (isLandscape) 16.dp else 22.dp
    val headerIconSize = if (isLandscape) 36.dp else 42.dp
    val headerTextSize = if (isLandscape) 18.sp else 20.sp
    val sectionGap = if (isLandscape) 14.dp else 22.dp
    val rowMinHeight = if (isLandscape) 42.dp else 50.dp

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(dialogWidth)
                .wrapContentHeight()
                .heightIn(max = dialogMaxHeight)
                .padding(if (isLandscape) 10.dp else 16.dp),
            shape = RoundedCornerShape(if (isLandscape) 22.dp else 28.dp),
            color = DeepBlack,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(headerIconSize)
                            .clip(CircleShape)
                            .background(PrimaryAccent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Subtitles,
                            contentDescription = null,
                            tint = PrimaryAccent,
                            modifier = Modifier.size(if (isLandscape) 20.dp else 22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Subtitles",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = headerTextSize
                        )
                        Text(
                            if (subtitleTracks.isEmpty()) "No subtitle tracks found" else "${subtitleTracks.size} tracks available",
                            color = Color.White.copy(alpha = 0.58f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.82f))
                    }
                }

                Spacer(modifier = Modifier.height(if (isLandscape) 12.dp else 20.dp))


                SubtitleSectionTitle(icon = Icons.Rounded.FormatSize, title = "Text Size")
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SubtitleSize.entries.forEach { size ->
                        SettingsChip(
                            selected = selectedSize == size,
                            label = size.name,
                            onClick = { onSubtitleSizeChange(size) }
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = if (isLandscape) 14.dp else 20.dp))

                SubtitleSectionTitle(icon = Icons.Rounded.Translate, title = "Language")
                Spacer(modifier = Modifier.height(8.dp))
                val languageOptions = listOf(
                    Triple("Off", null, subtitleTracks.none { it.isSelected })
                ) + subtitleTracks.map { track ->
                    Triple(track.displayLabel, track.id, track.isSelected)
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 8.dp)
                ) {
                    languageOptions.forEach { (title, trackId, selected) ->
                        val rowModifier = if (isLandscape) {
                            Modifier.fillMaxWidth(0.48f)
                        } else {
                            Modifier.fillMaxWidth()
                        }

                        SubtitleTrackRow(
                            title = title,
                            selected = selected,
                            minHeight = rowMinHeight,
                            modifier = rowModifier,
                            onClick = { onSubtitleTrackSelect(trackId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SubtitleSectionTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = PrimaryAccent, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SubtitleTrackRow(
    title: String,
    selected: Boolean,
    minHeight: androidx.compose.ui.unit.Dp = 50.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) PrimaryAccent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) PrimaryAccent.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.06f)
        ),
        modifier = modifier
            .heightIn(min = minHeight)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = if (minHeight < 50.dp) 9.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = PrimaryAccent, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
fun VerticalPillIndicator(
    value: Float, // 0f to 1f
    icon: ImageVector,
    fillBrush: Brush = Brush.linearGradient(listOf(Color.White, Color.White)),
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(180.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(4.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(value.coerceIn(0.05f, 1f)) // Ensure a tiny bit is always visible
                    .clip(RoundedCornerShape(20.dp))
                    .background(fillBrush)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

enum class GestureType { BRIGHTNESS, VOLUME }
enum class DragDirection { HORIZONTAL, VERTICAL }

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

fun enterPictureInPicture(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val activity = context as? Activity ?: return
    val params = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
        .build()
    activity.enterPictureInPictureMode(params)
}

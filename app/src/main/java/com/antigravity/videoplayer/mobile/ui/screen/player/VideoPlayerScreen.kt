package com.antigravity.videoplayer.mobile.ui.screen.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.antigravity.videoplayer.core.model.AudioTrackInfo
import com.antigravity.videoplayer.core.model.SubtitleTrackInfo
import com.antigravity.videoplayer.common.ui.player.SubtitleSize
import com.antigravity.videoplayer.common.ui.player.applyNetflixSubtitleStyle
import com.antigravity.videoplayer.mobile.viewmodel.MobilePlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.window.DialogProperties

private val TransparentBlack = Color.Black.copy(alpha = 0.5f)
private val DeepBlack = Color(0xFF0C0C0C)
private val PrimaryAccent = Color(0xFF3F51B5) // Indigo

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
    val volume by viewModel.volume.collectAsState()
    val brightness by viewModel.brightness.collectAsState()
    val isLocked by viewModel.isLocked.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val resizeMode by viewModel.resizeMode.collectAsState()

    val orientationMode by viewModel.orientationMode.collectAsState()
    val isInPipMode by viewModel.isInPipMode.collectAsState()
    val audioTracks by viewModel.audioTracks.collectAsState()
    val subtitleTracks by viewModel.subtitleTracks.collectAsState()
    
    var showSettings by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
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
                            activeGesture = null // Ensure volume/brightness pills don't show
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
                view.resizeMode = resizeMode
                view.applyNetflixSubtitleStyle(subtitleSize, isTv = false)
            },
        )
        
        if (!isInPipMode) {
            // Gesture Feedback UI (Pill style for Brightness/Volume)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Side: Brightness
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackPressed,
                        modifier = Modifier.background(TransparentBlack, CircleShape)
                    ) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    
                    Text(
                        text = "Now Playing",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { 
                                viewModel.toggleOrientation()
                                viewModel.resetHideTimer()
                            },
                            modifier = Modifier.background(TransparentBlack, CircleShape)
                        ) {
                            Icon(Icons.Rounded.ScreenRotation, contentDescription = "Rotate", tint = Color.White)
                        }
                        IconButton(
                            onClick = { 
                                viewModel.toggleLock()
                                viewModel.resetHideTimer()
                            },
                            modifier = Modifier.background(TransparentBlack, CircleShape)
                        ) {
                            Icon(Icons.Rounded.LockOpen, contentDescription = "Lock", tint = Color.White)
                        }
                        IconButton(
                            onClick = { 
                                showSubtitleDialog = true
                                viewModel.resetHideTimer()
                            },
                            modifier = Modifier.background(TransparentBlack, CircleShape)
                        ) {
                            Icon(Icons.Rounded.Subtitles, contentDescription = "Subtitles", tint = Color.White)
                        }
                        IconButton(
                            onClick = { 
                                showSettings = true
                                viewModel.resetHideTimer()
                            },
                            modifier = Modifier.background(TransparentBlack, CircleShape)
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.White)
                        }
                    }
                }
            }

            // 3. Middle Controls
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    IconButton(
                        onClick = { 
                            viewModel.seekTo(currentPosition - 10000)
                            viewModel.resetHideTimer()
                        },
                        modifier = Modifier.size(52.dp).background(TransparentBlack, CircleShape)
                    ) {
                        Icon(Icons.Rounded.Replay10, contentDescription = "Rewind", tint = Color.White, modifier = Modifier.size(28.dp))
                    }

                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(PrimaryAccent.copy(alpha = 0.8f))
                            .clickable { 
                                viewModel.togglePlayPause()
                                viewModel.resetHideTimer()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    IconButton(
                        onClick = { 
                            viewModel.seekTo(currentPosition + 10000)
                            viewModel.resetHideTimer()
                        },
                        modifier = Modifier.size(52.dp).background(TransparentBlack, CircleShape)
                    ) {
                        Icon(Icons.Rounded.Forward10, contentDescription = "Fast Forward", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }

            // 4. Bottom Bar
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .padding(bottom = 24.dp, start = 20.dp, end = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPosition), 
                            color = Color.White, 
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onValueChange = { 
                                viewModel.seekTo((it * duration).toLong())
                                viewModel.resetHideTimer()
                            },
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryAccent,
                                activeTrackColor = PrimaryAccent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        
                        Text(
                            text = formatTime(duration), 
                            color = Color.White, 
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
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
    }
}

    if (showSettings && !isInPipMode) {
        AdvancedSettingsDialog(
            currentSpeed = playbackSpeed,
            onSpeedChange = { viewModel.setPlaybackSpeed(it) },
            resizeMode = resizeMode,
            onResizeModeChange = { viewModel.setResizeMode(it) },
            audioTracks = audioTracks,
            onAudioTrackSelect = { viewModel.selectAudioTrack(it) },
            subtitleTracks = subtitleTracks,
            onSubtitleTrackSelect = { viewModel.selectSubtitleTrack(it) },
            onRotateClick = { viewModel.toggleOrientation() },
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
    subtitleTracks: List<SubtitleTrackInfo>,
    onSubtitleTrackSelect: (String?) -> Unit,
    onRotateClick: () -> Unit,
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
                            TracksSection(audioTracks, onAudioTrackSelect)
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            PlaybackSection(currentSpeed, onSpeedChange)
                            Spacer(modifier = Modifier.height(20.dp))
                            DisplaySection(resizeMode, onResizeModeChange)
                        }
                    }
                } else {
                    TracksSection(audioTracks, onAudioTrackSelect)
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
                    label = track.label ?: track.language ?: "Unknown",
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
    val modes = listOf(0 to "Fit", 3 to "Fill", 4 to "Zoom")
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        modes.forEach { (mode, label) ->
            SettingsChip(
                selected = resizeMode == mode,
                label = label,
                onClick = { onResizeModeChange(mode) }
            )
        }
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

@Composable
fun SubtitleSelectionDialog(
    subtitleTracks: List<SubtitleTrackInfo>,
    selectedSize: SubtitleSize,
    onSubtitleSizeChange: (SubtitleSize) -> Unit,
    onSubtitleTrackSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepBlack,
        shape = RoundedCornerShape(28.dp),
        title = { 
            Text(
                "Subtitles", 
                color = Color.White, 
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp
            ) 
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Style",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubtitleSize.entries.forEach { size ->
                        SettingsChip(
                            selected = selectedSize == size,
                            label = size.name,
                            onClick = { onSubtitleSizeChange(size) }
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 16.dp))

                Text(
                    "Language",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                FilterChip(
                    selected = subtitleTracks.none { it.isSelected },
                    onClick = { onSubtitleTrackSelect(null) },
                    label = { Text("Off", color = if (subtitleTracks.none { it.isSelected }) Color.White else Color.Gray) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryAccent,
                        containerColor = Color.White.copy(alpha = 0.05f)
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    border = null
                )
                subtitleTracks.forEach { track ->
                    FilterChip(
                        selected = track.isSelected,
                        onClick = { onSubtitleTrackSelect(track.id) },
                        label = { 
                            Text(
                                track.label ?: track.language ?: "Unknown", 
                                color = if (track.isSelected) Color.White else Color.Gray 
                            ) 
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryAccent,
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        border = null
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { 
                Text("Close", color = PrimaryAccent, fontWeight = FontWeight.Bold) 
            }
        }
    )
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

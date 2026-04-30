package com.antigravity.videoplayer.mobile.ui.screen.player

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.antigravity.videoplayer.mobile.viewmodel.MobilePlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(UnstableApi::class)
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

    var showSettings by remember { mutableStateOf(false) }
    var gestureInfo by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(brightness) {
        (context as? Activity)?.window?.attributes?.let {
            it.screenBrightness = brightness
            context.window.attributes = it
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked) {
                if (!isLocked) {
                    detectVerticalDragGestures(
                        onDragEnd = { gestureInfo = null },
                        onVerticalDrag = { change, dragAmount ->
                            val isLeftSide = change.position.x < size.width / 2
                            if (isLeftSide) {
                                val newBrightness = (brightness - dragAmount / size.height).coerceIn(0f, 1f)
                                viewModel.adjustBrightness(newBrightness)
                                gestureInfo = "Brightness: ${(newBrightness * 100).toInt()}%"
                            } else {
                                viewModel.adjustVolume(-dragAmount / size.height)
                                gestureInfo = "Volume: ${(volume * 100).toInt()}%"
                            }
                        }
                    )
                }
            }
            .pointerInput(isLocked) {
                if (!isLocked) {
                    detectHorizontalDragGestures(
                        onDragEnd = { gestureInfo = null },
                        onHorizontalDrag = { _, dragAmount ->
                            val seekAmount = (dragAmount * 100).toLong()
                            viewModel.seekTo(currentPosition + seekAmount)
                            gestureInfo = "Seek: ${formatTime(currentPosition)}"
                        }
                    )
                }
            }
            .pointerInput(isLocked) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (!isLocked) {
                            val isLeftSide = offset.x < size.width / 2
                            if (isLeftSide) {
                                viewModel.seekTo(currentPosition - 10000)
                                gestureInfo = "-10s"
                            } else {
                                viewModel.seekTo(currentPosition + 10000)
                                gestureInfo = "+10s"
                            }
                            scope.launch {
                                delay(500)
                                gestureInfo = null
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
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture Feedback
        AnimatedVisibility(
            visible = gestureInfo != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = CircleShape
            ) {
                Text(
                    text = gestureInfo ?: "",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Overlay Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                if (!isLocked) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBackPressed) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Text(
                            text = "Now Playing",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row {
                            IconButton(onClick = { viewModel.toggleLock() }) {
                                Icon(Icons.Default.LockOpen, contentDescription = "Lock", tint = Color.White)
                            }
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                            }
                        }
                    }

                    // Middle Controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.seekTo(currentPosition - 10000) }) {
                            Icon(Icons.Default.Replay10, contentDescription = "Rewind", tint = Color.White, modifier = Modifier.size(48.dp))
                        }
                        IconButton(onClick = { viewModel.togglePlayPause() }) {
                            Icon(
                                if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(80.dp)
                            )
                        }
                        IconButton(onClick = { viewModel.seekTo(currentPosition + 10000) }) {
                            Icon(Icons.Default.Forward10, contentDescription = "Fast Forward", tint = Color.White, modifier = Modifier.size(48.dp))
                        }
                    }

                    // Bottom Controls
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp, start = 24.dp, end = 24.dp)
                    ) {
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onValueChange = { viewModel.seekTo((it * duration).toLong()) },
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = formatTime(currentPosition), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            Text(text = formatTime(duration), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ControlOption(Icons.Default.Speed, "${playbackSpeed}x") { showSettings = true }
                            ControlOption(Icons.Default.AspectRatio, "Resize") { 
                                val nextMode = when(resizeMode) {
                                    0 -> 3
                                    3 -> 4
                                    else -> 0
                                }
                                viewModel.setResizeMode(nextMode)
                            }
                            ControlOption(Icons.Default.Timer, "Sleep") { /* Sleep timer logic */ }
                            ControlOption(Icons.Default.Subtitles, "Subs") { /* Subs logic */ }
                        }
                    }
                } else {
                    // Locked State UI
                    Box(modifier = Modifier.fillMaxSize()) {
                        IconButton(
                            onClick = { viewModel.toggleLock() },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Unlock", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            currentSpeed = playbackSpeed,
            onSpeedChange = { viewModel.setPlaybackSpeed(it) },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
fun ControlOption(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Text(text = label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback Settings") },
        text = {
            Column {
                Text("Playback Speed", style = MaterialTheme.typography.titleSmall)
                val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    speeds.forEach { speed ->
                        FilterChip(
                            selected = currentSpeed == speed,
                            onClick = { onSpeedChange(speed) },
                            label = { Text("${speed}x") }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Sleep Timer", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { }) { Text("Off") }
                    Button(onClick = { }) { Text("30m") }
                    Button(onClick = { }) { Text("60m") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

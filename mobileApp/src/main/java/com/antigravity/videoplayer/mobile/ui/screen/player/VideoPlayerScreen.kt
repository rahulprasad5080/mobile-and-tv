package com.antigravity.videoplayer.mobile.ui.screen.player

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    var showSubtitleSettings by remember { mutableStateOf(false) }
    var gestureInfo by remember { mutableStateOf<String?>(null) }

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
            .pointerInput(Unit) {
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
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { gestureInfo = null },
                    onHorizontalDrag = { _, dragAmount ->
                        val seekAmount = (dragAmount * 100).toLong()
                        viewModel.seekTo(currentPosition + seekAmount)
                        gestureInfo = "Seek: ${formatTime(currentPosition)}"
                    }
                )
            }
            .clickable { viewModel.toggleControls() }
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
                // Apply Subtitle Styles
                view.subtitleView?.apply {
                    setStyle(CaptionStyleCompat(
                        Color.White.hashCode(),
                        Color.Transparent.hashCode(),
                        Color.Transparent.hashCode(),
                        CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                        Color.Black.hashCode(),
                        null
                    ))
                    setFixedTextSize(2, 24f)
                }
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
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = gestureInfo ?: "",
                    color = Color.White,
                    modifier = Modifier.padding(16.dp),
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
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = { showSubtitleSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
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
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
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
                        .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
                ) {
                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                        onValueChange = { viewModel.seekTo((it * duration).toLong()) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Red,
                            activeTrackColor = Color.Red
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = formatTime(currentPosition), color = Color.White)
                        Text(text = formatTime(duration), color = Color.White)
                    }
                }
            }
        }
    }

    if (showSubtitleSettings) {
        SubtitleSettingsDialog(onDismiss = { showSubtitleSettings = false })
    }
}

@Composable
fun SubtitleSettingsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subtitle Settings") },
        text = {
            Column {
                Text("Text Size")
                var size by remember { mutableFloatStateOf(20f) }
                Slider(value = size, onValueChange = { size = it }, valueRange = 10f..40f)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Sync Adjustment")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { }) { Icon(Icons.Default.Remove, "") }
                    Text("0 ms")
                    IconButton(onClick = { }) { Icon(Icons.Default.Add, "") }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        }
    )
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

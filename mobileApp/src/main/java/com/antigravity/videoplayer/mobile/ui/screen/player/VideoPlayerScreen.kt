package com.antigravity.videoplayer.mobile.ui.screen.player

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import com.antigravity.videoplayer.mobile.viewmodel.MobilePlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TransparentBlack = Color.Black.copy(alpha = 0.5f)
private val DeepBlack = Color(0xFF0C0C0C)
private val PrimaryAccent = Color(0xFF3F51B5) // Indigo

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
    var gestureIcon by remember { mutableStateOf<ImageVector?>(null) }
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
                        onDragEnd = { 
                            gestureInfo = null
                            gestureIcon = null 
                        },
                        onVerticalDrag = { change, dragAmount ->
                            val isLeftSide = change.position.x < size.width / 2
                            if (isLeftSide) {
                                val newBrightness = (brightness - dragAmount / size.height).coerceIn(0f, 1f)
                                viewModel.adjustBrightness(newBrightness)
                                gestureInfo = "${(newBrightness * 100).toInt()}%"
                                gestureIcon = Icons.Rounded.BrightnessMedium
                            } else {
                                viewModel.adjustVolume(-dragAmount / size.height)
                                gestureInfo = "${(volume * 100).toInt()}%"
                                gestureIcon = Icons.Rounded.VolumeUp
                            }
                        }
                    )
                }
            }
            .pointerInput(isLocked) {
                if (!isLocked) {
                    detectHorizontalDragGestures(
                        onDragEnd = { 
                            gestureInfo = null
                            gestureIcon = null
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val seekAmount = (dragAmount * 100).toLong()
                            viewModel.seekTo(currentPosition + seekAmount)
                            gestureInfo = formatTime(currentPosition)
                            gestureIcon = Icons.Rounded.FastForward
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
                                gestureIcon = Icons.Rounded.Replay10
                            } else {
                                viewModel.seekTo(currentPosition + 10000)
                                gestureInfo = "+10s"
                                gestureIcon = Icons.Rounded.Forward10
                            }
                            scope.launch {
                                delay(600)
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

        // Gesture Feedback UI
        AnimatedVisibility(
            visible = gestureInfo != null,
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

        // Overlay Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
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
            ) {
                if (!isLocked) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
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
                                onClick = { viewModel.toggleLock() },
                                modifier = Modifier.background(TransparentBlack, CircleShape)
                            ) {
                                Icon(Icons.Rounded.LockOpen, contentDescription = "Lock", tint = Color.White)
                            }
                            IconButton(
                                onClick = { showSettings = true },
                                modifier = Modifier.background(TransparentBlack, CircleShape)
                            ) {
                                Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.White)
                            }
                        }
                    }

                    // Middle Controls (Advanced High Level)
                    Box(modifier = Modifier.align(Alignment.Center)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(40.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.seekTo(currentPosition - 10000) },
                                modifier = Modifier.size(56.dp).background(TransparentBlack, CircleShape)
                            ) {
                                Icon(Icons.Rounded.Replay10, contentDescription = "Rewind", tint = Color.White, modifier = Modifier.size(32.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryAccent.copy(alpha = 0.8f))
                                    .clickable { viewModel.togglePlayPause() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(56.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.seekTo(currentPosition + 10000) },
                                modifier = Modifier.size(56.dp).background(TransparentBlack, CircleShape)
                            ) {
                                Icon(Icons.Rounded.Forward10, contentDescription = "Fast Forward", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                    }

                    // Bottom Controls
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
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
                                onValueChange = { viewModel.seekTo((it * duration).toLong()) },
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
                } else {
                    // Locked State UI
                    Box(modifier = Modifier.fillMaxSize()) {
                        IconButton(
                            onClick = { viewModel.toggleLock() },
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

    if (showSettings) {
        AdvancedSettingsDialog(
            currentSpeed = playbackSpeed,
            onSpeedChange = { viewModel.setPlaybackSpeed(it) },
            resizeMode = resizeMode,
            onResizeModeChange = { viewModel.setResizeMode(it) },
            onSleepClick = { /* Sleep logic */ },
            onDismiss = { showSettings = false }
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
    onSleepClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepBlack,
        shape = RoundedCornerShape(28.dp),
        title = { 
            Text(
                "Playback Settings", 
                color = Color.White, 
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp
            ) 
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Playback Speed", color = PrimaryAccent, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${"%.2f".format(currentSpeed)}x",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(PrimaryAccent.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Slider(
                    value = currentSpeed,
                    onValueChange = { onSpeedChange(it) },
                    valueRange = 0.25f..3.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryAccent,
                        activeTrackColor = PrimaryAccent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                Text("Aspect Ratio", color = PrimaryAccent, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                val modes = listOf(0 to "Fit", 3 to "Fill", 4 to "Zoom")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    modes.forEach { (mode, label) ->
                        FilterChip(
                            selected = resizeMode == mode,
                            onClick = { onResizeModeChange(mode) },
                            label = { Text(label, color = if (resizeMode == mode) Color.White else Color.Gray) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryAccent,
                                containerColor = Color.White.copy(alpha = 0.05f)
                            ),
                            border = null
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text("Video Tools", color = PrimaryAccent, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsToolButton(Icons.Rounded.Timer, "Sleep", onSleepClick)
                    SettingsToolButton(Icons.Rounded.ScreenRotation, "Rotate", {})
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { 
                Text("Done", color = PrimaryAccent, fontWeight = FontWeight.Bold) 
            }
        }
    )
}

@Composable
fun SettingsToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.height(40.dp)
    ) { 
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, fontSize = 12.sp)
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

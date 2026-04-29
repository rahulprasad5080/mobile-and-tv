package com.antigravity.videoplayer.tv.ui.screen.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import com.antigravity.videoplayer.tv.viewmodel.TvPlayerViewModel

@OptIn(UnstableApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun TvPlayerScreen(
    viewModel: TvPlayerViewModel,
    onBackPressed: () -> Unit
) {
    val player = viewModel.playerManager.getPlayer()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()

    var showTrackSelection by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // TV Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Button(onClick = onBackPressed) {
                    Text("Back")
                }
            }

            // Bottom Controls
            Column(
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Text(
                    text = "Movie Title",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatTime(currentPosition), color = Color.White)
                    Text(text = formatTime(duration), color = Color.White)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { viewModel.togglePlayPause() }) {
                        Text(if (isPlaying) "Pause" else "Play")
                    }
                    Button(onClick = { showTrackSelection = true }) {
                        Text("Audio & Subtitles")
                    }
                    Button(onClick = { /* PiP logic */ }) {
                        Text("PiP")
                    }
                }
            }
        }
    }

    if (showTrackSelection) {
        TrackSelectionDialog(
            onDismiss = { showTrackSelection = false },
            audioTracks = viewModel.playerManager.getAudioTracks(),
            subtitleTracks = viewModel.playerManager.getSubtitleTracks(),
            onAudioSelect = { viewModel.playerManager.selectAudioTrack(it) },
            onSubtitleSelect = { viewModel.playerManager.selectSubtitleTrack(it) }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TrackSelectionDialog(
    onDismiss: () -> Unit,
    audioTracks: List<com.antigravity.videoplayer.core.model.AudioTrackInfo>,
    subtitleTracks: List<com.antigravity.videoplayer.core.model.SubtitleTrackInfo>,
    onAudioSelect: (String) -> Unit,
    onSubtitleSelect: (String?) -> Unit
) {
    // A simplified TV dialog
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.width(400.dp).padding(24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(text = "Audio Tracks", style = MaterialTheme.typography.titleMedium)
                audioTracks.forEach { track ->
                    Button(onClick = { onAudioSelect(track.id) }) {
                        Text(text = "${track.language ?: "Unknown"} - ${track.label ?: ""}")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "Subtitles", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { onSubtitleSelect(null) }) {
                    Text("Off")
                }
                subtitleTracks.forEach { track ->
                    Button(onClick = { onSubtitleSelect(track.id) }) {
                        Text(text = "${track.language ?: "Unknown"} - ${track.label ?: ""}")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

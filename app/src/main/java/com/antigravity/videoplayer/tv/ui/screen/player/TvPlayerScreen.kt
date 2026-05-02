package com.antigravity.videoplayer.tv.ui.screen.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.antigravity.videoplayer.core.model.AudioTrackInfo
import com.antigravity.videoplayer.core.model.SubtitleTrackInfo
import com.antigravity.videoplayer.tv.viewmodel.TvPlayerViewModel

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
    var showTrackSelection by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        viewModel.togglePlayPause()
                        true
                    }
                    Key.DirectionLeft -> {
                        viewModel.seekBy(-10_000)
                        true
                    }
                    Key.DirectionRight -> {
                        viewModel.seekBy(10_000)
                        true
                    }
                    Key.Back -> {
                        onBackPressed()
                        true
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
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp)
        ) {
            Button(onClick = onBackPressed) {
                Text("Back")
            }

            Column(
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Text(
                    text = currentTitle.ifBlank { "Now Playing" },
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(18.dp))

                LinearProgressIndicator(
                    progress = { if (duration > 0) currentPosition.toFloat() / duration else 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatTime(currentPosition), color = Color.White)
                    Text(text = formatTime(duration), color = Color.White)
                }

                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { viewModel.seekBy(-10_000) }) {
                        Text("Rewind")
                    }
                    Button(onClick = { viewModel.togglePlayPause() }) {
                        Text(if (isPlaying) "Pause" else "Play")
                    }
                    Button(onClick = { viewModel.seekBy(10_000) }) {
                        Text("Forward")
                    }
                    Button(onClick = { showTrackSelection = true }) {
                        Text("Audio & Subtitles")
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

@Composable
private fun TrackSelectionDialog(
    onDismiss: () -> Unit,
    audioTracks: List<AudioTrackInfo>,
    subtitleTracks: List<SubtitleTrackInfo>,
    onAudioSelect: (String) -> Unit,
    onSubtitleSelect: (String?) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(520.dp)
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.padding(28.dp)) {
                Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)

                Spacer(modifier = Modifier.height(18.dp))

                Text(text = "Audio Tracks", style = MaterialTheme.typography.titleMedium)
                audioTracks.forEach { track ->
                    Button(onClick = { onAudioSelect(track.id) }) {
                        Text(text = listOfNotNull(track.language, track.label).joinToString(" - ").ifBlank { "Unknown" })
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(text = "Subtitles", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { onSubtitleSelect(null) }) {
                    Text("Off")
                }
                subtitleTracks.forEach { track ->
                    Button(onClick = { onSubtitleSelect(track.id) }) {
                        Text(text = listOfNotNull(track.language, track.label).joinToString(" - ").ifBlank { "Unknown" })
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

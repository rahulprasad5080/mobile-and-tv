package com.antigravity.videoplayer.core.player

import android.content.Context
import kotlin.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.antigravity.videoplayer.core.model.AudioTrackInfo
import com.antigravity.videoplayer.core.model.SubtitleTrackInfo
import com.antigravity.videoplayer.core.model.VideoMediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(UnstableApi::class)
class PlayerManager(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private val trackSelector = DefaultTrackSelector(context)

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentTracks = MutableStateFlow<Tracks?>(null)
    val currentTracks: StateFlow<Tracks?> = _currentTracks.asStateFlow()

    sealed class PlaybackState {
        object Idle : PlaybackState()
        object Buffering : PlaybackState()
        object Ready : PlaybackState()
        object Ended : PlaybackState()
        data class Error(val message: String) : PlaybackState()
    }

    fun initializePlayer() {
        if (exoPlayer != null) return

        exoPlayer = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .build().apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        _playbackState.value = when (state) {
                            Player.STATE_BUFFERING -> PlaybackState.Buffering
                            Player.STATE_READY -> PlaybackState.Ready
                            Player.STATE_ENDED -> PlaybackState.Ended
                            else -> PlaybackState.Idle
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _playbackState.value = PlaybackState.Error(error.message ?: "Unknown Error")
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        _currentTracks.value = tracks
                    }
                })
            }
    }

    fun getPlayer(): Player? = exoPlayer

    fun playMedia(item: VideoMediaItem, startPositionMs: Long = 0) {
        val mediaItem = MediaItem.Builder()
            .setUri(item.uri)
            .setMimeType(item.mimeType)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .build()
            )
            .setSubtitleConfigurations(
                item.subtitles.map {
                    MediaItem.SubtitleConfiguration.Builder(it.uri)
                        .setMimeType(it.mimeType)
                        .setLanguage(it.language)
                        .setLabel(it.label)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                }
            )
            .build()

        exoPlayer?.setMediaItem(mediaItem)
        if (startPositionMs > 0) {
            exoPlayer?.seekTo(startPositionMs)
        }
        exoPlayer?.prepare()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        if (exoPlayer?.playbackState == Player.STATE_ENDED) {
            exoPlayer?.seekTo(0)
        }
        exoPlayer?.play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
    }

    // Track Selection Logic
    fun getAudioTracks(): List<AudioTrackInfo> {
        val tracks = _currentTracks.value ?: return emptyList()
        val audioTracks = mutableListOf<AudioTrackInfo>()
        
        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    audioTracks.add(
                        AudioTrackInfo(
                            id = "audio_${groupIndex}_$i",
                            language = format.language,
                            label = format.label,
                            bitRate = format.bitrate,
                            isSelected = group.isTrackSelected(i)
                        )
                    )
                }
            }
        }
        return audioTracks
    }

    fun getSubtitleTracks(): List<SubtitleTrackInfo> {
        val tracks = _currentTracks.value ?: return emptyList()
        val subtitleTracks = mutableListOf<SubtitleTrackInfo>()

        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    subtitleTracks.add(
                        SubtitleTrackInfo(
                            id = "subtitle_${groupIndex}_$i",
                            language = format.language,
                            label = format.label,
                            isSelected = group.isTrackSelected(i)
                        )
                    )
                }
            }
        }
        return subtitleTracks
    }

    fun selectAudioTrack(trackId: String) {
        val tracks = _currentTracks.value ?: return
        val parts = trackId.split("_")
        if (parts.size < 3) return
        
        val groupIndex = parts[1].toIntOrNull() ?: return
        val trackIndex = parts[2].toIntOrNull() ?: return
        
        if (groupIndex < tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            if (trackIndex < group.length) {
                trackSelector.parameters = trackSelector.buildUponParameters()
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                    .build()
            }
        }
    }

    fun selectSubtitleTrack(trackId: String?) {
        if (trackId == null) {
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            return
        }

        val tracks = _currentTracks.value ?: return
        val parts = trackId.split("_")
        if (parts.size < 3) return

        val groupIndex = parts[1].toIntOrNull() ?: return
        val trackIndex = parts[2].toIntOrNull() ?: return

        if (groupIndex < tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            if (trackIndex < group.length) {
                trackSelector.parameters = trackSelector.buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                    .build()
            }
        }
    }
}

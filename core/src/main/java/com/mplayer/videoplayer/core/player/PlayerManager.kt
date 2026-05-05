package com.mplayer.videoplayer.core.player

import android.content.Context
import kotlin.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.mplayer.videoplayer.core.model.AudioTrackInfo
import com.mplayer.videoplayer.core.model.SubtitleTrackInfo
import com.mplayer.videoplayer.core.model.VideoMediaItem
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(UnstableApi::class)
class PlayerManager(private val context: Context) {

    companion object {
        @UnstableApi
        private var instance: ExoPlayer? = null
        private var currentMediaId: String? = null
    }

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
        if (instance != null) return

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2_500,
                30_000,
                750,
                1_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        instance = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
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

    fun getPlayer(): Player? = instance

    fun playMedia(item: VideoMediaItem, startPositionMs: Long = 0) {
        initializePlayer()
        val player = instance ?: return
        val startPosition = startPositionMs.coerceAtLeast(0)

        if (currentMediaId == item.id && player.mediaItemCount > 0) {
            player.seekTo(startPosition)
            player.playWhenReady = true
            player.play()
            return
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(item.id)
            .setUri(item.uri)
            .setMimeType(item.mimeType.forPlayback())
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

        currentMediaId = item.id
        player.setMediaItem(mediaItem, startPosition)
        player.prepare()
        player.play()
    }

    fun preloadMedia(item: VideoMediaItem, startPositionMs: Long = 0) {
        initializePlayer()
        val player = instance ?: return
        if (currentMediaId == item.id && player.mediaItemCount > 0) return

        val mediaItem = MediaItem.Builder()
            .setMediaId(item.id)
            .setUri(item.uri)
            .setMimeType(item.mimeType.forPlayback())
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

        currentMediaId = item.id
        player.setMediaItem(mediaItem, startPositionMs.coerceAtLeast(0))
        player.playWhenReady = false
        player.prepare()
    }

    fun pause() {
        instance?.pause()
    }

    fun resume() {
        if (instance?.playbackState == Player.STATE_ENDED) {
            instance?.seekTo(0)
        }
        instance?.play()
    }

    fun seekTo(positionMs: Long) {
        instance?.seekTo(positionMs)
    }

    fun releasePlayer() {
        instance?.stop()
        instance?.clearMediaItems()
        instance?.release()
        instance = null
        currentMediaId = null
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
                            channelCount = format.channelCount,
                            displayLabel = buildAudioTrackLabel(format.label, format.language, format.channelCount),
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
                            displayLabel = buildSubtitleTrackLabel(format.label, format.language),
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

    private fun buildAudioTrackLabel(label: String?, language: String?, channelCount: Int): String {
        val baseLabel = label?.takeIf { it.isNotBlank() && !it.contains("MATROSKA", ignoreCase = true) } ?: resolveLanguageName(language)
        val channelLabel = resolveChannelLabel(channelCount)
        return if (channelLabel != null) "$baseLabel ($channelLabel)" else baseLabel
    }

    private fun String?.forPlayback(): String? {
        return this?.takeIf {
            it.startsWith("video/", ignoreCase = true) ||
                it.startsWith("audio/", ignoreCase = true) ||
                it.startsWith("application/x-mpegURL", ignoreCase = true) ||
                it.startsWith("application/dash+xml", ignoreCase = true)
        }
    }

    private fun buildSubtitleTrackLabel(label: String?, language: String?): String {
        val baseLabel = label?.takeIf { it.isNotBlank() && !it.contains("MATROSKA", ignoreCase = true) } ?: resolveLanguageName(language)
        return if (baseLabel.contains("subtitle", ignoreCase = true)) {
            baseLabel
        } else {
            "$baseLabel Subtitles"
        }
    }

    private fun resolveLanguageName(languageCode: String?): String {
        val normalizedCode = languageCode
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.replace('_', '-')
            ?: return "Unknown"

        if (normalizedCode.equals("und", ignoreCase = true)) return "Unknown"

        val locale = Locale.forLanguageTag(normalizedCode)
        val displayLanguage = locale.getDisplayLanguage(Locale.ENGLISH).takeIf { it.isNotBlank() }
        if (!displayLanguage.isNullOrBlank() && !displayLanguage.equals(normalizedCode, ignoreCase = true)) {
            return displayLanguage.replaceFirstChar { first ->
                if (first.isLowerCase()) first.titlecase(Locale.ENGLISH) else first.toString()
            }
        }

        val legacyLocale = Locale(normalizedCode)
        val legacyDisplayLanguage = legacyLocale.getDisplayLanguage(Locale.ENGLISH)
        return legacyDisplayLanguage
            .takeIf { it.isNotBlank() && !it.equals(normalizedCode, ignoreCase = true) }
            ?.replaceFirstChar { first ->
                if (first.isLowerCase()) first.titlecase(Locale.ENGLISH) else first.toString()
            }
            ?: "Unknown"
    }

    private fun resolveChannelLabel(channelCount: Int): String? {
        return when (channelCount) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1"
            8 -> "7.1"
            Format.NO_VALUE -> null
            else -> if (channelCount > 0) "$channelCount ch" else null
        }
    }
}

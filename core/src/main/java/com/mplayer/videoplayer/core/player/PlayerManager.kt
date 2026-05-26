package com.mplayer.videoplayer.core.player

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.mplayer.videoplayer.core.model.AudioTrackInfo
import com.mplayer.videoplayer.core.model.SubtitleTrack
import com.mplayer.videoplayer.core.model.SubtitleTrackInfo
import com.mplayer.videoplayer.core.model.VideoMediaItem
import java.io.File
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
        private var currentMediaMimeType: String? = null
        // Persist selected track IDs across play/pause — Bug 3 fix
        private var selectedAudioTrackId: String? = null
        private var selectedSubtitleTrackId: String? = null
        
        // Advanced features state
        private var currentVideoMediaItem: VideoMediaItem? = null
        private var loudnessEnhancer: LoudnessEnhancer? = null
        private var boostLevel: Int = 0
        private var isTunnelingEnabled = false
        private var isSkipSilenceEnabled = false
        private var repeatModeVal = Player.REPEAT_MODE_OFF
        private val externalSubtitles = mutableListOf<SubtitleTrack>()
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

        trackSelector.parameters = trackSelector.buildUponParameters()
            .setExceedVideoConstraintsIfNecessary(true)
            .setExceedAudioConstraintsIfNecessary(true)
            .setExceedRendererCapabilitiesIfNecessary(true)
            .setTunnelingEnabled(isTunnelingEnabled)
            // Bug 6 fix: explicitly enable subtitle/text tracks (were disabled by default)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2_500,
                30_000,
                750,
                1_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        instance = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
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
                setSkipSilenceEnabled(isSkipSilenceEnabled)
                repeatMode = repeatModeVal
                // TV devices don't need to pause on headphone disconnect
                setHandleAudioBecomingNoisy(false)
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
                        _playbackState.value = PlaybackState.Error(buildPlaybackErrorMessage(error))
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        _currentTracks.value = tracks
                    }

                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        applyLoudnessEnhancer()
                    }
                })
            }
        applyLoudnessEnhancer()
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

        if (currentMediaId != item.id) {
            externalSubtitles.clear()
        }
        currentVideoMediaItem = item
        val localSubs = findLocalSubtitles(item.filePath)
        val allSubs = (item.subtitles + localSubs + externalSubtitles).distinctBy { it.uri.toString() }

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
                allSubs.map {
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
        currentMediaMimeType = item.mimeType
        player.setMediaItem(mediaItem, startPosition)
        player.prepare()
        player.play()
    }

    fun preloadMedia(item: VideoMediaItem, startPositionMs: Long = 0) {
        initializePlayer()
        val player = instance ?: return
        if (currentMediaId == item.id && player.mediaItemCount > 0) return

        if (currentMediaId != item.id) {
            externalSubtitles.clear()
        }
        currentVideoMediaItem = item
        val localSubs = findLocalSubtitles(item.filePath)
        val allSubs = (item.subtitles + localSubs + externalSubtitles).distinctBy { it.uri.toString() }

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
                allSubs.map {
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
        currentMediaMimeType = item.mimeType
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

    fun seekForwardTo(positionMs: Long) {
        val player = instance ?: return
        player.setSeekParameters(SeekParameters.DEFAULT)
        player.seekTo(positionMs)
    }

    fun seekBackwardTo(positionMs: Long) {
        val player = instance ?: return
        player.setSeekParameters(SeekParameters.DEFAULT)
        player.seekTo(positionMs)
    }

    fun seekBackward(deltaMs: Long = 10_000L) {
        val player = instance ?: return
        val duration = player.duration.takeIf { it > 0 }
        val target = if (duration != null) {
            (player.currentPosition - deltaMs).coerceIn(0L, duration)
        } else {
            (player.currentPosition - deltaMs).coerceAtLeast(0L)
        }
        player.setSeekParameters(SeekParameters.DEFAULT)
        player.seekTo(target)
    }

    fun seekForward(deltaMs: Long = 10_000L) {
        val player = instance ?: return
        val duration = player.duration.takeIf { it > 0 }
        val target = if (duration != null) {
            (player.currentPosition + deltaMs).coerceIn(0L, duration)
        } else {
            (player.currentPosition + deltaMs).coerceAtLeast(0L)
        }
        player.setSeekParameters(SeekParameters.DEFAULT)
        player.seekTo(target)
    }

    fun findLocalSubtitles(videoPath: String?): List<SubtitleTrack> {
        if (videoPath.isNullOrBlank()) return emptyList()
        val videoFile = File(videoPath)
        val parentDir = videoFile.parentFile ?: return emptyList()
        if (!parentDir.exists() || !parentDir.isDirectory) return emptyList()

        val videoName = videoFile.nameWithoutExtension.lowercase(Locale.US)
        val subtitleExtensions = listOf("srt", "vtt", "ssa", "ass", "ttml")

        val list = mutableListOf<SubtitleTrack>()
        try {
            val files = parentDir.listFiles() ?: return emptyList()
            for (file in files) {
                if (file.isFile) {
                    val name = file.name.lowercase(Locale.US)
                    val ext = file.extension.lowercase(Locale.US)
                    if (ext in subtitleExtensions) {
                        if (name.startsWith(videoName)) {
                            val mimeType = when (ext) {
                                "srt" -> "application/x-subrip"
                                "vtt" -> "text/vtt"
                                "ssa", "ass" -> "text/x-ssa"
                                "ttml" -> "application/ttml+xml"
                                else -> "text/vtt"
                            }
                            val suffix = file.nameWithoutExtension.substringAfter(videoFile.nameWithoutExtension, "")
                            val label = if (suffix.startsWith(".")) {
                                val langCode = suffix.substring(1)
                                val loc = Locale.forLanguageTag(langCode)
                                val display = loc.getDisplayLanguage(Locale.ENGLISH)
                                if (display.isNotBlank() && display != langCode) display else langCode.uppercase(Locale.US)
                            } else {
                                "External (${ext.uppercase(Locale.US)})"
                            }
                            list.add(
                                SubtitleTrack(
                                    uri = Uri.fromFile(file),
                                    mimeType = mimeType,
                                    language = if (suffix.startsWith(".")) suffix.substring(1) else null,
                                    label = label
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun addExternalSubtitle(uri: Uri, mimeType: String, label: String) {
        val player = instance ?: return
        val currentPosition = player.currentPosition
        val currentItem = currentVideoMediaItem ?: return

        val newSub = SubtitleTrack(
            uri = uri,
            mimeType = mimeType,
            label = label
        )
        externalSubtitles.add(newSub)

        val localSubs = findLocalSubtitles(currentItem.filePath)
        val allSubs = (currentItem.subtitles + localSubs + externalSubtitles).distinctBy { it.uri.toString() }

        val mediaItem = MediaItem.Builder()
            .setMediaId(currentItem.id)
            .setUri(currentItem.uri)
            .setMimeType(currentItem.mimeType.forPlayback())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(currentItem.title)
                    .build()
            )
            .setSubtitleConfigurations(
                allSubs.map {
                    MediaItem.SubtitleConfiguration.Builder(it.uri)
                        .setMimeType(it.mimeType)
                        .setLanguage(it.language)
                        .setLabel(it.label)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                }
            )
            .build()

        player.setMediaItem(mediaItem, currentPosition)
        player.prepare()
        player.play()
    }

    fun applyLoudnessEnhancer() {
        val player = instance ?: return
        try {
            val audioSessionId = player.audioSessionId
            if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                if (loudnessEnhancer == null || loudnessEnhancer?.id != audioSessionId) {
                    loudnessEnhancer?.release()
                    loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                }
                loudnessEnhancer?.apply {
                    setTargetGain(boostLevel * 200)
                    setEnabled(boostLevel > 0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setVolumeBoost(level: Int) {
        boostLevel = level.coerceIn(0, 10)
        applyLoudnessEnhancer()
    }

    fun getVolumeBoost(): Int = boostLevel

    fun setSkipSilence(enabled: Boolean) {
        isSkipSilenceEnabled = enabled
        instance?.setSkipSilenceEnabled(enabled)
    }

    fun isSkipSilenceEnabled(): Boolean = isSkipSilenceEnabled

    fun setRepeatMode(mode: Int) {
        repeatModeVal = mode
        instance?.repeatMode = mode
    }

    fun getRepeatMode(): Int = repeatModeVal

    fun setTunnelingEnabled(enabled: Boolean) {
        if (isTunnelingEnabled != enabled) {
            isTunnelingEnabled = enabled
            val player = instance
            if (player != null) {
                val currentPosition = player.currentPosition
                val currentItem = currentVideoMediaItem
                releasePlayer()
                if (currentItem != null) {
                    playMedia(currentItem, currentPosition)
                } else {
                    initializePlayer()
                }
            }
        }
    }

    fun isTunnelingEnabled(): Boolean = isTunnelingEnabled

    fun releasePlayer() {
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        instance?.stop()
        instance?.clearMediaItems()
        instance?.release()
        instance = null
        currentMediaId = null
        currentMediaMimeType = null
        currentVideoMediaItem = null
    }

    // Track Selection Logic
    fun getAudioTracks(): List<AudioTrackInfo> {
        val tracks = _currentTracks.value ?: return emptyList()
        val audioTracks = mutableListOf<AudioTrackInfo>()

        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            // Bug 5 fix: also check mime type as fallback for MKV multi-audio detection
            val isAudioGroup = group.type == C.TRACK_TYPE_AUDIO ||
                (group.length > 0 && group.getTrackFormat(0).sampleMimeType?.startsWith("audio/") == true)
            if (isAudioGroup) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val trackId = "audio_${groupIndex}_$i"
                    audioTracks.add(
                        AudioTrackInfo(
                            id = trackId,
                            language = format.language,
                            label = format.label,
                            bitRate = format.bitrate,
                            channelCount = format.channelCount,
                            displayLabel = buildAudioTrackLabel(format.label, format.language, format.channelCount),
                            // Bug 3 fix: use persisted selectedAudioTrackId instead of volatile isTrackSelected
                            isSelected = if (selectedAudioTrackId != null) {
                                trackId == selectedAudioTrackId
                            } else {
                                group.isTrackSelected(i)
                            }
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
            val firstMime = if (group.length > 0) group.getTrackFormat(0).sampleMimeType else null
            // Bug 3 fix (Android TV 9): SRT subtitles use MIME 'application/x-subrip' which
            // doesn't start with 'text/', so we also match known subtitle application/ types.
            val isSubtitleMime = firstMime != null && (
                firstMime.startsWith("text/") ||
                firstMime == "application/x-subrip" ||
                firstMime == "application/ttml+xml" ||
                firstMime == "application/mp4" && group.type == C.TRACK_TYPE_TEXT
            )
            val isTextGroup = group.type == C.TRACK_TYPE_TEXT || (group.length > 0 && isSubtitleMime)
            if (isTextGroup) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val trackId = "subtitle_${groupIndex}_$i"
                    subtitleTracks.add(
                        SubtitleTrackInfo(
                            id = trackId,
                            language = format.language,
                            label = format.label,
                            displayLabel = buildSubtitleTrackLabel(format.label, format.language),
                            // Bug 3 fix: use persisted selectedSubtitleTrackId
                            isSelected = if (selectedSubtitleTrackId != null) {
                                trackId == selectedSubtitleTrackId
                            } else {
                                group.isTrackSelected(i)
                            }
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
                // Bug 3 fix: persist selection so popup shows correct state after play/pause
                selectedAudioTrackId = trackId
                trackSelector.parameters = trackSelector.buildUponParameters()
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                    .build()
            }
        }
    }

    fun selectSubtitleTrack(trackId: String?) {
        if (trackId == null) {
            // Bug 3 fix: clear persisted subtitle selection
            selectedSubtitleTrackId = null
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
                // Bug 3 fix: persist selection
                selectedSubtitleTrackId = trackId
                trackSelector.parameters = trackSelector.buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                    .build()
            }
        }
    }

    private fun buildAudioTrackLabel(label: String?, language: String?, channelCount: Int): String {
        // Prefer language-code resolution over the raw label when the label looks like a
        // watermark/website name (e.g. "HDHub4u.Tv", "YTS.MX", "www.TamilRockers.ws").
        // Such labels are embedded by piracy sites and mean nothing to the user.
        val resolvedLanguage = resolveLanguageName(language)
        val baseLabel = when {
            label.isNullOrBlank() -> resolvedLanguage
            label.contains("MATROSKA", ignoreCase = true) -> resolvedLanguage
            isWatermarkLabel(label) -> resolvedLanguage  // ← skip watermark, use language code
            resolvedLanguage != "Unknown" -> resolvedLanguage  // prefer resolved language name
            else -> label  // fallback to raw label only if language can't be resolved
        }
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

    private fun buildPlaybackErrorMessage(error: PlaybackException): String {
        val mimeType = currentMediaMimeType.orEmpty()
        val baseMessage = error.message?.takeIf { it.isNotBlank() } ?: "Playback failed"

        return when {
            isEmulator() && mimeType.contains("matroska", ignoreCase = true) ->
                "This Android TV emulator cannot decode this MKV/Matroska video. Try an MP4 file encoded with H.264 video and AAC audio."
            isEmulator() ->
                "This Android TV emulator may not support this video's codec or container. MP4 with H.264 video and AAC audio is the safest test format."
            else -> baseMessage
        }
    }

    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase(Locale.US)
        val model = Build.MODEL.lowercase(Locale.US)
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.US)
        val brand = Build.BRAND.lowercase(Locale.US)
        val device = Build.DEVICE.lowercase(Locale.US)
        val product = Build.PRODUCT.lowercase(Locale.US)

        return fingerprint.startsWith("generic") ||
            fingerprint.startsWith("unknown") ||
            model.contains("emulator") ||
            model.contains("android sdk built for") ||
            manufacturer.contains("genymotion") ||
            brand.startsWith("generic") && device.startsWith("generic") ||
            product.contains("sdk") ||
            product.contains("emulator")
    }

    private fun buildSubtitleTrackLabel(label: String?, language: String?): String {
        val resolvedLanguage = resolveLanguageName(language)
        val baseLabel = when {
            label.isNullOrBlank() -> resolvedLanguage
            label.contains("MATROSKA", ignoreCase = true) -> resolvedLanguage
            isWatermarkLabel(label) -> resolvedLanguage  // skip watermark, use language code
            resolvedLanguage != "Unknown" -> resolvedLanguage  // prefer resolved language name
            else -> label
        }
        return if (baseLabel.contains("subtitle", ignoreCase = true)) {
            baseLabel
        } else {
            "$baseLabel Subtitles"
        }
    }

    /**
     * Detects watermark/website labels embedded in MKV tracks by piracy sites.
     * Examples: "HDHub4u.Tv", "YTS.MX", "www.TamilRockers.ws", "1337x.to", "RARBG"
     *
     * Detection rules:
     * - Contains a dot (.) → likely a domain or version number watermark
     * - Contains digits mixed with letters in a non-language pattern
     * - Starts with "www." → clearly a URL
     * - Matches known watermark site patterns
     */
    private fun isWatermarkLabel(label: String): Boolean {
        val trimmed = label.trim()
        // Starts with www. → definitely a URL
        if (trimmed.startsWith("www.", ignoreCase = true)) return true
        // Contains a dot → domain-like or version watermark (e.g. "HDHub4u.Tv", "YTS.MX")
        if (trimmed.contains('.')) return true
        // All uppercase and short (e.g. "RARBG", "YIFY") → release group watermarks
        // But don't flag things like "ENG" or "HIN" which are valid language codes
        if (trimmed.length > 4 && trimmed == trimmed.uppercase(Locale.US) && trimmed.any { it.isDigit() }) return true
        // Contains digits and letters mixed together in a non-language pattern (e.g. "1337x", "x265")
        val hasDigitsAndLetters = trimmed.any { it.isDigit() } && trimmed.any { it.isLetter() }
        val tooLongForLanguage = trimmed.length > 8
        if (hasDigitsAndLetters && tooLongForLanguage) return true
        return false
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

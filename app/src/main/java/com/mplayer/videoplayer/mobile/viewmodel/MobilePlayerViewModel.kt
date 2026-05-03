package com.mplayer.videoplayer.mobile.viewmodel

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mplayer.videoplayer.core.model.AudioTrackInfo
import com.mplayer.videoplayer.core.model.SubtitleTrackInfo
import com.mplayer.videoplayer.core.model.VideoMediaItem
import com.mplayer.videoplayer.core.player.PlayerManager
import com.mplayer.videoplayer.core.repository.PlaybackProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MobilePlayerViewModel(application: Application) : AndroidViewModel(application) {

    val playerManager = PlayerManager(application)
    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val progressRepository = PlaybackProgressRepository(application)
    
    private var currentVideo: VideoMediaItem? = null
    private var playlist: List<VideoMediaItem> = emptyList()
    
    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _showControls = MutableStateFlow(true)
    val showControls: StateFlow<Boolean> = _showControls.asStateFlow()

    private val _volume = MutableStateFlow(0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _brightness = MutableStateFlow(0.5f)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _resizeMode = MutableStateFlow(0) // 0: Fit, 3: Fill, 4: Zoom
    val resizeMode: StateFlow<Int> = _resizeMode.asStateFlow()

    private val _orientationMode = MutableStateFlow(ActivityInfo.SCREEN_ORIENTATION_SENSOR)
    val orientationMode: StateFlow<Int> = _orientationMode.asStateFlow()
    
    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    val currentTracks = playerManager.currentTracks
    
    private val _audioTracks = MutableStateFlow<List<AudioTrackInfo>>(emptyList())
    val audioTracks: StateFlow<List<AudioTrackInfo>> = _audioTracks.asStateFlow()
    
    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrackInfo>>(emptyList())
    val subtitleTracks: StateFlow<List<SubtitleTrackInfo>> = _subtitleTracks.asStateFlow()

    private var hideJob: Job? = null
    private var releaseJob: Job? = null
    private var volumeBeforeMute = 0.5f
    private var hasManualOrientationOverride = false

    init {
        playerManager.initializePlayer()
        startProgressUpdate()
        
        viewModelScope.launch {
            playerManager.currentTracks.collect {
                _audioTracks.value = playerManager.getAudioTracks()
                _subtitleTracks.value = playerManager.getSubtitleTracks()
            }
        }
        
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        _volume.value = currentVolume / maxVolume
    }

    private fun startProgressUpdate() {
        viewModelScope.launch {
            while (true) {
                val player = playerManager.getPlayer()
                if (player != null) {
                    _currentPosition.value = player.currentPosition
                    _duration.value = player.duration.coerceAtLeast(0)
                    _isPlaying.value = player.isPlaying
                }
                delay(1000)
            }
        }
    }

    fun setPlaylist(items: List<VideoMediaItem>, currentItem: VideoMediaItem) {
        playlist = items.takeIf { list -> list.any { it.id == currentItem.id } } ?: listOf(currentItem)
    }

    fun playMedia(item: VideoMediaItem, startPositionMs: Long = 0) {
        releaseJob?.cancel()
        currentVideo
            ?.takeIf { it.id != item.id }
            ?.let { progressRepository.saveProgress(it.id, playerManager.getPlayer()?.currentPosition ?: 0L) }
        if (playlist.none { it.id == item.id }) {
            playlist = listOf(item)
        }
        currentVideo = item
        hasManualOrientationOverride = false
        _currentPosition.value = startPositionMs.coerceAtLeast(0)
        _duration.value = 0L
        applyDefaultOrientationForVideo(item)
        playerManager.playMedia(item, startPositionMs)
    }

    fun preloadMedia(item: VideoMediaItem, startPositionMs: Long = 0) {
        releaseJob?.cancel()
        playerManager.preloadMedia(item, startPositionMs)
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            _isPlaying.value = false
            playerManager.pause()
        } else {
            _isPlaying.value = true
            playerManager.resume()
        }
    }

    fun seekTo(position: Long) {
        playerManager.seekTo(position)
        _currentPosition.value = position
    }

    fun playNext() {
        playRelativeVideo(offset = 1)
    }

    fun playPrevious() {
        playRelativeVideo(offset = -1)
    }

    private fun playRelativeVideo(offset: Int) {
        val current = currentVideo ?: return
        val items = playlist.takeIf { it.isNotEmpty() } ?: listOf(current)
        val currentIndex = items.indexOfFirst { it.id == current.id }
        if (currentIndex == -1) return

        if (items.size == 1) {
            if (offset < 0) {
                seekTo(0L)
            }
            return
        }

        val nextIndex = (currentIndex + offset + items.size) % items.size
        val nextVideo = items[nextIndex]
        val startPosition = progressRepository.getProgress(nextVideo.id)
        playMedia(nextVideo, startPosition)
    }

    fun toggleControls() {
        _showControls.value = !_showControls.value
        if (_showControls.value) {
            resetHideTimer()
        }
    }

    fun resetHideTimer() {
        hideJob?.cancel()
        hideJob = viewModelScope.launch {
            delay(3500)
            _showControls.value = false
        }
    }

    fun adjustVolume(delta: Float) {
        setVolume(_volume.value + delta)
    }

    fun setVolume(value: Float) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        if (maxVolume <= 0) return

        val newVolumeFloat = value.coerceIn(0f, 1f)
        _volume.value = newVolumeFloat
        _isMuted.value = newVolumeFloat == 0f

        val systemVolume = (newVolumeFloat * maxVolume).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, systemVolume, 0)
    }

    fun toggleMute() {
        if (_isMuted.value) {
            setVolume(volumeBeforeMute.takeIf { it > 0f } ?: 0.5f)
        } else {
            volumeBeforeMute = _volume.value.takeIf { it > 0f } ?: 0.5f
            setVolume(0f)
        }
    }

    fun adjustBrightness(delta: Float) {
        setBrightness(_brightness.value + delta)
    }

    fun setBrightness(value: Float) {
        _brightness.value = value.coerceIn(0f, 1f)
    }

    fun toggleLock() {
        _isLocked.value = !_isLocked.value
        if (_isLocked.value) {
            _showControls.value = false
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        playerManager.getPlayer()?.setPlaybackSpeed(speed)
    }

    fun setResizeMode(mode: Int) {
        _resizeMode.value = mode
    }

    fun toggleOrientation() {
        hasManualOrientationOverride = true
        _orientationMode.value = when (_orientationMode.value) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
    }

    private fun applyDefaultOrientationForVideo(item: VideoMediaItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val orientation = resolveVideoOrientation(item)
            if (!hasManualOrientationOverride && currentVideo?.id == item.id) {
                _orientationMode.value = orientation
            }
        }
    }

    private fun resolveVideoOrientation(item: VideoMediaItem): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(getApplication(), item.uri)
            val width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
                ?: return ActivityInfo.SCREEN_ORIENTATION_SENSOR
            val height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?: return ActivityInfo.SCREEN_ORIENTATION_SENSOR
            val rotation = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?: 0

            val displayWidth = if (rotation == 90 || rotation == 270) height else width
            val displayHeight = if (rotation == 90 || rotation == 270) width else height

            when {
                displayWidth > displayHeight -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                displayHeight > displayWidth -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            }
        } catch (_: Exception) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } finally {
            retriever.release()
        }
    }

    fun getAudioTracks() = playerManager.getAudioTracks()
    fun getSubtitleTracks() = playerManager.getSubtitleTracks()
    fun selectAudioTrack(id: String) = playerManager.selectAudioTrack(id)
    fun selectSubtitleTrack(id: String?) = playerManager.selectSubtitleTrack(id)

    fun setPipMode(inPipMode: Boolean) {
        _isInPipMode.value = inPipMode
        if (inPipMode) {
            _showControls.value = false
        }
    }

    fun stopPlayback() {
        currentVideo?.let {
            progressRepository.saveProgress(it.id, playerManager.getPlayer()?.currentPosition ?: 0L)
        }
        playerManager.pause()
        releaseJob?.cancel()
        releaseJob = viewModelScope.launch {
            delay(30_000)
            playerManager.releasePlayer()
        }
    }

    override fun onCleared() {
        releaseJob?.cancel()
        currentVideo?.let {
            progressRepository.saveProgress(it.id, playerManager.getPlayer()?.currentPosition ?: 0L)
        }
        playerManager.releasePlayer()
        super.onCleared()
    }
}

package com.antigravity.videoplayer.mobile.viewmodel

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.videoplayer.core.model.AudioTrackInfo
import com.antigravity.videoplayer.core.model.SubtitleTrackInfo
import com.antigravity.videoplayer.core.model.VideoMediaItem
import com.antigravity.videoplayer.core.player.PlayerManager
import com.antigravity.videoplayer.core.repository.PlaybackProgressRepository
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

    fun playMedia(item: VideoMediaItem, startPositionMs: Long = 0) {
        currentVideo = item
        playerManager.playMedia(item, startPositionMs)
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            playerManager.pause()
        } else {
            playerManager.resume()
        }
    }

    fun seekTo(position: Long) {
        playerManager.seekTo(position)
        _currentPosition.value = position
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
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        if (maxVolume <= 0) return
        
        val currentVolumeFloat = _volume.value
        val newVolumeFloat = (currentVolumeFloat + delta).coerceIn(0f, 1f)
        _volume.value = newVolumeFloat
        
        val systemVolume = (newVolumeFloat * maxVolume).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, systemVolume, 0)
    }

    fun adjustBrightness(delta: Float) {
        _brightness.value = (_brightness.value + delta).coerceIn(0f, 1f)
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
        _orientationMode.value = when (_orientationMode.value) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
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
        playerManager.releasePlayer()
    }

    override fun onCleared() {
        super.onCleared()
        currentVideo?.let {
            progressRepository.saveProgress(it.id, playerManager.getPlayer()?.currentPosition ?: 0L)
        }
        playerManager.releasePlayer()
    }
}

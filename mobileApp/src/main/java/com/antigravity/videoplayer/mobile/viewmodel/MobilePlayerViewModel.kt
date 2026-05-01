package com.antigravity.videoplayer.mobile.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.videoplayer.core.model.VideoMediaItem
import com.antigravity.videoplayer.core.player.PlayerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MobilePlayerViewModel(application: Application) : AndroidViewModel(application) {

    val playerManager = PlayerManager(application)
    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
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

    init {
        playerManager.initializePlayer()
        startProgressUpdate()
        
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

    fun playMedia(item: VideoMediaItem) {
        playerManager.playMedia(item)
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

    override fun onCleared() {
        super.onCleared()
        playerManager.releasePlayer()
    }
}

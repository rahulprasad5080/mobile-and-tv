package com.antigravity.videoplayer.mobile.viewmodel

import android.app.Application
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
    
    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _showControls = MutableStateFlow(true)
    val showControls: StateFlow<Boolean> = _showControls.asStateFlow()

    init {
        playerManager.initializePlayer()
        startProgressUpdate()
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

    override fun onCleared() {
        super.onCleared()
        playerManager.releasePlayer()
    }
}

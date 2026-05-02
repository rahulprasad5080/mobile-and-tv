package com.antigravity.videoplayer.tv.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.videoplayer.core.model.VideoMediaItem
import com.antigravity.videoplayer.core.player.PlayerManager
import com.antigravity.videoplayer.core.repository.PlaybackProgressRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TvPlayerViewModel(application: Application) : AndroidViewModel(application) {

    val playerManager = PlayerManager(application)
    private val progressRepository = PlaybackProgressRepository(application)
    private var currentVideo: VideoMediaItem? = null
    private var releaseJob: kotlinx.coroutines.Job? = null

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _currentTitle = MutableStateFlow("")
    val currentTitle: StateFlow<String> = _currentTitle.asStateFlow()

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

    fun playMedia(item: VideoMediaItem, startPositionMs: Long = 0) {
        releaseJob?.cancel()
        currentVideo = item
        _currentTitle.value = item.title
        playerManager.playMedia(item, startPositionMs)
    }

    fun preloadMedia(item: VideoMediaItem, startPositionMs: Long = 0) {
        releaseJob?.cancel()
        _currentTitle.value = item.title
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

    fun seekBy(deltaMs: Long) {
        val player = playerManager.getPlayer() ?: return
        val duration = player.duration.coerceAtLeast(0)
        val nextPosition = (player.currentPosition + deltaMs).coerceIn(0, duration)
        playerManager.seekTo(nextPosition)
        _currentPosition.value = nextPosition
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

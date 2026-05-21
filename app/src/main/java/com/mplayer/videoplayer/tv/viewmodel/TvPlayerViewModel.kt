package com.mplayer.videoplayer.tv.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mplayer.videoplayer.core.model.AudioTrackInfo
import com.mplayer.videoplayer.core.model.SubtitleTrackInfo
import com.mplayer.videoplayer.core.model.VideoMediaItem
import com.mplayer.videoplayer.core.player.PlayerManager
import com.mplayer.videoplayer.core.repository.PlaybackProgressRepository
import com.mplayer.videoplayer.tv.util.cleanTvTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TvPlayerViewModel(application: Application) : AndroidViewModel(application) {

    val playerManager = PlayerManager(application)
    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val progressRepository = PlaybackProgressRepository(application)
    private var currentVideo: VideoMediaItem? = null
    private var playlist: List<VideoMediaItem> = emptyList()
    private var releaseJob: kotlinx.coroutines.Job? = null
    private var volumeBeforeMute = 0.5f

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _currentTitle = MutableStateFlow("")
    val currentTitle: StateFlow<String> = _currentTitle.asStateFlow()

    private val _volume = MutableStateFlow(0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _resizeMode = MutableStateFlow(0)
    val resizeMode: StateFlow<Int> = _resizeMode.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<AudioTrackInfo>>(emptyList())
    val audioTracks: StateFlow<List<AudioTrackInfo>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrackInfo>>(emptyList())
    val subtitleTracks: StateFlow<List<SubtitleTrackInfo>> = _subtitleTracks.asStateFlow()

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
        if (maxVolume > 0) {
            _volume.value = currentVolume / maxVolume
            _isMuted.value = currentVolume == 0f
        }
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
                delay(500) // 500ms for responsive TV remote seeking (was 1000ms)
            }
        }
    }

    fun playMedia(item: VideoMediaItem, startPositionMs: Long = 0) {
        // Cancel any pending release — prevents race condition where old releaseJob
        // would release the player we just started (reference: Player-master lifecycle logic)
        releaseJob?.cancel()
        releaseJob = null
        currentVideo
            ?.takeIf { it.id != item.id }
            ?.let { progressRepository.saveProgress(it.id, playerManager.getPlayer()?.currentPosition ?: 0L) }
        if (playlist.none { it.id == item.id }) {
            playlist = listOf(item)
        }
        currentVideo = item
        _currentTitle.value = item.cleanTvTitle()
        _currentPosition.value = startPositionMs.coerceAtLeast(0)
        _duration.value = 0L
        playerManager.playMedia(item, startPositionMs)
        playerManager.getPlayer()?.setPlaybackSpeed(_playbackSpeed.value)
    }

    fun preloadMedia(item: VideoMediaItem, startPositionMs: Long = 0) {
        releaseJob?.cancel()
        _currentTitle.value = item.cleanTvTitle()
        playerManager.preloadMedia(item, startPositionMs)
    }

    fun setPlaylist(items: List<VideoMediaItem>, currentItem: VideoMediaItem) {
        playlist = items.takeIf { list -> list.any { it.id == currentItem.id } } ?: listOf(currentItem)
    }

    fun togglePlayPause() {
        val player = playerManager.getPlayer()
        val shouldPause = player?.isPlaying ?: player?.playWhenReady ?: _isPlaying.value
        if (shouldPause) {
            _isPlaying.value = false
            playerManager.pause()
        } else {
            _isPlaying.value = true
            playerManager.resume()
        }
    }

    /**
     * Seek by delta — uses SeekParameters for TV-quality seeking.
     * Negative delta = backward (PREVIOUS_SYNC), positive = forward (NEXT_SYNC).
     * Mirrors reference Player-master DPAD_LEFT/RIGHT logic.
     */
    fun seekBy(deltaMs: Long) {
        if (deltaMs < 0) {
            playerManager.seekBackward(-deltaMs)
        } else {
            playerManager.seekForward(deltaMs)
        }
        val player = playerManager.getPlayer()
        if (player != null) {
            _currentPosition.value = player.currentPosition
            _isPlaying.value = player.playWhenReady
        }
    }

    fun seekTo(position: Long) {
        val safePosition = position.coerceAtLeast(0)
        playerManager.seekTo(safePosition)
        _currentPosition.value = safePosition
        playerManager.getPlayer()?.let { _isPlaying.value = it.playWhenReady }
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

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        playerManager.getPlayer()?.setPlaybackSpeed(speed)
    }

    fun setResizeMode(mode: Int) {
        _resizeMode.value = mode
    }

    fun adjustVolume(delta: Float) {
        setVolume(_volume.value + delta)
    }

    fun increaseVolume() {
        stepVolume(1)
    }

    fun decreaseVolume() {
        stepVolume(-1)
    }

    private fun stepVolume(direction: Int) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxVolume <= 0) return

        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val nextVolume = (currentVolume + direction).coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVolume, 0)
        _volume.value = nextVolume.toFloat() / maxVolume.toFloat()
        _isMuted.value = nextVolume == 0
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

    fun selectAudioTrack(id: String) = playerManager.selectAudioTrack(id)

    fun selectSubtitleTrack(id: String?) = playerManager.selectSubtitleTrack(id)

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

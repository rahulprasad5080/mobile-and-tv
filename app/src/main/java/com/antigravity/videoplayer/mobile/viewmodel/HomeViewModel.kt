package com.antigravity.videoplayer.mobile.viewmodel

import android.app.Application
import android.database.ContentObserver
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.videoplayer.core.model.VideoMediaItem
import com.antigravity.videoplayer.core.repository.VideoFileRepository
import com.antigravity.videoplayer.core.repository.VideoRepository
import com.antigravity.videoplayer.core.repository.PlaybackProgressRepository
import android.content.IntentSender
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VideoRepository()
    private val fileRepository = VideoFileRepository()
    private val progressRepository = PlaybackProgressRepository(application)
    
    private val _pendingIntent = MutableStateFlow<IntentSender?>(null)
    val pendingIntent: StateFlow<IntentSender?> = _pendingIntent.asStateFlow()
    
    private val _videos = MutableStateFlow<List<VideoMediaItem>>(emptyList())
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _recentlyPlayed = MutableStateFlow<List<VideoMediaItem>>(emptyList())
    val recentlyPlayed: StateFlow<List<VideoMediaItem>> = _recentlyPlayed.asStateFlow()

    private val _lastPlayedVideoId = MutableStateFlow<String?>(progressRepository.getLastPlayedVideoId())
    private val _cachedLastPlayedVideo = MutableStateFlow(progressRepository.getLastPlayedVideo())
    private var loadVideosJob: Job? = null
    private var refreshDebounceJob: Job? = null
    private var mediaObserver: ContentObserver? = null

    val videos: StateFlow<List<VideoMediaItem>> = combine(_videos, _searchQuery) { videos, query ->
        if (query.isBlank()) {
            videos
        } else {
            videos.filter { it.title.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val groupedVideos: StateFlow<Map<String, List<VideoMediaItem>>> = videos.combine(_searchQuery) { videoList, _ ->
        videoList.groupBy { it.folderName }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val lastPlayedVideo: StateFlow<VideoMediaItem?> = combine(
        _lastPlayedVideoId,
        _videos,
        _cachedLastPlayedVideo
    ) { lastId, all, cached ->
        all.find { it.id == lastId } ?: cached?.takeIf { it.id == lastId }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun loadVideos() {
        loadVideosJob?.cancel()
        loadVideosJob = viewModelScope.launch {
            repository.getVideos(getApplication()).collect {
                _videos.value = it
                val lastId = _lastPlayedVideoId.value
                val latestLastPlayed = it.find { video -> video.id == lastId }
                if (latestLastPlayed != null) {
                    _cachedLastPlayedVideo.value = latestLastPlayed
                    progressRepository.saveLastPlayedVideo(latestLastPlayed)
                }
            }
        }
    }

    private fun refreshVideosSoon() {
        refreshDebounceJob?.cancel()
        refreshDebounceJob = viewModelScope.launch {
            delay(300)
            loadVideos()
        }
    }

    fun startMediaStoreObserver() {
        if (mediaObserver != null) return

        mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refreshVideosSoon()
            }
        }.also { observer ->
            getApplication<Application>().contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun addToRecentlyPlayed(video: VideoMediaItem) {
        _lastPlayedVideoId.value = video.id
        _cachedLastPlayedVideo.value = video
        progressRepository.saveLastPlayedVideo(video)
        val currentList = _recentlyPlayed.value.toMutableList()
        currentList.remove(video)
        currentList.add(0, video)
        _recentlyPlayed.value = currentList.take(10)
    }

    fun deleteVideo(video: VideoMediaItem) {
        viewModelScope.launch {
            val intentSender = fileRepository.deleteVideo(getApplication(), Uri.parse(video.uri.toString()))
            if (intentSender != null) {
                _pendingIntent.value = intentSender
            } else {
                loadVideos()
            }
        }
    }

    fun renameVideo(video: VideoMediaItem, newName: String) {
        viewModelScope.launch {
            val intentSender = fileRepository.renameVideo(getApplication(), Uri.parse(video.uri.toString()), newName)
            if (intentSender != null) {
                _pendingIntent.value = intentSender
            } else {
                loadVideos()
            }
        }
    }

    fun copyVideo(video: VideoMediaItem, targetName: String) {
        viewModelScope.launch {
            if (fileRepository.copyVideo(getApplication(), Uri.parse(video.uri.toString()), targetName)) {
                loadVideos()
            }
        }
    }

    fun clearPendingIntent() {
        _pendingIntent.value = null
    }

    override fun onCleared() {
        mediaObserver?.let {
            getApplication<Application>().contentResolver.unregisterContentObserver(it)
        }
        mediaObserver = null
        super.onCleared()
    }
}

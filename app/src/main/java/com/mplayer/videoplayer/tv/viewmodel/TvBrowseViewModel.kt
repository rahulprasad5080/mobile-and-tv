package com.mplayer.videoplayer.tv.viewmodel

import android.app.Application
import android.content.IntentSender
import android.database.ContentObserver
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mplayer.videoplayer.core.model.VideoMediaItem
import com.mplayer.videoplayer.core.repository.VideoFileRepository
import com.mplayer.videoplayer.core.repository.VideoRepository
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TvBrowseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VideoRepository()
    private val fileRepository = VideoFileRepository()

    private val _pendingIntent = MutableStateFlow<IntentSender?>(null)
    val pendingIntent: StateFlow<IntentSender?> = _pendingIntent.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _videos = MutableStateFlow<List<VideoMediaItem>>(emptyList())
    private val _deletedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _renamedItems = MutableStateFlow<Map<String, String>>(emptyMap())

    val videos: StateFlow<List<VideoMediaItem>> = combine(
        _videos,
        _deletedIds,
        _renamedItems
    ) { videos, deleted, renamed ->
        videos.filter { it.id !in deleted }
            .map { video -> renamed[video.id]?.let { video.copy(title = it) } ?: video }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var loadVideosJob: Job? = null
    private var mediaObserver: ContentObserver? = null
    
    private var pendingDeleteId: String? = null
    private var pendingRenameId: String? = null

    fun loadVideos() {
        loadVideosJob?.cancel()
        loadVideosJob = viewModelScope.launch {
            _isLoading.value = _videos.value.isEmpty()
            repository.getVideos(getApplication()).collect { latestVideos ->
                // Bug 4 fix (Android TV 9): Do NOT prune _deletedIds based on whether the
                // file is still in MediaStore. On Android 9, MediaStore takes 1-2 seconds to
                // reflect a deletion after the file is physically removed. The ContentObserver
                // fires immediately, triggering loadVideos() — and the old code would remove
                // the ID from _deletedIds because it still appeared in MediaStore, making the
                // deleted file reappear in the UI. Now we keep _deletedIds as-is; the ID
                // becomes a no-op filter once MediaStore eventually removes the file from _videos.
                _renamedItems.value = _renamedItems.value.filter { (id, newTitle) ->
                    latestVideos.find { it.id == id }?.title != newTitle
                }
                _videos.value = latestVideos
                _isLoading.value = false
            }
        }
    }

    fun deleteVideo(video: VideoMediaItem) {
        _deletedIds.value = _deletedIds.value + video.id
        viewModelScope.launch {
            try {
                val intentSender = fileRepository.deleteVideo(getApplication(), video.uri)
                if (intentSender != null) {
                    pendingDeleteId = video.id
                    _pendingIntent.value = intentSender
                } else {
                    loadVideos()
                }
            } catch (_: Exception) {
                _deletedIds.value = _deletedIds.value - video.id
                loadVideos()
            }
        }
    }

    fun renameVideo(video: VideoMediaItem, newName: String) {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return

        _renamedItems.value = _renamedItems.value + (video.id to cleanName)
        viewModelScope.launch {
            try {
                val intentSender = fileRepository.renameVideo(getApplication(), video.uri, cleanName)
                if (intentSender != null) {
                    pendingRenameId = video.id
                    _pendingIntent.value = intentSender
                } else {
                    loadVideos()
                }
            } catch (_: Exception) {
                _renamedItems.value = _renamedItems.value - video.id
                loadVideos()
            }
        }
    }

    fun clearPendingIntent() {
        _pendingIntent.value = null
    }

    fun handleIntentSenderResult(success: Boolean) {
        val pRenameId = pendingRenameId
        val pDeleteId = pendingDeleteId
        pendingDeleteId = null
        pendingRenameId = null

        if (!success) {
            pDeleteId?.let { _deletedIds.value = _deletedIds.value - it }
            pRenameId?.let { _renamedItems.value = _renamedItems.value - it }
        }
        // Always refresh list after intent result
        loadVideos()
    }

    fun startMediaStoreObserver() {
        if (mediaObserver != null) return

        mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                loadVideos()
            }
        }.also { observer ->
            getApplication<Application>().contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        }
    }

    override fun onCleared() {
        mediaObserver?.let {
            getApplication<Application>().contentResolver.unregisterContentObserver(it)
        }
        mediaObserver = null
        super.onCleared()
    }
}

package com.mplayer.videoplayer.mobile.viewmodel

import android.app.Application
import android.database.ContentObserver
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mplayer.videoplayer.core.model.VideoMediaItem
import com.mplayer.videoplayer.core.repository.VideoFileRepository
import com.mplayer.videoplayer.core.repository.VideoRepository
import com.mplayer.videoplayer.core.repository.PlaybackProgressRepository
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    private var folderObserver: android.os.FileObserver? = null

    private val _deletedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _renamedItems = MutableStateFlow<Map<String, String>>(emptyMap())

    private var pendingDeleteId: String? = null
    private var pendingRenameId: String? = null

    private val _copiedVideo = MutableStateFlow<VideoMediaItem?>(null)
    val copiedVideo: StateFlow<VideoMediaItem?> = _copiedVideo.asStateFlow()

    val videos: StateFlow<List<VideoMediaItem>> = combine(
        _videos, 
        _searchQuery, 
        _deletedIds, 
        _renamedItems
    ) { videos, query, deleted, renamed ->
        videos.filter { it.id !in deleted }
            .map { video ->
                renamed[video.id]?.let { video.copy(title = it) } ?: video
            }
            .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val groupedVideos: StateFlow<Map<String, List<VideoMediaItem>>> = videos.map { videoList ->
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
            repository.getVideos(getApplication()).collect { it ->
                // Clear state if MediaStore has caught up
                val currentDeleted = _deletedIds.value
                val currentRenamed = _renamedItems.value
                
                if (currentDeleted.isNotEmpty() || currentRenamed.isNotEmpty()) {
                    val stillInMediaStore = it.map { it.id }.toSet()
                    val newDeleted = currentDeleted.filter { id -> stillInMediaStore.contains(id) }.toSet()
                    
                    val newRenamed = currentRenamed.filter { (id, newTitle) ->
                        val item = it.find { video -> video.id == id }
                        item != null && item.title != newTitle
                    }
                    
                    _deletedIds.value = newDeleted
                    _renamedItems.value = newRenamed
                }

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

    fun startFolderObserver(folderPath: String?) {
        folderObserver?.stopWatching()
        if (folderPath == null) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            folderObserver = object : android.os.FileObserver(File(folderPath), 
                android.os.FileObserver.CREATE or android.os.FileObserver.DELETE or android.os.FileObserver.MOVED_FROM or android.os.FileObserver.MOVED_TO) {
                override fun onEvent(event: Int, path: String?) {
                    refreshVideosSoon()
                }
            }
        } else {
            @Suppress("DEPRECATION")
            folderObserver = object : android.os.FileObserver(folderPath, 
                android.os.FileObserver.CREATE or android.os.FileObserver.DELETE or android.os.FileObserver.MOVED_FROM or android.os.FileObserver.MOVED_TO) {
                override fun onEvent(event: Int, path: String?) {
                    refreshVideosSoon()
                }
            }
        }
        folderObserver?.startWatching()
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
            } catch (e: Exception) {
                _deletedIds.value = _deletedIds.value - video.id
                loadVideos()
            }
        }
    }

    fun renameVideo(video: VideoMediaItem, newName: String) {
        _renamedItems.value = _renamedItems.value + (video.id to newName)

        viewModelScope.launch {
            try {
                val intentSender = fileRepository.renameVideo(getApplication(), video.uri, newName)
                if (intentSender != null) {
                    pendingRenameId = video.id
                    _pendingIntent.value = intentSender
                } else {
                    loadVideos()
                }
            } catch (e: Exception) {
                _renamedItems.value = _renamedItems.value - video.id
                loadVideos()
            }
        }
    }

    fun copyVideoToClipboard(video: VideoMediaItem) {
        _copiedVideo.value = video
    }

    fun pasteVideo(targetFolderName: String, targetFolderPath: String?) {
        val video = _copiedVideo.value ?: return
        if (targetFolderPath == null) return

        viewModelScope.launch {
            val success = fileRepository.copyVideoToFolder(
                getApplication(), 
                video.uri, 
                File(targetFolderPath)
            )
            if (success) {
                _copiedVideo.value = null
                loadVideos()
            }
        }
    }

    fun copyVideosToFolder(videos: List<VideoMediaItem>, targetFolderPath: String?) {
        if (videos.isEmpty() || targetFolderPath == null) return

        viewModelScope.launch {
            val targetFolder = File(targetFolderPath)
            var copiedAny = false
            videos.forEach { video ->
                val success = fileRepository.copyVideoToFolder(
                    getApplication(),
                    video.uri,
                    targetFolder
                )
                copiedAny = copiedAny || success
            }
            if (copiedAny) {
                loadVideos()
            }
        }
    }

    fun moveVideosToFolder(videos: List<VideoMediaItem>, targetFolderPath: String?) {
        if (videos.isEmpty() || targetFolderPath == null) return

        viewModelScope.launch {
            val targetFolder = File(targetFolderPath)
            val movedIds = mutableListOf<String>()
            videos.forEach { video ->
                val copied = fileRepository.copyVideoToFolder(
                    getApplication(),
                    video.uri,
                    targetFolder
                )
                if (copied) {
                    movedIds += video.id
                    _deletedIds.value = _deletedIds.value + video.id
                    try {
                        val intentSender = fileRepository.deleteVideo(getApplication(), video.uri)
                        if (intentSender != null) {
                            _pendingIntent.value = intentSender
                        }
                    } catch (e: Exception) {
                        _deletedIds.value = _deletedIds.value - video.id
                    }
                }
            }
            if (movedIds.isNotEmpty()) {
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
            loadVideos()
        } else {
            if (pRenameId != null) {
                val newName = _renamedItems.value[pRenameId]
                val video = _videos.value.find { it.id == pRenameId }
                if (newName != null && video != null) {
                    viewModelScope.launch {
                        try {
                            fileRepository.renameVideo(getApplication(), video.uri, newName)
                        } catch (_: Exception) {
                            _renamedItems.value = _renamedItems.value - pRenameId
                        }
                        loadVideos()
                    }
                } else {
                    loadVideos()
                }
            } else {
                loadVideos()
            }
        }
    }

    override fun onCleared() {
        mediaObserver?.let {
            getApplication<Application>().contentResolver.unregisterContentObserver(it)
        }
        mediaObserver = null
        folderObserver?.stopWatching()
        folderObserver = null
        super.onCleared()
    }
}

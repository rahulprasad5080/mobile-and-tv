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
    private val _renamedFolders = MutableStateFlow<Map<String, String>>(emptyMap())

    private var pendingDeleteId: String? = null
    private var pendingRenameId: String? = null
    private var pendingRenameVideo: VideoMediaItem? = null
    private var pendingRenameVideoNewName: String? = null
    private var pendingDeleteIds: List<String>? = null
    private var pendingRenameFolderOldName: String? = null
    private var pendingRenameFolderNewName: String? = null
    private var pendingRenameFolderVideos: List<VideoMediaItem>? = null

    private val _copiedVideo = MutableStateFlow<VideoMediaItem?>(null)
    val copiedVideo: StateFlow<VideoMediaItem?> = _copiedVideo.asStateFlow()

    val videos: StateFlow<List<VideoMediaItem>> = combine(
        _videos, 
        _searchQuery, 
        _deletedIds, 
        _renamedItems,
        _renamedFolders
    ) { videos, query, deleted, renamedItems, renamedFolders ->
        videos.filter { it.id !in deleted }
            .map { video ->
                var updatedVideo = video
                renamedItems[video.id]?.let { updatedVideo = updatedVideo.copy(title = it) }
                renamedFolders[video.folderName]?.let { updatedVideo = updatedVideo.copy(folderName = it) }
                updatedVideo
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
                // Keep delete tombstones until an explicit delete failure/cancel.
                // MediaStore can briefly report stale rows after deletion, which would make
                // deleted videos reappear if we prune _deletedIds during a refresh.
                val currentRenamed = _renamedItems.value
                if (currentRenamed.isNotEmpty()) {
                    val newRenamed = currentRenamed.filter { (id, newTitle) ->
                        val item = it.find { video -> video.id == id }
                        item != null && item.title != newTitle
                    }
                    _renamedItems.value = newRenamed
                }

                val currentRenamedFolders = _renamedFolders.value
                if (currentRenamedFolders.isNotEmpty()) {
                    val newRenamedFolders = currentRenamedFolders.filter { (oldFolder, _) ->
                        it.any { video -> video.folderName == oldFolder }
                    }
                    _renamedFolders.value = newRenamedFolders
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

    fun deleteVideos(videos: List<VideoMediaItem>) {
        if (videos.isEmpty()) return
        val ids = videos.map { it.id }.toSet()
        _deletedIds.value = _deletedIds.value + ids
        viewModelScope.launch {
            try {
                val intentSender = fileRepository.deleteVideos(getApplication(), videos.map { it.uri })
                if (intentSender != null) {
                    pendingDeleteIds = videos.map { it.id }
                    _pendingIntent.value = intentSender
                } else {
                    loadVideos()
                }
            } catch (e: Exception) {
                _deletedIds.value = _deletedIds.value - ids
                loadVideos()
            }
        }
    }

    fun deleteFolder(folderName: String) {
        val folderVideos = _videos.value.filter { it.folderName == folderName }
        deleteVideos(folderVideos)
    }

    fun renameVideo(video: VideoMediaItem, newName: String) {
        _renamedItems.value = _renamedItems.value + (video.id to newName)

        viewModelScope.launch {
            try {
                val intentSender = fileRepository.renameVideo(getApplication(), video.uri, newName)
                if (intentSender != null) {
                    pendingRenameVideo = video
                    pendingRenameVideoNewName = newName
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

    fun renameFolder(oldFolderName: String, newFolderName: String) {
        val folderVideos = _videos.value.filter { it.folderName == oldFolderName }
        if (folderVideos.isEmpty()) return

        _renamedFolders.value = _renamedFolders.value + (oldFolderName to newFolderName)

        viewModelScope.launch {
            try {
                val intentSender = fileRepository.renameFolder(getApplication(), folderVideos, newFolderName)
                if (intentSender != null) {
                    pendingRenameFolderOldName = oldFolderName
                    pendingRenameFolderNewName = newFolderName
                    pendingRenameFolderVideos = folderVideos
                    _pendingIntent.value = intentSender
                } else {
                    loadVideos()
                }
            } catch (e: Exception) {
                _renamedFolders.value = _renamedFolders.value - oldFolderName
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
            val movedVideos = mutableListOf<VideoMediaItem>()
            
            videos.forEach { video ->
                val copied = fileRepository.copyVideoToFolder(
                    getApplication(),
                    video.uri,
                    targetFolder
                )
                if (copied) {
                    movedVideos.add(video)
                }
            }

            if (movedVideos.isNotEmpty()) {
                val movedIds = movedVideos.map { it.id }.toSet()
                _deletedIds.value = _deletedIds.value + movedIds
                
                try {
                    val urisToDelete = movedVideos.map { it.uri }
                    val intentSender = fileRepository.deleteVideos(getApplication(), urisToDelete)
                    if (intentSender != null) {
                        pendingDeleteIds = movedVideos.map { it.id }
                        _pendingIntent.value = intentSender
                    } else {
                        loadVideos()
                    }
                } catch (e: Exception) {
                    _deletedIds.value = _deletedIds.value - movedIds
                    loadVideos()
                }
            }
        }
    }

    fun clearPendingIntent() {
        _pendingIntent.value = null
    }

    fun handleIntentSenderResult(success: Boolean) {
        val pRenameVideo = pendingRenameVideo
        val pRenameVideoNewName = pendingRenameVideoNewName
        val pDeleteId = pendingDeleteId
        val pDeleteIds = pendingDeleteIds
        
        val pRenameOld = pendingRenameFolderOldName
        val pRenameNew = pendingRenameFolderNewName
        val pRenameVideos = pendingRenameFolderVideos

        pendingDeleteId = null
        pendingDeleteIds = null
        pendingRenameVideo = null
        pendingRenameVideoNewName = null
        pendingRenameFolderOldName = null
        pendingRenameFolderNewName = null
        pendingRenameFolderVideos = null

        if (success) {
            if (pRenameVideo != null && pRenameVideoNewName != null) {
                viewModelScope.launch {
                    try {
                        fileRepository.renameVideo(getApplication(), pRenameVideo.uri, pRenameVideoNewName)
                        loadVideos()
                    } catch (e: Exception) {
                        _renamedItems.value = _renamedItems.value - pRenameVideo.id
                        loadVideos()
                    }
                }
            }
            if (pRenameOld != null && pRenameNew != null && pRenameVideos != null) {
                viewModelScope.launch {
                    try {
                        fileRepository.renameFolder(getApplication(), pRenameVideos, pRenameNew, requestPermission = false)
                        loadVideos()
                    } catch (e: Exception) {
                        loadVideos()
                    }
                }
            }
        } else {
            pDeleteId?.let { _deletedIds.value = _deletedIds.value - it }
            pDeleteIds?.let { _deletedIds.value = _deletedIds.value - it.toSet() }
            pRenameVideo?.let { _renamedItems.value = _renamedItems.value - it.id }
            pRenameOld?.let { _renamedFolders.value = _renamedFolders.value - it }
        }
        // Always refresh after intent result — OS handles the actual delete on success
        loadVideos()
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

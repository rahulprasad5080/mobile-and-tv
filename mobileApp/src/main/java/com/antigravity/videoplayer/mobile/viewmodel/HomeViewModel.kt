package com.antigravity.videoplayer.mobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.videoplayer.core.model.VideoMediaItem
import com.antigravity.videoplayer.core.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VideoRepository()
    
    private val _videos = MutableStateFlow<List<VideoMediaItem>>(emptyList())
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val videos: StateFlow<List<VideoMediaItem>> = combine(_videos, _searchQuery) { videos, query ->
        if (query.isBlank()) {
            videos
        } else {
            videos.filter { it.title.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        loadVideos()
    }

    fun loadVideos() {
        viewModelScope.launch {
            repository.getVideos(getApplication()).collect {
                _videos.value = it
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }
}

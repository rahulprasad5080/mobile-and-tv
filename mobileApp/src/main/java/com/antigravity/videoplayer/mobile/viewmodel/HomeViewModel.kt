package com.antigravity.videoplayer.mobile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.videoplayer.core.model.VideoMediaItem
import com.antigravity.videoplayer.core.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val repository = VideoRepository()
    
    private val _videos = MutableStateFlow<List<VideoMediaItem>>(emptyList())
    val videos: StateFlow<List<VideoMediaItem>> = _videos.asStateFlow()

    init {
        loadVideos()
    }

    private fun loadVideos() {
        viewModelScope.launch {
            repository.getVideos().collect {
                _videos.value = it
            }
        }
    }
}

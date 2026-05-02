package com.antigravity.videoplayer.tv.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.videoplayer.core.model.VideoMediaItem
import com.antigravity.videoplayer.core.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TvBrowseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VideoRepository()

    private val _videos = MutableStateFlow<List<VideoMediaItem>>(emptyList())
    val videos: StateFlow<List<VideoMediaItem>> = _videos.asStateFlow()

    fun loadVideos() {
        viewModelScope.launch {
            repository.getVideos(getApplication()).collect {
                _videos.value = it
            }
        }
    }
}

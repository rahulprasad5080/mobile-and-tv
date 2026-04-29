package com.antigravity.videoplayer.core.repository

import android.net.Uri
import com.antigravity.videoplayer.core.model.VideoMediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class VideoRepository {
    fun getVideos(): Flow<List<VideoMediaItem>> = flow {
        val videos = listOf(
            VideoMediaItem(
                id = "1",
                title = "Big Buck Bunny",
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
            ),
            VideoMediaItem(
                id = "2",
                title = "Elephant Dream",
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4")
            ),
            VideoMediaItem(
                id = "3",
                title = "For Bigger Blazes",
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4")
            ),
            VideoMediaItem(
                id = "4",
                title = "For Bigger Escapes",
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4")
            )
        )
        emit(videos)
    }
}

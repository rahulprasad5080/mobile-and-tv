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
                description = "A large, lovable rabbit is bullied by three smaller rodents.",
                thumbnailUri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg"),
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
            ),
            VideoMediaItem(
                id = "2",
                title = "Elephant Dream",
                description = "Two people explore a strange, mechanical world.",
                thumbnailUri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ElephantsDream.jpg"),
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4")
            ),
            VideoMediaItem(
                id = "3",
                title = "For Bigger Blazes",
                description = "A short promotional video about a fire-related theme.",
                thumbnailUri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerBlazes.jpg"),
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4")
            ),
            VideoMediaItem(
                id = "4",
                title = "For Bigger Escapes",
                description = "A short promotional video about an escape theme.",
                thumbnailUri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerEscapes.jpg"),
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4")
            )
        )
        emit(videos)
    }
}

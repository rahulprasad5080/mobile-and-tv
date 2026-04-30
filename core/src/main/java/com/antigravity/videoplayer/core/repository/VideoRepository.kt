package com.antigravity.videoplayer.core.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.antigravity.videoplayer.core.model.VideoMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class VideoRepository {
    
    fun getVideos(context: Context): Flow<List<VideoMediaItem>> = flow {
        val remoteVideos = listOf(
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
            )
        )
        
        val localVideos = mutableListOf<VideoMediaItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATA
        )
        
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val duration = cursor.getLong(durationColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                
                localVideos.add(
                    VideoMediaItem(
                        id = id.toString(),
                        title = name,
                        description = "Local Video • ${formatDuration(duration)}",
                        thumbnailUri = contentUri, // ContentUri can be used with coil for thumbnails
                        uri = contentUri
                    )
                )
            }
        }
        
        emit(remoteVideos + localVideos)
    }.flowOn(Dispatchers.IO)

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}

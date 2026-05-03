package com.mplayer.videoplayer.core.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.mplayer.videoplayer.core.model.VideoMediaItem
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class VideoRepository {

    fun getVideos(context: Context): Flow<List<VideoMediaItem>> = flow {
        val localVideos = mutableListOf<VideoMediaItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.MIME_TYPE,
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
            val durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
            val mimeTypeColumn = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
            val dataColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val duration = durationColumn
                    .takeIf { it >= 0 }
                    ?.let { cursor.getLong(it) }
                    ?: 0L
                val mimeType = mimeTypeColumn
                    .takeIf { it >= 0 }
                    ?.let { cursor.getString(it) }
                val filePath = dataColumn
                    .takeIf { it >= 0 }
                    ?.let { cursor.getString(it) }
                val folderName = filePath
                    ?.let { File(it).parentFile?.name }
                    ?.takeIf { it.isNotBlank() }
                    ?: "Internal memory"
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                localVideos.add(
                    VideoMediaItem(
                        id = id.toString(),
                        title = name,
                        description = "Local Video - ${formatDuration(duration)}",
                        thumbnailUri = contentUri,
                        uri = contentUri,
                        filePath = filePath,
                        folderName = folderName,
                        mimeType = mimeType
                    )
                )
            }
        }

        emit(localVideos)
    }.flowOn(Dispatchers.IO)

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}

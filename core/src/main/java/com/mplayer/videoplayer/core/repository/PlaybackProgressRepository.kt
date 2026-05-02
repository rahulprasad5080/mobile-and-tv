package com.mplayer.videoplayer.core.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.mplayer.videoplayer.core.model.VideoMediaItem

class PlaybackProgressRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("playback_progress", Context.MODE_PRIVATE)

    fun saveProgress(videoId: String, positionMs: Long) {
        prefs.edit()
            .putLong(videoId, positionMs)
            .putString("last_played_video_id", videoId)
            .apply()
    }

    fun saveLastPlayedVideo(video: VideoMediaItem) {
        prefs.edit()
            .putString("last_video_id", video.id)
            .putString("last_video_title", video.title)
            .putString("last_video_description", video.description)
            .putString("last_video_thumbnail_uri", video.thumbnailUri?.toString())
            .putString("last_video_uri", video.uri.toString())
            .putString("last_video_folder_name", video.folderName)
            .putString("last_video_mime_type", video.mimeType)
            .putString("last_played_video_id", video.id)
            .apply()
    }

    fun getLastPlayedVideo(): VideoMediaItem? {
        val id = prefs.getString("last_video_id", null) ?: return null
        val uri = prefs.getString("last_video_uri", null)?.let(Uri::parse) ?: return null
        val thumbnailUri = prefs.getString("last_video_thumbnail_uri", null)?.let(Uri::parse)

        return VideoMediaItem(
            id = id,
            title = prefs.getString("last_video_title", null).orEmpty(),
            description = prefs.getString("last_video_description", null).orEmpty(),
            thumbnailUri = thumbnailUri,
            uri = uri,
            folderName = prefs.getString("last_video_folder_name", null) ?: "Others",
            mimeType = prefs.getString("last_video_mime_type", null)
        )
    }

    fun getProgress(videoId: String): Long {
        return prefs.getLong(videoId, 0L)
    }

    fun getLastPlayedVideoId(): String? {
        return prefs.getString("last_played_video_id", null)
    }
}

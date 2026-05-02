package com.antigravity.videoplayer.core.repository

import android.content.Context
import android.content.SharedPreferences

class PlaybackProgressRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("playback_progress", Context.MODE_PRIVATE)

    fun saveProgress(videoId: String, positionMs: Long) {
        prefs.edit()
            .putLong(videoId, positionMs)
            .putString("last_played_video_id", videoId)
            .apply()
    }

    fun getProgress(videoId: String): Long {
        return prefs.getLong(videoId, 0L)
    }

    fun getLastPlayedVideoId(): String? {
        return prefs.getString("last_played_video_id", null)
    }
}

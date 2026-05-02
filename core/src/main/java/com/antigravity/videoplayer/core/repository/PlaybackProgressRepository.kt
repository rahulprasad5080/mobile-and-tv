package com.antigravity.videoplayer.core.repository

import android.content.Context
import android.content.SharedPreferences

class PlaybackProgressRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("playback_progress", Context.MODE_PRIVATE)

    fun saveProgress(videoId: String, positionMs: Long) {
        prefs.edit().putLong(videoId, positionMs).apply()
    }

    fun getProgress(videoId: String): Long {
        return prefs.getLong(videoId, 0L)
    }
}

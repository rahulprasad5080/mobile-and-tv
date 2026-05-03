package com.mplayer.videoplayer.core.model

import android.net.Uri

data class VideoMediaItem(
    val id: String,
    val title: String,
    val description: String = "",
    val thumbnailUri: Uri? = null,
    val uri: Uri,
    val filePath: String? = null,
    val folderName: String = "Others",
    val mimeType: String? = null,
    val subtitles: List<SubtitleTrack> = emptyList()
)

data class SubtitleTrack(
    val uri: Uri,
    val mimeType: String,
    val language: String? = null,
    val label: String? = null
)

data class AudioTrackInfo(
    val id: String,
    val language: String?,
    val label: String?,
    val bitRate: Int,
    val channelCount: Int,
    val displayLabel: String,
    val isSelected: Boolean
)

data class SubtitleTrackInfo(
    val id: String,
    val language: String?,
    val label: String?,
    val displayLabel: String,
    val isSelected: Boolean
)

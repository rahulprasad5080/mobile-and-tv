package com.mplayer.videoplayer.tv.util

import com.mplayer.videoplayer.core.model.VideoMediaItem
import java.io.File

private val containerSuffixPattern = Regex(
    pattern = """(?i)(?:\s+|[\s\-_:|]+|\s*[\[(])(?:video/|application/)?(?:x-)?(?:matroska|mp4|mpeg4|quicktime|msvideo|avi|flv|webm|ogg|3gpp2?|mp2t|m4v|wmv|hevc|h26[45]|avc|mpeg)\)?\]?$"""
)

private val metadataOnlyPattern = Regex(
    pattern = """(?i)^(?:video/|application/)?(?:x-)?(?:matroska|mp4|mpeg4|quicktime|msvideo|avi|flv|webm|ogg|3gpp2?|mp2t|m4v|wmv|hevc|h26[45]|avc|mpeg)$"""
)

fun VideoMediaItem.cleanTvTitle(): String {
    val fileName = filePath
        ?.let { File(it).name }
        ?.takeIf { it.isNotBlank() }

    val sourceTitle = title
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .trim()

    val cleanedTitle = sourceTitle.stripContainerSuffixes()

    return cleanedTitle
        .takeIf { it.isNotBlank() && !it.isTechnicalMetadataOnly() }
        ?: fileName?.stripContainerSuffixes()?.takeIf { it.isNotBlank() }
        ?: sourceTitle.takeIf { it.isNotBlank() }
        ?: "Unknown video"
}

private fun String.stripContainerSuffixes(): String {
    var current = trim()
    while (true) {
        val next = current.replace(containerSuffixPattern, "").trim()
        if (next == current) return current
        current = next
    }
}

private fun String.isTechnicalMetadataOnly(): Boolean = metadataOnlyPattern.matches(trim())

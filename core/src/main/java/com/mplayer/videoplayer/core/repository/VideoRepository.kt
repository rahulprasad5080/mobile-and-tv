package com.mplayer.videoplayer.core.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
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

        val volumes = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                val detected = MediaStore.getExternalVolumeNames(context)
                if (detected.contains("external_primary")) {
                    detected.filter { it != "external" }
                } else {
                    listOf("external")
                }
            } catch (e: Exception) {
                listOf("external")
            }
        } else {
            listOf("external")
        }

        val seenPaths = mutableSetOf<String>()

        for (volume in volumes) {
            try {
                val contentUri = MediaStore.Video.Media.getContentUri(volume)
                context.contentResolver.query(
                    contentUri,
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

                        val uniqueKey = filePath ?: id.toString()
                        if (seenPaths.contains(uniqueKey)) continue
                        seenPaths.add(uniqueKey)

                        val folderName = filePath
                            ?.let { File(it).parentFile?.name }
                            ?.takeIf { it.isNotBlank() }
                            ?: "Internal memory"

                        val videoUri = ContentUris.withAppendedId(
                            contentUri,
                            id
                        )

                        localVideos.add(
                            VideoMediaItem(
                                id = id.toString(),
                                title = name,
                                description = "Local Video - ${formatDuration(duration)}",
                                thumbnailUri = videoUri,
                                uri = videoUri,
                                filePath = filePath,
                                folderName = folderName,
                                mimeType = mimeType,
                                duration = duration
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Perform direct filesystem scans to find any missed videos (both internal and USB)
        try {
            val storageDir = File("/storage")
            if (storageDir.exists() && storageDir.isDirectory) {
                val volumesDirs = storageDir.listFiles()
                if (volumesDirs != null) {
                    for (volume in volumesDirs) {
                        if (!volume.isDirectory || volume.name == "self") continue
                        if (volume.name == "emulated") {
                            // Primary internal storage is under /storage/emulated/0
                            val primaryInternal = File(volume, "0")
                            if (primaryInternal.exists() && primaryInternal.isDirectory) {
                                // Scan standard folders to keep it very fast and avoid deep system folders
                                val standardFolders = listOf("Movies", "Download", "DCIM", "Pictures", "Video", "Videos", "Documents")
                                for (folderName in standardFolders) {
                                    val folder = File(primaryInternal, folderName)
                                    if (folder.exists() && folder.isDirectory) {
                                        scanDirectoryForVideos(folder, localVideos, seenPaths, depth = 1)
                                    }
                                }
                                // Also scan files directly in the root of internal storage
                                val rootFiles = primaryInternal.listFiles()
                                if (rootFiles != null) {
                                    for (file in rootFiles) {
                                        if (file.isFile) {
                                            addLocalVideoFile(file, localVideos, seenPaths)
                                        }
                                    }
                                }
                            }
                        } else {
                            // Removable storage volume (USB pendrive / SD card)
                            // We can scan the entire USB drive because they are usually dedicated to media
                            scanDirectoryForVideos(volume, localVideos, seenPaths, depth = 1)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        emit(localVideos)
    }.flowOn(Dispatchers.IO)

    private fun addLocalVideoFile(file: File, localVideos: MutableList<VideoMediaItem>, seenPaths: MutableSet<String>) {
        val path = file.absolutePath
        if (seenPaths.contains(path)) return
        val ext = file.extension.lowercase()
        val isVideo = ext in listOf("mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "ts")
        if (isVideo) {
            seenPaths.add(path)
            val contentUri = Uri.fromFile(file)
            localVideos.add(
                VideoMediaItem(
                    id = file.hashCode().toString(),
                    title = file.name,
                    description = "Local Video",
                    thumbnailUri = contentUri,
                    uri = contentUri,
                    filePath = path,
                    folderName = file.parentFile?.name ?: "Internal memory",
                    mimeType = "video/$ext",
                    duration = 0L
                )
            )
        }
    }

    private fun scanDirectoryForVideos(dir: File, localVideos: MutableList<VideoMediaItem>, seenPaths: MutableSet<String>, depth: Int) {
        if (depth > 3 || !dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                val name = file.name.lowercase()
                if (!file.name.startsWith(".") && name != "android" && name != "lost.dir") {
                    scanDirectoryForVideos(file, localVideos, seenPaths, depth + 1)
                }
            } else {
                addLocalVideoFile(file, localVideos, seenPaths)
            }
        }
    }

    private fun formatDuration(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0)
        val totalSeconds = safeMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }
}

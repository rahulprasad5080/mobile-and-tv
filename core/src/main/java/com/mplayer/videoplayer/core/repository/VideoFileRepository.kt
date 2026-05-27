package com.mplayer.videoplayer.core.repository

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.media.MediaScannerConnection
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.mplayer.videoplayer.core.model.VideoMediaItem

class VideoFileRepository {

    suspend fun deleteVideo(context: Context, uri: Uri): IntentSender? {
        return withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uri.scheme == "content") {
                return@withContext MediaStore.createDeleteRequest(
                    context.contentResolver,
                    listOf(uri)
                ).intentSender
            }

            val path = getPathFromUri(context, uri)
            try {
                val deletedRows = if (uri.scheme == "content") {
                    context.contentResolver.delete(uri, null, null)
                } else {
                    0
                }

                val file = path?.let { File(it) }
                val deletedFile = when {
                    file != null && file.exists() -> file.delete() || !file.exists()
                    deletedRows > 0 -> true
                    file != null -> !file.exists()
                    else -> false
                }

                if (!deletedFile) {
                    throw IllegalStateException("Unable to delete video from storage")
                }

                path?.takeIf { !File(it).exists() }?.let {
                    MediaScannerConnection.scanFile(context, arrayOf(it), null, null)
                }
                null
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    e.userAction.actionIntent.intentSender
                } else {
                    throw e
                }
            }
        }
    }

    suspend fun deleteVideos(context: Context, uris: List<Uri>): IntentSender? {
        return withContext(Dispatchers.IO) {
            if (uris.isEmpty()) return@withContext null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uris.first().scheme == "content") {
                return@withContext MediaStore.createDeleteRequest(
                    context.contentResolver,
                    uris
                ).intentSender
            }

            var pendingIntentSender: IntentSender? = null
            for (uri in uris) {
                val path = getPathFromUri(context, uri)
                try {
                    val deletedRows = if (uri.scheme == "content") {
                        context.contentResolver.delete(uri, null, null)
                    } else {
                        0
                    }

                    val file = path?.let { File(it) }
                    if (file != null && file.exists()) {
                        file.delete()
                    }

                    path?.takeIf { !File(it).exists() }?.let {
                        MediaScannerConnection.scanFile(context, arrayOf(it), null, null)
                    }
                } catch (e: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                        pendingIntentSender = e.userAction.actionIntent.intentSender
                        break
                    }
                }
            }
            pendingIntentSender
        }
    }

    suspend fun renameVideo(context: Context, uri: Uri, newName: String): IntentSender? {
        return withContext(Dispatchers.IO) {
            val oldPath = getPathFromUri(context, uri)
            val oldFileName = getFileNameFromUri(context, uri)
            var finalName = newName

            val extension = oldFileName?.substringAfterLast('.', "")
                ?.takeIf { it.isNotEmpty() && oldFileName.contains('.') }
                ?: oldPath?.substringAfterLast('.', "")
                ?.takeIf { it.isNotEmpty() && oldPath.contains('.') }
                ?: ""

            if (extension.isNotBlank() && !newName.endsWith(".$extension", ignoreCase = true)) {
                finalName = "$newName.$extension"
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, finalName)
            }
            try {
                val updatedRows = if (uri.scheme == "content") {
                    context.contentResolver.update(uri, contentValues, null, null)
                } else {
                    0
                }
                val renamedFile = if (updatedRows <= 0) {
                    renameFilePath(oldPath, finalName)
                } else {
                    true
                }

                if (!renamedFile) {
                    throw IllegalStateException("Unable to rename video in storage")
                }

                val newPath = getPathFromUri(context, uri) ?: oldPath?.let { path ->
                    File(path).parentFile?.let { parent -> File(parent, finalName).absolutePath }
                }
                val pathsToScan = mutableListOf<String>()
                oldPath?.let { pathsToScan.add(it) }
                newPath?.let { pathsToScan.add(it) }
                if (pathsToScan.isNotEmpty()) {
                    MediaScannerConnection.scanFile(context, pathsToScan.toTypedArray(), null, null)
                }
                null
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uri.scheme == "content") {
                    MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    e.userAction.actionIntent.intentSender
                } else {
                    throw e
                }
            }
        }
    }

    suspend fun renameFolder(
        context: Context,
        videos: List<VideoMediaItem>,
        newFolderName: String,
        requestPermission: Boolean = true
    ): IntentSender? {
        return withContext(Dispatchers.IO) {
            if (videos.isEmpty()) return@withContext null
            val uris = videos.map { it.uri }

            if (requestPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uris.first().scheme == "content") {
                return@withContext MediaStore.createWriteRequest(
                    context.contentResolver,
                    uris
                ).intentSender
            }

            var pendingIntentSender: IntentSender? = null
            
            // 1. Try to rename the directory itself using direct File API.
            // On legacy external storage (like Android 10 with requestLegacyExternalStorage="true" or API <= 28)
            // this will succeed and rename all files (including subtitles, .nomedia, etc.) instantly.
            var directoryRenamed = false
            val firstVideo = videos.firstOrNull()
            val firstOldPath = firstVideo?.filePath ?: firstVideo?.let { getPathFromUri(context, it.uri) }
            if (firstOldPath != null) {
                val file = File(firstOldPath)
                val parentFile = file.parentFile
                if (parentFile != null && parentFile.exists()) {
                    val grandparentFile = parentFile.parentFile
                    if (grandparentFile != null && grandparentFile.exists()) {
                        val targetFolder = File(grandparentFile, newFolderName)
                        try {
                            if (parentFile.renameTo(targetFolder)) {
                                directoryRenamed = true
                                val oldPaths = videos.mapNotNull { it.filePath ?: getPathFromUri(context, it.uri) }
                                val newPaths = videos.mapNotNull { video ->
                                    val path = video.filePath ?: getPathFromUri(context, video.uri)
                                    path?.let { p ->
                                        val filename = File(p).name
                                        File(targetFolder, filename).absolutePath
                                    }
                                }
                                val pathsToScan = (oldPaths + newPaths).toTypedArray()
                                MediaScannerConnection.scanFile(context, pathsToScan, null, null)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            // 2. If directory renaming did not succeed (e.g. Scoped Storage on Android 11+), fall back to moving files individually
            if (!directoryRenamed) {
                for (video in videos) {
                    val uri = video.uri
                    val oldPath = getPathFromUri(context, uri) ?: video.filePath ?: continue

                    val file = File(oldPath)
                    val parentFile = file.parentFile ?: continue
                    val grandparentFile = parentFile.parentFile ?: continue

                    val relativePath = parentFile.absolutePath.replace('\\', '/').trimEnd('/')
                    val storageMarker = "/storage/emulated/0/"
                    val newRelativePath = if (relativePath.startsWith(storageMarker)) {
                        val rel = relativePath.removePrefix(storageMarker)
                        val parentRel = rel.substringBeforeLast('/', "")
                        if (parentRel.isNotEmpty()) "$parentRel/$newFolderName/" else "$newFolderName/"
                    } else {
                        "$newFolderName/"
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content") {
                        val values = ContentValues().apply {
                            put(MediaStore.Video.Media.RELATIVE_PATH, newRelativePath)
                        }
                        try {
                            context.contentResolver.update(uri, values, null, null)
                            val newPath = getPathFromUri(context, uri)
                            val pathsToScan = mutableListOf<String>()
                            oldPath.let { pathsToScan.add(it) }
                            newPath?.let { pathsToScan.add(it) }
                            if (pathsToScan.isNotEmpty()) {
                                MediaScannerConnection.scanFile(context, pathsToScan.toTypedArray(), null, null)
                            }
                        } catch (e: SecurityException) {
                            if (e is RecoverableSecurityException) {
                                pendingIntentSender = e.userAction.actionIntent.intentSender
                                break
                            }
                        }
                    } else {
                        try {
                            val targetFolder = File(grandparentFile, newFolderName)
                            if (!targetFolder.exists()) {
                                targetFolder.mkdirs()
                            }
                            val targetFile = File(targetFolder, file.name)
                            if (file.renameTo(targetFile)) {
                                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath, targetFile.absolutePath), null, null)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            pendingIntentSender
        }
    }

    private fun renameFilePath(oldPath: String?, finalName: String): Boolean {
        val source = oldPath?.let { File(it) } ?: return false
        val parent = source.parentFile ?: return false
        if (!source.exists()) return false
        if (source.name == finalName) return true

        val target = uniqueTargetFile(parent, finalName)
        return source.renameTo(target)
    }

    suspend fun copyVideoToFolder(context: Context, sourceUri: Uri, targetFolder: File): Boolean {
        return withContext(Dispatchers.IO) {
            // 1. First, try direct file system copy.
            // On Android 9 and below, or Android 10 with requestLegacyExternalStorage="true",
            // or writeable directories on Android 11+, this will succeed and support custom folder paths (e.g. WhatsApp Video).
            try {
                val sourceName = getFileNameFromUri(context, sourceUri) ?: "video_${System.currentTimeMillis()}.mp4"
                if (!targetFolder.exists()) {
                    targetFolder.mkdirs()
                }
                val targetFile = uniqueTargetFile(targetFolder, sourceName)
                
                val inputStream = context.contentResolver.openInputStream(sourceUri)
                if (inputStream != null) {
                    inputStream.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), null, null)
                    return@withContext true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Fallback to MediaStore insertion (required for Scoped Storage restricted folders on Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return@withContext copyVideoToMediaStore(context, sourceUri, targetFolder)
            }
            false
        }
    }

    private fun copyVideoToMediaStore(context: Context, sourceUri: Uri, targetFolder: File): Boolean {
        return try {
            val resolver = context.contentResolver
            val sourceName = getFileNameFromUri(context, sourceUri) ?: "video_${System.currentTimeMillis()}.mp4"
            val relativePath = relativeMediaPath(targetFolder)
            val targetName = uniqueMediaStoreName(context, relativePath, sourceName)
            val mimeType = resolver.getType(sourceUri) ?: "video/mp4"

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, targetName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val targetUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            try {
                val inputStream = resolver.openInputStream(sourceUri) ?: error("Unable to open source video")
                inputStream.use { input ->
                    val outputStream = resolver.openOutputStream(targetUri) ?: error("Unable to open target video")
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }

                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(targetUri, values, null, null)
                true
            } catch (e: Exception) {
                resolver.delete(targetUri, null, null)
                e.printStackTrace()
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getPathFromUri(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        val projection = arrayOf(MediaStore.Video.Media.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                if (index != -1) return cursor.getString(index)
            }
        }
        return null
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment
    }

    private fun uniqueTargetFile(targetFolder: File, sourceName: String): File {
        val dotIndex = sourceName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) sourceName.substring(0, dotIndex) else sourceName
        val extension = if (dotIndex > 0) sourceName.substring(dotIndex) else ""
        var candidate = File(targetFolder, sourceName)
        var index = 1

        while (candidate.exists()) {
            candidate = File(targetFolder, "$baseName ($index)$extension")
            index++
        }

        return candidate
    }

    private fun relativeMediaPath(targetFolder: File): String {
        val normalizedPath = targetFolder.absolutePath.replace('\\', '/').trimEnd('/')
        val storageMarker = "/storage/emulated/0/"
        var relativePath = if (normalizedPath.startsWith(storageMarker)) {
            normalizedPath.removePrefix(storageMarker)
        } else {
            targetFolder.name
        }
        relativePath = relativePath.trim('/')
        if (relativePath.isBlank()) {
            return "Movies/"
        }

        val lower = relativePath.lowercase() + "/"
        val isStandard = lower.startsWith("dcim/") ||
                         lower.startsWith("movies/") ||
                         lower.startsWith("pictures/") ||
                         lower.startsWith("download/")

        return if (isStandard) {
            "$relativePath/"
        } else {
            "Movies/$relativePath/"
        }
    }

    private fun uniqueMediaStoreName(context: Context, relativePath: String, sourceName: String): String {
        val existingNames = mutableSetOf<String>()
        val projection = arrayOf(MediaStore.Video.Media.DISPLAY_NAME)
        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(relativePath)

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
            while (cursor.moveToNext() && nameIndex != -1) {
                cursor.getString(nameIndex)?.let { existingNames += it }
            }
        }

        if (sourceName !in existingNames) return sourceName

        val dotIndex = sourceName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) sourceName.substring(0, dotIndex) else sourceName
        val extension = if (dotIndex > 0) sourceName.substring(dotIndex) else ""
        var index = 1
        var candidate: String

        do {
            candidate = "$baseName ($index)$extension"
            index++
        } while (candidate in existingNames)

        return candidate
    }
}

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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class VideoFileRepository {

    suspend fun deleteVideo(context: Context, uri: Uri): IntentSender? {
        return withContext(Dispatchers.IO) {
            val path = getPathFromUri(context, uri)
            try {
                context.contentResolver.delete(uri, null, null)
                path?.let {
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

    suspend fun renameVideo(context: Context, uri: Uri, newName: String): IntentSender? {
        return withContext(Dispatchers.IO) {
            val oldPath = getPathFromUri(context, uri)
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, newName)
            }
            try {
                context.contentResolver.update(uri, contentValues, null, null)
                val newPath = getPathFromUri(context, uri)
                val pathsToScan = mutableListOf<String>()
                oldPath?.let { pathsToScan.add(it) }
                newPath?.let { pathsToScan.add(it) }
                if (pathsToScan.isNotEmpty()) {
                    MediaScannerConnection.scanFile(context, pathsToScan.toTypedArray(), null, null)
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

    suspend fun copyVideoToFolder(context: Context, sourceUri: Uri, targetFolder: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val sourceName = getFileNameFromUri(context, sourceUri) ?: "video_${System.currentTimeMillis()}.mp4"
                val targetFile = File(targetFolder, sourceName)
                
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), null, null)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
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
}

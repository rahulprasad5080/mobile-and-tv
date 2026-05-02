package com.antigravity.videoplayer.core.repository

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class VideoFileRepository {

    suspend fun deleteVideo(context: Context, uri: Uri): IntentSender? {
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.delete(uri, null, null)
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
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, newName)
            }
            try {
                context.contentResolver.update(uri, contentValues, null, null)
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

    suspend fun copyVideo(context: Context, sourceUri: Uri, targetFileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return@withContext false
                
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, targetFileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }

                val targetUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext false

                context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                    inputStream.copyTo(outputStream)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    context.contentResolver.update(targetUri, contentValues, null, null)
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}

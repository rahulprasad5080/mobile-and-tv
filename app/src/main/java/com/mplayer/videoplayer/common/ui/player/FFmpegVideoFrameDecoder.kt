package com.mplayer.videoplayer.common.ui.player

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.fetch.SourceResult
import coil.request.Options
import wseemann.media.FFmpegMediaMetadataRetriever
import java.io.File

class FFmpegVideoFrameDecoder(
    private val source: SourceResult,
    private val options: Options
) : Decoder {

    override suspend fun decode(): DecodeResult? {
        val retriever = FFmpegMediaMetadataRetriever()
        try {
            val file = source.source.fileOrNull()
            if (file != null) {
                retriever.setDataSource(file.toFile().absolutePath)
            } else {
                return null
            }

            // Extract frame in software ARGB_8888 config to prevent YUV color conversion green bugs
            val bitmap = retriever.frameAtTime ?: return null
            
            return DecodeResult(
                drawable = BitmapDrawable(options.context.resources, bitmap),
                isSampled = false
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            retriever.release()
        }
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
            val file = result.source.fileOrNull() ?: return null
            val ext = file.name.lowercase()
            val isVideo = ext.endsWith(".mp4") || ext.endsWith(".mkv") || ext.endsWith(".avi") || 
                          ext.endsWith(".mov") || ext.endsWith(".webm") || ext.endsWith(".flv") || 
                          ext.endsWith(".3gp") || ext.endsWith(".ts")
            
            if (!isVideo) return null
            return FFmpegVideoFrameDecoder(result, options)
        }
    }
}

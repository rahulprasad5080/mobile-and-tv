package com.mplayer.videoplayer.mobile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.mplayer.videoplayer.common.ui.player.FFmpegVideoFrameDecoder

class VideoPlayerApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(FFmpegVideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}
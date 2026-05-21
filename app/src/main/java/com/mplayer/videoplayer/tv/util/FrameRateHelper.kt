package com.mplayer.videoplayer.tv.util

import android.app.Activity
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.view.Display
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.media3.common.Format
import java.util.ArrayList
import java.util.Collections

object FrameRateHelper {

    private var frameRateSwitchThread: Thread? = null

    fun getFrameRate(context: Context, videoUri: Uri): Float {
        val mediaExtractor = MediaExtractor()
        val timestamps = ArrayList<Long>()
        var frameRate = Format.NO_VALUE.toFloat()
        val ignoreSamples = 30
        try {
            mediaExtractor.setDataSource(context, videoUri, null)
            for (i in 0 until mediaExtractor.trackCount) {
                val format = mediaExtractor.getTrackFormat(i)
                val mimeType = format.getString(MediaFormat.KEY_MIME)
                if (mimeType != null && mimeType.startsWith("video/")) {
                    mediaExtractor.selectTrack(i)
                    while (timestamps.size < 350 + ignoreSamples) {
                        val timestamp = mediaExtractor.sampleTime
                        if (timestamp < 0) {
                            break
                        }
                        timestamps.add(timestamp)
                        mediaExtractor.advance()
                    }
                    break
                }
            }
            timestamps.sort()
            var totalFrameDuration: Long = 0
            for (i in 1 until (timestamps.size - ignoreSamples)) {
                totalFrameDuration += (timestamps[i] - timestamps[i - 1])
            }
            if (timestamps.size > ignoreSamples + 1) {
                val averageFrameDuration = totalFrameDuration.toFloat() / (timestamps.size - ignoreSamples - 1)
                frameRate = 1_000_000f / averageFrameDuration
                if (frameRate > 23.95f && frameRate < 23.988f) {
                    frameRate = 24000f / 1001f
                } else if (frameRate > 23.988f && frameRate < 24.1f) {
                    frameRate = 24f
                } else if (frameRate > 24.9f && frameRate < 25.1f) {
                    frameRate = 25f
                } else if (frameRate > 29.95f && frameRate < 29.985f) {
                    frameRate = 30000f / 1001f
                } else if (frameRate > 29.985f && frameRate < 30.1f) {
                    frameRate = 30f
                } else if (frameRate > 49.9f && frameRate < 50.1f) {
                    frameRate = 50f
                } else if (frameRate > 59.9f && frameRate < 59.97f) {
                    frameRate = 60000f / 1001f
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaExtractor.release()
        }
        return frameRate
    }

    private fun normRate(rate: Float): Int {
        return (rate * 100f).toInt()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun handleFrameRate(activity: Activity, frameRate: Float, onComplete: () -> Unit) {
        activity.runOnUiThread {
            var switchingModes = false

            if (frameRate > 0) {
                val display = activity.window?.decorView?.display
                if (display != null) {
                    val supportedModes = display.supportedModes
                    val activeMode = display.mode

                    if (supportedModes != null && supportedModes.size > 1) {
                        val modesHigh = ArrayList<Display.Mode>()
                        var modeTop = activeMode
                        var modesResolutionCount = 0

                        for (mode in supportedModes) {
                            if (mode.physicalWidth == activeMode.physicalWidth &&
                                    mode.physicalHeight == activeMode.physicalHeight) {
                                modesResolutionCount++

                                if (normRate(mode.refreshRate) >= normRate(frameRate)) {
                                    modesHigh.add(mode)
                                }

                                if (normRate(mode.refreshRate) > normRate(modeTop.refreshRate)) {
                                    modeTop = mode
                                }
                            }
                        }

                        if (modesResolutionCount > 1) {
                            var modeBest: Display.Mode? = null

                            for (mode in modesHigh) {
                                if (normRate(mode.refreshRate) % normRate(frameRate) <= 0.0001f) {
                                    if (modeBest == null || normRate(mode.refreshRate) > normRate(modeBest.refreshRate)) {
                                        modeBest = mode
                                    }
                                }
                            }

                            val window = activity.window
                            val layoutParams = window?.attributes

                            if (modeBest == null) {
                                modeBest = modeTop
                            }

                            if (layoutParams != null && modeBest != null) {
                                switchingModes = modeBest.modeId != activeMode.modeId
                                if (switchingModes) {
                                    layoutParams.preferredDisplayModeId = modeBest.modeId
                                    window.attributes = layoutParams
                                }
                            }
                        }
                    }
                }
            }

            onComplete()
        }
    }

    fun switchFrameRate(activity: Activity, videoUri: Uri, onComplete: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 23) {
            frameRateSwitchThread?.interrupt()
            frameRateSwitchThread = Thread {
                val frameRate = getFrameRate(activity, videoUri)
                handleFrameRate(activity, frameRate, onComplete)
            }.apply {
                start()
            }
        } else {
            onComplete()
        }
    }

    fun abort() {
        frameRateSwitchThread?.interrupt()
        frameRateSwitchThread = null
    }
}

package com.mplayer.videoplayer.common.ui.player

import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import androidx.media3.ui.PlayerView
import com.mplayer.videoplayer.R
import java.util.Locale

fun createCompatiblePlayerView(context: Context): PlayerView {
    val layoutRes = if (isRunningOnEmulator()) {
        R.layout.player_view_texture
    } else {
        R.layout.player_view_surface
    }

    return LayoutInflater.from(context).inflate(layoutRes, null) as PlayerView
}

fun isRunningOnEmulator(): Boolean {
    val fingerprint = Build.FINGERPRINT.lowercase(Locale.US)
    val model = Build.MODEL.lowercase(Locale.US)
    val manufacturer = Build.MANUFACTURER.lowercase(Locale.US)
    val brand = Build.BRAND.lowercase(Locale.US)
    val device = Build.DEVICE.lowercase(Locale.US)
    val product = Build.PRODUCT.lowercase(Locale.US)

    return fingerprint.startsWith("generic") ||
        fingerprint.startsWith("unknown") ||
        model.contains("emulator") ||
        model.contains("android sdk built for") ||
        manufacturer.contains("genymotion") ||
        brand.startsWith("generic") && device.startsWith("generic") ||
        product.contains("sdk") ||
        product.contains("emulator")
}

package com.mplayer.videoplayer.common.ui.player

import android.graphics.Color
import android.graphics.Typeface
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView

enum class SubtitleSize {
    Small,
    Medium,
    Large
}

fun PlayerView.applyNetflixSubtitleStyle(
    size: SubtitleSize,
    isTv: Boolean
) {
    subtitleView?.apply {
        setApplyEmbeddedStyles(false)
        setApplyEmbeddedFontSizes(false)
        setBottomPaddingFraction(if (isTv) 0.12f else 0.08f)
        setFractionalTextSize(size.asFraction(isTv))
        setStyle(
            CaptionStyleCompat(
                Color.WHITE,
                Color.argb(190, 0, 0, 0),
                Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                Color.argb(220, 0, 0, 0),
                Typeface.DEFAULT_BOLD
            )
        )
    }
}

private fun SubtitleSize.asFraction(isTv: Boolean): Float {
    return when (this) {
        SubtitleSize.Small -> if (isTv) 0.055f else 0.042f
        SubtitleSize.Medium -> if (isTv) 0.070f else 0.052f
        SubtitleSize.Large -> if (isTv) 0.085f else 0.062f
    }
}

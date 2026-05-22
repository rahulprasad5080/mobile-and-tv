package com.mplayer.videoplayer.tv.ui.screen.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPlayerRemoteKeyHandlingTest {

    @Test
    fun dpadLeftRightAreReleasedToFocusedControlsWhenControlsAreVisible() {
        assertFalse(shouldTvDpadSeek(showControls = true, rootHasFocus = false))
    }

    @Test
    fun dpadLeftRightSeekWhenControlsAreHidden() {
        assertTrue(shouldTvDpadSeek(showControls = false, rootHasFocus = false))
    }

    @Test
    fun dpadLeftRightSeekWhenVideoSurfaceHasFocus() {
        assertTrue(shouldTvDpadSeek(showControls = true, rootHasFocus = true))
    }
}

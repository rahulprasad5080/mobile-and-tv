package com.mplayer.videoplayer.tv.ui.screen.player

import android.view.KeyEvent
import org.junit.Assert.assertEquals
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

    @Test
    fun dedicatedRemoteRewindAndFastForwardAlwaysSeek() {
        assertEquals(
            -1,
            tvRemoteSeekDirection(
                keyCode = KeyEvent.KEYCODE_MEDIA_REWIND,
                showControls = true,
                rootHasFocus = false
            )
        )
        assertEquals(
            1,
            tvRemoteSeekDirection(
                keyCode = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                showControls = true,
                rootHasFocus = false
            )
        )
    }

    @Test
    fun dpadLeftRightStillNavigateFocusedControls() {
        assertEquals(
            0,
            tvRemoteSeekDirection(
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
                showControls = true,
                rootHasFocus = false
            )
        )
    }
}

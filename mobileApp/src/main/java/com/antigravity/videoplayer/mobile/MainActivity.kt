package com.antigravity.videoplayer.mobile

import android.app.PictureInPictureParams
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.antigravity.videoplayer.core.model.VideoMediaItem
import com.antigravity.videoplayer.mobile.ui.screen.player.VideoPlayerScreen
import com.antigravity.videoplayer.mobile.viewmodel.MobilePlayerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MobilePlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sampleItem = VideoMediaItem(
            id = "sample_1",
            title = "Big Buck Bunny",
            uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
        )
        viewModel.playMedia(sampleItem)

        setContent {
            VideoPlayerScreen(
                viewModel = viewModel,
                onBackPressed = { finish() }
            )
        }
    }

    override fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_1_8) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }
}

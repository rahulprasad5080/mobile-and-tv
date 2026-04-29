package com.antigravity.videoplayer.tv

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.antigravity.videoplayer.core.model.VideoMediaItem
import com.antigravity.videoplayer.tv.ui.screen.player.TvPlayerScreen
import com.antigravity.videoplayer.tv.viewmodel.TvPlayerViewModel

class TvMainActivity : ComponentActivity() {

    private val viewModel: TvPlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sampleItem = VideoMediaItem(
            id = "sample_1",
            title = "Big Buck Bunny",
            uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
        )
        viewModel.playMedia(sampleItem)

        setContent {
            TvPlayerScreen(viewModel = viewModel)
        }
    }
}

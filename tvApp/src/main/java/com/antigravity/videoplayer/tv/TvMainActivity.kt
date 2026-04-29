package com.antigravity.videoplayer.tv

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.antigravity.videoplayer.tv.ui.screen.browse.TvBrowseScreen
import com.antigravity.videoplayer.tv.ui.screen.player.TvPlayerScreen
import com.antigravity.videoplayer.tv.viewmodel.TvBrowseViewModel
import com.antigravity.videoplayer.tv.viewmodel.TvPlayerViewModel

class TvMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvAppNavigation()
        }
    }

    @Composable
    fun TvAppNavigation() {
        val navController = rememberNavController()
        val playerViewModel: TvPlayerViewModel = viewModel()
        val browseViewModel: TvBrowseViewModel = viewModel()

        NavHost(navController = navController, startDestination = "browse") {
            composable("browse") {
                TvBrowseScreen(
                    viewModel = browseViewModel,
                    onVideoClick = { video ->
                        playerViewModel.playMedia(video)
                        navController.navigate("player")
                    }
                )
            }
            composable("player") {
                TvPlayerScreen(
                    viewModel = playerViewModel,
                    onBackPressed = { navController.popBackStack() }
                )
            }
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

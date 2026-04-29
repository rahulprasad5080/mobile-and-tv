package com.antigravity.videoplayer.mobile

import android.app.PictureInPictureParams
import android.net.Uri
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
import com.antigravity.videoplayer.core.model.VideoMediaItem
import com.antigravity.videoplayer.mobile.ui.screen.home.HomeScreen
import com.antigravity.videoplayer.mobile.ui.screen.player.VideoPlayerScreen
import com.antigravity.videoplayer.mobile.viewmodel.HomeViewModel
import com.antigravity.videoplayer.mobile.viewmodel.MobilePlayerViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            AppNavigation()
        }
    }

    @Composable
    fun AppNavigation() {
        val navController = rememberNavController()
        val playerViewModel: MobilePlayerViewModel = viewModel()
        val homeViewModel: HomeViewModel = viewModel()

        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    viewModel = homeViewModel,
                    onVideoClick = { video ->
                        playerViewModel.playMedia(video)
                        navController.navigate("player")
                    }
                )
            }
            composable("player") {
                VideoPlayerScreen(
                    viewModel = playerViewModel,
                    onBackPressed = { navController.popBackStack() }
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }
}

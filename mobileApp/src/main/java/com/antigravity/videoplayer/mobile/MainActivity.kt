package com.antigravity.videoplayer.mobile

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.antigravity.videoplayer.mobile.ui.screen.home.HomeScreen
import com.antigravity.videoplayer.mobile.ui.screen.player.VideoPlayerScreen
import com.antigravity.videoplayer.mobile.viewmodel.HomeViewModel
import com.antigravity.videoplayer.mobile.viewmodel.MobilePlayerViewModel

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, refresh videos
            // This will be handled by the ViewModel when it initializes or we can trigger it
        } else {
            Toast.makeText(this, "Permission denied to read videos", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkAndRequestPermissions()

        setContent {
            AppNavigation()
        }
    }

    private fun checkAndRequestPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    @Composable
    fun AppNavigation() {
        val navController = rememberNavController()
        val playerViewModel: MobilePlayerViewModel = viewModel()
        val homeViewModel: HomeViewModel = viewModel()

        // Refresh videos when permission is granted or on start
        LaunchedEffect(Unit) {
            homeViewModel.loadVideos()
        }

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

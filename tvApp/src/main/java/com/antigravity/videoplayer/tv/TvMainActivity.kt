package com.antigravity.videoplayer.tv

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
import com.antigravity.videoplayer.tv.ui.screen.browse.TvBrowseScreen
import com.antigravity.videoplayer.tv.ui.screen.player.TvPlayerScreen
import com.antigravity.videoplayer.tv.viewmodel.TvBrowseViewModel
import com.antigravity.videoplayer.tv.viewmodel.TvPlayerViewModel

class TvMainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Permission denied to read videos", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkAndRequestPermissions()
        
        setContent {
            TvAppNavigation()
        }
    }

    private fun checkAndRequestPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(permission)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }
}

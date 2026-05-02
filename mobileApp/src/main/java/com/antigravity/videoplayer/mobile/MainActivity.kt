package com.antigravity.videoplayer.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.antigravity.videoplayer.core.model.VideoMediaItem
import com.antigravity.videoplayer.core.repository.PlaybackProgressRepository
import com.antigravity.videoplayer.mobile.ui.screen.home.FolderVideosScreen
import com.antigravity.videoplayer.mobile.ui.screen.home.HomeScreen
import com.antigravity.videoplayer.mobile.ui.screen.player.VideoPlayerScreen
import com.antigravity.videoplayer.mobile.viewmodel.HomeViewModel
import com.antigravity.videoplayer.mobile.viewmodel.MobilePlayerViewModel

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            homeViewModel.loadVideos()
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
                homeViewModel.loadVideos()
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
        val progressRepository = remember { PlaybackProgressRepository(this) }
        
        var selectedFolderName by remember { mutableStateOf("") }
        var selectedFolderVideos by remember { mutableStateOf<List<VideoMediaItem>>(emptyList()) }

        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    viewModel = homeViewModel,
                    onVideoClick = { video ->
                        homeViewModel.addToRecentlyPlayed(video)
                        val progress = progressRepository.getProgress(video.id)
                        playerViewModel.playMedia(video, progress)
                        navController.navigate("player")
                    },
                    onFolderClick = { name, videos ->
                        selectedFolderName = name
                        selectedFolderVideos = videos
                        navController.navigate("folder_videos")
                    }
                )
            }
            composable("folder_videos") {
                FolderVideosScreen(
                    folderName = selectedFolderName,
                    videos = selectedFolderVideos,
                    onVideoClick = { video ->
                        homeViewModel.addToRecentlyPlayed(video)
                        val progress = progressRepository.getProgress(video.id)
                        playerViewModel.playMedia(video, progress)
                        navController.navigate("player")
                    },
                    onBackPressed = { navController.popBackStack() }
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
}

package com.mplayer.videoplayer.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mplayer.videoplayer.core.repository.PlaybackProgressRepository
import com.mplayer.videoplayer.mobile.ui.screen.home.FolderVideosScreen
import com.mplayer.videoplayer.mobile.ui.screen.home.HomeScreen
import com.mplayer.videoplayer.mobile.ui.screen.player.VideoPlayerScreen
import com.mplayer.videoplayer.mobile.viewmodel.HomeViewModel
import com.mplayer.videoplayer.mobile.viewmodel.MobilePlayerViewModel
import com.mplayer.videoplayer.tv.ui.screen.browse.TvBrowseScreen
import com.mplayer.videoplayer.tv.ui.screen.player.TvPlayerScreen
import com.mplayer.videoplayer.tv.viewmodel.TvBrowseViewModel
import com.mplayer.videoplayer.tv.viewmodel.TvPlayerViewModel
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.WindowManager
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration




class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()
    private val playerViewModel: MobilePlayerViewModel by viewModels()
    private val tvBrowseViewModel: TvBrowseViewModel by viewModels()
    private val tvPlayerViewModel: TvPlayerViewModel by viewModels()
    private var hasStartedMobilePlayback = false
    private var hasStartedTvPlayback = false
    private var skipNextStartRefresh = false

    private val intentSenderLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            homeViewModel.loadVideos()
        }
        homeViewModel.clearPendingIntent()
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            if (isTelevisionDevice()) {
                tvBrowseViewModel.startMediaStoreObserver()
                tvBrowseViewModel.loadVideos()
            } else {
                homeViewModel.startMediaStoreObserver()
                homeViewModel.loadVideos()
            }
        } else {
            Toast.makeText(this, "Permission denied to read videos", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }


        if (!isTelevisionDevice()) {
            lifecycleScope.launch {
                homeViewModel.pendingIntent.collect { intentSender ->
                    intentSender?.let {
                        intentSenderLauncher.launch(IntentSenderRequest.Builder(it).build())
                    }
                }
            }
        }

        setContent {
            if (isTelevisionDevice()) {
                TvAppNavigation()
            } else {
                AppNavigation()
            }
        }

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        when {
            hasVideoPermission() -> {
                if (isTelevisionDevice()) {
                    tvBrowseViewModel.startMediaStoreObserver()
                    tvBrowseViewModel.loadVideos()
                } else {
                    homeViewModel.startMediaStoreObserver()
                    homeViewModel.loadVideos()
                }
                skipNextStartRefresh = true
            }
            else -> {
                requestPermissionLauncher.launch(videoPermission())
            }
        }
    }

    private fun videoPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun hasVideoPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, videoPermission()) == PackageManager.PERMISSION_GRANTED
    }

    private fun isTelevisionDevice(): Boolean {
        val uiModeType = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return uiModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    @Composable
    fun AppNavigation() {
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        LaunchedEffect(isLandscape) {
            val window = (context as? Activity)?.window ?: return@LaunchedEffect
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (isLandscape) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        val navController = rememberNavController()
        val progressRepository = remember { PlaybackProgressRepository(this) }
        val lastPlayedVideo by homeViewModel.lastPlayedVideo.collectAsState()
        
        var selectedFolderName by remember { mutableStateOf("") }

        LaunchedEffect(lastPlayedVideo?.id) {
            val video = lastPlayedVideo ?: return@LaunchedEffect
            val progress = progressRepository.getProgress(video.id)
            playerViewModel.preloadMedia(video, progress)
        }

        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    viewModel = homeViewModel,
                    onVideoClick = { video ->
                        homeViewModel.addToRecentlyPlayed(video)
                        val progress = progressRepository.getProgress(video.id)
                        hasStartedMobilePlayback = true
                        playerViewModel.playMedia(video, progress)
                        navController.navigate("player")
                    },
                    onFolderClick = { name, _ ->
                        selectedFolderName = name
                        navController.navigate("folder_videos")
                    }
                )
            }
            composable("folder_videos") {
                val groupedVideos by homeViewModel.groupedVideos.collectAsState()
                val folderVideos = groupedVideos[selectedFolderName] ?: emptyList()
                FolderVideosScreen(
                    folderName = selectedFolderName,
                    videos = folderVideos,
                    onVideoClick = { video ->
                        homeViewModel.addToRecentlyPlayed(video)
                        val progress = progressRepository.getProgress(video.id)
                        hasStartedMobilePlayback = true
                        playerViewModel.playMedia(video, progress)
                        navController.navigate("player")
                    },
                    onRename = { video, newName -> homeViewModel.renameVideo(video, newName) },
                    onDelete = { video -> homeViewModel.deleteVideo(video) },
                    onCopy = { video -> homeViewModel.copyVideoToClipboard(video) },
                    onPaste = { 
                        val path = folderVideos.firstOrNull()?.filePath?.let { java.io.File(it).parent }
                        homeViewModel.pasteVideo(selectedFolderName, path) 
                    },
                    copiedVideo = homeViewModel.copiedVideo.collectAsState().value,

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

    @Composable
    fun TvAppNavigation() {
        val navController = rememberNavController()
        val progressRepository = remember { PlaybackProgressRepository(this) }

        NavHost(navController = navController, startDestination = "tv_browse") {
            composable("tv_browse") {
                TvBrowseScreen(
                    viewModel = tvBrowseViewModel,
                    onVideoClick = { video ->
                        val progress = progressRepository.getProgress(video.id)
                        hasStartedTvPlayback = true
                        tvPlayerViewModel.playMedia(video, progress)
                        navController.navigate("tv_player")
                    }
                )
            }
            composable("tv_player") {
                TvPlayerScreen(
                    viewModel = tvPlayerViewModel,
                    onBackPressed = { navController.popBackStack() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!hasVideoPermission()) return
        if (skipNextStartRefresh) {
            skipNextStartRefresh = false
            return
        }

        if (isTelevisionDevice()) {
            tvBrowseViewModel.startMediaStoreObserver()
            tvBrowseViewModel.loadVideos()
        } else {
            homeViewModel.startMediaStoreObserver()
            homeViewModel.loadVideos()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isTelevisionDevice() && hasStartedTvPlayback && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
            return
        }

        if (hasStartedMobilePlayback && playerViewModel.isPlaying.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        }
    }

    override fun onStop() {
        super.onStop()
        if (isTelevisionDevice()) {
            if (hasStartedTvPlayback && (!isInPictureInPictureMode || isFinishing)) {
                tvPlayerViewModel.stopPlayback()
                hasStartedTvPlayback = false
            }
            return
        }

        // Stop playback if we are not in PiP mode, or if the activity is finishing (e.g. PiP closed)
        if (hasStartedMobilePlayback && (!isInPictureInPictureMode || isFinishing)) {
            playerViewModel.stopPlayback()
            hasStartedMobilePlayback = false
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isTelevisionDevice()) {
            if (hasStartedTvPlayback && !isInPictureInPictureMode) {
                tvPlayerViewModel.stopPlayback()
                hasStartedTvPlayback = false
            }
            return
        }

        if (!hasStartedMobilePlayback) return

        playerViewModel.setPipMode(isInPictureInPictureMode)
        
        // If we are exiting PiP mode (either returning to app or closing PiP)
        // we stop playback to ensure no ghost audio remains.
        if (!isInPictureInPictureMode) {
            playerViewModel.stopPlayback()
            hasStartedMobilePlayback = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isTelevisionDevice() && hasStartedTvPlayback) {
            tvPlayerViewModel.stopPlayback()
            hasStartedTvPlayback = false
        } else if (hasStartedMobilePlayback) {
            playerViewModel.stopPlayback()
            hasStartedMobilePlayback = false
        }
    }
}

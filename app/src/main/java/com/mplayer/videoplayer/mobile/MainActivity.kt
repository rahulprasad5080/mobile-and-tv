package com.mplayer.videoplayer.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Rational
import android.widget.Toast
import android.content.Context
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.mplayer.videoplayer.core.model.VideoMediaItem
import com.mplayer.videoplayer.mobile.ui.theme.MPlayerTheme




class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()
    private val playerViewModel: MobilePlayerViewModel by viewModels()
    private val tvBrowseViewModel: TvBrowseViewModel by viewModels()
    private val tvPlayerViewModel: TvPlayerViewModel by viewModels()
    private var hasStartedMobilePlayback = false
    private var hasStartedTvPlayback = false
    private var skipNextStartRefresh = false
    private var externalVideoToOpen by mutableStateOf<VideoMediaItem?>(null)

    private val intentSenderLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val success = result.resultCode == RESULT_OK
        homeViewModel.handleIntentSenderResult(success)
        tvBrowseViewModel.handleIntentSenderResult(success)
        homeViewModel.clearPendingIntent()
        tvBrowseViewModel.clearPendingIntent()
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (videoPermissions().all { results[it] == true || ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
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

        externalVideoToOpen = intent.toExternalVideoItem()

        lifecycleScope.launch {
            homeViewModel.pendingIntent.collect { intentSender ->
                intentSender?.let {
                    intentSenderLauncher.launch(IntentSenderRequest.Builder(it).build())
                }
            }
        }

        lifecycleScope.launch {
            tvBrowseViewModel.pendingIntent.collect { intentSender ->
                intentSender?.let {
                    intentSenderLauncher.launch(IntentSenderRequest.Builder(it).build())
                }
            }
        }

        setContent {
            if (isTelevisionDevice()) {
                TvAppNavigation()
            } else {
                MPlayerTheme {
                    AppNavigation()
                }
            }
        }

        checkAndRequestPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        externalVideoToOpen = intent.toExternalVideoItem()
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
                requestPermissionLauncher.launch(videoPermissions())
            }
        }
    }

    private fun Intent.toExternalVideoItem(): VideoMediaItem? {
        if (action != Intent.ACTION_VIEW) return null
        val uri = data ?: return null
        val mimeType = resolveMimeType(uri, type)
        if (!isSupportedVideoOpen(uri, mimeType)) return null

        return VideoMediaItem(
            id = uri.toString(),
            title = resolveDisplayName(uri),
            description = "External Video",
            thumbnailUri = uri,
            uri = uri,
            filePath = uri.path,
            folderName = "External",
            mimeType = mimeType
        )
    }

    private fun resolveMimeType(uri: Uri, intentType: String?): String? {
        val resolverType = runCatching { contentResolver.getType(uri) }.getOrNull()
        return intentType
            ?.takeIf { it.isNotBlank() }
            ?: resolverType?.takeIf { it.isNotBlank() }
    }

    private fun isSupportedVideoOpen(uri: Uri, mimeType: String?): Boolean {
        if (mimeType?.startsWith("video/") == true) return true
        if (mimeType == "application/octet-stream") return hasKnownVideoExtension(uri)
        return hasKnownVideoExtension(uri)
    }

    private fun hasKnownVideoExtension(uri: Uri): Boolean {
        val name = resolveDisplayName(uri).lowercase()
        return listOf(
            ".mp4", ".m4v", ".mkv", ".webm", ".avi", ".mov", ".3gp", ".3g2",
            ".ts", ".m2ts", ".mts", ".flv", ".f4v", ".wmv", ".mpg", ".mpeg",
            ".vob", ".ogv", ".rm", ".rmvb", ".asf", ".divx"
        ).any { name.endsWith(it) }
    }

    private fun resolveDisplayName(uri: Uri): String {
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        cursor.getString(index)?.takeIf { it.isNotBlank() }?.let { return it }
                    }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "External video"
    }

    private fun videoPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun videoPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            arrayOf(videoPermission())
        }
    }

    private fun hasVideoPermission(): Boolean {
        return videoPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun supportsPictureInPicture(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
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
        val videos by homeViewModel.videos.collectAsState()
        
        var selectedFolderName by rememberSaveable { mutableStateOf("") }

        LaunchedEffect(externalVideoToOpen?.id) {
            val video = externalVideoToOpen ?: return@LaunchedEffect
            hasStartedMobilePlayback = true
            playerViewModel.setPlaylist(listOf(video), video)
            playerViewModel.playMedia(video)
            externalVideoToOpen = null
            navController.navigate("player") {
                launchSingleTop = true
            }
        }

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
                        playerViewModel.setPlaylist(videos, video)
                        playerViewModel.playMedia(video, progress)
                        navController.navigate("player")
                    },
                    onFolderClick = { name, _ ->
                        selectedFolderName = name
                        navController.navigate("folder_videos")
                    },
                    onDeleteFolder = { folderName ->
                        homeViewModel.deleteFolder(folderName)
                    },
                    onRenameFolder = { oldName, newName ->
                        homeViewModel.renameFolder(oldName, newName)
                    }
                )
            }
            composable("folder_videos") {
                val groupedVideos by homeViewModel.groupedVideos.collectAsState()
                val folderVideos = groupedVideos[selectedFolderName] ?: emptyList()
                val folderDestinations = groupedVideos.map { (name, videos) ->
                    name to videos.firstOrNull()?.filePath?.let { java.io.File(it).parent }
                }
                FolderVideosScreen(
                    folderName = selectedFolderName,
                    videos = folderVideos,
                    folderDestinations = folderDestinations,
                    onVideoClick = { video ->
                        homeViewModel.addToRecentlyPlayed(video)
                        val progress = progressRepository.getProgress(video.id)
                        hasStartedMobilePlayback = true
                        playerViewModel.setPlaylist(folderVideos, video)
                        playerViewModel.playMedia(video, progress)
                        navController.navigate("player")
                    },
                    onRename = { video, newName -> homeViewModel.renameVideo(video, newName) },
                    onDelete = { video -> homeViewModel.deleteVideo(video) },
                    onDeleteSelected = { videos -> homeViewModel.deleteVideos(videos) },
                    onCopy = { video -> homeViewModel.copyVideoToClipboard(video) },
                    onCopySelected = { videos, targetPath ->
                        homeViewModel.copyVideosToFolder(videos, targetPath)
                    },
                    onMoveSelected = { videos, targetPath ->
                        homeViewModel.moveVideosToFolder(videos, targetPath)
                    },
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
                    onBackPressed = {
                        closeMobilePlayer()
                        navController.popBackStack()
                    }
                )
            }
        }
    }

    private fun closeMobilePlayer() {
        playerViewModel.stopPlayback()
        hasStartedMobilePlayback = false
    }

    @Composable
    fun TvAppNavigation() {
        val navController = rememberNavController()
        val progressRepository = remember { PlaybackProgressRepository(this) }

        LaunchedEffect(externalVideoToOpen?.id) {
            val video = externalVideoToOpen ?: return@LaunchedEffect
            hasStartedTvPlayback = true
            tvPlayerViewModel.setPlaylist(listOf(video), video)
            tvPlayerViewModel.playMedia(video)
            externalVideoToOpen = null
            navController.navigate("tv_player") {
                launchSingleTop = true
            }
        }

        NavHost(navController = navController, startDestination = "tv_browse") {
            composable("tv_browse") {
                TvBrowseScreen(
                    viewModel = tvBrowseViewModel,
                    onVideoClick = { video ->
                        val progress = progressRepository.getProgress(video.id)
                        hasStartedTvPlayback = true
                        tvPlayerViewModel.setPlaylist(tvBrowseViewModel.videos.value, video)
                        tvPlayerViewModel.playMedia(video, progress)
                        navController.navigate("tv_player")
                    },
                    onPlayAllClick = { videosList ->
                        if (videosList.isNotEmpty()) {
                            val firstVideo = videosList.first()
                            val progress = progressRepository.getProgress(firstVideo.id)
                            hasStartedTvPlayback = true
                            tvPlayerViewModel.setPlaylist(videosList, firstVideo)
                            tvPlayerViewModel.playMedia(firstVideo, progress)
                            navController.navigate("tv_player")
                        }
                    }
                )
            }
            composable("tv_player") {
                TvPlayerScreen(
                    viewModel = tvPlayerViewModel,
                    onBackPressed = {
                        closeTvPlayer()
                        navController.popBackStack()
                    }
                )
            }
        }
    }

    private fun closeTvPlayer() {
        tvPlayerViewModel.stopPlayback()
        hasStartedTvPlayback = false
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
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) return

        if (isTelevisionDevice() && hasStartedTvPlayback && supportsPictureInPicture()) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            runCatching { enterPictureInPictureMode(params) }
            return
        }

        if (hasStartedMobilePlayback && playerViewModel.isPlaying.value && supportsPictureInPicture()) {
            runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().build()) }
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

    @TargetApi(Build.VERSION_CODES.O)
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

        if (!isInPictureInPictureMode) {
            lifecycleScope.launch {
                delay(300)
                if (hasStartedMobilePlayback && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    playerViewModel.stopPlayback()
                    hasStartedMobilePlayback = false
                }
            }
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

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (isTelevisionDevice() && event.action == android.view.KeyEvent.ACTION_DOWN) {
            val currentFocus = window.currentFocus
            if (currentFocus == null) {
                window.decorView.requestFocus()
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

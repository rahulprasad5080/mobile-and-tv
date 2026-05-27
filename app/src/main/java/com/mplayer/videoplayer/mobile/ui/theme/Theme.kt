package com.mplayer.videoplayer.mobile.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF2196F3),     // PrimaryBlue
    secondary = Color(0xFF455A64),   // FolderGrey
    background = Color(0xFF0F1517),  // DarkBackground
    surface = Color(0xFF1B2428),     // SurfaceColor
    onBackground = Color.White,
    onSurface = Color.White,
    onPrimary = Color.White,
    error = Color(0xFFF44336),       // BadgeRed
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2),     // A slightly darker, higher-contrast blue for light mode
    secondary = Color(0xFF78909C),   // A lighter folder grey with good contrast in light mode
    background = Color(0xFFF5F7F8),  // Beautiful premium off-white/light-grey background
    surface = Color(0xFFFFFFFF),     // Pure white surface for cards/folders
    onBackground = Color(0xFF0F1517),// Dark slate for readability
    onSurface = Color(0xFF0F1517),   // Dark slate on cards
    onPrimary = Color.White,
    error = Color(0xFFD32F2F),       // A robust red for light mode badges
    onError = Color.White
)

@Composable
fun MPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    // Update status bar icon colors based on theme:
    // Light mode → dark icons (visible on white background)
    // Dark mode  → light icons (visible on dark background)
    val view = LocalView.current
    val context = LocalContext.current
    SideEffect {
        val activity = context as? ComponentActivity ?: return@SideEffect
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, view)
        // true = dark icons for light mode, false = light icons for dark mode
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

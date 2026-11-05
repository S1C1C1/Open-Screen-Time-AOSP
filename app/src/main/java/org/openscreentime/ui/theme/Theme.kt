package org.openscreentime.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback colors, used in screenshot tests so the images don't depend on the
// wallpaper. On a phone, the wallpaper-based colors below are used instead.
private val LightColors = lightColorScheme(
    primary = Color(0xFF3F5F90),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF555F71),
    surface = Color(0xFFF9F9FF),
    surfaceContainer = Color(0xFFEDEDF4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C8FF),
    onPrimary = Color(0xFF07305F),
    primaryContainer = Color(0xFF254777),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBDC7DC),
    surface = Color(0xFF111318),
    surfaceContainer = Color(0xFF1D2024),
)

/**
 * App theme. Like AOSP Settings, it uses the phone's wallpaper-based
 * ("Material You") colors and follows the system light or dark setting.
 */
@Composable
fun OpenScreenTimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(LocalContext.current)
        dynamicColor -> dynamicLightColorScheme(LocalContext.current)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

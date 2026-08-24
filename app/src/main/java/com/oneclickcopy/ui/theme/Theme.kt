package com.oneclickcopy.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = Grey60,
    onPrimary = Neutral99,
    primaryContainer = Neutral25,
    onPrimaryContainer = Neutral90,
    secondary = Grey60,
    onSecondary = Neutral99,
    secondaryContainer = Neutral20,
    onSecondaryContainer = Neutral90,
    tertiary = Grey60,
    onTertiary = Neutral99,
    background = Neutral00,
    onBackground = Neutral90,
    surface = Neutral00,
    onSurface = Neutral90,
    surfaceVariant = Neutral15,
    onSurfaceVariant = Grey60,
    surfaceDim = Neutral00,
    surfaceBright = Neutral20,
    surfaceContainerLowest = Neutral00,
    surfaceContainerLow = Neutral08,
    surfaceContainer = Neutral05,
    surfaceContainerHigh = Neutral15,
    surfaceContainerHighest = Neutral20,
    outline = Grey30,
    outlineVariant = Neutral25,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral10,
    inversePrimary = Grey30,
    error = ErrorDark,
    onError = Color(0xFF690005),
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorContainerLight,
    scrim = Color(0xFF000000),
    surfaceTint = Grey60,
)

/** Semantic colors that Material's scheme does not model. */
data class ModeColors(
    val copyMode: Color,
    val editMode: Color,
    val onCopyMode: Color,
    val onEditMode: Color,
)

private val DarkModeColors = ModeColors(
    copyMode = CopyModeGreenContainer,
    editMode = EditModeAmberContainer,
    onCopyMode = OnCopyModeGreenContainer,
    onEditMode = OnEditModeAmberContainer,
)

val LocalModeColors = staticCompositionLocalOf { DarkModeColors }

/**
 * App theme.
 *
 * Dark is the product look. The palette does not follow the system light
 * setting and does not pull Material You wallpaper colors.
 */
@Composable
fun OneClickCopyTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? ComponentActivity ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    CompositionLocalProvider(LocalModeColors provides DarkModeColors) {
        MaterialTheme(
            colorScheme = DarkColors,
            typography = Typography,
            content = content,
        )
    }
}

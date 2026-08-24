package com.oneclickcopy.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = Teal80,
    onPrimary = Color(0xFF003737),
    primaryContainer = TealContainerDark,
    onPrimaryContainer = TealContainerLight,
    secondary = Teal80,
    onSecondary = Color(0xFF003737),
    secondaryContainer = Ink20,
    onSecondaryContainer = Mist90,
    tertiary = Teal80,
    onTertiary = Color(0xFF003737),
    background = Ink00,
    onBackground = Mist90,
    surface = Ink00,
    onSurface = Mist90,
    surfaceVariant = Ink20,
    onSurfaceVariant = Mist70,
    surfaceDim = Ink00,
    surfaceBright = Ink20,
    surfaceContainerLowest = Ink00,
    surfaceContainerLow = Ink10,
    surfaceContainer = Ink15,
    surfaceContainerHigh = Ink20,
    surfaceContainerHighest = Ink25,
    outline = Color(0xFF3E5557),
    outlineVariant = Ink25,
    inverseSurface = Mist90,
    inverseOnSurface = Ink10,
    inversePrimary = Teal40,
    error = ErrorDark,
    onError = Color(0xFF690005),
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorContainerLight,
    scrim = Color(0xFF000000),
    surfaceTint = Teal80,
)

private val LightColors = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = Color(0xFF002020),
    secondary = Teal40,
    onSecondary = Color.White,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral95,
    onSurfaceVariant = Grey30,
    outline = Grey60,
    error = ErrorLight,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = Color(0xFF410002),
)

/** Semantic colors that Material's scheme does not model. */
data class ModeColors(
    val copyMode: Color,
    val editMode: Color,
    val onCopyMode: Color,
    val onEditMode: Color,
)

val LocalModeColors = staticCompositionLocalOf {
    ModeColors(
        copyMode = CopyModeGreen,
        editMode = EditModeAmber,
        onCopyMode = Color.White,
        onEditMode = Color.White,
    )
}

/**
 * App theme.
 *
 * Dark is the product look. Defaults do not follow the system light setting and
 * do not pull Material You wallpaper colors, so the ink/teal palette stays
 * consistent on every device. Previews and tests can still pass [darkTheme]
 * or [dynamicColor] explicitly.
 */
@Composable
fun OneClickCopyTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val modeColors = if (darkTheme) {
        ModeColors(
            copyMode = CopyModeGreenLight,
            editMode = EditModeAmberLight,
            onCopyMode = OnCopyModeDark,
            onEditMode = OnEditModeDark,
        )
    } else {
        ModeColors(
            copyMode = CopyModeGreen,
            editMode = EditModeAmber,
            onCopyMode = Color.White,
            onEditMode = Color.White,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? ComponentActivity ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalModeColors provides modeColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

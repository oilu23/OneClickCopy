package com.oneclickcopy.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.activity.ComponentActivity

private val DarkColors = darkColorScheme(
    primary = Teal80,
    onPrimary = Color(0xFF003737),
    primaryContainer = TealContainerDark,
    onPrimaryContainer = TealContainerLight,
    secondary = Teal80,
    onSecondary = Color(0xFF003737),
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = Neutral20,
    onSurfaceVariant = Grey60,
    surfaceContainer = Neutral15,
    surfaceContainerHigh = Neutral20,
    surfaceContainerHighest = Neutral25,
    outline = Grey30,
    outlineVariant = Neutral25,
    error = ErrorDark,
    onError = Color(0xFF690005),
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorContainerLight,
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
)

val LocalModeColors = staticCompositionLocalOf {
    ModeColors(copyMode = CopyModeGreen, editMode = EditModeAmber)
}

/**
 * App theme.
 *
 * Unlike the original — which hardcoded dark and ignored its own [darkTheme]
 * parameter — this honours the system setting and supports Material You dynamic
 * color on Android 12+.
 */
@Composable
fun OneClickCopyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
        ModeColors(copyMode = CopyModeGreenLight, editMode = EditModeAmberLight)
    } else {
        ModeColors(copyMode = CopyModeGreen, editMode = EditModeAmber)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? ComponentActivity ?: return@SideEffect
            WindowCompat.getInsetsController(activity.window, view)
                .isAppearanceLightStatusBars = !darkTheme
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

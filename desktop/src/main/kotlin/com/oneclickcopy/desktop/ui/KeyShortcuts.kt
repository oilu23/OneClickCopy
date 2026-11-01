package com.oneclickcopy.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.key.KeyEvent

/**
 * Routes window-level keyboard shortcuts to whichever screen is showing.
 * The window offers every key press to [handler] before focused widgets see it.
 */
class KeyShortcuts {
    var handler: ((KeyEvent) -> Boolean)? = null
}

val LocalKeyShortcuts = staticCompositionLocalOf { KeyShortcuts() }

/** The app window, for native file dialogs. */
val LocalAppWindow = staticCompositionLocalOf<ComposeWindow?> { null }

/** Registers [onKey] as the active shortcut handler while this screen is shown. */
@Composable
fun KeyShortcutHandler(onKey: (KeyEvent) -> Boolean) {
    val shortcuts = LocalKeyShortcuts.current
    val current by rememberUpdatedState(onKey)
    DisposableEffect(shortcuts) {
        val handler: (KeyEvent) -> Boolean = { current(it) }
        shortcuts.handler = handler
        onDispose { if (shortcuts.handler === handler) shortcuts.handler = null }
    }
}

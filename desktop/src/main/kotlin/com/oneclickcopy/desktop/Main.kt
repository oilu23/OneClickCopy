package com.oneclickcopy.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.oneclickcopy.desktop.ui.KeyShortcuts
import com.oneclickcopy.desktop.ui.LocalAppWindow
import com.oneclickcopy.desktop.ui.LocalKeyShortcuts
import com.oneclickcopy.desktop.ui.Strings
import com.oneclickcopy.desktop.ui.editor.EditorScreen
import com.oneclickcopy.desktop.ui.editor.EditorViewModel
import com.oneclickcopy.desktop.ui.home.HomeScreen
import com.oneclickcopy.desktop.ui.home.HomeViewModel
import com.oneclickcopy.desktop.ui.theme.OneClickCopyTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import kotlin.system.exitProcess

/**
 * The saved window bounds if they are still on a connected screen, otherwise a
 * default size that fits the primary screen's work area (small laptop screens
 * at 125% scaling are only ~800 units tall), centered on it.
 */
private fun initialWindowBounds(saved: WindowBounds?): WindowBounds {
    val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val work = env.maximumWindowBounds
    if (saved != null) {
        val savedRect = Rectangle(saved.x, saved.y, saved.width, saved.height)
        val onScreen = env.screenDevices.any { device ->
            val visible = device.defaultConfiguration.bounds.intersection(savedRect)
            visible.width >= MIN_VISIBLE && visible.height >= MIN_VISIBLE
        }
        if (onScreen) {
            return saved.copy(
                width = saved.width.coerceAtMost(work.width),
                height = saved.height.coerceAtMost(work.height),
                y = saved.y.coerceAtLeast(work.y),
            )
        }
    }
    val width = minOf(DEFAULT_WIDTH, work.width - 2 * SCREEN_MARGIN)
    val height = minOf(DEFAULT_HEIGHT, work.height - 2 * SCREEN_MARGIN)
    return WindowBounds(
        x = work.x + (work.width - width) / 2,
        y = work.y + (work.height - height) / 2,
        width = width,
        height = height,
    )
}

private const val DEFAULT_WIDTH = 520
private const val DEFAULT_HEIGHT = 780
private const val SCREEN_MARGIN = 24
private const val MIN_VISIBLE = 120

/** Window and taskbar icon, read from the classpath. */
private fun loadAppIcon(): BitmapPainter {
    val bytes = checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")) {
        "icon.png missing from resources"
    }.use { it.readBytes() }
    return BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap())
}

private data class OpenDocument(val id: Long, val isNew: Boolean)

fun main() {
    val instance = SingleInstance.acquire(AppPaths.dataDir) ?: exitProcess(0)
    val container = AppContainer()
    val home = HomeViewModel(
        scope = container.appScope,
        repository = container.repository,
        syncManager = container.syncManager,
        gitHubBackup = container.gitHubBackup,
        documentTransfer = container.documentTransfer,
    )

    application {
        val initial = remember { initialWindowBounds(container.settings.windowBounds) }
        val windowState = rememberWindowState(
            size = DpSize(initial.width.dp, initial.height.dp),
            position = WindowPosition(initial.x.dp, initial.y.dp),
        )
        val shortcuts = remember { KeyShortcuts() }
        val appIcon = remember { loadAppIcon() }
        var alwaysOnTop by remember { mutableStateOf(container.settings.alwaysOnTop) }
        var openDocument by remember { mutableStateOf<OpenDocument?>(null) }
        var editor by remember { mutableStateOf<EditorViewModel?>(null) }
        val uiScope = rememberCoroutineScope()

        fun closeEditor(then: () -> Unit = {}) {
            val current = editor
            editor = null
            openDocument = null
            uiScope.launch {
                current?.close()
                then()
            }
        }

        fun quit() {
            windowState.placement.takeIf { it == WindowPlacement.Floating }?.let {
                container.settings.windowBounds = WindowBounds(
                    windowState.position.x.value.toInt(),
                    windowState.position.y.value.toInt(),
                    windowState.size.width.value.toInt(),
                    windowState.size.height.value.toInt(),
                )
            }
            val current = editor
            editor = null
            runBlocking {
                current?.close()
                container.syncManager.flushOnExit()
            }
            exitApplication()
        }

        Window(
            onCloseRequest = ::quit,
            state = windowState,
            title = Strings.APP_NAME,
            icon = appIcon,
            alwaysOnTop = alwaysOnTop,
            onPreviewKeyEvent = { event ->
                event.type == KeyEventType.KeyDown && shortcuts.handler?.invoke(event) == true
            },
        ) {
            window.minimumSize = java.awt.Dimension(380, 480)
            // Window.background also fills the frame before Compose draws its
            // first frame, avoiding a white flash on startup and resize.
            window.background = java.awt.Color(0x000000)
            LaunchedEffect(window) {
                // Neutral00 caption to match the app background, Neutral90 text,
                // Neutral20 border (see ui/theme/Color.kt).
                WindowsTitleBar.applyDark(
                    window,
                    caption = java.awt.Color(0x000000),
                    text = java.awt.Color(0xE3E3E3),
                    border = java.awt.Color(0x242424),
                )
            }
            LaunchedEffect(Unit) {
                instance.listen {
                    java.awt.EventQueue.invokeLater {
                        if (window.extendedState == Frame.ICONIFIED) window.extendedState = Frame.NORMAL
                        window.toFront()
                        window.requestFocus()
                    }
                }
            }
            val toggleOnTop = {
                alwaysOnTop = !alwaysOnTop
                container.settings.alwaysOnTop = alwaysOnTop
            }

            CompositionLocalProvider(
                LocalKeyShortcuts provides shortcuts,
                LocalAppWindow provides window,
            ) {
                OneClickCopyTheme {
                    val open = openDocument
                    val currentEditor = editor
                    if (open != null && currentEditor != null) {
                        EditorScreen(
                            viewModel = currentEditor,
                            alwaysOnTop = alwaysOnTop,
                            onToggleAlwaysOnTop = toggleOnTop,
                            onNavigateBack = { closeEditor() },
                        )
                    } else {
                        HomeScreen(
                            viewModel = home,
                            alwaysOnTop = alwaysOnTop,
                            onToggleAlwaysOnTop = toggleOnTop,
                            onOpenDocument = { id, isNew ->
                                editor = EditorViewModel(
                                    repository = container.repository,
                                    documentId = id,
                                    isNewDocument = isNew,
                                    viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
                                )
                                openDocument = OpenDocument(id, isNew)
                            },
                        )
                    }
                }
            }
        }
    }
}

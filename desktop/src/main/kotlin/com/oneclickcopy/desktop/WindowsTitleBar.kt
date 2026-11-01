package com.oneclickcopy.desktop

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.Color
import java.awt.Window

/**
 * Colors the native Windows title bar to match the app's dark theme.
 *
 * Uses DWM window attributes rather than an undecorated window with a drawn
 * title bar, so snapping, the system menu and double-click-to-maximize keep
 * working. Windows 10 (1809+) gets the system dark title bar; Windows 11 also
 * takes the exact caption, text and border colors. Older systems ignore the
 * call and keep the default title bar.
 */
object WindowsTitleBar {

    private interface DwmApi : StdCallLibrary {
        fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: IntByReference, size: Int): Int
    }

    private val dwm: DwmApi? by lazy {
        if (Platform.isWindows()) runCatching { Native.load("dwmapi", DwmApi::class.java) }.getOrNull() else null
    }

    fun applyDark(window: Window, caption: Color, text: Color, border: Color) {
        val api = dwm ?: return
        runCatching {
            val hwnd = WinDef.HWND(Native.getWindowPointer(window))
            fun set(attribute: Int, value: Int) =
                api.DwmSetWindowAttribute(hwnd, attribute, IntByReference(value), Int.SIZE_BYTES)

            // Attribute 20 is the documented id; builds before 20H1 used 19.
            if (set(DWMWA_USE_IMMERSIVE_DARK_MODE, 1) != 0) set(DWMWA_USE_IMMERSIVE_DARK_MODE_OLD, 1)
            set(DWMWA_CAPTION_COLOR, caption.toColorRef())
            set(DWMWA_TEXT_COLOR, text.toColorRef())
            set(DWMWA_BORDER_COLOR, border.toColorRef())
        }
    }

    /** COLORREF is 0x00BBGGRR. */
    private fun Color.toColorRef(): Int = (blue shl 16) or (green shl 8) or red

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_BORDER_COLOR = 34
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36
}

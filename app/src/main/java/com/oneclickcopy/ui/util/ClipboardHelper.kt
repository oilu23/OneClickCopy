package com.oneclickcopy.ui.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.core.content.getSystemService

/**
 * Clipboard access with correct platform behaviour.
 *
 * The original inlined this into a composable and always showed its own feedback.
 * Android 13+ shows a system clipboard confirmation automatically, so duplicating
 * it produced a double toast; [copiedFeedbackNeeded] lets callers suppress theirs.
 */
object ClipboardHelper {

    fun copy(context: Context, text: String, label: String = "OneClickCopy") {
        val manager = context.getSystemService<ClipboardManager>() ?: return
        val clip = ClipData.newPlainText(label, text)
        manager.setPrimaryClip(clip)
    }

    /** Copies text flagged as sensitive so it is hidden from clipboard previews. */
    fun copySensitive(context: Context, text: String, label: String = "OneClickCopy") {
        val manager = context.getSystemService<ClipboardManager>() ?: return
        val clip = ClipData.newPlainText(label, text).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
        }
        manager.setPrimaryClip(clip)
    }

    /**
     * True when the app should surface its own copy confirmation.
     * Android 13 (API 33) and above render a system-level confirmation.
     */
    val copiedFeedbackNeeded: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
}

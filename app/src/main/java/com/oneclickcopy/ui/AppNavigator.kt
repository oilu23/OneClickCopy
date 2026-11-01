package com.oneclickcopy.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue

/** The document open in the editor, and whether it was just created. */
data class EditorRoute(val documentId: Long, val isNew: Boolean)

/**
 * The app's whole navigation state: the document list, optionally with one
 * document open on top of it.
 *
 * Replaces Navigation Compose, which is a lot of machinery for two screens.
 * With no back stack to pop, the old failure modes go away by construction:
 * a double-tapped Back cannot pop the document list, and a double-tapped row
 * cannot stack two editors.
 */
class AppNavigator(initial: EditorRoute? = null) {

    var editor: EditorRoute? by mutableStateOf(initial)
        private set

    /** Opens [documentId] unless an editor is already open (e.g. a double tap). */
    fun openEditor(documentId: Long, isNew: Boolean): Boolean {
        if (editor != null) return false
        editor = EditorRoute(documentId, isNew)
        return true
    }

    /** Returns to the document list. Returns false if it was already showing. */
    fun back(): Boolean {
        if (editor == null) return false
        editor = null
        return true
    }

    companion object {
        /** Keeps the open document across rotation and process death. */
        val Saver: Saver<AppNavigator, List<Any>> = Saver(
            save = { navigator ->
                navigator.editor?.let { listOf(it.documentId, it.isNew) } ?: emptyList()
            },
            restore = { saved ->
                AppNavigator(
                    if (saved.size == 2) EditorRoute(saved[0] as Long, saved[1] as Boolean) else null
                )
            },
        )
    }
}

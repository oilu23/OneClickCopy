package com.oneclickcopy.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.oneclickcopy.data.DocumentRepository
import com.oneclickcopy.domain.CopiedStateCodec
import com.oneclickcopy.domain.Snippet
import com.oneclickcopy.domain.SnippetKey
import com.oneclickcopy.domain.SnippetParser
import com.oneclickcopy.ui.common.UiText
import com.oneclickcopy.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable UI state for the editor screen. */
data class EditorUiState(
    val isLoading: Boolean = true,
    val documentExists: Boolean = true,
    val title: String = "",
    val rawText: String = "",
    val isCopyMode: Boolean = false,
    val snippets: List<Snippet> = emptyList(),
    val canUndoReorder: Boolean = false,
    // Whether a "Paste" action is available in the long-press menu, i.e. some
    // item has been copied to the paste buffer this session.
    val canPaste: Boolean = false,
    // Non-null when a long-press on a copy-field row should drop the user into
    // edit mode with the cursor at the end of that row's text. Consumed by the
    // edit field once applied.
    val editRequestedOffset: Int? = null,
) {
    val copiedCount: Int get() = snippets.count { it.isCopied }
    val totalCount: Int get() = snippets.size
    val allCopied: Boolean get() = snippets.isNotEmpty() && copiedCount == totalCount
    val progress: Float
        get() = if (totalCount == 0) 0f else copiedCount.toFloat() / totalCount
}

/** One-shot events the UI consumes exactly once. */
sealed interface EditorEvent {
    data class Copied(val text: String) : EditorEvent
    data object ChecksReset : EditorEvent
    data class Error(val message: UiText) : EditorEvent
}

class EditorViewModel(
    private val repository: DocumentRepository,
    private val documentId: Long,
    private val isNewDocument: Boolean = false,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<EditorEvent?>(null)
    val events: StateFlow<EditorEvent?> = _events.asStateFlow()

    /** Copied-state keys, held separately so they survive mode switches. */
    private var copiedKeys: Set<SnippetKey> = emptySet()
    /** Text of the last item copied to the paste buffer, or null if none yet. */
    private var lastCopiedText: String? = null
    private var autoSaveJob: Job? = null
    private var loaded = false

    /**
     * Previous copy-mode lists, newest last. Popped by [onUndoReorder] so an
     * accidental drag can be reversed without leaving Copy mode.
     */
    private val reorderUndoStack = ArrayDeque<ReorderSnapshot>()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val document = repository.getDocument(documentId)
            if (document == null) {
                _uiState.update { it.copy(isLoading = false, documentExists = false) }
                return@launch
            }
            copiedKeys = CopiedStateCodec.decode(document.copiedItems)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    documentExists = true,
                    title = document.title,
                    rawText = document.content,
                    isCopyMode = !isNewDocument,
                    snippets = SnippetParser.parse(document.content, copiedKeys),
                )
            }
            loaded = true
        }
    }

    fun onTitleChanged(title: String) {
        _uiState.update { it.copy(title = title) }
        scheduleSave()
    }

    fun onTextChanged(rawText: String) {
        if (rawText != _uiState.value.rawText) {
            // Typing rewrites the body, so reorder snapshots would restore stale
            // lines. Clear rather than try to reconcile.
            clearReorderUndo()
        }
        _uiState.update { state ->
            state.copy(
                rawText = rawText,
                // Keep snippets in sync only while in copy mode; parsing on every
                // keystroke in edit mode was the source of the documented lag.
                snippets = if (state.isCopyMode) {
                    SnippetParser.parse(rawText, copiedKeys)
                } else {
                    state.snippets
                },
                canUndoReorder = reorderUndoStack.isNotEmpty(),
            )
        }
        scheduleSave()
    }

    fun onToggleMode() {
        _uiState.update { state ->
            val entering = !state.isCopyMode
            state.copy(
                isCopyMode = entering,
                snippets = if (entering) {
                    SnippetParser.parse(state.rawText, copiedKeys)
                } else {
                    state.snippets
                },
            )
        }
    }

    /**
     * A long-press on a copy-mode row drops the user back into edit mode with the
     * text cursor placed at the end of that row, ready to add or change text.
     */
    fun onSnippetLongPress(snippet: Snippet) {
        val state = _uiState.value
        val editOffset = lineEndOffset(state.rawText, snippet.sourceLineIndex)
        _uiState.update {
            it.copy(
                isCopyMode = false,
                editRequestedOffset = editOffset ?: state.rawText.length,
            )
        }
    }

    /** Consumes a requested edit offset after the field has applied it. */
    fun consumeEditRequest() {
        _uiState.update { it.copy(editRequestedOffset = null) }
    }

    /**
     * Deletes the line for [snippet] from the document. Removes it from the
     * copied-state keys as well. The prior state is pushed onto the undo stack
     * so the deleted item can be restored with Undo.
     */
    fun onSnippetDelete(snippet: Snippet) {
        val state = _uiState.value
        val newText = removeLine(state.rawText, snippet.sourceLineIndex) ?: return
        pushReorderUndo(state)
        copiedKeys = copiedKeys - snippet.key
        rewriteDocument(newText)
    }

    /**
     * Pastes the last-copied item's text as a new line immediately below
     * [snippet]. A no-op if nothing has been copied yet.
     */
    fun onSnippetPasteAfter(snippet: Snippet) {
        val text = lastCopiedText ?: return
        val state = _uiState.value
        val newText = insertLineAfter(state.rawText, snippet.sourceLineIndex, text) ?: return
        // Paste rewrites the body, so reorder snapshots would restore stale lines.
        clearReorderUndo()
        rewriteDocument(newText)
    }

    /** Re-parses [rawText] into snippets and persists it. */
    private fun rewriteDocument(rawText: String) {
        _uiState.update {
            it.copy(
                rawText = rawText,
                snippets = SnippetParser.parse(rawText, copiedKeys),
                canUndoReorder = reorderUndoStack.isNotEmpty(),
            )
        }
        scheduleSave()
    }

    /**
     * Returns the [start, endExclusive) character range of the [lineIndex]-th
     * line (0-based) in [rawText], or null if that line does not exist.
     * [endExclusive] sits just past the line's content — at its trailing newline
     * when it has one, or at the end of the string for the final line.
     */
    private fun lineSpan(rawText: String, lineIndex: Int): IntRange? {
        if (lineIndex < 0) return null
        var currentLine = 0
        var offset = 0
        while (offset <= rawText.length) {
            val nl = rawText.indexOf('\n', offset)
            val endExclusive = if (nl == -1) rawText.length else nl
            if (currentLine == lineIndex) return offset..endExclusive
            currentLine++
            if (nl == -1) return null
            offset = nl + 1
        }
        return null
    }

    /**
     * Character offset just past the end of a line — where a cursor sits to edit
     * it — or null if the line does not exist.
     */
    private fun lineEndOffset(rawText: String, lineIndex: Int): Int? =
        lineSpan(rawText, lineIndex)?.last

    /**
     * Removes the [lineIndex]-th line (including its trailing newline) from
     * [rawText], returning the new text, or null if the line does not exist.
     */
    private fun removeLine(rawText: String, lineIndex: Int): String? {
        val span = lineSpan(rawText, lineIndex) ?: return null
        return if (span.last >= rawText.length) {
            // Final line: also consume the preceding newline so we do not leave
            // a dangling trailing newline behind.
            val preceding =
                if (span.first > 0 && rawText[span.first - 1] == '\n') span.first - 1 else span.first
            rawText.removeRange(preceding, rawText.length)
        } else {
            rawText.removeRange(span.first, span.last + 1)
        }
    }

    /**
     * Inserts [text] as a new line immediately after the [lineIndex]-th line in
     * [rawText], returning the new text, or null if the line does not exist.
     */
    private fun insertLineAfter(rawText: String, lineIndex: Int, text: String): String? {
        val endExclusive = lineSpan(rawText, lineIndex)?.last ?: return null
        return if (endExclusive < rawText.length) {
            // There is a newline after this line; insert text plus a newline
            // right after it so the following line keeps its own row.
            rawText.substring(0, endExclusive + 1) + text + "\n" + rawText.substring(endExclusive + 1)
        } else {
            // Last line, no trailing newline; append on its own line.
            rawText + "\n" + text
        }
    }

    fun onSnippetCopied(snippet: Snippet) {
        copiedKeys = copiedKeys + snippet.key
        lastCopiedText = snippet.text
        _uiState.update { state ->
            state.copy(
                snippets = state.snippets.map {
                    if (it.key == snippet.key) it.copy(isCopied = true) else it
                },
                canPaste = true,
            )
        }
        _events.value = EditorEvent.Copied(snippet.text)
        scheduleSave()
    }

    /** Lets the user manually clear a single checkmark without a full reset. */
    fun onSnippetUnchecked(snippet: Snippet) {
        copiedKeys = copiedKeys - snippet.key
        _uiState.update { state ->
            state.copy(
                snippets = state.snippets.map {
                    if (it.key == snippet.key) it.copy(isCopied = false) else it
                },
            )
        }
        scheduleSave()
    }

    fun onReorder(fromIndex: Int, toIndex: Int) {
        val state = _uiState.value
        val snippets = state.snippets
        if (fromIndex !in snippets.indices || toIndex !in snippets.indices) return
        if (fromIndex == toIndex) return

        pushReorderUndo(state)
        val reordered = snippets.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        val reindexed = SnippetParser.reindex(reordered)
        // Reordering rewrites the document, so copied keys must be remapped to
        // the new occurrence indices or checkmarks would jump between rows.
        copiedKeys = reindexed.filter { it.isCopied }.map { it.key }.toSet()
        _uiState.update {
            it.copy(
                snippets = reindexed,
                rawText = SnippetParser.render(reindexed),
                canUndoReorder = true,
            )
        }
        scheduleSave()
    }

    /** Restores the list as it was before the most recent drag. */
    fun onUndoReorder() {
        val snapshot = reorderUndoStack.removeLastOrNull() ?: return
        copiedKeys = snapshot.copiedKeys
        _uiState.update {
            it.copy(
                snippets = snapshot.snippets,
                rawText = snapshot.rawText,
                canUndoReorder = reorderUndoStack.isNotEmpty(),
            )
        }
        scheduleSave()
    }

    private fun pushReorderUndo(state: EditorUiState) {
        reorderUndoStack.addLast(
            ReorderSnapshot(
                snippets = state.snippets,
                rawText = state.rawText,
                copiedKeys = copiedKeys,
            )
        )
        while (reorderUndoStack.size > MAX_REORDER_UNDO) {
            reorderUndoStack.removeFirst()
        }
    }

    private fun clearReorderUndo() {
        reorderUndoStack.clear()
    }

    fun onResetChecks() {
        copiedKeys = emptySet()
        _uiState.update { state ->
            state.copy(snippets = state.snippets.map { it.copy(isCopied = false) })
        }
        _events.value = EditorEvent.ChecksReset
        scheduleSave()
    }

    fun consumeEvent() {
        _events.value = null
    }

    private fun scheduleSave() {
        if (!loaded) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DEBOUNCE_MS)
            persist()
        }
    }

    /**
     * Writes immediately, cancelling any pending debounce.
     *
     * Called when the editor leaves the composition so the final keystrokes are
     * never lost — the original app could drop up to 500 ms of typing on back.
     */
    fun saveNow() {
        autoSaveJob?.cancel()
        viewModelScope.launch { persist() }
    }

    private suspend fun persist() {
        val state = _uiState.value
        if (!state.documentExists) return
        runCatching {
            repository.saveDocument(
                id = documentId,
                title = state.title,
                content = state.rawText,
                copiedKeys = copiedKeys,
            )
        }.onFailure {
            _events.value = EditorEvent.Error(UiText.res(R.string.error_save_document))
        }
    }

    companion object {
        private const val AUTO_SAVE_DEBOUNCE_MS = 400L
        private const val MAX_REORDER_UNDO = 20

        fun factory(
            repository: DocumentRepository,
            documentId: Long,
            isNewDocument: Boolean = false,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { EditorViewModel(repository, documentId, isNewDocument) }
            }
    }
}

private data class ReorderSnapshot(
    val snippets: List<Snippet>,
    val rawText: String,
    val copiedKeys: Set<SnippetKey>,
)

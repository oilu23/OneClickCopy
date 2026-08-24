package com.oneclickcopy.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<EditorEvent?>(null)
    val events: StateFlow<EditorEvent?> = _events.asStateFlow()

    /** Copied-state keys, held separately so they survive mode switches. */
    private var copiedKeys: Set<SnippetKey> = emptySet()
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

    fun onSnippetCopied(snippet: Snippet) {
        copiedKeys = copiedKeys + snippet.key
        _uiState.update { state ->
            state.copy(
                snippets = state.snippets.map {
                    if (it.key == snippet.key) it.copy(isCopied = true) else it
                },
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

        fun factory(repository: DocumentRepository, documentId: Long): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { EditorViewModel(repository, documentId) }
            }
    }
}

private data class ReorderSnapshot(
    val snippets: List<Snippet>,
    val rawText: String,
    val copiedKeys: Set<SnippetKey>,
)

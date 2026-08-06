package com.oneclickcopy.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.oneclickcopy.data.DocumentEntity
import com.oneclickcopy.data.DocumentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val documents: List<DocumentEntity> = emptyList(),
    val searchQuery: String = "",
) {
    val isEmpty: Boolean get() = !isLoading && documents.isEmpty()
    val isSearching: Boolean get() = searchQuery.isNotBlank()
}

sealed interface HomeEvent {
    data class DocumentDeleted(val document: DocumentEntity) : HomeEvent
    data class Error(val message: String) : HomeEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: DocumentRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    private val _events = MutableStateFlow<HomeEvent?>(null)
    val events: StateFlow<HomeEvent?> = _events.asStateFlow()

    val uiState: StateFlow<HomeUiState> =
        combine(
            repository.observeDocuments(),
            searchQuery,
        ) { documents, query ->
            val filtered = if (query.isBlank()) {
                documents
            } else {
                documents.filter {
                    it.title.contains(query, ignoreCase = true) ||
                        it.content.contains(query, ignoreCase = true)
                }
            }
            HomeUiState(isLoading = false, documents = filtered, searchQuery = query)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HomeUiState(),
        )

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun onCreateDocument(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.createDocument() }
                .onSuccess(onCreated)
                .onFailure {
                    _events.value = HomeEvent.Error(it.message ?: "Could not create document")
                }
        }
    }

    fun onDeleteDocument(document: DocumentEntity) {
        viewModelScope.launch {
            runCatching { repository.deleteDocument(document) }
                .onSuccess { _events.value = HomeEvent.DocumentDeleted(document) }
                .onFailure {
                    _events.value = HomeEvent.Error(it.message ?: "Could not delete document")
                }
        }
    }

    /** Undo support for an accidental delete. */
    fun onUndoDelete(document: DocumentEntity) {
        viewModelScope.launch {
            runCatching { repository.restoreDocument(document) }
                .onFailure {
                    _events.value = HomeEvent.Error(it.message ?: "Could not restore document")
                }
        }
    }

    /** Drops a document that was created but left completely untouched. */
    fun discardIfEmpty(documentId: Long) {
        viewModelScope.launch {
            runCatching { repository.discardIfEmpty(documentId) }
        }
    }

    fun consumeEvent() {
        _events.value = null
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(repository: DocumentRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { HomeViewModel(repository) }
            }
    }
}

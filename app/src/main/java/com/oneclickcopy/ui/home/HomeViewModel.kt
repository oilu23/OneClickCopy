package com.oneclickcopy.ui.home

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.oneclickcopy.backup.DriveBackupManager
import com.oneclickcopy.data.DocumentEntity
import com.oneclickcopy.data.DocumentRepository
import com.oneclickcopy.data.DocumentTransfer
import android.net.Uri
import com.oneclickcopy.sync.SyncManager
import com.oneclickcopy.sync.SyncState
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
    val syncState: SyncState = SyncState.SignedOut,
    val accountEmail: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && documents.isEmpty()
    val isSearching: Boolean get() = searchQuery.isNotBlank()
    val isSignedIn: Boolean get() = syncState !is SyncState.SignedOut
}

sealed interface HomeEvent {
    data class DocumentDeleted(val document: DocumentEntity) : HomeEvent
    data class Message(val text: String) : HomeEvent
    data class Error(val message: String) : HomeEvent
    data class RestoreCompleted(val inserted: Int, val updated: Int) : HomeEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: DocumentRepository,
    private val syncManager: SyncManager,
    private val driveBackupManager: DriveBackupManager,
    private val documentTransfer: DocumentTransfer,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    private val _events = MutableStateFlow<HomeEvent?>(null)
    val events: StateFlow<HomeEvent?> = _events.asStateFlow()

    val uiState: StateFlow<HomeUiState> =
        combine(
            repository.observeDocuments(),
            searchQuery,
            syncManager.state,
        ) { documents, query, syncState ->
            val filtered = if (query.isBlank()) {
                documents
            } else {
                documents.filter {
                    it.title.contains(query, ignoreCase = true) ||
                        it.content.contains(query, ignoreCase = true)
                }
            }
            HomeUiState(
                isLoading = false,
                documents = filtered,
                searchQuery = query,
                syncState = syncState,
                accountEmail = syncManager.accountEmail,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HomeUiState(),
        )

    fun signInIntent(): Intent = driveBackupManager.signInIntent()

    /**
     * Completes sign-in: pulls the cloud copy, merges it in, then pushes local
     * documents up. Runs on every sign-in so a reinstall always recovers data.
     */
    fun onSignInSucceeded() {
        viewModelScope.launch {
            syncManager.onSignedIn()
                .onSuccess { merge ->
                    if (merge.inserted > 0 || merge.updated > 0) {
                        _events.value = HomeEvent.RestoreCompleted(merge.inserted, merge.updated)
                    }
                }
                .onFailure { _events.value = HomeEvent.Error(it.message ?: "Restore failed") }
        }
    }

    fun onSignInFailed(message: String) {
        _events.value = HomeEvent.Error(message)
    }

    fun onSignOut() {
        viewModelScope.launch {
            syncManager.onSignOut()
            _events.value = HomeEvent.Message("Signed out")
        }
    }

    fun onSyncNow() {
        syncManager.syncNow()
    }

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

    fun onUndoDelete(document: DocumentEntity) {
        viewModelScope.launch {
            runCatching { repository.restoreDocument(document) }
                .onFailure {
                    _events.value = HomeEvent.Error(it.message ?: "Could not restore document")
                }
        }
    }

    fun discardIfEmpty(documentId: Long) {
        viewModelScope.launch { runCatching { repository.discardIfEmpty(documentId) } }
    }

    fun onExportTo(uri: Uri) {
        viewModelScope.launch {
            documentTransfer.exportTo(uri)
                .onSuccess { _events.value = HomeEvent.Message("Exported $it documents") }
                .onFailure { _events.value = HomeEvent.Error(it.message ?: "Export failed") }
        }
    }

    fun onImportFrom(uri: Uri) {
        viewModelScope.launch {
            documentTransfer.importFrom(uri)
                .onSuccess {
                    _events.value = HomeEvent.Message(
                        "Imported ${it.inserted + it.updated} documents"
                    )
                }
                .onFailure { _events.value = HomeEvent.Error(it.message ?: "Import failed") }
        }
    }

    fun consumeEvent() {
        _events.value = null
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(
            repository: DocumentRepository,
            syncManager: SyncManager,
            driveBackupManager: DriveBackupManager,
            documentTransfer: DocumentTransfer,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(repository, syncManager, driveBackupManager, documentTransfer)
            }
        }
    }
}

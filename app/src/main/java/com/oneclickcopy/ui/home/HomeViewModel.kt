package com.oneclickcopy.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.oneclickcopy.R
import com.oneclickcopy.backup.github.DeviceCode
import com.oneclickcopy.backup.github.GitHubBackup
import com.oneclickcopy.data.DocumentEntity
import com.oneclickcopy.data.DocumentRepository
import com.oneclickcopy.data.DocumentTransfer
import com.oneclickcopy.sync.SyncManager
import com.oneclickcopy.sync.SyncState
import com.oneclickcopy.ui.common.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    val accountLabel: String? = null,
) {
    val isSearching: Boolean get() = searchQuery.isNotBlank()
    val isSignedIn: Boolean get() = syncState !is SyncState.SignedOut
}

/** Progress of the GitHub device-flow sign-in dialog. */
sealed interface GitHubSignInState {
    data object Hidden : GitHubSignInState
    data object RequestingCode : GitHubSignInState
    data class AwaitingApproval(val code: DeviceCode) : GitHubSignInState
}

sealed interface HomeEvent {
    data class DocumentDeleted(val document: DocumentEntity) : HomeEvent
    data class Message(val text: UiText) : HomeEvent
    data class Error(val message: UiText) : HomeEvent
}

class HomeViewModel(
    private val repository: DocumentRepository,
    private val syncManager: SyncManager,
    private val gitHubBackup: GitHubBackup,
    private val documentTransfer: DocumentTransfer,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    private val _events = MutableStateFlow<HomeEvent?>(null)
    val events: StateFlow<HomeEvent?> = _events.asStateFlow()

    private val _signInState = MutableStateFlow<GitHubSignInState>(GitHubSignInState.Hidden)
    val signInState: StateFlow<GitHubSignInState> = _signInState.asStateFlow()
    private var signInJob: Job? = null

    /** Prevents a double tap on the create button from making two documents. */
    private var isCreatingDocument = false

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
                accountLabel = syncManager.accountLabel,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HomeUiState(),
        )

    /**
     * Starts GitHub sign-in: fetches a one-time code for the dialog, then waits
     * in the background while the user approves it in the browser.
     */
    fun onSignInClicked() {
        signInJob?.cancel()
        signInJob = viewModelScope.launch {
            _signInState.value = GitHubSignInState.RequestingCode
            val code = gitHubBackup.startSignIn().getOrElse {
                if (it is CancellationException) throw it
                signInFailed(it)
                return@launch
            }
            _signInState.value = GitHubSignInState.AwaitingApproval(code)
            gitHubBackup.completeSignIn(code)
                .onSuccess {
                    _signInState.value = GitHubSignInState.Hidden
                    onSignInSucceeded()
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    signInFailed(it)
                }
        }
    }

    /** The user closed the sign-in dialog; stop waiting for approval. */
    fun onSignInCancelled() {
        signInJob?.cancel()
        signInJob = null
        _signInState.value = GitHubSignInState.Hidden
    }

    private fun signInFailed(error: Throwable) {
        _signInState.value = GitHubSignInState.Hidden
        _events.value = HomeEvent.Error(
            UiText.res(R.string.backup_sign_in_failed, error.localizedMessage.orEmpty())
        )
    }

    /**
     * Completes sign-in: pulls the cloud copy, merges it in, then pushes local
     * documents up. Runs on every sign-in so a reinstall always recovers data.
     */
    private fun onSignInSucceeded() {
        viewModelScope.launch {
            syncManager.onSignedIn()
                .onSuccess { merge ->
                    val restored = merge.inserted + merge.updated
                    if (restored > 0) {
                        _events.value = HomeEvent.Message(
                            UiText.res(R.string.sync_restore_completed, restored)
                        )
                    }
                }
                .onFailure {
                    _events.value = HomeEvent.Error(
                        UiText.res(R.string.backup_restore_failed, it.localizedMessage.orEmpty())
                    )
                }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            syncManager.onSignOut()
            _events.value = HomeEvent.Message(UiText.res(R.string.backup_signed_out))
        }
    }

    fun onSyncNow() {
        syncManager.syncNow()
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun onCreateDocument(onCreated: (Long) -> Unit) {
        // Guard against a double tap on the create button producing two
        // documents: the second tap lands before navigation removes the button
        // from the screen.
        if (isCreatingDocument) return
        isCreatingDocument = true

        viewModelScope.launch {
            runCatching { repository.createDocument() }
                .onSuccess(onCreated)
                .onFailure {
                    _events.value = HomeEvent.Error(UiText.res(R.string.error_create_document))
                }
            isCreatingDocument = false
        }
    }

    fun onDeleteDocument(document: DocumentEntity) {
        viewModelScope.launch {
            runCatching { repository.deleteDocument(document) }
                .onSuccess { _events.value = HomeEvent.DocumentDeleted(document) }
                .onFailure {
                    _events.value = HomeEvent.Error(UiText.res(R.string.error_delete_document))
                }
        }
    }

    /** Copies [document] under [copyTitle], already localized by the caller. */
    fun onDuplicateDocument(document: DocumentEntity, copyTitle: String) {
        viewModelScope.launch {
            runCatching { repository.duplicateDocument(document, copyTitle) }
                .onSuccess {
                    _events.value = HomeEvent.Message(UiText.res(R.string.home_duplicated_message))
                }
                .onFailure {
                    _events.value = HomeEvent.Error(UiText.res(R.string.error_duplicate_document))
                }
        }
    }

    fun onUndoDelete(document: DocumentEntity) {
        viewModelScope.launch {
            runCatching { repository.restoreDocument(document) }
                .onFailure {
                    _events.value = HomeEvent.Error(UiText.res(R.string.error_restore_document))
                }
        }
    }

    fun onExportTo(uri: Uri) {
        viewModelScope.launch {
            documentTransfer.exportTo(uri)
                .onSuccess {
                    _events.value = HomeEvent.Message(UiText.res(R.string.transfer_exported, it))
                }
                .onFailure {
                    _events.value = HomeEvent.Error(
                        UiText.Dynamic(it.localizedMessage.orEmpty())
                    )
                }
        }
    }

    fun onImportFrom(uri: Uri) {
        viewModelScope.launch {
            documentTransfer.importFrom(uri)
                .onSuccess {
                    _events.value = HomeEvent.Message(
                        UiText.res(R.string.transfer_imported, it.inserted + it.updated)
                    )
                }
                .onFailure {
                    _events.value = HomeEvent.Error(
                        UiText.Dynamic(it.localizedMessage.orEmpty())
                    )
                }
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
            gitHubBackup: GitHubBackup,
            documentTransfer: DocumentTransfer,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(repository, syncManager, gitHubBackup, documentTransfer)
            }
        }
    }
}

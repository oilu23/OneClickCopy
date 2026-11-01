package com.oneclickcopy.desktop.ui.home

import com.oneclickcopy.backup.github.DeviceCode
import com.oneclickcopy.backup.github.GitHubBackup
import com.oneclickcopy.data.DocumentEntity
import com.oneclickcopy.data.DocumentRepository
import com.oneclickcopy.desktop.DocumentTransfer
import com.oneclickcopy.desktop.sync.SyncManager
import com.oneclickcopy.desktop.ui.Strings
import com.oneclickcopy.sync.SyncState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.nio.file.Path

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

sealed interface GitHubSignInState {
    data object Hidden : GitHubSignInState
    data object RequestingCode : GitHubSignInState
    data class AwaitingApproval(val code: DeviceCode) : GitHubSignInState
}

sealed interface HomeEvent {
    data class DocumentDeleted(val document: DocumentEntity) : HomeEvent
    data class Message(val text: String) : HomeEvent
}

/** Port of the Android HomeViewModel; it lives for the whole app session. */
class HomeViewModel(
    private val scope: CoroutineScope,
    private val repository: DocumentRepository,
    private val syncManager: SyncManager,
    private val gitHubBackup: GitHubBackup,
    private val documentTransfer: DocumentTransfer,
) {

    private val searchQuery = MutableStateFlow("")

    private val _events = MutableStateFlow<HomeEvent?>(null)
    val events: StateFlow<HomeEvent?> = _events.asStateFlow()

    private val _signInState = MutableStateFlow<GitHubSignInState>(GitHubSignInState.Hidden)
    val signInState: StateFlow<GitHubSignInState> = _signInState.asStateFlow()
    private var signInJob: Job? = null

    private var isCreatingDocument = false

    val uiState: StateFlow<HomeUiState> =
        combine(repository.observeDocuments(), searchQuery, syncManager.state) { documents, query, syncState ->
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
        }.stateIn(scope, SharingStarted.Eagerly, HomeUiState())

    fun onSignInClicked() {
        signInJob?.cancel()
        signInJob = scope.launch {
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

    fun onSignInCancelled() {
        signInJob?.cancel()
        signInJob = null
        _signInState.value = GitHubSignInState.Hidden
    }

    private fun signInFailed(error: Throwable) {
        _signInState.value = GitHubSignInState.Hidden
        message(Strings.backupSignInFailed(error.localizedMessage.orEmpty()))
    }

    private fun onSignInSucceeded() {
        scope.launch {
            syncManager.onSignedIn()
                .onSuccess { merge ->
                    val restored = merge.inserted + merge.updated
                    if (restored > 0) message(Strings.syncRestoreCompleted(restored))
                }
                .onFailure { message(Strings.backupRestoreFailed(it.localizedMessage.orEmpty())) }
        }
    }

    fun onSignOut() {
        scope.launch {
            syncManager.onSignOut()
            message(Strings.BACKUP_SIGNED_OUT)
        }
    }

    fun onSyncNow() = syncManager.syncNow()

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun onCreateDocument(onCreated: (Long) -> Unit) {
        if (isCreatingDocument) return
        isCreatingDocument = true
        scope.launch {
            runCatching { repository.createDocument() }
                .onSuccess(onCreated)
                .onFailure { message(Strings.ERROR_CREATE_DOCUMENT) }
            isCreatingDocument = false
        }
    }

    fun onDeleteDocument(document: DocumentEntity) {
        scope.launch {
            runCatching { repository.deleteDocument(document) }
                .onSuccess { _events.value = HomeEvent.DocumentDeleted(document) }
                .onFailure { message(Strings.ERROR_DELETE_DOCUMENT) }
        }
    }

    fun onDuplicateDocument(document: DocumentEntity, copyTitle: String) {
        scope.launch {
            runCatching { repository.duplicateDocument(document, copyTitle) }
                .onSuccess { message(Strings.HOME_DUPLICATED_MESSAGE) }
                .onFailure { message(Strings.ERROR_DUPLICATE_DOCUMENT) }
        }
    }

    fun onUndoDelete(document: DocumentEntity) {
        scope.launch {
            runCatching { repository.restoreDocument(document) }
                .onFailure { message(Strings.ERROR_RESTORE_DOCUMENT) }
        }
    }

    fun onExportTo(path: Path) {
        scope.launch {
            documentTransfer.exportTo(path)
                .onSuccess { message(Strings.transferExported(it)) }
                .onFailure { message(it.localizedMessage.orEmpty()) }
        }
    }

    fun onImportFrom(path: Path) {
        scope.launch {
            documentTransfer.importFrom(path)
                .onSuccess { message(Strings.transferImported(it.inserted + it.updated)) }
                .onFailure { message(it.localizedMessage.orEmpty()) }
        }
    }

    private fun message(text: String) {
        _events.value = HomeEvent.Message(text)
    }

    fun consumeEvent() {
        _events.value = null
    }
}

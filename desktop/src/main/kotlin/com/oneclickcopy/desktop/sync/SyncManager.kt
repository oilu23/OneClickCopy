package com.oneclickcopy.desktop.sync

import com.oneclickcopy.backup.BackupError
import com.oneclickcopy.backup.BackupTransport
import com.oneclickcopy.backup.toBackup
import com.oneclickcopy.backup.toEntity
import com.oneclickcopy.data.DocumentRepository
import com.oneclickcopy.data.MergeResult
import com.oneclickcopy.desktop.Settings
import com.oneclickcopy.sync.SyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * Desktop counterpart of the Android SyncManager.
 *
 * Same behaviour, without WorkManager: local changes are debounced into a
 * single upload, failed uploads retry with exponential backoff, and a pending
 * upload is flushed when the window closes. Local storage stays the source of
 * truth; the gist is a mirror.
 */
class SyncManager(
    private val scope: CoroutineScope,
    private val repository: DocumentRepository,
    private val transport: BackupTransport,
    private val settings: Settings,
) {

    private val _state = MutableStateFlow<SyncState>(
        if (transport.isSignedIn()) SyncState.Idle(settings.lastSyncedAt) else SyncState.SignedOut
    )
    val state: StateFlow<SyncState> = _state.asStateFlow()

    val accountLabel: String? get() = transport.accountLabel()

    private var job: Job? = null
    @Volatile private var dirty = false

    /** Requests a sync after a local change. Safe to call on every edit. */
    fun requestSync() {
        if (!transport.isSignedIn()) {
            _state.value = SyncState.SignedOut
            return
        }
        dirty = true
        _state.value = SyncState.Pending
        schedule(DEBOUNCE_MS)
    }

    fun syncNow() {
        if (!transport.isSignedIn()) return
        dirty = true
        schedule(0)
    }

    @Synchronized
    private fun schedule(delayMillis: Long) {
        job?.cancel()
        job = scope.launch {
            delay(delayMillis)
            var backoff = BACKOFF_START_MS
            while (true) {
                val result = performUpload()
                val error = result.exceptionOrNull() ?: break
                if (error is BackupError.NotSignedIn) break
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(BACKOFF_MAX_MS)
            }
        }
    }

    private suspend fun performUpload(): Result<Unit> {
        _state.value = SyncState.Syncing
        dirty = false
        return runCatching { repository.getAllDocuments().map { it.toBackup() } }
            .mapCatching { documents -> transport.upload(documents).getOrThrow() }
            .onSuccess {
                settings.lastSyncedAt = System.currentTimeMillis()
                _state.value = SyncState.Idle(settings.lastSyncedAt)
            }
            .onFailure { error ->
                dirty = true
                _state.value = when {
                    error is BackupError.NotSignedIn -> SyncState.SignedOut
                    error is IOException -> SyncState.WaitingForNetwork
                    else -> SyncState.Failed(error.message.orEmpty())
                }
            }
    }

    /** Pulls the cloud copy and merges it in (by UUID, newest wins). */
    suspend fun restoreFromCloud(): Result<MergeResult> {
        _state.value = SyncState.Syncing
        return transport.download()
            .mapCatching { documents -> repository.mergeDocuments(documents.map { it.toEntity() }) }
            .onSuccess {
                settings.lastSyncedAt = System.currentTimeMillis()
                _state.value = SyncState.Idle(settings.lastSyncedAt)
            }
            .onFailure { error ->
                _state.value = when (error) {
                    is BackupError.NoBackupFound -> SyncState.Idle(settings.lastSyncedAt)
                    is BackupError.NotSignedIn -> SyncState.SignedOut
                    else -> SyncState.Failed(error.message ?: "Restore failed")
                }
            }
    }

    /** Called after a successful interactive sign-in. */
    suspend fun onSignedIn(): Result<MergeResult> {
        val result = restoreFromCloud()
        syncNow()
        return result
    }

    suspend fun onSignOut() {
        job?.cancel()
        dirty = false
        transport.signOut()
        settings.lastSyncedAt = 0L
        _state.value = SyncState.SignedOut
    }

    /** Uploads any unsent change before the app exits, waiting a few seconds at most. */
    suspend fun flushOnExit() {
        if (!dirty || !transport.isSignedIn()) return
        job?.cancel()
        withTimeoutOrNull(EXIT_FLUSH_TIMEOUT_MS) { performUpload() }
    }

    private companion object {
        const val DEBOUNCE_MS = 2_000L
        const val BACKOFF_START_MS = 15_000L
        const val BACKOFF_MAX_MS = 10 * 60_000L
        const val EXIT_FLUSH_TIMEOUT_MS = 5_000L
    }
}

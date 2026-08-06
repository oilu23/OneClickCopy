package com.oneclickcopy.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.oneclickcopy.backup.BackupError
import com.oneclickcopy.backup.DriveBackupManager
import com.oneclickcopy.backup.toBackup
import com.oneclickcopy.backup.toEntity
import com.oneclickcopy.data.DocumentRepository
import com.oneclickcopy.data.MergeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

/**
 * Coordinates automatic Google Drive sync.
 *
 * Design goals, in priority order:
 *  1. The user never has to think about backups.
 *  2. A change is never lost, even if the app is killed or offline.
 *  3. Sync never blocks or slows down local editing.
 *
 * Local storage remains the source of truth; Drive is a mirror. Uploads are
 * debounced and delegated to [SyncWorker] via WorkManager, which provides
 * retries with backoff, a network constraint, and persistence across process
 * death and reboot.
 */
class SyncManager(
    private val context: Context,
    private val repository: DocumentRepository,
    private val driveBackupManager: DriveBackupManager,
    private val preferences: SyncPreferences,
) {

    private val workManager get() = WorkManager.getInstance(context)

    private val _state = MutableStateFlow<SyncState>(
        if (driveBackupManager.isSignedIn()) {
            SyncState.Idle(preferences.lastSyncedAt)
        } else {
            SyncState.SignedOut
        }
    )
    val state: StateFlow<SyncState> = _state.asStateFlow()

    val accountEmail: String? get() = driveBackupManager.accountEmail()

    fun isSignedIn(): Boolean = driveBackupManager.isSignedIn()

    /**
     * Requests a sync after a local change.
     *
     * Safe to call on every edit: WorkManager's [ExistingWorkPolicy.REPLACE]
     * collapses rapid successive requests into a single upload, so typing does
     * not produce a burst of API calls.
     */
    fun requestSync() {
        if (!driveBackupManager.isSignedIn()) {
            _state.value = SyncState.SignedOut
            return
        }
        // Surface the offline case explicitly. WorkManager still holds the request
        // and sends it when connectivity returns, but the user should be able to
        // see why "Backed up" has not appeared yet.
        _state.value = if (isOnline()) SyncState.Pending else SyncState.WaitingForNetwork
        enqueue(delayMillis = DEBOUNCE_MS)
    }

    private fun isOnline(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    /** Uploads immediately, e.g. when the app moves to the background. */
    fun syncNow() {
        if (!driveBackupManager.isSignedIn()) return
        enqueue(delayMillis = 0)
    }

    private fun enqueue(delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_SECONDS,
                TimeUnit.SECONDS,
            )
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /**
     * Performs the actual upload. Called from [SyncWorker].
     *
     * Returns failure so the worker can decide whether to retry.
     */
    suspend fun performUpload(): Result<Unit> {
        _state.value = SyncState.Syncing
        val documents = repository.getAllDocuments().map { it.toBackup() }
        return driveBackupManager.upload(documents)
            .onSuccess {
                preferences.lastSyncedAt = System.currentTimeMillis()
                _state.value = SyncState.Idle(preferences.lastSyncedAt)
            }
            .onFailure { error ->
                _state.value = if (!isOnline()) {
                    SyncState.WaitingForNetwork
                } else {
                    SyncState.Failed(error.message.orEmpty())
                }
            }
    }

    /**
     * Pulls the cloud copy and merges it into local storage.
     *
     * Runs on every sign-in rather than only the first. The previous
     * implementation set a "has restored once" flag, which meant a user who
     * signed in, signed out, then reinstalled would never get their data back —
     * exactly the scenario backup exists for. Merging is idempotent (matched by
     * document UUID, newest wins), so repeating it is safe.
     */
    suspend fun restoreFromCloud(): Result<MergeResult> {
        _state.value = SyncState.Syncing
        return driveBackupManager.download()
            .mapCatching { documents ->
                repository.mergeDocuments(documents.map { it.toEntity() })
            }
            .onSuccess {
                preferences.lastSyncedAt = System.currentTimeMillis()
                _state.value = SyncState.Idle(preferences.lastSyncedAt)
            }
            .onFailure { error ->
                _state.value = if (error is BackupError.NoBackupFound) {
                    // A fresh account with no backup is a normal state, not an error.
                    SyncState.Idle(preferences.lastSyncedAt)
                } else {
                    SyncState.Failed(error.message ?: "Restore failed")
                }
            }
    }

    /** Called after a successful interactive sign-in. */
    suspend fun onSignedIn(): Result<MergeResult> {
        _state.value = SyncState.Syncing
        val result = restoreFromCloud()
        // Push local documents up immediately so the cloud copy reflects any
        // work done before signing in.
        syncNow()
        return result
    }

    suspend fun onSignOut() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
        driveBackupManager.signOut()
        preferences.lastSyncedAt = 0L
        _state.value = SyncState.SignedOut
    }

    companion object {
        internal const val UNIQUE_WORK_NAME = "oneclickcopy_sync"
        private const val WORK_TAG = "sync"
        private const val DEBOUNCE_MS = 2_000L
        private const val BACKOFF_SECONDS = 15L
    }
}

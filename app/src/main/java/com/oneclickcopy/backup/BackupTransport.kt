package com.oneclickcopy.backup

/**
 * A remote place to mirror the local database.
 *
 * [com.oneclickcopy.sync.SyncManager] depends only on this, so the storage
 * provider can change without touching sync scheduling or merge logic.
 */
interface BackupTransport {

    fun isSignedIn(): Boolean

    /** Human-readable account name for the UI, or null when signed out. */
    fun accountLabel(): String?

    suspend fun signOut()

    /** Replaces the remote backup with [documents]. */
    suspend fun upload(documents: List<BackupDocument>): Result<Unit>

    /** Fetches the remote backup. Fails with [BackupError.NoBackupFound] if none exists. */
    suspend fun download(): Result<List<BackupDocument>>
}

/** Typed backup failures so callers can react without string matching. */
sealed class BackupError(message: String) : Exception(message) {
    data object NotSignedIn : BackupError("Not signed in")
    data object NoBackupFound : BackupError("No backup found")
}

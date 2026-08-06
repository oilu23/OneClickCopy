package com.oneclickcopy.sync

/**
 * User-visible sync state.
 *
 * Surfaced in the UI so the user can *see* that their data is safe rather than
 * having to trust it silently — the whole point of automatic sync.
 */
sealed interface SyncState {

    /** Not signed in; syncing is unavailable. */
    data object SignedOut : SyncState

    /** Signed in, nothing pending. [lastSyncedAt] is epoch millis, 0 if never. */
    data class Idle(val lastSyncedAt: Long) : SyncState

    /** A change is queued but not yet uploaded. */
    data object Pending : SyncState

    /** Upload or download in progress. */
    data object Syncing : SyncState

    /** Queued, waiting for connectivity. Will send automatically. */
    data object WaitingForNetwork : SyncState

    /** Last attempt failed. Retries are scheduled automatically. */
    data class Failed(val message: String) : SyncState
}

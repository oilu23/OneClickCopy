package com.oneclickcopy.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.oneclickcopy.OneClickCopyApplication

/**
 * Uploads the local database to Drive.
 *
 * Running as a [CoroutineWorker] is what makes sync durable: WorkManager
 * persists the request to disk, so a pending upload survives the app being
 * killed, swiped away, or the device rebooting, and is retried with exponential
 * backoff until it succeeds.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? OneClickCopyApplication)?.container
            ?: return Result.failure()

        val syncManager = container.syncManager

        if (!syncManager.isSignedIn()) {
            // Nothing to do; do not burn retries on an unauthenticated state.
            return Result.success()
        }

        return syncManager.performUpload().fold(
            onSuccess = { Result.success() },
            onFailure = {
                // Retry transient failures (network, throttling) with backoff.
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            },
        )
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}

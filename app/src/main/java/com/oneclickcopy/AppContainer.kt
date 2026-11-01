package com.oneclickcopy

import android.content.Context
import com.oneclickcopy.R
import com.oneclickcopy.backup.github.GitHubBackup
import com.oneclickcopy.backup.github.GitHubDeviceFlow
import com.oneclickcopy.backup.github.GitHubGistClient
import com.oneclickcopy.backup.github.KeystoreGitHubTokenStore
import com.oneclickcopy.data.AppDatabase
import com.oneclickcopy.data.DocumentRepository
import com.oneclickcopy.data.DocumentTransfer
import com.oneclickcopy.sync.SyncManager
import com.oneclickcopy.sync.SyncPreferences
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Minimal manual dependency container.
 *
 * Deliberately not Hilt: the graph is a handful of objects, and manual
 * construction keeps the build free of DI annotation processing while remaining
 * trivially swappable in tests.
 */
interface AppContainer {
    val documentRepository: DocumentRepository
    val gitHubBackup: GitHubBackup
    val syncManager: SyncManager
    val documentTransfer: DocumentTransfer
}

class DefaultAppContainer(context: Context) : AppContainer {

    // Store the application context explicitly. These objects outlive any single
    // screen, so holding an Activity here would leak it across rotations.
    private val context: Context = context.applicationContext

    private val database by lazy { AppDatabase.get(context) }

    override val documentRepository: DocumentRepository by lazy {
        DocumentRepository(
            dao = database.documentDao(),
            ioDispatcher = Dispatchers.IO,
        ).also { repository ->
            // Every local write requests a sync. The repository stays unaware of
            // the backup provider; it just announces that something changed.
            repository.onChanged = { syncManager.requestSync() }
        }
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override val gitHubBackup: GitHubBackup by lazy {
        GitHubBackup(
            store = KeystoreGitHubTokenStore(context),
            deviceFlow = GitHubDeviceFlow(httpClient, context.getString(R.string.github_client_id)),
            gists = GitHubGistClient(httpClient),
        )
    }

    override val syncManager: SyncManager by lazy {
        SyncManager(
            context = context,
            repository = documentRepository,
            transport = gitHubBackup,
            preferences = SyncPreferences(context),
        )
    }

    override val documentTransfer: DocumentTransfer by lazy {
        DocumentTransfer(context, documentRepository)
    }
}

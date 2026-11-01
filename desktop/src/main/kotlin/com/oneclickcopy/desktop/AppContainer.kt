package com.oneclickcopy.desktop

import com.oneclickcopy.backup.github.FileGitHubTokenStore
import com.oneclickcopy.backup.github.GitHubBackup
import com.oneclickcopy.backup.github.GitHubDeviceFlow
import com.oneclickcopy.backup.github.GitHubGistClient
import com.oneclickcopy.data.DocumentRepository
import com.oneclickcopy.data.JsonFileDocumentDao
import com.oneclickcopy.desktop.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency wiring, as in the Android app. */
class AppContainer {

    /** Lives as long as the app. UI-facing work runs on the Swing thread. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val settings = Settings(AppPaths.settings)

    val repository = DocumentRepository(
        dao = JsonFileDocumentDao(AppPaths.documents),
        ioDispatcher = Dispatchers.IO,
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val gitHubBackup = GitHubBackup(
        store = FileGitHubTokenStore(AppPaths.githubSession),
        deviceFlow = GitHubDeviceFlow(http, GITHUB_CLIENT_ID),
        gists = GitHubGistClient(http),
    )

    val syncManager = SyncManager(appScope, repository, gitHubBackup, settings)

    val documentTransfer = DocumentTransfer(repository)

    init {
        repository.onChanged = { syncManager.requestSync() }
    }

    private companion object {
        /** Same public OAuth App as the Android build (device flow, no secret). */
        const val GITHUB_CLIENT_ID = "Ov23liqbv4Y86IVrE1BS"
    }
}

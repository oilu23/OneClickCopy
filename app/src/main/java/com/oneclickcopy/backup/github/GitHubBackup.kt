package com.oneclickcopy.backup.github

import com.oneclickcopy.backup.BackupCodec
import com.oneclickcopy.backup.BackupDocument
import com.oneclickcopy.backup.BackupError
import com.oneclickcopy.backup.BackupTransport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Backs up to a secret gist on the user's GitHub account.
 *
 * A secret gist is unlisted, not private: anyone holding its URL can read it.
 * The token is limited to the `gist` scope, so it cannot touch repositories.
 */
class GitHubBackup(
    private val store: GitHubTokenStore,
    private val deviceFlow: GitHubDeviceFlow,
    private val gists: GitHubGistClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BackupTransport {

    override fun isSignedIn(): Boolean = store.token != null

    override fun accountLabel(): String? = store.login?.let { "@$it" }

    /** Step 1 of sign-in: get a code for the user to enter on GitHub. */
    suspend fun startSignIn(): Result<DeviceCode> = withContext(ioDispatcher) {
        runCatching { deviceFlow.requestCode() }
    }

    /**
     * Step 2: wait for the user to approve [code], then save the session.
     * Returns the GitHub login. Cancel the calling coroutine to abandon it.
     */
    suspend fun completeSignIn(code: DeviceCode): Result<String> = withContext(ioDispatcher) {
        runCatching {
            val token = deviceFlow.awaitToken(code)
            val login = gists.currentUser(token)
            store.clear()
            store.token = token
            store.login = login
            login
        }
    }

    override suspend fun signOut() = withContext(ioDispatcher) {
        // The token stays valid on GitHub until the user revokes it under
        // Settings > Applications; revoking needs the client secret, which the
        // app deliberately does not have.
        store.clear()
    }

    override suspend fun upload(documents: List<BackupDocument>): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                authorized { token ->
                    val content = BackupCodec.encode(documents)
                    val id = store.gistId ?: gists.findGistWithFile(token, FILE_NAME)
                    if (id == null) {
                        store.gistId = gists.createSecretGist(token, DESCRIPTION, FILE_NAME, content)
                        return@authorized
                    }
                    try {
                        gists.updateFile(token, id, FILE_NAME, content)
                        store.gistId = id
                    } catch (e: GitHubHttpException) {
                        if (e.code != 404) throw e
                        // The gist was deleted on github.com; start a fresh one.
                        store.gistId = gists.createSecretGist(token, DESCRIPTION, FILE_NAME, content)
                    }
                }
            }
        }

    override suspend fun download(): Result<List<BackupDocument>> = withContext(ioDispatcher) {
        runCatching {
            authorized { token ->
                val id = store.gistId ?: gists.findGistWithFile(token, FILE_NAME)
                    ?: throw BackupError.NoBackupFound
                val raw = try {
                    gists.readFile(token, id, FILE_NAME)
                } catch (e: GitHubHttpException) {
                    if (e.code != 404) throw e
                    store.gistId = null
                    throw BackupError.NoBackupFound
                } ?: throw BackupError.NoBackupFound
                store.gistId = id
                BackupCodec.decode(raw)
            }
        }
    }

    /** Runs [block] with the token; a 401 means it was revoked, so sign out. */
    private inline fun <T> authorized(block: (String) -> T): T {
        val token = store.token ?: throw BackupError.NotSignedIn
        try {
            return block(token)
        } catch (e: GitHubHttpException) {
            if (e.code == 401) {
                store.clear()
                throw BackupError.NotSignedIn
            }
            throw e
        }
    }

    companion object {
        const val FILE_NAME = "oneclickcopy_backup.json"
        private const val DESCRIPTION = "OneClickCopy backup (created by the app; do not rename the file)"
    }
}

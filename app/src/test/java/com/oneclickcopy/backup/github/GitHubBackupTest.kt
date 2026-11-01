package com.oneclickcopy.backup.github

import com.google.common.truth.Truth.assertThat
import com.oneclickcopy.backup.BackupCodec
import com.oneclickcopy.backup.BackupDocument
import com.oneclickcopy.backup.BackupError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class GitHubBackupTest {

    private val server = MockWebServer()

    private class MemoryStore : GitHubTokenStore {
        override var token: String? = null
        override var login: String? = null
        override var gistId: String? = null
    }

    private val store = MemoryStore()
    private lateinit var backup: GitHubBackup

    private val docs = listOf(BackupDocument(uuid = "u1", title = "Groceries", content = "milk\neggs"))

    @Before
    fun setUp() {
        server.start()
        val base = server.url("").toString().trimEnd('/')
        val http = OkHttpClient()
        backup = GitHubBackup(
            store = store,
            deviceFlow = GitHubDeviceFlow(http, "client-123", base, sleep = {}),
            gists = GitHubGistClient(http, base),
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setBody(body).setHeader("Content-Type", "application/json")

    /** A gist API object holding the backup file with [content]. */
    private fun gistJson(id: String, content: String) =
        """{"id":"$id","files":{"oneclickcopy_backup.json":{"content":${kotlinx.serialization.json.JsonPrimitive(content)},"truncated":false}}}"""

    private fun signIn() {
        store.token = "gho_token"
        store.login = "octocat"
    }

    @Test
    fun `completeSignIn saves the token and login`() = runTest {
        server.enqueue(json("""{"access_token":"gho_token"}"""))
        server.enqueue(json("""{"login":"octocat"}"""))
        store.gistId = "stale-from-another-account"

        val login = backup.completeSignIn(DeviceCode("dev", "AAAA-BBBB", "x", 900, 5))

        assertThat(login.getOrThrow()).isEqualTo("octocat")
        assertThat(backup.isSignedIn()).isTrue()
        assertThat(backup.accountLabel()).isEqualTo("@octocat")
        assertThat(store.gistId).isNull()
        server.takeRequest()
        assertThat(server.takeRequest().getHeader("Authorization")).isEqualTo("Bearer gho_token")
    }

    @Test
    fun `first upload creates a secret gist and remembers it`() = runTest {
        signIn()
        server.enqueue(json("""[{"id":"other","files":{"notes.txt":{}}}]"""))
        server.enqueue(json(gistJson("g1", ""), code = 201))

        backup.upload(docs).getOrThrow()

        assertThat(store.gistId).isEqualTo("g1")
        assertThat(server.takeRequest().path).isEqualTo("/gists?per_page=100&page=1")
        val create = server.takeRequest()
        assertThat(create.method).isEqualTo("POST")
        val body = create.body.readUtf8()
        assertThat(body).contains("\"public\":false")
        assertThat(body).contains("oneclickcopy_backup.json")
        assertThat(body).contains("Groceries")
    }

    @Test
    fun `later uploads patch the cached gist`() = runTest {
        signIn()
        store.gistId = "g1"
        server.enqueue(json(gistJson("g1", "")))

        backup.upload(docs).getOrThrow()

        val patch = server.takeRequest()
        assertThat(patch.method).isEqualTo("PATCH")
        assertThat(patch.path).isEqualTo("/gists/g1")
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `upload recreates the gist if it was deleted on GitHub`() = runTest {
        signIn()
        store.gistId = "gone"
        server.enqueue(json("""{"message":"Not Found"}""", code = 404))
        server.enqueue(json(gistJson("g2", ""), code = 201))

        backup.upload(docs).getOrThrow()

        assertThat(store.gistId).isEqualTo("g2")
    }

    @Test
    fun `download finds the gist and decodes the backup`() = runTest {
        signIn()
        server.enqueue(json("""[{"id":"g1","files":{"oneclickcopy_backup.json":{}}}]"""))
        server.enqueue(json(gistJson("g1", BackupCodec.encode(docs))))

        val restored = backup.download().getOrThrow()

        assertThat(restored).isEqualTo(docs)
        assertThat(store.gistId).isEqualTo("g1")
    }

    @Test
    fun `download fetches raw_url when GitHub truncates the file`() = runTest {
        signIn()
        store.gistId = "g1"
        val raw = server.url("/raw/backup.json")
        server.enqueue(
            json("""{"id":"g1","files":{"oneclickcopy_backup.json":{"content":"{\"ver","truncated":true,"raw_url":"$raw"}}}""")
        )
        server.enqueue(MockResponse().setBody(BackupCodec.encode(docs)))

        assertThat(backup.download().getOrThrow()).isEqualTo(docs)
        server.takeRequest()
        // The token is not sent to the raw content host.
        assertThat(server.takeRequest().getHeader("Authorization")).isNull()
    }

    @Test
    fun `download with no backup gist reports NoBackupFound`() = runTest {
        signIn()
        server.enqueue(json("[]"))

        assertThat(backup.download().exceptionOrNull()).isEqualTo(BackupError.NoBackupFound)
    }

    @Test
    fun `a revoked token signs out`() = runTest {
        signIn()
        store.gistId = "g1"
        server.enqueue(json("""{"message":"Bad credentials"}""", code = 401))

        val error = backup.upload(docs).exceptionOrNull()

        assertThat(error).isEqualTo(BackupError.NotSignedIn)
        assertThat(backup.isSignedIn()).isFalse()
        assertThat(store.login).isNull()
    }

    @Test
    fun `signed out upload fails without network`() = runTest {
        assertThat(backup.upload(docs).exceptionOrNull()).isEqualTo(BackupError.NotSignedIn)
        assertThat(server.requestCount).isEqualTo(0)
    }
}

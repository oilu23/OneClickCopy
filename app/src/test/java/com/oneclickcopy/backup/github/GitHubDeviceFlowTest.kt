package com.oneclickcopy.backup.github

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class GitHubDeviceFlowTest {

    private val server = MockWebServer()
    private var clock = 0L
    private val sleeps = mutableListOf<Long>()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    private fun flow(clientId: String = "client-123") = GitHubDeviceFlow(
        http = OkHttpClient(),
        clientId = clientId,
        baseUrl = server.url("").toString().trimEnd('/'),
        sleep = { sleeps += it; clock += it },
        now = { clock },
    )

    private fun json(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    private val code = DeviceCode("dev", "ABCD-1234", "https://github.com/login/device", 900, 5)

    @Test
    fun `requestCode asks for gist scope and parses the code`() {
        server.enqueue(
            json(
                """{"device_code":"dev","user_code":"WDJB-MJHT","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}"""
            )
        )

        val result = flow().requestCode()

        assertThat(result).isEqualTo(DeviceCode("dev", "WDJB-MJHT", "https://github.com/login/device", 900, 5))
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/login/device/code")
        assertThat(request.getHeader("Accept")).isEqualTo("application/json")
        assertThat(request.body.readUtf8()).isEqualTo("client_id=client-123&scope=gist")
    }

    @Test
    fun `placeholder client id fails without touching the network`() {
        assertThrows(DeviceFlowError.Misconfigured::class.java) {
            flow(clientId = "REPLACE_WITH_GITHUB_CLIENT_ID").requestCode()
        }
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `device flow disabled on the OAuth App is reported`() {
        server.enqueue(json("""{"error":"device_flow_disabled","error_description":"Device Flow must be explicitly enabled for this App"}"""))

        val error = assertThrows(DeviceFlowError.Misconfigured::class.java) { flow().requestCode() }
        assertThat(error).hasMessageThat().contains("Device Flow must be explicitly enabled")
    }

    @Test
    fun `awaitToken polls through pending and slow_down`() = runTest {
        server.enqueue(json("""{"error":"authorization_pending"}"""))
        server.enqueue(json("""{"error":"slow_down","interval":10}"""))
        server.enqueue(json("""{"access_token":"gho_token","token_type":"bearer","scope":"gist"}"""))

        val token = flow().awaitToken(code)

        assertThat(token).isEqualTo("gho_token")
        assertThat(sleeps).containsExactly(5_000L, 5_000L, 10_000L).inOrder()
        val poll = server.takeRequest()
        assertThat(poll.path).isEqualTo("/login/oauth/access_token")
        assertThat(poll.body.readUtf8()).contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code")
    }

    @Test
    fun `denial on GitHub ends the wait`() = runTest {
        server.enqueue(json("""{"error":"access_denied"}"""))

        val error = runCatching { flow().awaitToken(code) }.exceptionOrNull()
        assertThat(error).isEqualTo(DeviceFlowError.Denied)
    }

    @Test
    fun `stops once the code has expired`() = runTest {
        repeat(3) { server.enqueue(json("""{"error":"authorization_pending"}""")) }

        val shortLived = code.copy(expiresInSeconds = 12)
        val error = runCatching { flow().awaitToken(shortLived) }.exceptionOrNull()

        assertThat(error).isEqualTo(DeviceFlowError.Expired)
        // Polled at 5s and 10s; the 15s wake-up is past the 12s deadline.
        assertThat(server.requestCount).isEqualTo(2)
    }
}

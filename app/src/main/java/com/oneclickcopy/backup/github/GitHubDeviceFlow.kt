package com.oneclickcopy.backup.github

import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/** A pending device-flow sign-in: show [userCode] and send the user to [verificationUri]. */
data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int,
)

/** Ways a device-flow sign-in can end without a token. */
sealed class DeviceFlowError(message: String) : Exception(message) {
    /** The code expired before the user approved it. */
    data object Expired : DeviceFlowError("The sign-in code expired. Try again.")

    /** The user pressed Cancel on GitHub. */
    data object Denied : DeviceFlowError("Sign-in was cancelled on GitHub.")

    /** The OAuth App is missing, misconfigured, or has device flow disabled. */
    class Misconfigured(detail: String) : DeviceFlowError(detail)
}

/**
 * GitHub's OAuth device flow (RFC 8628).
 *
 * Chosen over the browser redirect flow because it needs only the public
 * client ID: no client secret ships in the APK, no backend is required, and
 * nothing is tied to the package name or signing key, so debug and release
 * builds sign in identically.
 *
 * Network calls block; callers run them off the main thread.
 */
class GitHubDeviceFlow(
    private val http: OkHttpClient,
    private val clientId: String,
    private val baseUrl: String = "https://github.com",
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun requestCode(): DeviceCode {
        if (clientId.isBlank() || clientId.startsWith(PLACEHOLDER_PREFIX)) {
            throw DeviceFlowError.Misconfigured(
                "GitHub client ID is not set. Add it to github_client_id in config.xml."
            )
        }
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", SCOPE)
            .build()
        val raw = post("$baseUrl/login/device/code", body)
        val response = json.decodeFromString(CodeResponse.serializer(), raw)
        if (response.deviceCode == null || response.userCode == null) {
            throw DeviceFlowError.Misconfigured(
                response.errorDescription ?: response.error ?: "GitHub returned no device code"
            )
        }
        return DeviceCode(
            deviceCode = response.deviceCode,
            userCode = response.userCode,
            verificationUri = response.verificationUri ?: "$baseUrl/login/device",
            expiresInSeconds = response.expiresIn ?: DEFAULT_EXPIRY_SECONDS,
            intervalSeconds = response.interval ?: MIN_INTERVAL_SECONDS,
        )
    }

    /**
     * Polls until the user approves [code] on GitHub, then returns the access
     * token. Honors GitHub's `slow_down` back-off; cancel the coroutine to stop.
     */
    suspend fun awaitToken(code: DeviceCode): String {
        var intervalSeconds = code.intervalSeconds.coerceAtLeast(MIN_INTERVAL_SECONDS)
        val deadline = now() + code.expiresInSeconds * 1000L
        while (true) {
            sleep(intervalSeconds * 1000L)
            if (now() >= deadline) throw DeviceFlowError.Expired

            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("device_code", code.deviceCode)
                .add("grant_type", GRANT_TYPE)
                .build()
            val response = json.decodeFromString(
                TokenResponse.serializer(),
                post("$baseUrl/login/oauth/access_token", body),
            )
            response.accessToken?.let { return it }
            when (response.error) {
                "authorization_pending" -> Unit
                // GitHub adds 5s each time; it also sends the new interval.
                "slow_down" -> intervalSeconds = response.interval ?: (intervalSeconds + 5)
                "expired_token" -> throw DeviceFlowError.Expired
                "access_denied" -> throw DeviceFlowError.Denied
                else -> throw DeviceFlowError.Misconfigured(
                    response.errorDescription ?: response.error ?: "Unexpected GitHub response"
                )
            }
        }
    }

    private fun post(url: String, body: FormBody): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw GitHubHttpException(response.code, text)
            }
            return text
        }
    }

    @Serializable
    private data class CodeResponse(
        @SerialName("device_code") val deviceCode: String? = null,
        @SerialName("user_code") val userCode: String? = null,
        @SerialName("verification_uri") val verificationUri: String? = null,
        @SerialName("expires_in") val expiresIn: Int? = null,
        val interval: Int? = null,
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null,
    )

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
        val interval: Int? = null,
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null,
    )

    companion object {
        /** Only gists: the token cannot read or write repositories. */
        const val SCOPE = "gist"
        const val PLACEHOLDER_PREFIX = "REPLACE_"
        private const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
        private const val MIN_INTERVAL_SECONDS = 5
        private const val DEFAULT_EXPIRY_SECONDS = 900
    }
}

/** A non-2xx response from GitHub. */
class GitHubHttpException(val code: Int, body: String) :
    Exception("GitHub returned HTTP $code${if (body.isNotBlank()) ": ${body.take(200)}" else ""}")

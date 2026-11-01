package com.oneclickcopy.backup.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The handful of GitHub REST calls the backup needs.
 *
 * Calls block; callers run them off the main thread. Non-2xx responses throw
 * [GitHubHttpException] so the caller can tell an expired token (401) from a
 * deleted gist (404).
 */
class GitHubGistClient(
    private val http: OkHttpClient,
    private val apiBaseUrl: String = "https://api.github.com",
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** The signed-in user's login, e.g. "octocat". */
    fun currentUser(token: String): String =
        json.decodeFromString(User.serializer(), call(token, "GET", "/user")).login

    /** Finds the gist holding [fileName], searching the user's gists newest first. */
    fun findGistWithFile(token: String, fileName: String): String? {
        for (page in 1..MAX_PAGES) {
            val gists = json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(Gist.serializer()),
                call(token, "GET", "/gists?per_page=$PAGE_SIZE&page=$page"),
            )
            gists.firstOrNull { fileName in it.files }?.let { return it.id }
            if (gists.size < PAGE_SIZE) return null
        }
        return null
    }

    /** Returns [fileName]'s content in gist [gistId], or null if the file is absent. */
    fun readFile(token: String, gistId: String, fileName: String): String? {
        val gist = json.decodeFromString(Gist.serializer(), call(token, "GET", "/gists/$gistId"))
        val file = gist.files[fileName] ?: return null
        // The API inlines at most ~1 MB per file; larger files must be fetched
        // from raw_url. Secret-gist raw URLs need no auth, so the token is not
        // sent to the raw content host.
        if (file.truncated && file.rawUrl != null) {
            val request = Request.Builder().url(file.rawUrl).get().build()
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw GitHubHttpException(response.code, text)
                return text
            }
        }
        return file.content
    }

    /** Creates a secret gist containing one file and returns its id. */
    fun createSecretGist(token: String, description: String, fileName: String, content: String): String {
        val body = buildJsonObject {
            put("description", description)
            put("public", false)
            putJsonObject("files") { putJsonObject(fileName) { put("content", content) } }
        }
        return json.decodeFromString(
            Gist.serializer(),
            call(token, "POST", "/gists", body.toString().toRequestBody(JSON)),
        ).id
    }

    /** Replaces [fileName]'s content in gist [gistId]. */
    fun updateFile(token: String, gistId: String, fileName: String, content: String) {
        val body = buildJsonObject {
            putJsonObject("files") { putJsonObject(fileName) { put("content", content) } }
        }
        call(token, "PATCH", "/gists/$gistId", body.toString().toRequestBody(JSON))
    }

    private fun call(token: String, method: String, path: String, body: RequestBody? = null): String {
        val request = Request.Builder()
            .url(apiBaseUrl + path)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .method(method, body)
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw GitHubHttpException(response.code, text)
            return text
        }
    }

    @Serializable
    private data class User(val login: String)

    @Serializable
    private data class Gist(
        val id: String,
        val files: Map<String, GistFile> = emptyMap(),
    )

    @Serializable
    private data class GistFile(
        val content: String? = null,
        val truncated: Boolean = false,
        @SerialName("raw_url") val rawUrl: String? = null,
    )

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val API_VERSION = "2022-11-28"
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 10
    }
}

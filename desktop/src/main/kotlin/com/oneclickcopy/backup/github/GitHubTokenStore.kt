package com.oneclickcopy.backup.github

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.Properties

/** Persists the GitHub session: the access token plus non-secret bookkeeping. */
interface GitHubTokenStore {
    var token: String?
    var login: String?
    /** Cached id of the backup gist, so uploads skip the search. */
    var gistId: String?

    fun clear() {
        token = null
        login = null
        gistId = null
    }
}

/**
 * Stores the session in a properties file in the app's data folder.
 *
 * On Windows the token is encrypted with DPAPI, which ties it to the current
 * Windows user account, the desktop equivalent of the Android Keystore: a copy
 * of the file is useless to anyone else. A token that fails to decrypt reads
 * as signed out. Other platforms (used only for development) store it as is.
 */
class FileGitHubTokenStore(private val file: Path) : GitHubTokenStore {

    private val props = Properties().apply {
        if (Files.exists(file)) Files.newInputStream(file).use { load(it) }
    }

    override var token: String?
        get() = props.getProperty(KEY_TOKEN)?.let { runCatching { unprotect(it) }.getOrNull() }
        set(value) = put(KEY_TOKEN, value?.let(::protect))

    override var login: String?
        get() = props.getProperty(KEY_LOGIN)
        set(value) = put(KEY_LOGIN, value)

    override var gistId: String?
        get() = props.getProperty(KEY_GIST_ID)
        set(value) = put(KEY_GIST_ID, value)

    @Synchronized
    private fun put(key: String, value: String?) {
        if (value == null) props.remove(key) else props.setProperty(key, value)
        Files.createDirectories(file.parent)
        Files.newOutputStream(file).use { props.store(it, null) }
    }

    private fun protect(plain: String): String {
        val bytes = plain.toByteArray(Charsets.UTF_8)
        return if (Platform.isWindows()) {
            DPAPI_PREFIX + Base64.getEncoder().encodeToString(Crypt32Util.cryptProtectData(bytes))
        } else {
            PLAIN_PREFIX + Base64.getEncoder().encodeToString(bytes)
        }
    }

    private fun unprotect(stored: String): String = when {
        stored.startsWith(DPAPI_PREFIX) -> String(
            Crypt32Util.cryptUnprotectData(
                Base64.getDecoder().decode(stored.removePrefix(DPAPI_PREFIX))
            ),
            Charsets.UTF_8,
        )
        stored.startsWith(PLAIN_PREFIX) -> String(
            Base64.getDecoder().decode(stored.removePrefix(PLAIN_PREFIX)),
            Charsets.UTF_8,
        )
        else -> error("Unknown token encoding")
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_LOGIN = "login"
        const val KEY_GIST_ID = "gist_id"
        const val DPAPI_PREFIX = "dpapi:"
        const val PLAIN_PREFIX = "plain:"
    }
}

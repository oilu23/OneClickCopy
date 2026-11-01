package com.oneclickcopy.backup.github

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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
 * Stores the token in SharedPreferences, encrypted with an AES-GCM key held in
 * the Android Keystore so it is unreadable outside this app install.
 *
 * The prefs file is excluded from Android backup (see backup_rules.xml): the
 * Keystore key never leaves the device, so a restored ciphertext could not be
 * decrypted anyway. A token that fails to decrypt reads as signed out.
 */
class KeystoreGitHubTokenStore(context: Context) : GitHubTokenStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override var token: String?
        get() = prefs.getString(KEY_TOKEN, null)?.let { runCatching { decrypt(it) }.getOrNull() }
        set(value) = prefs.edit {
            if (value == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, encrypt(value))
        }

    override var login: String?
        get() = prefs.getString(KEY_LOGIN, null)
        set(value) = prefs.edit { putString(KEY_LOGIN, value) }

    override var gistId: String?
        get() = prefs.getString(KEY_GIST_ID, null)
        set(value) = prefs.edit { putString(KEY_GIST_ID, value) }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val sealed = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV_BYTES))
        }
        return String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8)
    }

    private companion object {
        const val PREFS_NAME = "github_auth"
        const val KEY_TOKEN = "token"
        const val KEY_LOGIN = "login"
        const val KEY_GIST_ID = "gist_id"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "oneclickcopy_github_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}

package com.oneclickcopy.backup

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.oneclickcopy.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream

/**
 * Google Drive backup transport.
 *
 * The backup lives in Drive's `appDataFolder`, a private per-app area that does
 * not appear among the user's normal Drive files and does not count against a
 * developer quota. The requested scope (`drive.appdata`) is classified
 * non-sensitive by Google, so it requires no security assessment.
 */
open class DriveBackupManager(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val signInOptions: GoogleSignInOptions by lazy {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(DriveScopes.DRIVE_APPDATA),
                // Also request the legacy scope so a one-time migration can read
                // backups written by older versions, which stored the file in the
                // user's visible Drive under drive.file. Without this, upgrading
                // users would silently lose access to their existing backup.
                Scope(DriveScopes.DRIVE_FILE),
            )
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .build()
    }

    private val signInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, signInOptions)
    }

    open fun signedInAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    open fun isSignedIn(): Boolean = signedInAccount() != null

    open fun accountEmail(): String? = signedInAccount()?.email

    fun signInIntent(): Intent = signInClient.signInIntent

    suspend fun signOut() = withContext(ioDispatcher) {
        runCatching { signInClient.signOut() }
        Unit
    }

    open suspend fun upload(documents: List<BackupDocument>): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val drive = driveService() ?: throw BackupError.NotSignedIn
                val payload = BackupPayload(documents = documents)
                val body = ByteArrayContent.fromString(
                    MIME_TYPE,
                    json.encodeToString(BackupPayload.serializer(), payload),
                )

                val existingId = findFileId(drive, APP_DATA_FOLDER)
                if (existingId != null) {
                    drive.files().update(existingId, null, body).execute()
                } else {
                    val metadata = com.google.api.services.drive.model.File().apply {
                        name = BACKUP_FILE_NAME
                        mimeType = MIME_TYPE
                        parents = listOf(APP_DATA_FOLDER)
                    }
                    drive.files().create(metadata, body).execute()
                }
                Unit
            }
        }

    /**
     * Downloads the backup, falling back to the legacy pre-appdata location.
     *
     * Older releases wrote `oneclickcopy_backup.json` into the user's visible
     * Drive using the `drive.file` scope. Files created under that scope are not
     * visible to `appDataFolder` queries, so without this fallback an upgrading
     * user would appear to have no backup at all.
     */
    open suspend fun download(): Result<List<BackupDocument>> = withContext(ioDispatcher) {
        runCatching {
            val drive = driveService() ?: throw BackupError.NotSignedIn

            val fileId = findFileId(drive, APP_DATA_FOLDER)
                ?: findFileId(drive, DRIVE_SPACE)
                ?: throw BackupError.NoBackupFound

            val output = ByteArrayOutputStream()
            drive.files().get(fileId).executeMediaAndDownloadTo(output)

            val raw = output.toString(Charsets.UTF_8.name())
            decodePayload(raw)
        }
    }

    /**
     * Parses a backup document, accepting both the current versioned payload and
     * the original v1 format produced by the pre-refactor Gson implementation.
     */
    internal fun decodePayload(raw: String): List<BackupDocument> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(BackupPayload.serializer(), raw).documents
        }.getOrElse {
            // v1 payloads had the same top-level shape but documents carried no
            // uuid field. ignoreUnknownKeys handles extra fields; a bare array is
            // the other historical shape.
            runCatching {
                json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(BackupDocument.serializer()),
                    raw,
                )
            }.getOrElse { emptyList() }
        }
    }

    private fun driveService(): Drive? {
        val account = signedInAccount() ?: return null
        val credential = GoogleAccountCredential
            .usingOAuth2(context, listOf(DriveScopes.DRIVE_APPDATA, DriveScopes.DRIVE_FILE))
            .apply { selectedAccount = account.account }

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential,
        )
            .setApplicationName(context.getString(R.string.app_name))
            .build()
    }

    private fun findFileId(drive: Drive, space: String): String? =
        runCatching {
            drive.files().list()
                .setQ("name='$BACKUP_FILE_NAME' and trashed=false")
                .setSpaces(space)
                .setFields("files(id, name, modifiedTime)")
                .execute()
                .files
                ?.firstOrNull()
                ?.id
        }.getOrNull()

    companion object {
        private const val BACKUP_FILE_NAME = "oneclickcopy_backup.json"
        private const val MIME_TYPE = "application/json"
        private const val APP_DATA_FOLDER = "appDataFolder"
        private const val DRIVE_SPACE = "drive"
    }
}

/** Typed backup failures so callers can react without string matching. */
sealed class BackupError(message: String) : Exception(message) {
    data object NotSignedIn : BackupError("Not signed in")
    data object NoBackupFound : BackupError("No backup found")
}

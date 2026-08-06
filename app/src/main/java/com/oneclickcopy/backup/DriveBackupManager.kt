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
 * Changes from the original: the backup file lives in the Drive *appDataFolder*
 * rather than the user's visible root, JSON is handled by kotlinx.serialization
 * with unknown-key tolerance, and errors surface as typed [BackupError]s instead
 * of raw exception strings.
 */
class DriveBackupManager(
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
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .build()
    }

    private val signInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, signInOptions)
    }

    fun signedInAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    fun isSignedIn(): Boolean = signedInAccount() != null

    fun signInIntent(): Intent = signInClient.signInIntent

    suspend fun signOut() = withContext(ioDispatcher) {
        runCatching { signInClient.signOut() }
        Unit
    }

    suspend fun backup(documents: List<BackupDocument>): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val drive = driveService() ?: throw BackupError.NotSignedIn
                val payload = BackupPayload(documents = documents)
                val body = ByteArrayContent.fromString(
                    MIME_TYPE,
                    json.encodeToString(BackupPayload.serializer(), payload),
                )

                val existingId = findBackupFileId(drive)
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

    suspend fun restore(): Result<List<BackupDocument>> = withContext(ioDispatcher) {
        runCatching {
            val drive = driveService() ?: throw BackupError.NotSignedIn
            val fileId = findBackupFileId(drive) ?: throw BackupError.NoBackupFound

            val output = ByteArrayOutputStream()
            drive.files().get(fileId).executeMediaAndDownloadTo(output)

            val payload = json.decodeFromString(
                BackupPayload.serializer(),
                output.toString(Charsets.UTF_8.name()),
            )
            payload.documents
        }
    }

    private fun driveService(): Drive? {
        val account = signedInAccount() ?: return null
        val credential = GoogleAccountCredential
            .usingOAuth2(context, listOf(DriveScopes.DRIVE_APPDATA))
            .apply { selectedAccount = account.account }

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential,
        )
            .setApplicationName(context.getString(R.string.app_name))
            .build()
    }

    private fun findBackupFileId(drive: Drive): String? =
        drive.files().list()
            .setQ("name='$BACKUP_FILE_NAME' and trashed=false")
            .setSpaces(APP_DATA_FOLDER)
            .setFields("files(id, name)")
            .execute()
            .files
            ?.firstOrNull()
            ?.id

    companion object {
        private const val BACKUP_FILE_NAME = "oneclickcopy_backup.json"
        private const val MIME_TYPE = "application/json"
        private const val APP_DATA_FOLDER = "appDataFolder"
    }
}

/** Typed backup failures so the UI can react without string matching. */
sealed class BackupError(message: String) : Exception(message) {
    data object NotSignedIn : BackupError("Not signed in")
    data object NoBackupFound : BackupError("No backup found")
}

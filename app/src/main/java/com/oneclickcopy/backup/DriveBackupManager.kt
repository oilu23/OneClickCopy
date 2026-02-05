package com.oneclickcopy.backup

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
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
import com.google.gson.Gson
import com.oneclickcopy.data.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class BackupData(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val documents: List<Document>
)

class DriveBackupManager(private val context: Context) {
    
    companion object {
        private const val WEB_CLIENT_ID = "611391687410-eoru9cihkk7n67lgba0m3jnqr5gfsrti.apps.googleusercontent.com"
        private const val BACKUP_FILE_NAME = "oneclickcopy_backup.json"
        private const val BACKUP_MIME_TYPE = "application/json"
    }
    
    private val gson = Gson()
    
    private val signInOptions: GoogleSignInOptions by lazy {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .requestIdToken(WEB_CLIENT_ID)
            .build()
    }
    
    val googleSignInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, signInOptions)
    }
    
    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }
    
    fun isSignedIn(): Boolean {
        return getSignedInAccount() != null
    }
    
    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }
    
    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            googleSignInClient.signOut()
        }
    }
    
    private fun getDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE)
        ).apply {
            selectedAccount = account.account
        }
        
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("OneClickCopy")
            .build()
    }
    
    suspend fun backup(documents: List<Document>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val account = getSignedInAccount() ?: return@withContext Result.failure(Exception("Not signed in"))
            val driveService = getDriveService(account)
            
            val backupData = BackupData(documents = documents)
            val jsonContent = gson.toJson(backupData)
            
            // Check if backup file already exists
            val existingFileId = findBackupFile(driveService)
            
            if (existingFileId != null) {
                // Update existing file
                val content = ByteArrayContent.fromString(BACKUP_MIME_TYPE, jsonContent)
                driveService.files().update(existingFileId, null, content).execute()
            } else {
                // Create new file
                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = BACKUP_FILE_NAME
                    mimeType = BACKUP_MIME_TYPE
                }
                val content = ByteArrayContent.fromString(BACKUP_MIME_TYPE, jsonContent)
                driveService.files().create(fileMetadata, content).execute()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun restore(): Result<List<Document>> = withContext(Dispatchers.IO) {
        try {
            val account = getSignedInAccount() ?: return@withContext Result.failure(Exception("Not signed in"))
            val driveService = getDriveService(account)
            
            val fileId = findBackupFile(driveService)
                ?: return@withContext Result.failure(Exception("No backup found"))
            
            val outputStream = ByteArrayOutputStream()
            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            
            val jsonContent = outputStream.toString("UTF-8")
            val backupData = gson.fromJson(jsonContent, BackupData::class.java)
            
            Result.success(backupData.documents)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun findBackupFile(driveService: Drive): String? {
        val result = driveService.files().list()
            .setQ("name='$BACKUP_FILE_NAME' and trashed=false")
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()
        
        return result.files?.firstOrNull()?.id
    }
}

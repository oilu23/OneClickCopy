package com.oneclickcopy.backup

import android.content.Context
import android.content.SharedPreferences
import com.oneclickcopy.data.Document
import kotlinx.coroutines.*

class AutoBackupManager(
    private val context: Context,
    private val driveBackupManager: DriveBackupManager,
    private val onBackupFailed: ((String) -> Unit)? = null,
    private val onRestoreFailed: ((String) -> Unit)? = null,
    private val onRestoreSuccess: ((List<Document>) -> Unit)? = null
) {
    companion object {
        private const val PREFS_NAME = "auto_backup_prefs"
        private const val KEY_LAST_BACKUP_TIME = "last_backup_time"
        private const val KEY_HAS_RESTORED = "has_restored_once"
        private const val COOLDOWN_MS = 60_000L // 1 minute
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var pendingBackupJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var lastBackupTime: Long
        get() = prefs.getLong(KEY_LAST_BACKUP_TIME, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_BACKUP_TIME, value).apply()
    
    private var hasRestoredOnce: Boolean
        get() = prefs.getBoolean(KEY_HAS_RESTORED, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_RESTORED, value).apply()
    
    /**
     * Request an auto-backup. Will either:
     * - Run immediately if cooldown has passed
     * - Schedule for after cooldown (replacing any existing scheduled backup)
     */
    fun requestBackup(documents: List<Document>) {
        // Only backup if signed in
        if (!driveBackupManager.isSignedIn()) {
            return
        }
        
        // Cancel any pending backup (new request replaces old)
        pendingBackupJob?.cancel()
        
        val now = System.currentTimeMillis()
        val timeSinceLastBackup = now - lastBackupTime
        
        if (timeSinceLastBackup >= COOLDOWN_MS) {
            // Cooldown passed, backup immediately
            performBackup(documents)
        } else {
            // Schedule backup for when cooldown ends
            val delayMs = COOLDOWN_MS - timeSinceLastBackup
            pendingBackupJob = scope.launch {
                delay(delayMs)
                performBackup(documents)
            }
        }
    }
    
    private fun performBackup(documents: List<Document>) {
        scope.launch {
            val result = driveBackupManager.backup(documents)
            if (result.isSuccess) {
                lastBackupTime = System.currentTimeMillis()
                // Silent on success
            } else {
                // Alert on failure
                val errorMessage = result.exceptionOrNull()?.message ?: "Unknown error"
                withContext(Dispatchers.Main) {
                    onBackupFailed?.invoke(errorMessage)
                }
            }
        }
    }
    
    /**
     * Auto-restore on first login only.
     * Call this after successful Google Sign-In.
     */
    fun tryAutoRestore() {
        // Only restore if signed in and hasn't restored before
        if (!driveBackupManager.isSignedIn() || hasRestoredOnce) {
            return
        }
        
        scope.launch {
            val result = driveBackupManager.restore()
            
            // Mark as restored regardless of outcome (one-time only)
            hasRestoredOnce = true
            
            result.fold(
                onSuccess = { documents ->
                    if (documents.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            onRestoreSuccess?.invoke(documents)
                        }
                    }
                    // Silent on success (including empty backup)
                },
                onFailure = { e ->
                    // Silent if no backup found
                    val message = e.message ?: ""
                    if (!message.contains("No backup found", ignoreCase = true)) {
                        withContext(Dispatchers.Main) {
                            onRestoreFailed?.invoke(message)
                        }
                    }
                }
            )
        }
    }
    
    fun cleanup() {
        pendingBackupJob?.cancel()
        scope.cancel()
    }
}

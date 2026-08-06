package com.oneclickcopy

import android.content.Context
import com.oneclickcopy.backup.DriveBackupManager
import com.oneclickcopy.data.AppDatabase
import com.oneclickcopy.data.DocumentRepository
import com.oneclickcopy.data.DocumentTransfer
import com.oneclickcopy.sync.SyncManager
import com.oneclickcopy.sync.SyncPreferences
import kotlinx.coroutines.Dispatchers

/**
 * Minimal manual dependency container.
 *
 * Deliberately not Hilt: the graph is a handful of objects, and manual
 * construction keeps the build free of DI annotation processing while remaining
 * trivially swappable in tests.
 */
interface AppContainer {
    val documentRepository: DocumentRepository
    val driveBackupManager: DriveBackupManager
    val syncManager: SyncManager
    val documentTransfer: DocumentTransfer
}

class DefaultAppContainer(context: Context) : AppContainer {

    // Store the application context explicitly. These objects outlive any single
    // screen, so holding an Activity here would leak it across rotations.
    private val context: Context = context.applicationContext

    private val database by lazy { AppDatabase.get(context) }

    override val documentRepository: DocumentRepository by lazy {
        DocumentRepository(
            dao = database.documentDao(),
            ioDispatcher = Dispatchers.IO,
        ).also { repository ->
            // Every local write requests a sync. The repository stays unaware of
            // Drive; it just announces that something changed.
            repository.onChanged = { syncManager.requestSync() }
        }
    }

    override val driveBackupManager: DriveBackupManager by lazy {
        DriveBackupManager(context)
    }

    override val syncManager: SyncManager by lazy {
        SyncManager(
            context = context,
            repository = documentRepository,
            driveBackupManager = driveBackupManager,
            preferences = SyncPreferences(context),
        )
    }

    override val documentTransfer: DocumentTransfer by lazy {
        DocumentTransfer(context, documentRepository)
    }
}

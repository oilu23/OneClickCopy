package com.oneclickcopy.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.util.UUID

/**
 * Carries documents over from the Room database used up to version 2.2.0.
 *
 * Runs once, before the document store opens: if the JSON file does not exist
 * yet but the old database does, every row is copied into a new JSON file
 * with its id, timestamps, checkmarks and uuid intact. Uses the platform
 * SQLite API, so Room is no longer needed to read it.
 *
 * Safe by construction:
 *  - The JSON file is written atomically, and its existence is what marks the
 *    import as done. If the app dies mid-import, the next launch retries.
 *  - The old database files (with their -wal/-shm) are copied byte for byte
 *    to legacy-room-backup/ before anything opens them, and the database is
 *    never deleted. Either copy can redo the import after deleting
 *    documents.json.
 *  - Databases still on schema v1 or v2 (an app updated straight from an old
 *    release) are read too; missing columns get the defaults the Room
 *    migrations used to write.
 */
object LegacyDatabaseImport {

    const val DATABASE_NAME = "oneclickcopy_database"
    const val BACKUP_DIR = "legacy-room-backup"
    private const val TAG = "LegacyDatabaseImport"

    /**
     * Imports into [target] if needed. Returns the number of documents
     * imported, or null when there was nothing to do.
     */
    fun importIfNeeded(context: Context, target: File): Int? {
        if (target.exists()) return null
        val database = context.getDatabasePath(DATABASE_NAME)
        if (!database.exists()) return null

        return try {
            backUp(database, context.filesDir.resolve(BACKUP_DIR))
            val documents = SQLiteDatabase.openDatabase(
                database.path,
                null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING,
            ).use(::readDocuments)
            JsonFileDocumentDao.seed(target.toPath(), documents)
            Log.i(TAG, "Imported ${documents.size} documents from the Room database")
            documents.size
        } catch (e: Exception) {
            // Leave both files as they are: the next launch tries again, and the
            // old database still holds the data.
            Log.e(TAG, "Could not import the Room database", e)
            null
        }
    }

    /** Copies the database and its -wal/-shm companions, once. */
    private fun backUp(database: File, backupDir: File) {
        if (backupDir.exists()) return
        val staging = File(backupDir.path + ".tmp").apply { deleteRecursively(); mkdirs() }
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            val file = File(database.path + suffix)
            if (file.exists()) file.copyTo(File(staging, file.name))
        }
        check(staging.renameTo(backupDir)) { "Could not create $backupDir" }
    }

    /**
     * Room kept the database in WAL mode, so recent writes may still sit in the
     * -wal file. The database is opened in WAL mode (read-write, as WAL
     * requires), the way Room opened it, so those writes are read in place.
     * No rows are modified; on close SQLite folds the log into the main file,
     * as it did whenever Room closed it.
     */
    internal fun readDocuments(db: SQLiteDatabase): List<DocumentEntity> {
        val hasTable = db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'documents'",
            null,
        ).use { it.moveToFirst() }
        if (!hasTable) return emptyList()

        return db.rawQuery("SELECT * FROM documents ORDER BY id", null).use { cursor ->
            val documents = ArrayList<DocumentEntity>(cursor.count)
            while (cursor.moveToNext()) {
                documents += DocumentEntity(
                    id = cursor.long("id") ?: continue,
                    title = cursor.string("title").orEmpty(),
                    content = cursor.string("content").orEmpty(),
                    copiedItems = cursor.string("copiedItems").orEmpty(),
                    createdAt = cursor.long("createdAt") ?: 0L,
                    updatedAt = cursor.long("updatedAt") ?: 0L,
                    // Schema v1/v2 had no uuid. The v3 migration assigned a random
                    // one; do the same so these documents dedupe on restore.
                    uuid = cursor.string("uuid")?.takeIf { it.isNotEmpty() }
                        ?: UUID.randomUUID().toString(),
                )
            }
            documents
        }
    }

    private fun Cursor.string(column: String): String? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)

    private fun Cursor.long(column: String): Long? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)
}

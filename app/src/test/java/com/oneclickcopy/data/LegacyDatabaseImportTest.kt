package com.oneclickcopy.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The one-time move from the Room database to documents.json.
 *
 * A failed import would cost a user their whole library, so each test builds a
 * real SQLite file in one of the three schemas Room ever wrote, imports it, and
 * checks every field arrived. Replaces the old Room MigrationTest: databases
 * still on v1 or v2 are now imported directly instead of migrated.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LegacyDatabaseImportTest {

    private lateinit var context: Context
    private lateinit var target: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LegacyDatabaseImport.DATABASE_NAME)
        target = context.filesDir.resolve("documents.json").also { it.delete() }
        context.filesDir.resolve(LegacyDatabaseImport.BACKUP_DIR).deleteRecursively()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(LegacyDatabaseImport.DATABASE_NAME)
        target.delete()
    }

    private fun legacyDatabase(createSql: String, vararg rows: ContentValues) {
        val path = context.getDatabasePath(LegacyDatabaseImport.DATABASE_NAME).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL(createSql)
            rows.forEach { db.insertOrThrow("documents", null, it) }
        }
    }

    private fun row(vararg pairs: Pair<String, Any>) = ContentValues().apply {
        pairs.forEach { (key, value) ->
            when (value) {
                is String -> put(key, value)
                is Long -> put(key, value)
                is Int -> put(key, value)
            }
        }
    }

    private fun imported(): List<DocumentEntity> = runBlocking {
        JsonFileDocumentDao(target.toPath()).getAllOnce().sortedBy { it.id }
    }

    @Test
    fun `imports a current v3 database field for field`() {
        legacyDatabase(
            CREATE_V3,
            row("id" to 3L, "title" to "Replies", "content" to "hi\nbye", "copiedItems" to "[\"0:hi\"]",
                "createdAt" to 100L, "updatedAt" to 200L, "uuid" to "uuid-a"),
            row("id" to 7L, "title" to "", "content" to "x", "copiedItems" to "",
                "createdAt" to 300L, "updatedAt" to 400L, "uuid" to "uuid-b"),
        )

        assertThat(LegacyDatabaseImport.importIfNeeded(context, target)).isEqualTo(2)

        assertThat(imported()).containsExactly(
            DocumentEntity(3, "Replies", "hi\nbye", "[\"0:hi\"]", 100, 200, "uuid-a"),
            DocumentEntity(7, "", "x", "", 300, 400, "uuid-b"),
        ).inOrder()
    }

    @Test
    fun `new documents are numbered after the imported ones`() = runBlocking<Unit> {
        legacyDatabase(CREATE_V3, row("id" to 41L, "title" to "a", "content" to "", "copiedItems" to "",
            "createdAt" to 1L, "updatedAt" to 1L, "uuid" to "u"))
        LegacyDatabaseImport.importIfNeeded(context, target)

        val repository = DocumentRepository(JsonFileDocumentDao(target.toPath()), kotlinx.coroutines.Dispatchers.Unconfined)
        assertThat(repository.createDocument()).isEqualTo(42)
    }

    @Test
    fun `a v1 database gets empty checkmarks and a unique uuid per document`() {
        legacyDatabase(
            CREATE_V1,
            row("title" to "Old", "content" to "a", "createdAt" to 1L, "updatedAt" to 2L),
            row("title" to "Older", "content" to "b", "createdAt" to 3L, "updatedAt" to 4L),
        )

        LegacyDatabaseImport.importIfNeeded(context, target)

        val documents = imported()
        assertThat(documents.map { it.title }).containsExactly("Old", "Older").inOrder()
        assertThat(documents.map { it.copiedItems }).containsExactly("", "")
        assertThat(documents.map { it.uuid }.toSet()).hasSize(2)
        assertThat(documents.all { it.uuid.isNotBlank() }).isTrue()
    }

    @Test
    fun `a v2 database keeps its checkmarks`() {
        legacyDatabase(CREATE_V2, row("title" to "T", "content" to "a\nb", "copiedItems" to "[\"0:b\"]",
            "createdAt" to 1L, "updatedAt" to 2L))

        LegacyDatabaseImport.importIfNeeded(context, target)

        assertThat(imported().single().copiedItems).isEqualTo("[\"0:b\"]")
    }

    @Test
    fun `v3 rows left without a uuid get one`() {
        legacyDatabase(CREATE_V3, row("title" to "T", "content" to "", "copiedItems" to "",
            "createdAt" to 1L, "updatedAt" to 2L, "uuid" to ""))

        LegacyDatabaseImport.importIfNeeded(context, target)

        assertThat(imported().single().uuid).isNotEmpty()
    }

    @Test
    fun `does nothing on a fresh install`() {
        assertThat(LegacyDatabaseImport.importIfNeeded(context, target)).isNull()
        assertThat(target.exists()).isFalse()
    }

    @Test
    fun `runs only once and never overwrites the document file`() {
        legacyDatabase(CREATE_V3, row("title" to "first", "content" to "", "copiedItems" to "",
            "createdAt" to 1L, "updatedAt" to 1L, "uuid" to "u1"))
        LegacyDatabaseImport.importIfNeeded(context, target)
        runBlocking { JsonFileDocumentDao(target.toPath()).let { it.delete(it.getAllOnce().single()) } }

        // The user deleted everything; the old database must not bring it back.
        assertThat(LegacyDatabaseImport.importIfNeeded(context, target)).isNull()
        assertThat(imported()).isEmpty()
    }

    @Test
    fun `keeps the old database and a byte-for-byte backup of it`() {
        legacyDatabase(CREATE_V3, row("title" to "T", "content" to "c", "copiedItems" to "",
            "createdAt" to 1L, "updatedAt" to 2L, "uuid" to "u"))
        val original = context.getDatabasePath(LegacyDatabaseImport.DATABASE_NAME)
        val before = original.readBytes()

        LegacyDatabaseImport.importIfNeeded(context, target)

        assertThat(original.exists()).isTrue()
        val backup = context.filesDir.resolve(LegacyDatabaseImport.BACKUP_DIR).resolve(original.name)
        assertThat(backup.readBytes()).isEqualTo(before)
    }

    @Test
    fun `reads rows that Room left in the write-ahead log`() {
        // Room keeps the database in WAL mode and its connection open, so recent
        // edits live only in the -wal file until a checkpoint.
        val path = context.getDatabasePath(LegacyDatabaseImport.DATABASE_NAME).also { it.parentFile?.mkdirs() }
        val room = SQLiteDatabase.openDatabase(
            path.path, null,
            SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING,
        )
        try {
            room.execSQL(CREATE_V3)
            room.insertOrThrow("documents", null, row("title" to "recent", "content" to "in wal",
                "copiedItems" to "", "createdAt" to 1L, "updatedAt" to 2L, "uuid" to "w"))

            LegacyDatabaseImport.importIfNeeded(context, target)
        } finally {
            room.close()
        }

        assertThat(imported().single().content).isEqualTo("in wal")
    }

    @Test
    fun `an unreadable database leaves nothing behind so the next launch retries`() {
        context.getDatabasePath(LegacyDatabaseImport.DATABASE_NAME).apply {
            parentFile?.mkdirs()
            writeText("this is not a sqlite file, but it is long enough to have a header")
        }

        assertThat(LegacyDatabaseImport.importIfNeeded(context, target)).isNull()
        assertThat(target.exists()).isFalse()
    }

    private companion object {
        // Exactly as Room created them (see the removed app/schemas exports).
        const val CREATE_V1 = """CREATE TABLE documents (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL,
            createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"""
        const val CREATE_V2 = """CREATE TABLE documents (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL,
            copiedItems TEXT NOT NULL DEFAULT '', createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"""
        const val CREATE_V3 = """CREATE TABLE documents (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL,
            copiedItems TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
            uuid TEXT NOT NULL DEFAULT '')"""
    }
}

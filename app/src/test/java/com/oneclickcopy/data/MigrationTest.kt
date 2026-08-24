package com.oneclickcopy.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room migrations executed against real SQLite files.
 *
 * A failed migration destroys a user's entire local library and, unlike a sync
 * bug, cannot be undone. Each test builds a database at the *old* schema, writes
 * rows, applies the migration, and asserts the data survived.
 *
 * Room's MigrationTestHelper is not used here: it resolves exported schemas
 * through the instrumentation asset manager, which local JVM tests do not
 * populate. Driving the migrations directly exercises the same SQL.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MigrationTest {

    private companion object {
        const val DB_NAME = "migration-test.db"

        const val CREATE_V1 = """
            CREATE TABLE IF NOT EXISTS documents (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """

        const val CREATE_V2 = """
            CREATE TABLE IF NOT EXISTS documents (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                copiedItems TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """
    }

    private lateinit var context: Context
    private var helper: SupportSQLiteOpenHelper? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        helper?.close()
        context.deleteDatabase(DB_NAME)
    }

    /** Opens a raw SQLite database seeded with [createSql]. */
    private fun openWith(createSql: String): SupportSQLiteDatabase {
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(createSql)
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int,
            ) = Unit
        }
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DB_NAME)
            .callback(callback)
            .build()

        return FrameworkSQLiteOpenHelperFactory()
            .create(configuration)
            .also { helper = it }
            .writableDatabase
    }

    private fun SupportSQLiteDatabase.columns(): List<String> =
        query("PRAGMA table_info(documents)").use { cursor ->
            val names = mutableListOf<String>()
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) names += cursor.getString(nameIndex)
            names
        }

    @Test
    fun `migrate 1 to 2 preserves documents and adds copiedItems`() {
        val db = openWith(CREATE_V1)
        db.execSQL(
            "INSERT INTO documents (title, content, createdAt, updatedAt) " +
                "VALUES ('Notes', 'alpha\nbeta', 100, 200)"
        )

        AppDatabase.MIGRATION_1_2.migrate(db)

        assertThat(db.columns()).contains("copiedItems")
        db.query("SELECT title, content, copiedItems FROM documents").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("Notes")
            assertThat(cursor.getString(1)).contains("alpha")
            // Must default to empty rather than null, or decoding would fail.
            assertThat(cursor.getString(2)).isEmpty()
        }
    }

    @Test
    fun `migrate 2 to 3 backfills a unique uuid for every existing row`() {
        val db = openWith(CREATE_V2)
        repeat(3) { index ->
            db.execSQL(
                "INSERT INTO documents (title, content, copiedItems, createdAt, updatedAt) " +
                    "VALUES ('Doc $index', 'body $index', '', 100, 200)"
            )
        }

        AppDatabase.MIGRATION_2_3.migrate(db)

        assertThat(db.columns()).contains("uuid")
        val uuids = mutableListOf<String>()
        db.query("SELECT uuid FROM documents").use { cursor ->
            while (cursor.moveToNext()) uuids += cursor.getString(0)
        }

        assertThat(uuids).hasSize(3)
        // Without a unique identity per row, cloud merge cannot match documents
        // and every restore would duplicate the library.
        assertThat(uuids.none { it.isEmpty() }).isTrue()
        assertThat(uuids.toSet()).hasSize(3)
    }

    @Test
    fun `migrating all the way from version 1 keeps user data intact`() {
        val db = openWith(CREATE_V1)
        db.execSQL(
            "INSERT INTO documents (title, content, createdAt, updatedAt) " +
                "VALUES ('Legacy', 'sudo apt update', 1000, 2000)"
        )

        AppDatabase.MIGRATION_1_2.migrate(db)
        AppDatabase.MIGRATION_2_3.migrate(db)

        db.query("SELECT title, content, copiedItems, uuid, createdAt FROM documents")
            .use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("Legacy")
                assertThat(cursor.getString(1)).isEqualTo("sudo apt update")
                assertThat(cursor.getString(2)).isEmpty()
                assertThat(cursor.getString(3)).isNotEmpty()
                // Original timestamps must not be rewritten by the migration.
                assertThat(cursor.getLong(4)).isEqualTo(1000)
            }
    }

    @Test
    fun `migration on an empty database succeeds`() {
        val db = openWith(CREATE_V1)

        AppDatabase.MIGRATION_1_2.migrate(db)
        AppDatabase.MIGRATION_2_3.migrate(db)

        db.query("SELECT COUNT(*) FROM documents").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(0)
        }
    }

    @Test
    fun `migration preserves a large library`() {
        val db = openWith(CREATE_V2)
        db.beginTransaction()
        try {
            repeat(500) { index ->
                db.execSQL(
                    "INSERT INTO documents (title, content, copiedItems, createdAt, updatedAt) " +
                        "VALUES ('Doc $index', 'body', '', 1, 2)"
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        AppDatabase.MIGRATION_2_3.migrate(db)

        val uuids = mutableSetOf<String>()
        db.query("SELECT uuid FROM documents").use { cursor ->
            while (cursor.moveToNext()) uuids += cursor.getString(0)
        }
        // Every row keeps a distinct identity even at scale.
        assertThat(uuids).hasSize(500)
    }
}

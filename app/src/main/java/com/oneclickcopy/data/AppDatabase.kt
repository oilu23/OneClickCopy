package com.oneclickcopy.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

@Database(
    entities = [DocumentEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao

    companion object {
        private const val DATABASE_NAME = "oneclickcopy_database"

        @Volatile
        private var instance: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE documents ADD COLUMN copiedItems TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * Adds the stable [DocumentEntity.uuid] identity column and backfills a
         * unique value for every existing row, so pre-existing documents also
         * participate in de-duplicated restore.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE documents ADD COLUMN uuid TEXT NOT NULL DEFAULT ''"
                )
                db.query("SELECT id FROM documents").use { cursor ->
                    val ids = ArrayList<Long>(cursor.count)
                    while (cursor.moveToNext()) {
                        ids += cursor.getLong(0)
                    }
                    ids.forEach { id ->
                        db.execSQL(
                            "UPDATE documents SET uuid = ? WHERE id = ?",
                            arrayOf(UUID.randomUUID().toString(), id),
                        )
                    }
                }
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}

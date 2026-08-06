package com.oneclickcopy.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    /** Reactive single-document read so the editor survives process death. */
    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeById(id: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): DocumentEntity?

    @Query("SELECT * FROM documents")
    suspend fun getAllOnce(): List<DocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: DocumentEntity): Long

    @Update
    suspend fun update(document: DocumentEntity)

    @Delete
    suspend fun delete(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Deletes documents that were auto-created but never given a title or body.
     * Prevents the "empty Untitled" rows the original app accumulated whenever a
     * user tapped + and immediately backed out.
     */
    @Query("DELETE FROM documents WHERE id = :id AND title = '' AND content = ''")
    suspend fun deleteIfEmpty(id: Long)
}

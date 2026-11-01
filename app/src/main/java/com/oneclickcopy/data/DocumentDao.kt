package com.oneclickcopy.data

import kotlinx.coroutines.flow.Flow

/** The persistence contract [DocumentRepository] is written against. */
interface DocumentDao {

    /** All documents, most recently updated first. */
    fun observeAll(): Flow<List<DocumentEntity>>

    suspend fun getById(id: Long): DocumentEntity?

    suspend fun getByUuid(uuid: String): DocumentEntity?

    suspend fun getAllOnce(): List<DocumentEntity>

    /** Inserts, or replaces a row with the same non-zero id. Returns the row id. */
    suspend fun insert(document: DocumentEntity): Long

    suspend fun update(document: DocumentEntity)

    suspend fun delete(document: DocumentEntity)

    /**
     * Deletes the document only if both its title and body are empty, so a
     * created-then-abandoned document does not linger as an empty "Untitled".
     */
    suspend fun deleteIfEmpty(id: Long)
}

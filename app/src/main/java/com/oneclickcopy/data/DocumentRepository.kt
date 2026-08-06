package com.oneclickcopy.data

import com.oneclickcopy.domain.CopiedStateCodec
import com.oneclickcopy.domain.SnippetKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Single source of truth for document persistence.
 *
 * All database access funnels through here rather than living inside composables,
 * so UI code never touches Room, every write happens on an IO dispatcher, and the
 * behaviour is testable with an in-memory database.
 */
class DocumentRepository(
    private val dao: DocumentDao,
    private val ioDispatcher: CoroutineDispatcher,
) {

    fun observeDocuments(): Flow<List<DocumentEntity>> = dao.observeAll()

    /** Filters documents by title or body, case-insensitively. */
    fun observeDocuments(query: String): Flow<List<DocumentEntity>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return observeDocuments()
        return dao.observeAll().map { documents ->
            documents.filter { document ->
                document.title.contains(trimmed, ignoreCase = true) ||
                    document.content.contains(trimmed, ignoreCase = true)
            }
        }
    }

    fun observeDocument(id: Long): Flow<DocumentEntity?> = dao.observeById(id)

    suspend fun getDocument(id: Long): DocumentEntity? =
        withContext(ioDispatcher) { dao.getById(id) }

    suspend fun createDocument(): Long = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        dao.insert(
            DocumentEntity(
                title = "",
                content = "",
                createdAt = now,
                updatedAt = now,
                uuid = UUID.randomUUID().toString(),
            )
        )
    }

    suspend fun saveDocument(
        id: Long,
        title: String,
        content: String,
        copiedKeys: Set<SnippetKey>,
    ) = withContext(ioDispatcher) {
        val existing = dao.getById(id) ?: return@withContext
        val updated = existing.copy(
            title = title,
            content = content,
            copiedItems = CopiedStateCodec.encode(copiedKeys),
            updatedAt = System.currentTimeMillis(),
            uuid = existing.uuid.ifEmpty { UUID.randomUUID().toString() },
        )
        // Skip the write entirely when nothing changed. The original app wrote on
        // every keystroke debounce tick even when the content was identical.
        if (updated.copyOf(updatedAt = existing.updatedAt) != existing) {
            dao.update(updated)
        }
    }

    suspend fun deleteDocument(document: DocumentEntity) =
        withContext(ioDispatcher) { dao.delete(document) }

    /** Re-inserts a deleted document, preserving its identity, for undo support. */
    suspend fun restoreDocument(document: DocumentEntity): Long =
        withContext(ioDispatcher) { dao.insert(document) }

    suspend fun discardIfEmpty(id: Long) =
        withContext(ioDispatcher) { dao.deleteIfEmpty(id) }

    /**
     * Merges backup documents into the local database.
     *
     * Matching is done on [DocumentEntity.uuid] and the newer [DocumentEntity.updatedAt]
     * wins, making restore idempotent — repeated restores no longer create endless
     * duplicate copies the way the original implementation did.
     */
    suspend fun mergeDocuments(incoming: List<DocumentEntity>): MergeResult =
        withContext(ioDispatcher) {
            var inserted = 0
            var updated = 0
            var skipped = 0

            incoming.forEach { candidate ->
                val uuid = candidate.uuid.ifEmpty { UUID.randomUUID().toString() }
                val existing = dao.getByUuid(uuid)
                when {
                    existing == null -> {
                        dao.insert(candidate.copy(id = 0, uuid = uuid))
                        inserted++
                    }
                    candidate.updatedAt > existing.updatedAt -> {
                        dao.update(candidate.copy(id = existing.id, uuid = uuid))
                        updated++
                    }
                    else -> skipped++
                }
            }
            MergeResult(inserted = inserted, updated = updated, skipped = skipped)
        }

    suspend fun getAllDocuments(): List<DocumentEntity> =
        withContext(ioDispatcher) { dao.getAllOnce() }

    private fun DocumentEntity.copyOf(updatedAt: Long) = copy(updatedAt = updatedAt)
}

data class MergeResult(
    val inserted: Int,
    val updated: Int,
    val skipped: Int,
) {
    val changed: Boolean get() = inserted > 0 || updated > 0
}

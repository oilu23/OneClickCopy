package com.oneclickcopy.backup

import com.oneclickcopy.data.DocumentEntity
import kotlinx.serialization.Serializable

/**
 * Wire format for the Drive backup file.
 *
 * Explicitly versioned and decoupled from the Room entity so a future schema
 * change cannot silently corrupt older backups.
 */
@Serializable
data class BackupPayload(
    val version: Int = CURRENT_VERSION,
    val timestamp: Long = System.currentTimeMillis(),
    val documents: List<BackupDocument> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 2
    }
}

@Serializable
data class BackupDocument(
    val uuid: String = "",
    val title: String = "",
    val content: String = "",
    val copiedItems: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

fun DocumentEntity.toBackup(): BackupDocument = BackupDocument(
    uuid = uuid,
    title = title,
    content = content,
    copiedItems = copiedItems,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun BackupDocument.toEntity(): DocumentEntity = DocumentEntity(
    id = 0,
    title = title,
    content = content,
    copiedItems = copiedItems,
    createdAt = if (createdAt > 0) createdAt else System.currentTimeMillis(),
    updatedAt = if (updatedAt > 0) updatedAt else System.currentTimeMillis(),
    uuid = uuid,
)

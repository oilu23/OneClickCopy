package com.oneclickcopy.backup

import com.oneclickcopy.data.DocumentEntity
import kotlinx.serialization.Serializable

/**
 * Wire format for the backup file (cloud backup and file export).
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

/** Reads and writes the backup file shared by every transport. */
object BackupCodec {

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(documents: List<BackupDocument>): String =
        json.encodeToString(BackupPayload.serializer(), BackupPayload(documents = documents))

    /**
     * Parses a backup, accepting the current versioned payload and the original
     * bare-array format. Blank input is an empty backup.
     */
    fun decode(raw: String): List<BackupDocument> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(BackupPayload.serializer(), raw).documents
        }.getOrElse {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(BackupDocument.serializer()),
                raw,
            )
        }
    }
}

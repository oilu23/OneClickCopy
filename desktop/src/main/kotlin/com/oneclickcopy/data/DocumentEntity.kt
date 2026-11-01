package com.oneclickcopy.data

import kotlinx.serialization.Serializable

/**
 * Desktop counterpart of the Android Room entity: same fields and defaults, no
 * Room annotations. Shared code (DocumentRepository, BackupPayload) compiles
 * against this class unchanged.
 */
@Serializable
data class DocumentEntity(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    /** Serialized set of copied snippet keys. See CopiedStateCodec. */
    val copiedItems: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Stable identity used to de-duplicate documents across backup/restore. */
    val uuid: String = "",
)

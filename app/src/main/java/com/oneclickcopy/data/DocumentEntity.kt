package com.oneclickcopy.data

import kotlinx.serialization.Serializable

/** One snippet list. Persisted by [JsonFileDocumentDao]; shared by both apps. */
@Serializable
data class DocumentEntity(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    /** Serialized set of copied snippet keys. See CopiedStateCodec. */
    val copiedItems: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /**
     * Stable identity used to de-duplicate documents across backup/restore
     * cycles. Row ids are device-local, so restoring previously duplicated every
     * document; matching on this UUID makes restore idempotent.
     */
    val uuid: String = "",
)

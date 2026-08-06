package com.oneclickcopy.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
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
    @ColumnInfo(defaultValue = "")
    val uuid: String = "",
)

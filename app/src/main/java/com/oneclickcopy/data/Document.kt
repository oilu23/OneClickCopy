package com.oneclickcopy.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val copiedItems: String = "", // JSON array of copied text items
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

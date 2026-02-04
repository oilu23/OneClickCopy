package com.oneclickcopy.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun getAllDocuments(): Flow<List<Document>>
    
    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): Document?
    
    @Insert
    suspend fun insertDocument(document: Document): Long
    
    @Update
    suspend fun updateDocument(document: Document)
    
    @Delete
    suspend fun deleteDocument(document: Document)
    
    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)
}

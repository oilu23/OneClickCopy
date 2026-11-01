package com.oneclickcopy.desktop

import com.oneclickcopy.backup.BackupDocument
import com.oneclickcopy.backup.BackupPayload
import com.oneclickcopy.backup.toBackup
import com.oneclickcopy.backup.toEntity
import com.oneclickcopy.data.DocumentRepository
import com.oneclickcopy.data.MergeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.nio.file.Files
import java.nio.file.Path

/**
 * Plain-file export and import, in the same JSON format as the Android app, so
 * a file exported on the phone imports on the PC and the other way round.
 */
class DocumentTransfer(private val repository: DocumentRepository) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    suspend fun exportTo(path: Path): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val documents = repository.getAllDocuments().map { it.toBackup() }
            Files.writeString(path, json.encodeToString(BackupPayload.serializer(), BackupPayload(documents = documents)))
            documents.size
        }
    }

    suspend fun importFrom(path: Path): Result<MergeResult> = withContext(Dispatchers.IO) {
        runCatching {
            val documents = parse(Files.readString(path))
            repository.mergeDocuments(documents.map { it.toEntity() })
        }
    }

    internal fun parse(raw: String): List<BackupDocument> {
        if (raw.isBlank()) return emptyList()
        val element = runCatching { json.parseToJsonElement(raw) }
            .getOrElse { throw IllegalArgumentException(NOT_A_BACKUP) }
        return when {
            element is JsonObject && element.containsKey("documents") ->
                json.decodeFromString(BackupPayload.serializer(), raw).documents
            element is JsonArray -> runCatching {
                json.decodeFromString(ListSerializer(BackupDocument.serializer()), raw)
            }.getOrElse { throw IllegalArgumentException(NOT_A_BACKUP) }
            else -> throw IllegalArgumentException(NOT_A_BACKUP)
        }
    }

    companion object {
        private const val NOT_A_BACKUP = "This file is not a OneClickCopy backup"
        fun defaultFileName(): String = "oneclickcopy-backup-${System.currentTimeMillis()}.json"
    }
}

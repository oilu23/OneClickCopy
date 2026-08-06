package com.oneclickcopy.data

import android.content.Context
import android.net.Uri
import com.oneclickcopy.backup.BackupDocument
import com.oneclickcopy.backup.BackupPayload
import com.oneclickcopy.backup.toBackup
import com.oneclickcopy.backup.toEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Plain-file export and import.
 *
 * Deliberately independent of Google sign-in: it is the escape hatch if a user
 * declines an account, OAuth breaks, or the app is ever unpublished. The format
 * is the same versioned JSON used for Drive backups, so the two are compatible.
 */
class DocumentTransfer(
    private val context: Context,
    private val repository: DocumentRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /** Serializes every document to the user-chosen [uri]. */
    suspend fun exportTo(uri: Uri): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            val documents = repository.getAllDocuments().map { it.toBackup() }
            val payload = BackupPayload(documents = documents)
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(
                    json.encodeToString(BackupPayload.serializer(), payload)
                        .toByteArray(Charsets.UTF_8)
                )
            } ?: error("Could not open file for writing")
            documents.size
        }
    }

    /** Reads documents from [uri] and merges them, newest-wins, by UUID. */
    suspend fun importFrom(uri: Uri): Result<MergeResult> = withContext(ioDispatcher) {
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            } ?: error("Could not open file for reading")

            val documents = parse(raw)
            repository.mergeDocuments(documents.map { it.toEntity() })
        }
    }

    /**
     * Accepts both the versioned payload and a bare document array.
     *
     * Rejects JSON that parses but carries no recognisable backup structure.
     * Without that check, importing an unrelated file reported "0 documents
     * imported" instead of an error, leaving the user unsure whether their
     * backup was empty or simply the wrong file.
     */
    internal fun parse(raw: String): List<BackupDocument> {
        if (raw.isBlank()) return emptyList()

        val element = runCatching {
            json.parseToJsonElement(raw)
        }.getOrElse { throw IllegalArgumentException(NOT_A_BACKUP) }

        return when {
            element is kotlinx.serialization.json.JsonObject &&
                element.containsKey("documents") ->
                json.decodeFromString(BackupPayload.serializer(), raw).documents

            element is kotlinx.serialization.json.JsonArray ->
                runCatching {
                    json.decodeFromString(
                        kotlinx.serialization.builtins.ListSerializer(BackupDocument.serializer()),
                        raw,
                    )
                }.getOrElse { throw IllegalArgumentException(NOT_A_BACKUP) }

            else -> throw IllegalArgumentException(NOT_A_BACKUP)
        }
    }

    companion object {
        const val MIME_TYPE = "application/json"
        private const val NOT_A_BACKUP = "This file is not a OneClickCopy backup"
        fun defaultFileName(): String =
            "oneclickcopy-backup-${System.currentTimeMillis()}.json"
    }
}

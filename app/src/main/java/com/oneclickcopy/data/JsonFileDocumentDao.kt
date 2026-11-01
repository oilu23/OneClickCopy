package com.oneclickcopy.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Stores every document in one JSON file. Used by both the Android and the
 * Windows app.
 *
 * A personal snippet library is small, so the whole set is held in memory and
 * the file is rewritten on each change. Writes go to a temp file that is then
 * moved over the original, so a crash mid-write never leaves a truncated file.
 * A file that cannot be parsed is kept aside as `.corrupt` rather than
 * overwritten, so the user's data can still be recovered by hand.
 */
class JsonFileDocumentDao(private val file: Path) : DocumentDao {

    @Serializable
    private data class Store(
        val nextId: Long = 1,
        val documents: List<DocumentEntity> = emptyList(),
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val mutex = Mutex()
    private val state = MutableStateFlow(load())

    override fun observeAll(): Flow<List<DocumentEntity>> =
        state.map { store -> store.documents.sortedByDescending { it.updatedAt } }

    override suspend fun getById(id: Long): DocumentEntity? =
        state.value.documents.firstOrNull { it.id == id }

    override suspend fun getByUuid(uuid: String): DocumentEntity? =
        state.value.documents.firstOrNull { it.uuid == uuid }

    override suspend fun getAllOnce(): List<DocumentEntity> = state.value.documents

    override suspend fun insert(document: DocumentEntity): Long = mutate { store ->
        if (document.id != 0L) {
            // REPLACE semantics, matching the Room DAO. Used by undo-delete.
            val others = store.documents.filterNot { it.id == document.id }
            store.copy(
                nextId = maxOf(store.nextId, document.id + 1),
                documents = others + document,
            ) to document.id
        } else {
            val id = store.nextId
            store.copy(
                nextId = id + 1,
                documents = store.documents + document.copy(id = id),
            ) to id
        }
    }

    override suspend fun update(document: DocumentEntity) = mutate { store ->
        store.copy(
            documents = store.documents.map { if (it.id == document.id) document else it },
        ) to Unit
    }

    override suspend fun delete(document: DocumentEntity) = mutate { store ->
        store.copy(documents = store.documents.filterNot { it.id == document.id }) to Unit
    }

    override suspend fun deleteIfEmpty(id: Long) = mutate { store ->
        store.copy(
            documents = store.documents.filterNot {
                it.id == id && it.title.isEmpty() && it.content.isEmpty()
            },
        ) to Unit
    }

    private suspend fun <T> mutate(block: (Store) -> Pair<Store, T>): T = mutex.withLock {
        val (updated, result) = block(state.value)
        if (updated != state.value) {
            write(updated)
            state.value = updated
        }
        result
    }

    private fun load(): Store {
        if (!Files.exists(file)) return Store()
        val raw = String(Files.readAllBytes(file), Charsets.UTF_8)
        if (raw.isBlank()) return Store()
        return runCatching { json.decodeFromString(Store.serializer(), raw) }
            .getOrElse {
                val aside = file.resolveSibling("${file.fileName}.corrupt-${System.currentTimeMillis()}")
                Files.move(file, aside)
                Store()
            }
    }

    private fun write(store: Store) = writeAtomically(file, json.encodeToString(Store.serializer(), store))

    companion object {
        private val seedJson = Json { encodeDefaults = true; prettyPrint = true }

        /**
         * Creates [file] holding [documents], keeping their ids. Used once, to
         * carry documents over from the old Room database on Android.
         */
        fun seed(file: Path, documents: List<DocumentEntity>) {
            val store = Store(
                nextId = (documents.maxOfOrNull { it.id } ?: 0) + 1,
                documents = documents,
            )
            writeAtomically(file, seedJson.encodeToString(Store.serializer(), store))
        }

        /** Writes to a temp file, then moves it over [file]: readers never see half a file. */
        private fun writeAtomically(file: Path, text: String) {
            file.parent?.let { Files.createDirectories(it) }
            val temp = file.resolveSibling("${file.fileName}.tmp")
            Files.write(temp, text.toByteArray(Charsets.UTF_8))
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

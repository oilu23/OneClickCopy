package com.oneclickcopy.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.oneclickcopy.domain.SnippetKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DocumentRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: DocumentRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        repository = DocumentRepository(
            dao = database.documentDao(),
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `createDocument assigns a non-empty uuid`() = runTest {
        val id = repository.createDocument()

        val document = repository.getDocument(id)
        assertThat(document).isNotNull()
        assertThat(document!!.uuid).isNotEmpty()
    }

    @Test
    fun `saveDocument persists title content and copied keys`() = runTest {
        val id = repository.createDocument()

        repository.saveDocument(
            id = id,
            title = "Greetings",
            content = "hello\nhello",
            copiedKeys = setOf(SnippetKey("hello", 1)),
        )

        val saved = repository.getDocument(id)!!
        assertThat(saved.title).isEqualTo("Greetings")
        assertThat(saved.content).isEqualTo("hello\nhello")
        assertThat(saved.copiedItems).contains("1:hello")
    }

    @Test
    fun `saveDocument on missing id is a no-op`() = runTest {
        repository.saveDocument(9_999L, "ghost", "body", emptySet())

        assertThat(repository.getDocument(9_999L)).isNull()
    }

    @Test
    fun `observeDocuments emits inserted documents`() = runTest {
        repository.createDocument()
        repository.createDocument()

        assertThat(repository.observeDocuments().first()).hasSize(2)
    }

    @Test
    fun `search filters by title and content case-insensitively`() = runTest {
        val first = repository.createDocument()
        repository.saveDocument(first, "Support macros", "thanks for writing", emptySet())
        val second = repository.createDocument()
        repository.saveDocument(second, "Shipping", "tracking number", emptySet())

        assertThat(repository.observeDocuments("SUPPORT").first()).hasSize(1)
        assertThat(repository.observeDocuments("tracking").first()).hasSize(1)
        assertThat(repository.observeDocuments("nothing").first()).isEmpty()
    }

    @Test
    fun `discardIfEmpty removes untouched document`() = runTest {
        val id = repository.createDocument()

        repository.discardIfEmpty(id)

        assertThat(repository.getDocument(id)).isNull()
    }

    @Test
    fun `discardIfEmpty keeps document with content`() = runTest {
        val id = repository.createDocument()
        repository.saveDocument(id, "", "some text", emptySet())

        repository.discardIfEmpty(id)

        assertThat(repository.getDocument(id)).isNotNull()
    }

    @Test
    fun `merge inserts documents that are not present locally`() = runTest {
        val incoming = listOf(
            DocumentEntity(title = "Remote", content = "body", uuid = "uuid-remote"),
        )

        val result = repository.mergeDocuments(incoming)

        assertThat(result.inserted).isEqualTo(1)
        assertThat(repository.getAllDocuments()).hasSize(1)
    }

    @Test
    fun `merge is idempotent across repeated restores`() = runTest {
        // The original app re-inserted every backup document on each restore,
        // duplicating the user's whole library. Merging on uuid must not.
        val incoming = listOf(
            DocumentEntity(
                title = "Remote",
                content = "body",
                uuid = "uuid-stable",
                updatedAt = 1_000L,
            ),
        )

        repository.mergeDocuments(incoming)
        val second = repository.mergeDocuments(incoming)

        assertThat(second.inserted).isEqualTo(0)
        assertThat(second.skipped).isEqualTo(1)
        assertThat(repository.getAllDocuments()).hasSize(1)
    }

    @Test
    fun `merge prefers the newer document`() = runTest {
        val base = DocumentEntity(
            title = "Old title",
            content = "old",
            uuid = "uuid-1",
            updatedAt = 1_000L,
        )
        repository.mergeDocuments(listOf(base))

        val result = repository.mergeDocuments(
            listOf(base.copy(title = "New title", content = "new", updatedAt = 2_000L))
        )

        assertThat(result.updated).isEqualTo(1)
        val stored = repository.getAllDocuments().single()
        assertThat(stored.title).isEqualTo("New title")
    }

    @Test
    fun `merge keeps local copy when it is newer`() = runTest {
        val base = DocumentEntity(
            title = "Local wins",
            content = "local",
            uuid = "uuid-2",
            updatedAt = 5_000L,
        )
        repository.mergeDocuments(listOf(base))

        repository.mergeDocuments(
            listOf(base.copy(title = "Stale remote", updatedAt = 1_000L))
        )

        assertThat(repository.getAllDocuments().single().title).isEqualTo("Local wins")
    }

    @Test
    fun `restoreDocument reinstates a deleted document for undo`() = runTest {
        val id = repository.createDocument()
        val document = repository.getDocument(id)!!
        repository.deleteDocument(document)
        assertThat(repository.getDocument(id)).isNull()

        repository.restoreDocument(document)

        assertThat(repository.getDocument(id)).isNotNull()
    }
}

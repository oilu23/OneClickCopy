package com.oneclickcopy.data

import org.junit.rules.TemporaryFolder
import org.junit.Rule
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DocumentTransferTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    @get:Rule val temp = TemporaryFolder()
    private lateinit var dao: JsonFileDocumentDao
    private lateinit var repository: DocumentRepository
    private lateinit var transfer: DocumentTransfer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        dao = JsonFileDocumentDao(temp.root.toPath().resolve("documents.json"))
        repository = DocumentRepository(dao, dispatcher)
        transfer = DocumentTransfer(context, repository, dispatcher)
    }


    @Test
    fun `export then import round trips documents`() = runTest(scheduler) {
        val id = repository.createDocument()
        repository.saveDocument(id, "Macros", "hello\nworld", emptySet())

        val file = File.createTempFile("backup", ".json")
        val uri = Uri.fromFile(file)

        val exported = transfer.exportTo(uri).getOrThrow()
        assertThat(exported).isEqualTo(1)

        // Simulate a fresh device by clearing local data.
        repository.getAllDocuments().forEach { repository.deleteDocument(it) }
        assertThat(repository.getAllDocuments()).isEmpty()

        val merge = transfer.importFrom(uri).getOrThrow()

        assertThat(merge.inserted).isEqualTo(1)
        val restored = repository.getAllDocuments().single()
        assertThat(restored.title).isEqualTo("Macros")
        assertThat(restored.content).isEqualTo("hello\nworld")
    }

    @Test
    fun `import is idempotent`() = runTest(scheduler) {
        val id = repository.createDocument()
        repository.saveDocument(id, "Once", "body", emptySet())
        val file = File.createTempFile("backup", ".json")
        val uri = Uri.fromFile(file)
        transfer.exportTo(uri).getOrThrow()

        transfer.importFrom(uri).getOrThrow()
        transfer.importFrom(uri).getOrThrow()

        // Re-importing the same file must not duplicate the library.
        assertThat(repository.getAllDocuments()).hasSize(1)
    }

    @Test
    fun `parse accepts a bare document array`() {
        val raw = """[{"uuid":"u","title":"Bare","content":"x",
                      "copiedItems":"","createdAt":1,"updatedAt":2}]"""

        val documents = transfer.parse(raw)

        assertThat(documents).hasSize(1)
        assertThat(documents.single().title).isEqualTo("Bare")
    }

    @Test
    fun `parse rejects a file that is not a backup`() {
        val error = runCatching { transfer.parse("""{"unrelated":"content"}""") }

        assertThat(error.isFailure).isTrue()
    }

    @Test
    fun `parse treats blank input as empty`() {
        assertThat(transfer.parse("")).isEmpty()
    }
}

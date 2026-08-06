package com.oneclickcopy.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that local writes actually notify the sync layer.
 *
 * This class exists because a previous refactor left the Drive backup code
 * present but unreachable — no caller, no UI entry point — and the test suite
 * still passed. Testing that the wiring fires, not merely that the components
 * compile, is what catches that class of regression.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DocumentRepositorySyncHookTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    private lateinit var database: AppDatabase
    private lateinit var repository: DocumentRepository
    private var changeCount = 0

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()

        changeCount = 0
        repository = DocumentRepository(database.documentDao(), dispatcher).apply {
            onChanged = { changeCount++ }
        }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `creating a document requests a sync`() = runTest(scheduler) {
        repository.createDocument()

        assertThat(changeCount).isEqualTo(1)
    }

    @Test
    fun `saving changed content requests a sync`() = runTest(scheduler) {
        val id = repository.createDocument()
        changeCount = 0

        repository.saveDocument(id, "Title", "body", emptySet())

        assertThat(changeCount).isEqualTo(1)
    }

    @Test
    fun `saving identical content does not request a sync`() = runTest(scheduler) {
        val id = repository.createDocument()
        repository.saveDocument(id, "Title", "body", emptySet())
        changeCount = 0

        repository.saveDocument(id, "Title", "body", emptySet())

        // No-op writes must not generate needless upload traffic.
        assertThat(changeCount).isEqualTo(0)
    }

    @Test
    fun `deleting a document requests a sync`() = runTest(scheduler) {
        val id = repository.createDocument()
        val document = repository.getDocument(id)!!
        changeCount = 0

        repository.deleteDocument(document)

        assertThat(changeCount).isEqualTo(1)
    }

    @Test
    fun `undo restore requests a sync`() = runTest(scheduler) {
        val id = repository.createDocument()
        val document = repository.getDocument(id)!!
        repository.deleteDocument(document)
        changeCount = 0

        repository.restoreDocument(document)

        assertThat(changeCount).isEqualTo(1)
    }

    @Test
    fun `repository works when no sync observer is attached`() = runTest(scheduler) {
        repository.onChanged = null

        val id = repository.createDocument()

        assertThat(repository.getDocument(id)).isNotNull()
    }
}

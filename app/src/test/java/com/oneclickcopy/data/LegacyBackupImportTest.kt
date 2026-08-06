package com.oneclickcopy.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.oneclickcopy.backup.toEntity
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
 * Import of a real v1 backup produced by the pre-refactor release.
 *
 * The fixture is an actual exported file (secrets scrubbed): 17 documents,
 * `version: 1`, and crucially **no `uuid` field**, because that column did not
 * exist yet. Restoring such a file is the single highest-risk path in the app —
 * it is what a user hits after losing their phone — so it is tested against real
 * data rather than hand-written samples.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LegacyBackupImportTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    private lateinit var database: AppDatabase
    private lateinit var repository: DocumentRepository
    private lateinit var transfer: DocumentTransfer

    private fun fixture(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("legacy_v1_backup.json"))
            .bufferedReader()
            .use { it.readText() }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
        repository = DocumentRepository(database.documentDao(), dispatcher)
        transfer = DocumentTransfer(context, repository, dispatcher)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `parses every document from a real v1 export`() {
        val documents = transfer.parse(fixture())

        assertThat(documents).hasSize(17)
        assertThat(documents.map { it.title }).contains("Ubuntu setup")
        // v1 files carry no uuid; the importer must cope rather than reject them.
        assertThat(documents.all { it.uuid.isEmpty() }).isTrue()
    }

    @Test
    fun `preserves content and copied state through import`() {
        val documents = transfer.parse(fixture())
        val tmux = documents.first { it.title.startsWith("Essential tmux") }

        assertThat(tmux.content).contains("tmux attach -t work")
        assertThat(tmux.copiedItems).contains("tmux attach")
        assertThat(tmux.createdAt).isGreaterThan(0L)
        assertThat(tmux.updatedAt).isGreaterThan(0L)
    }

    @Test
    fun `restores a full library into an empty database`() = runTest(scheduler) {
        val result = repository.mergeDocuments(
            transfer.parse(fixture()).map { it.toEntity() }
        )

        assertThat(result.inserted).isEqualTo(17)
        assertThat(repository.getAllDocuments()).hasSize(17)
    }

    @Test
    fun `importing the same legacy file twice does not duplicate documents`() =
        runTest(scheduler) {
            // Regression: legacy documents have no uuid, and assigning a fresh
            // random uuid per import made every restore look like new content,
            // silently doubling the user's library on a second import.
            val documents = transfer.parse(fixture()).map { it.toEntity() }

            repository.mergeDocuments(documents)
            val second = repository.mergeDocuments(documents)

            assertThat(repository.getAllDocuments()).hasSize(17)
            assertThat(second.inserted).isEqualTo(0)
        }

    @Test
    fun `legacy import assigns stable identity so later syncs match`() =
        runTest(scheduler) {
            repository.mergeDocuments(transfer.parse(fixture()).map { it.toEntity() })

            val stored = repository.getAllDocuments()

            // Every restored document must end up with a non-empty, unique uuid,
            // otherwise subsequent cloud merges cannot match them.
            assertThat(stored.none { it.uuid.isEmpty() }).isTrue()
            assertThat(stored.map { it.uuid }.toSet()).hasSize(17)
        }

    @Test
    fun `a document edited after restore wins over the older backup copy`() =
        runTest(scheduler) {
            val documents = transfer.parse(fixture()).map { it.toEntity() }
            repository.mergeDocuments(documents)

            val target = repository.getAllDocuments().first { it.title == "nep" }
            repository.saveDocument(target.id, "nep", "locally edited", emptySet())

            // Re-importing the older file must not clobber newer local edits.
            repository.mergeDocuments(documents)

            val after = repository.getAllDocuments().first { it.title == "nep" }
            assertThat(after.content).isEqualTo("locally edited")
            assertThat(repository.getAllDocuments()).hasSize(17)
        }

    @Test
    fun `the same legacy library from Drive and from a file converges`() =
        runTest(scheduler) {
            // A user may restore from Drive on sign-in and also import the same
            // exported file by hand. Both paths funnel through mergeDocuments and
            // must resolve to one library, not two.
            val fromDrive = transfer.parse(fixture()).map { it.toEntity() }
            val fromFile = transfer.parse(fixture()).map { it.toEntity() }

            repository.mergeDocuments(fromDrive)
            repository.mergeDocuments(fromFile)

            assertThat(repository.getAllDocuments()).hasSize(17)
        }

    @Test
    fun `restoring legacy data three times remains stable`() = runTest(scheduler) {
        val documents = transfer.parse(fixture()).map { it.toEntity() }

        repeat(3) { repository.mergeDocuments(documents) }

        assertThat(repository.getAllDocuments()).hasSize(17)
    }
}

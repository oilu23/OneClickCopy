package com.oneclickcopy.ui.home

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.oneclickcopy.backup.DriveBackupManager
import com.oneclickcopy.data.AppDatabase
import com.oneclickcopy.data.DocumentRepository
import com.oneclickcopy.data.DocumentTransfer
import com.oneclickcopy.sync.SyncManager
import com.oneclickcopy.sync.SyncPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Rapid, repeated taps on interactive controls.
 *
 * Touch events arrive faster than navigation removes a button from the screen,
 * so any action that creates or destroys data must tolerate being invoked
 * several times before its effect is visible.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeViewModelRapidTapTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    private lateinit var database: AppDatabase
    private lateinit var repository: DocumentRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
        repository = DocumentRepository(database.documentDao(), dispatcher)

        val drive = object : DriveBackupManager(context) {
            override fun isSignedIn(): Boolean = false
        }
        viewModel = HomeViewModel(
            repository = repository,
            syncManager = SyncManager(context, repository, drive, SyncPreferences(context)),
            driveBackupManager = drive,
            documentTransfer = DocumentTransfer(context, repository, dispatcher),
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `a single tap creates one document`() = runTest(scheduler) {
        viewModel.onCreateDocument { }
        advanceUntilIdle()

        assertThat(repository.getAllDocuments()).hasSize(1)
    }

    @Test
    fun `double tapping create makes only one document`() = runTest(scheduler) {
        // Both taps land before the first creation completes and navigation
        // takes the button off screen. Previously this produced two "Untitled"
        // documents and navigated to the second.
        viewModel.onCreateDocument { }
        viewModel.onCreateDocument { }
        advanceUntilIdle()

        assertThat(repository.getAllDocuments()).hasSize(1)
    }

    @Test
    fun `five rapid taps still make only one document`() = runTest(scheduler) {
        repeat(5) { viewModel.onCreateDocument { } }
        advanceUntilIdle()

        assertThat(repository.getAllDocuments()).hasSize(1)
    }

    @Test
    fun `creating again after the first completes is allowed`() = runTest(scheduler) {
        viewModel.onCreateDocument { }
        advanceUntilIdle()
        viewModel.onCreateDocument { }
        advanceUntilIdle()

        // The guard must not latch permanently; it only collapses a burst.
        assertThat(repository.getAllDocuments()).hasSize(2)
    }

    @Test
    fun `the created document id is reported exactly once`() = runTest(scheduler) {
        val ids = mutableListOf<Long>()

        repeat(4) { viewModel.onCreateDocument { id -> ids += id } }
        advanceUntilIdle()

        assertThat(ids).hasSize(1)
    }

    @Test
    fun `deleting the same document twice does not throw`() = runTest(scheduler) {
        viewModel.onCreateDocument { }
        advanceUntilIdle()
        val document = repository.getAllDocuments().single()

        viewModel.onDeleteDocument(document)
        viewModel.onDeleteDocument(document)
        advanceUntilIdle()

        assertThat(repository.getAllDocuments()).isEmpty()
    }
}

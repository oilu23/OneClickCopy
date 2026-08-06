package com.oneclickcopy.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.oneclickcopy.backup.BackupDocument
import com.oneclickcopy.backup.DriveBackupManager
import com.oneclickcopy.data.AppDatabase
import com.oneclickcopy.data.DocumentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Failure handling in the sync layer.
 *
 * Sync runs unattended in the background, so an unhandled exception here is
 * invisible until a user notices their data stopped backing up. These tests
 * assert that every failure path resolves to a state the UI can display, rather
 * than escaping and killing the worker.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncManagerFailureTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: DocumentRepository

    /** Drive stub that fails, so no network is touched. */
    private class FailingDrive(context: Context) : DriveBackupManager(context) {
        override fun isSignedIn(): Boolean = true
        override suspend fun upload(documents: List<BackupDocument>): Result<Unit> =
            Result.failure(IllegalStateException("upload boom"))
        override suspend fun download(): Result<List<BackupDocument>> =
            Result.failure(IllegalStateException("download boom"))
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
        repository = DocumentRepository(database.documentDao(), dispatcher)
    }

    @After
    fun tearDown() = database.close()

    private fun manager(drive: DriveBackupManager) = SyncManager(
        context = context,
        repository = repository,
        driveBackupManager = drive,
        preferences = SyncPreferences(context),
    )

    @Test
    fun `upload failure resolves to a terminal state rather than throwing`() =
        runTest(scheduler) {
            val syncManager = manager(FailingDrive(context))

            val result = syncManager.performUpload()
            advanceUntilIdle()

            assertThat(result.isFailure).isTrue()
            // Must not remain stuck on Syncing, which would show a spinner forever.
            assertThat(syncManager.state.value).isNotEqualTo(SyncState.Syncing)
        }

    @Test
    fun `a storage failure during upload does not escape`() = runTest(scheduler) {
        // Stands in for disk corruption or a full disk. Previously this exception
        // propagated out of performUpload, through SyncWorker.doWork, and killed
        // the worker while the UI stayed on "Syncing" forever.
        val throwingRepository = object : DocumentRepository(
            database.documentDao(),
            dispatcher,
        ) {
            override suspend fun getAllDocuments(): List<com.oneclickcopy.data.DocumentEntity> =
                throw IllegalStateException("disk failure")
        }
        val syncManager = SyncManager(
            context = context,
            repository = throwingRepository,
            driveBackupManager = FailingDrive(context),
            preferences = SyncPreferences(context),
        )

        val escaped = runCatching { syncManager.performUpload() }
        advanceUntilIdle()

        // The call itself must not throw; it must return a failed Result.
        assertThat(escaped.isSuccess).isTrue()
        assertThat(escaped.getOrThrow().isFailure).isTrue()
        assertThat(syncManager.state.value).isNotEqualTo(SyncState.Syncing)
    }

    @Test
    fun `restore failure resolves to a terminal state`() = runTest(scheduler) {
        val syncManager = manager(FailingDrive(context))

        val result = syncManager.restoreFromCloud()
        advanceUntilIdle()

        assertThat(result.isFailure).isTrue()
        assertThat(syncManager.state.value).isNotEqualTo(SyncState.Syncing)
    }

    @Test
    fun `requesting sync while signed out reports signed out`() = runTest(scheduler) {
        val signedOut = object : DriveBackupManager(context) {
            override fun isSignedIn(): Boolean = false
        }
        val syncManager = manager(signedOut)

        syncManager.requestSync()

        assertThat(syncManager.state.value).isEqualTo(SyncState.SignedOut)
    }

    @Test
    fun `syncNow while signed out is a no-op and does not throw`() = runTest(scheduler) {
        val signedOut = object : DriveBackupManager(context) {
            override fun isSignedIn(): Boolean = false
        }
        val syncManager = manager(signedOut)

        syncManager.syncNow()
        advanceUntilIdle()

        assertThat(syncManager.state.value).isEqualTo(SyncState.SignedOut)
    }
}

package com.oneclickcopy.ui.editor

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.oneclickcopy.data.AppDatabase
import com.oneclickcopy.data.DocumentRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EditorViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private lateinit var database: AppDatabase
    private lateinit var repository: DocumentRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            // Route Room's internal query/transaction work onto the test
            // dispatcher. Without this, Room hops to a real background thread and
            // advanceUntilIdle() returns before fire-and-forget ViewModel loads
            // have completed, making these tests flaky.
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
        repository = DocumentRepository(database.documentDao(), dispatcher)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private suspend fun seedDocument(content: String): Long {
        val id = repository.createDocument()
        repository.saveDocument(id, "Test", content, emptySet())
        return id
    }

    @Test
    fun `loads existing document into state`() = runTest(scheduler) {
        val id = seedDocument("alpha\nbeta")

        val viewModel = EditorViewModel(repository, id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.documentExists).isTrue()
        assertThat(state.rawText).isEqualTo("alpha\nbeta")
    }

    @Test
    fun `flags missing document instead of crashing`() = runTest(scheduler) {
        val viewModel = EditorViewModel(repository, 4_242L)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.documentExists).isFalse()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `toggling to copy mode parses snippets`() = runTest(scheduler) {
        val id = seedDocument("one\ntwo\nthree")
        val viewModel = EditorViewModel(repository, id)
        advanceUntilIdle()

        viewModel.onToggleMode()

        val state = viewModel.uiState.value
        assertThat(state.isCopyMode).isTrue()
        assertThat(state.snippets.map { it.text })
            .containsExactly("one", "two", "three").inOrder()
    }

    @Test
    fun `copying a duplicate line checks only that occurrence`() = runTest(scheduler) {
        val id = seedDocument("dup\ndup")
        val viewModel = EditorViewModel(repository, id)
        advanceUntilIdle()
        viewModel.onToggleMode()

        viewModel.onSnippetCopied(viewModel.uiState.value.snippets[1])

        assertThat(viewModel.uiState.value.snippets.map { it.isCopied })
            .containsExactly(false, true).inOrder()
    }

    @Test
    fun `copied state persists across a mode switch`() = runTest(scheduler) {
        val id = seedDocument("a\nb")
        val viewModel = EditorViewModel(repository, id)
        advanceUntilIdle()
        viewModel.onToggleMode()
        viewModel.onSnippetCopied(viewModel.uiState.value.snippets[0])

        viewModel.onToggleMode() // back to edit
        viewModel.onToggleMode() // and into copy again

        assertThat(viewModel.uiState.value.snippets[0].isCopied).isTrue()
    }

    @Test
    fun `unchecking clears only the targeted snippet`() = runTest(scheduler) {
        val id = seedDocument("a\nb")
        val viewModel = EditorViewModel(repository, id)
        advanceUntilIdle()
        viewModel.onToggleMode()
        viewModel.onSnippetCopied(viewModel.uiState.value.snippets[0])
        viewModel.onSnippetCopied(viewModel.uiState.value.snippets[1])

        viewModel.onSnippetUnchecked(viewModel.uiState.value.snippets[0])

        assertThat(viewModel.uiState.value.snippets.map { it.isCopied })
            .containsExactly(false, true).inOrder()
    }

    @Test
    fun `reset clears all checkmarks`() = runTest(scheduler) {
        val id = seedDocument("a\nb\nc")
        val viewModel = EditorViewModel(repository, id)
        advanceUntilIdle()
        viewModel.onToggleMode()
        viewModel.uiState.value.snippets.forEach(viewModel::onSnippetCopied)
        assertThat(viewModel.uiState.value.allCopied).isTrue()

        viewModel.onResetChecks()

        assertThat(viewModel.uiState.value.copiedCount).isEqualTo(0)
    }

    @Test
    fun `reorder rewrites raw text and keeps checkmarks on the right rows`() =
        runTest(scheduler) {
            val id = seedDocument("first\nsecond\nthird")
            val viewModel = EditorViewModel(repository, id)
            advanceUntilIdle()
            viewModel.onToggleMode()
            viewModel.onSnippetCopied(viewModel.uiState.value.snippets[2]) // "third"

            viewModel.onReorder(fromIndex = 2, toIndex = 0)

            val state = viewModel.uiState.value
            assertThat(state.rawText).isEqualTo("third\nfirst\nsecond")
            assertThat(state.snippets[0].text).isEqualTo("third")
            assertThat(state.snippets[0].isCopied).isTrue()
            assertThat(state.copiedCount).isEqualTo(1)
        }

    @Test
    fun `reorder with out of bounds index is ignored`() = runTest(scheduler) {
        val id = seedDocument("a\nb")
        val viewModel = EditorViewModel(repository, id)
        advanceUntilIdle()
        viewModel.onToggleMode()

        viewModel.onReorder(fromIndex = 0, toIndex = 99)

        assertThat(viewModel.uiState.value.snippets.map { it.text })
            .containsExactly("a", "b").inOrder()
    }

    @Test
    fun `saveNow flushes pending edits immediately`() = runTest(scheduler) {
        val id = seedDocument("original")
        val viewModel = EditorViewModel(repository, id)
        advanceUntilIdle()

        viewModel.onTextChanged("edited before leaving")
        viewModel.saveNow()
        advanceUntilIdle()

        assertThat(repository.getDocument(id)!!.content)
            .isEqualTo("edited before leaving")
    }

    @Test
    fun `progress reflects copied ratio`() = runTest(scheduler) {
        val id = seedDocument("a\nb\nc\nd")
        val viewModel = EditorViewModel(repository, id)
        advanceUntilIdle()
        viewModel.onToggleMode()

        viewModel.onSnippetCopied(viewModel.uiState.value.snippets[0])

        assertThat(viewModel.uiState.value.progress).isWithin(0.001f).of(0.25f)
    }

    @Test
    fun `undo restores order and checkmarks after a reorder`() = runTest(scheduler) {
        val id = seedDocument("first\nsecond\nthird")
        val viewModel = EditorViewModel(repository, id)
        advanceUntilIdle()
        viewModel.onToggleMode()
        viewModel.onSnippetCopied(viewModel.uiState.value.snippets[2])

        viewModel.onReorder(fromIndex = 2, toIndex = 0)
        assertThat(viewModel.uiState.value.rawText).isEqualTo("third\nfirst\nsecond")
        assertThat(viewModel.uiState.value.canUndoReorder).isTrue()

        viewModel.onUndoReorder()

        val state = viewModel.uiState.value
        assertThat(state.rawText).isEqualTo("first\nsecond\nthird")
        assertThat(state.snippets.map { it.text })
            .containsExactly("first", "second", "third").inOrder()
        assertThat(state.snippets.map { it.isCopied })
            .containsExactly(false, false, true).inOrder()
        assertThat(state.canUndoReorder).isFalse()
    }

    @Test
    fun `undo walks back multiple reorders`() = runTest(scheduler) {
        val id = seedDocument("a\nb\nc")
        val viewModel = EditorViewModel(repository, id)
        advanceUntilIdle()
        viewModel.onToggleMode()

        viewModel.onReorder(fromIndex = 2, toIndex = 0) // c, a, b
        viewModel.onReorder(fromIndex = 2, toIndex = 1) // c, b, a
        assertThat(viewModel.uiState.value.rawText).isEqualTo("c\nb\na")

        viewModel.onUndoReorder()
        assertThat(viewModel.uiState.value.rawText).isEqualTo("c\na\nb")
        assertThat(viewModel.uiState.value.canUndoReorder).isTrue()

        viewModel.onUndoReorder()
        assertThat(viewModel.uiState.value.rawText).isEqualTo("a\nb\nc")
        assertThat(viewModel.uiState.value.canUndoReorder).isFalse()
    }

    @Test
    fun `undo with an empty stack is a no-op`() = runTest(scheduler) {
        val id = seedDocument("a\nb")
        val viewModel = EditorViewModel(repository, id)
        advanceUntilIdle()
        viewModel.onToggleMode()

        viewModel.onUndoReorder()

        assertThat(viewModel.uiState.value.snippets.map { it.text })
            .containsExactly("a", "b").inOrder()
        assertThat(viewModel.uiState.value.canUndoReorder).isFalse()
    }

    @Test
    fun `reordering a row onto itself does not enable undo`() = runTest(scheduler) {
        val id = seedDocument("a\nb")
        val viewModel = EditorViewModel(repository, id)
        advanceUntilIdle()
        viewModel.onToggleMode()

        viewModel.onReorder(fromIndex = 1, toIndex = 1)

        assertThat(viewModel.uiState.value.canUndoReorder).isFalse()
        assertThat(viewModel.uiState.value.snippets.map { it.text })
            .containsExactly("a", "b").inOrder()
    }

    @Test
    fun `editing the body after a reorder clears undo`() = runTest(scheduler) {
        val id = seedDocument("a\nb\nc")
        val viewModel = EditorViewModel(repository, id)
        advanceUntilIdle()
        viewModel.onToggleMode()
        viewModel.onReorder(fromIndex = 2, toIndex = 0)
        assertThat(viewModel.uiState.value.canUndoReorder).isTrue()

        viewModel.onToggleMode()
        viewModel.onTextChanged("a\nb\nc\nextra")

        assertThat(viewModel.uiState.value.canUndoReorder).isFalse()
        viewModel.onUndoReorder()
        assertThat(viewModel.uiState.value.rawText).isEqualTo("a\nb\nc\nextra")
    }
}

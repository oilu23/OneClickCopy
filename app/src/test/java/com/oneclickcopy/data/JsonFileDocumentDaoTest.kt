package com.oneclickcopy.data

import com.google.common.truth.Truth.assertThat
import com.oneclickcopy.domain.SnippetKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class JsonFileDocumentDaoTest {

    @get:Rule val temp = TemporaryFolder()

    private val file get() = temp.root.toPath().resolve("documents.json")

    private fun repository() = DocumentRepository(JsonFileDocumentDao(file), Dispatchers.Unconfined)

    @Test
    fun documentsSurviveReopening() = runBlocking<Unit> {
        val id = repository().run {
            createDocument().also { saveDocument(it, "Replies", "a\nb", setOf(SnippetKey("a", 0))) }
        }
        val reopened = repository().getDocument(id)!!
        assertThat(reopened.title).isEqualTo("Replies")
        assertThat(reopened.content).isEqualTo("a\nb")
        assertThat(reopened.copiedItems).contains("0:a")
    }

    @Test
    fun idsAreNotReusedAfterDelete() = runBlocking<Unit> {
        val repo = repository()
        val first = repo.createDocument()
        repo.deleteDocument(repo.getDocument(first)!!)
        assertThat(repository().createDocument()).isGreaterThan(first)
    }

    @Test
    fun undoDeleteRestoresSameId() = runBlocking<Unit> {
        val repo = repository()
        val id = repo.createDocument()
        val doc = repo.getDocument(id)!!
        repo.deleteDocument(doc)
        assertThat(repo.restoreDocument(doc)).isEqualTo(id)
        assertThat(repo.observeDocuments().first().map { it.id }).containsExactly(id)
    }

    @Test
    fun seedKeepsIdsAndContinuesNumberingAfterThem() = runBlocking<Unit> {
        JsonFileDocumentDao.seed(file, listOf(DocumentEntity(id = 4, title = "a"), DocumentEntity(id = 9, title = "b")))
        val repo = repository()
        assertThat(repo.getDocument(9)!!.title).isEqualTo("b")
        assertThat(repo.createDocument()).isEqualTo(10)
    }

    @Test
    fun corruptFileIsSetAsideNotOverwritten() = runBlocking<Unit> {
        Files.write(file, "{ not json".toByteArray())
        val repo = repository()
        assertThat(repo.getAllDocuments()).isEmpty()
        repo.createDocument()
        val aside = Files.list(temp.root.toPath()).use { s -> s.filter { it.fileName.toString().contains(".corrupt-") }.toList() }
        assertThat(aside).hasSize(1)
        assertThat(String(Files.readAllBytes(aside.single()))).isEqualTo("{ not json")
    }
}

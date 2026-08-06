package com.oneclickcopy.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SnippetParserTest {

    @Test
    fun `parse returns empty list for empty text`() {
        assertThat(SnippetParser.parse("")).isEmpty()
    }

    @Test
    fun `parse skips blank lines`() {
        val snippets = SnippetParser.parse("alpha\n\n   \nbeta")

        assertThat(snippets).hasSize(2)
        assertThat(snippets.map { it.text }).containsExactly("alpha", "beta").inOrder()
    }

    @Test
    fun `parse trims surrounding whitespace`() {
        val snippets = SnippetParser.parse("   padded   ")

        assertThat(snippets.single().text).isEqualTo("padded")
    }

    @Test
    fun `parse preserves original line index across blank lines`() {
        val snippets = SnippetParser.parse("first\n\n\nsecond")

        assertThat(snippets[0].sourceLineIndex).isEqualTo(0)
        assertThat(snippets[1].sourceLineIndex).isEqualTo(3)
    }

    @Test
    fun `duplicate lines receive distinct occurrence indices`() {
        val snippets = SnippetParser.parse("same\nsame\nsame")

        assertThat(snippets.map { it.occurrence }).containsExactly(0, 1, 2).inOrder()
        assertThat(snippets.map { it.key }.toSet()).hasSize(3)
    }

    @Test
    fun `copied state applies only to the matching occurrence`() {
        // This is the exact bug in the original app: copied state was keyed by raw
        // text, so copying one duplicate checked every identical line.
        val copied = setOf(SnippetKey("same", 1))
        val snippets = SnippetParser.parse("same\nsame\nsame", copied)

        assertThat(snippets.map { it.isCopied })
            .containsExactly(false, true, false)
            .inOrder()
    }

    @Test
    fun `render round trips parsed text`() {
        val snippets = SnippetParser.parse("one\ntwo\nthree")

        assertThat(SnippetParser.render(snippets)).isEqualTo("one\ntwo\nthree")
    }

    @Test
    fun `reindex assigns canonical occurrences after reorder`() {
        val original = SnippetParser.parse("a\nb\na")
        val reordered = listOf(original[2], original[0], original[1])

        val reindexed = SnippetParser.reindex(reordered)

        assertThat(reindexed.map { it.text }).containsExactly("a", "a", "b").inOrder()
        assertThat(reindexed.map { it.occurrence }).containsExactly(0, 1, 0).inOrder()
    }

    @Test
    fun `reindex preserves copied flags`() {
        val snippets = listOf(
            Snippet("x", 0, 0, isCopied = true),
            Snippet("y", 0, 1, isCopied = false),
        )

        val reindexed = SnippetParser.reindex(snippets)

        assertThat(reindexed[0].isCopied).isTrue()
        assertThat(reindexed[1].isCopied).isFalse()
    }

    @Test
    fun `parse handles a large document without error`() {
        val text = (1..5_000).joinToString("\n") { "line $it" }

        val snippets = SnippetParser.parse(text)

        assertThat(snippets).hasSize(5_000)
        assertThat(snippets.last().text).isEqualTo("line 5000")
    }

    @Test
    fun `parse handles very long single line`() {
        val longLine = "x".repeat(20_000)

        val snippets = SnippetParser.parse(longLine)

        assertThat(snippets.single().text).hasLength(20_000)
    }
}

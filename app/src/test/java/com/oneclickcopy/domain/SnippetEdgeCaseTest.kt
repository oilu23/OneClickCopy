package com.oneclickcopy.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Robustness of the parsing and encoding layer against real-world text.
 *
 * The app's documents are arbitrary user text: shell commands, URLs, JSON,
 * emoji, pasted output with odd whitespace. Anything that throws here becomes a
 * crash when opening a document, so these paths must be total.
 */
class SnippetEdgeCaseTest {

    @Test
    fun `handles windows line endings`() {
        val snippets = SnippetParser.parse("first\r\nsecond\r\nthird")

        assertThat(snippets).hasSize(3)
        // The \r must be trimmed, or copied text carries an invisible character.
        assertThat(snippets.map { it.text }).containsExactly("first", "second", "third")
    }

    @Test
    fun `handles a document of only blank lines`() {
        val snippets = SnippetParser.parse("\n\n   \n\t\n")

        assertThat(snippets).isEmpty()
    }

    @Test
    fun `handles tabs and mixed indentation`() {
        val snippets = SnippetParser.parse("\tindented\n    spaced\n\t  mixed")

        assertThat(snippets.map { it.text })
            .containsExactly("indented", "spaced", "mixed")
    }

    @Test
    fun `preserves internal whitespace while trimming edges`() {
        val snippets = SnippetParser.parse("  git commit -m \"a message\"  ")

        assertThat(snippets.single().text).isEqualTo("git commit -m \"a message\"")
    }

    @Test
    fun `handles text containing json and quotes`() {
        val line = """curl -d '{"model":"x","prompt":"hi"}' http://localhost"""

        val snippets = SnippetParser.parse(line)
        val roundTrip = CopiedStateCodec.decode(
            CopiedStateCodec.encode(setOf(snippets.single().key))
        )

        assertThat(snippets.single().text).isEqualTo(line)
        assertThat(roundTrip.single().text).isEqualTo(line)
    }

    @Test
    fun `handles emoji and combining characters`() {
        val line = "deploy 🚀 café naïve 日本語"

        val snippets = SnippetParser.parse(line)
        val roundTrip = CopiedStateCodec.decode(
            CopiedStateCodec.encode(setOf(snippets.single().key))
        )

        assertThat(roundTrip.single().text).isEqualTo(line)
    }

    @Test
    fun `handles a line that is only whitespace between content`() {
        val snippets = SnippetParser.parse("alpha\n \nbeta")

        assertThat(snippets.map { it.text }).containsExactly("alpha", "beta")
        assertThat(snippets[1].sourceLineIndex).isEqualTo(2)
    }

    @Test
    fun `render then reparse is stable`() {
        val original = SnippetParser.parse("one\ntwo\nthree")

        val reparsed = SnippetParser.parse(SnippetParser.render(original))

        assertThat(reparsed.map { it.text }).isEqualTo(original.map { it.text })
        assertThat(reparsed.map { it.key }).isEqualTo(original.map { it.key })
    }

    @Test
    fun `many duplicate lines all receive distinct keys`() {
        val snippets = SnippetParser.parse(List(500) { "same" }.joinToString("\n"))

        assertThat(snippets).hasSize(500)
        assertThat(snippets.map { it.key }.toSet()).hasSize(500)
    }

    @Test
    fun `copied state survives a large document round trip`() {
        val text = (1..2_000).joinToString("\n") { "line $it" }
        val parsed = SnippetParser.parse(text)
        val keys = parsed.filterIndexed { i, _ -> i % 3 == 0 }.map { it.key }.toSet()

        val restored = CopiedStateCodec.decode(CopiedStateCodec.encode(keys))
        val reparsed = SnippetParser.parse(text, restored)

        assertThat(reparsed.count { it.isCopied }).isEqualTo(keys.size)
    }

    @Test
    fun `reorder to the same position is a no-op`() {
        val snippets = SnippetParser.parse("a\nb\nc")

        val reindexed = SnippetParser.reindex(snippets)

        assertThat(reindexed.map { it.text }).isEqualTo(snippets.map { it.text })
    }

    @Test
    fun `codec ignores keys that no longer match any line`() {
        // A user edits a line after copying it; the stale key must simply not
        // match rather than corrupting the parse.
        val stale = setOf(SnippetKey("deleted line", 0))

        val snippets = SnippetParser.parse("kept line", stale)

        assertThat(snippets.single().isCopied).isFalse()
    }
}

package com.oneclickcopy.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CopiedStateCodecTest {

    @Test
    fun `encode empty set produces empty string`() {
        assertThat(CopiedStateCodec.encode(emptySet())).isEmpty()
    }

    @Test
    fun `decode blank string produces empty set`() {
        assertThat(CopiedStateCodec.decode("")).isEmpty()
        assertThat(CopiedStateCodec.decode("   ")).isEmpty()
    }

    @Test
    fun `encode then decode round trips`() {
        val keys = setOf(
            SnippetKey("hello", 0),
            SnippetKey("hello", 1),
            SnippetKey("world", 0),
        )

        val decoded = CopiedStateCodec.decode(CopiedStateCodec.encode(keys))

        assertThat(decoded).containsExactlyElementsIn(keys)
    }

    @Test
    fun `round trip preserves text containing colons`() {
        // The encoded form is "<occurrence>:<text>", so text with its own colon
        // must not confuse the decoder.
        val keys = setOf(SnippetKey("https://example.com:8080/path", 2))

        val decoded = CopiedStateCodec.decode(CopiedStateCodec.encode(keys))

        assertThat(decoded).containsExactlyElementsIn(keys)
    }

    @Test
    fun `round trip preserves unicode and emoji`() {
        val keys = setOf(SnippetKey("héllo wörld 🎉 日本語", 0))

        val decoded = CopiedStateCodec.decode(CopiedStateCodec.encode(keys))

        assertThat(decoded).containsExactlyElementsIn(keys)
    }

    @Test
    fun `decode tolerates malformed payload`() {
        assertThat(CopiedStateCodec.decode("{not json")).isEmpty()
        assertThat(CopiedStateCodec.decode("42")).isEmpty()
    }

    @Test
    fun `decode maps legacy bare text entries to first occurrence`() {
        // v2 databases stored a plain JSON array of raw strings.
        val legacy = """["alpha","beta"]"""

        val decoded = CopiedStateCodec.decode(legacy)

        assertThat(decoded).containsExactly(
            SnippetKey("alpha", 0),
            SnippetKey("beta", 0),
        )
    }

    @Test
    fun `SnippetKey decode rejects malformed input`() {
        assertThat(SnippetKey.decode("nocolon")).isNull()
        assertThat(SnippetKey.decode(":leading")).isNull()
        assertThat(SnippetKey.decode("abc:text")).isNull()
        assertThat(SnippetKey.decode("-1:text")).isNull()
    }

    @Test
    fun `SnippetKey decode accepts valid input`() {
        assertThat(SnippetKey.decode("3:some text"))
            .isEqualTo(SnippetKey("some text", 3))
    }
}

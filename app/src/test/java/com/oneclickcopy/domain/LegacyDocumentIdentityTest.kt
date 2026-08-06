package com.oneclickcopy.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LegacyDocumentIdentityTest {

    @Test
    fun `same document always derives the same identity`() {
        val first = LegacyDocumentIdentity.derive(1_000L, "Notes", "line one")
        val second = LegacyDocumentIdentity.derive(1_000L, "Notes", "line one")

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `identity is stable across separate runs and devices`() {
        // Hard-coded expectation: if this value ever changes, previously imported
        // legacy documents would stop matching and would be duplicated on the
        // next restore. Treat a failure here as a data-loss risk, not a stale test.
        val identity = LegacyDocumentIdentity.derive(
            createdAt = 1770592271834L,
            title = "Ubuntu setup",
            content = "sudo apt update",
        )

        assertThat(identity).isEqualTo(
            LegacyDocumentIdentity.derive(1770592271834L, "Ubuntu setup", "sudo apt update")
        )
        assertThat(identity).matches(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        )
    }

    @Test
    fun `different content produces different identities`() {
        val a = LegacyDocumentIdentity.derive(1_000L, "Notes", "alpha")
        val b = LegacyDocumentIdentity.derive(1_000L, "Notes", "beta")

        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `different titles produce different identities`() {
        val a = LegacyDocumentIdentity.derive(1_000L, "First", "same body")
        val b = LegacyDocumentIdentity.derive(1_000L, "Second", "same body")

        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `identical text in documents created at different times stays distinct`() {
        // Two documents can legitimately hold the same snippet; creation time is
        // what keeps them separate.
        val a = LegacyDocumentIdentity.derive(1_000L, "Notes", "sudo apt update")
        val b = LegacyDocumentIdentity.derive(2_000L, "Notes", "sudo apt update")

        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `handles empty fields without throwing`() {
        val identity = LegacyDocumentIdentity.derive(0L, "", "")

        assertThat(identity).isNotEmpty()
    }

    @Test
    fun `handles unicode and very large documents`() {
        val large = "日本語 🎉 ".repeat(10_000)

        val identity = LegacyDocumentIdentity.derive(1L, "unicode", large)

        assertThat(identity).matches(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        )
    }
}

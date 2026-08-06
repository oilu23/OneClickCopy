package com.oneclickcopy.domain

import java.security.MessageDigest

/**
 * Derives a stable identity for documents that predate the `uuid` column.
 *
 * Backups written before v2 carry no uuid. Assigning a fresh random uuid at
 * import time made every import look like new content, so restoring the same
 * file twice silently doubled the user's library.
 *
 * Hashing immutable fields instead means the same legacy document always
 * resolves to the same identity, on any device, in any order — making legacy
 * restore idempotent without needing a server or migration table.
 */
object LegacyDocumentIdentity {

    /**
     * Builds a deterministic UUID-shaped identifier from a document's original
     * creation time, title, and content.
     *
     * [createdAt] is included because it is assigned once and never changes,
     * which keeps the identity stable even if the same snippet text appears in
     * two different documents.
     */
    fun derive(createdAt: Long, title: String, content: String): String {
        val seed = buildString {
            append(NAMESPACE)
            append('|')
            append(createdAt)
            append('|')
            append(title)
            append('|')
            append(content)
        }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray(Charsets.UTF_8))

        return formatAsUuid(digest)
    }

    /** Renders the first 16 bytes of [digest] in canonical UUID form. */
    private fun formatAsUuid(digest: ByteArray): String {
        val hex = StringBuilder(32)
        for (i in 0 until 16) {
            hex.append("%02x".format(digest[i]))
        }
        return buildString {
            append(hex, 0, 8); append('-')
            append(hex, 8, 12); append('-')
            append(hex, 12, 16); append('-')
            append(hex, 16, 20); append('-')
            append(hex, 20, 32)
        }
    }

    private const val NAMESPACE = "oneclickcopy.legacy.v1"
}

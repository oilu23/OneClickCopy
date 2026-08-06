package com.oneclickcopy.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Serializes the set of copied snippet keys to and from the string column stored
 * on [com.oneclickcopy.data.DocumentEntity].
 *
 * Decoding is deliberately total: malformed or legacy payloads degrade to a
 * best-effort result rather than throwing, because a corrupt checkmark list must
 * never prevent a user from opening their document.
 */
object CopiedStateCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun encode(keys: Set<SnippetKey>): String {
        if (keys.isEmpty()) return ""
        return json.encodeToString(
            ListSerializer(String.serializer()),
            keys.map { it.encode() },
        )
    }

    fun decode(raw: String): Set<SnippetKey> {
        if (raw.isBlank()) return emptySet()
        val entries = runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), raw)
        }.getOrElse { return emptySet() }

        return entries.mapNotNullTo(LinkedHashSet()) { entry ->
            // Encoded form is "<occurrence>:<text>". Anything else is a v2 legacy
            // payload that stored bare text, which we map to first occurrence.
            SnippetKey.decode(entry) ?: SnippetKey(entry, 0)
        }
    }
}

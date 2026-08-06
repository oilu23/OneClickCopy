package com.oneclickcopy.domain

/**
 * A single copyable line derived from a document's raw text.
 *
 * [key] is the stable identity used for copied-state tracking and as the Compose
 * list key. It combines the trimmed text with its occurrence index, so a document
 * containing the same line twice tracks each occurrence independently — the
 * central bug in the original text-keyed implementation.
 */
data class Snippet(
    val text: String,
    val occurrence: Int,
    val sourceLineIndex: Int,
    val isCopied: Boolean = false,
) {
    val key: SnippetKey get() = SnippetKey(text, occurrence)
}

/** Stable identity of a snippet within a document. */
data class SnippetKey(
    val text: String,
    val occurrence: Int,
) {
    /** Compact serialized form: occurrence, a colon, then the raw text. */
    fun encode(): String = "$occurrence:$text"

    companion object {
        fun decode(raw: String): SnippetKey? {
            val separator = raw.indexOf(':')
            if (separator <= 0) return null
            val occurrence = raw.substring(0, separator).toIntOrNull() ?: return null
            if (occurrence < 0) return null
            return SnippetKey(raw.substring(separator + 1), occurrence)
        }
    }
}

/**
 * Pure, dependency-free parsing of document text into snippets.
 *
 * Extracted from the composable body so it can be unit tested on the JVM without
 * an emulator, and so recomposition never re-runs parsing implicitly.
 */
object SnippetParser {

    /**
     * Splits [rawText] into snippets, one per non-blank line.
     *
     * Blank lines are skipped but [Snippet.sourceLineIndex] preserves the original
     * position so reordering can be written back without destroying formatting.
     */
    fun parse(rawText: String, copiedKeys: Set<SnippetKey> = emptySet()): List<Snippet> {
        if (rawText.isEmpty()) return emptyList()

        val seen = HashMap<String, Int>()
        val result = ArrayList<Snippet>()

        rawText.lineSequence().forEachIndexed { lineIndex, line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEachIndexed

            val occurrence = seen.getOrDefault(trimmed, 0)
            seen[trimmed] = occurrence + 1

            val key = SnippetKey(trimmed, occurrence)
            result += Snippet(
                text = trimmed,
                occurrence = occurrence,
                sourceLineIndex = lineIndex,
                isCopied = key in copiedKeys,
            )
        }
        return result
    }

    /** Rebuilds document text from a (possibly reordered) snippet list. */
    fun render(snippets: List<Snippet>): String =
        snippets.joinToString("\n") { it.text }

    /**
     * Reassigns occurrence indices after a reorder so keys stay canonical
     * (first appearance is always occurrence 0, top to bottom).
     */
    fun reindex(snippets: List<Snippet>): List<Snippet> {
        val seen = HashMap<String, Int>()
        return snippets.mapIndexed { index, snippet ->
            val occurrence = seen.getOrDefault(snippet.text, 0)
            seen[snippet.text] = occurrence + 1
            snippet.copy(occurrence = occurrence, sourceLineIndex = index)
        }
    }
}

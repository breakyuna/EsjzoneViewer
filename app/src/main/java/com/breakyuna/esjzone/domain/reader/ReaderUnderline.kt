package com.breakyuna.esjzone.domain.reader

import com.google.gson.Gson
import java.security.MessageDigest

/** Each range uses full rendered-block offsets, before page splitting. */
data class ReaderUnderlineRange(val blockIndex: Int, val signature: String, val start: Int, val end: Int)

data class ReaderUnderline(val blockIndex: Int, val signature: String, val start: Int, val end: Int,
    val quote: String? = null, val continuation: List<ReaderUnderlineRange>? = null) {
    fun ranges(): List<ReaderUnderlineRange> =
        listOf(ReaderUnderlineRange(blockIndex, signature, start, end)) + continuation.orEmpty()
}

object ReaderUnderlines {
    const val KEY_PREFIX = "reader_underlines_v2:"
    private val gson = Gson()
    private val signaturePattern = Regex("[0-9a-f]{64}")
    private val blankLine = Regex("\\r?\\n[\\t\\x0B\\f\\p{Zs}]*\\r?\\n")
    fun signature(block: ReaderBlock, renderedText: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$block\u0000$renderedText".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun decode(value: String?): List<ReaderUnderline> {
        if (value == null) return emptyList()
        require(value.isNotBlank()) { "Invalid underline data" }
        val rows: List<ReaderUnderline?> = requireNotNull(gson.fromJson(value, Array<ReaderUnderline>::class.java)) {
            "Invalid underline data"
        }.toList()
        return rows.map { row ->
            val mark = requireNotNull(row) { "Invalid underline record" }
            // Gson can bypass Kotlin constructors, so validate before constructing ranges.
            require(validRange(mark.blockIndex, mark.signature, mark.start, mark.end)) {
                "Invalid underline range"
            }
            val continuation: List<ReaderUnderlineRange?> = mark.continuation.orEmpty()
            require(continuation.all { range -> range != null &&
                validRange(range.blockIndex, range.signature, range.start, range.end) }) {
                "Invalid underline continuation"
            }
            require(mark.ranges().zipWithNext().all { (a, b) -> a.blockIndex < b.blockIndex })
            mark
        }
    }

    private fun validRange(blockIndex: Int, signature: String?, start: Int, end: Int): Boolean =
        blockIndex >= 0 && start >= 0 && end > start && signature != null && signaturePattern.matches(signature)

    fun encode(rows: List<ReaderUnderline>): String = gson.toJson(rows)
    fun overlaps(a: ReaderUnderline, b: ReaderUnderline) = a.ranges().any { first ->
        b.ranges().any { second -> first.blockIndex == second.blockIndex &&
            first.signature == second.signature && first.start < second.end && second.start < first.end }
    }

    /** Consecutive source paragraphs share one mark until a blank line or a non-text block. */
    internal fun selection(parts: List<Pair<ReaderUnderlineRange, String>>): List<ReaderUnderline> {
        val result = mutableListOf<ReaderUnderline>()
        val group = mutableListOf<ReaderUnderlineRange>()
        var previousText = ""
        fun flush() {
            if (group.isNotEmpty()) result += fromRanges(group.toList())
            group.clear()
        }
        for ((range, text) in parts.sortedWith(compareBy({ it.first.blockIndex }, { it.first.start }))) {
            var start = 0
            val boundaries = blankLine.findAll(text).map { it.range }.toList()
            for (boundary in boundaries + listOf(text.length..text.length)) {
                val end = boundary.first
                val piece = text.substring(start, end)
                if (piece.isBlank()) flush() else {
                    val previous = group.lastOrNull()
                    if (previous != null && (range.blockIndex != previous.blockIndex + 1 ||
                        previousText.substringAfterLast('\n').isBlank() || piece.substringBefore('\n').isBlank())) flush()
                    group += range.copy(start = range.start + start, end = range.start + end)
                    previousText = piece
                }
                if (end < text.length) flush()
                start = boundary.last + 1
            }
        }
        flush()
        return result
    }

    private fun fromRanges(ranges: List<ReaderUnderlineRange>, quote: String? = null): ReaderUnderline {
        val first = ranges.first()
        return ReaderUnderline(first.blockIndex, first.signature, first.start, first.end, quote,
            ranges.drop(1).takeIf { it.isNotEmpty() })
    }

    fun update(rows: List<ReaderUnderline>, selection: ReaderUnderline, remove: Boolean,
        renderedText: (Int) -> String? = { null }): List<ReaderUnderline> {
        val overlapping = rows.filter { overlaps(it, selection) }
        if (remove) return rows - overlapping.toSet()
        val sources = selection.ranges().associate { it.blockIndex to it.signature }.toMutableMap()
        val compatible = overlapping.filter { mark ->
            val ranges = mark.ranges()
            if (ranges.any { sources[it.blockIndex]?.let { source -> source != it.signature } == true }) false
            else {
                ranges.forEach { sources[it.blockIndex] = it.signature }
                true
            }
        }
        val remaining = rows - compatible.toSet()
        val marks = listOf(selection) + compatible
        val ranges = marks.flatMap { it.ranges() }.groupBy { it.blockIndex to it.signature }.values.map { group ->
            group.first().copy(start = group.minOf { it.start }, end = group.maxOf { it.end })
        }.sortedWith(compareBy({ it.blockIndex }, { it.start }))
        val quotes = ranges.map { range ->
            renderedText(range.blockIndex)?.takeIf { range.end <= it.length }?.substring(range.start, range.end)
        }
        val quote = if (quotes.all { it != null }) quotes.joinToString("\n")
            else marks.firstOrNull { it.ranges() == ranges && it.quote != null }?.quote
        return remaining + fromRanges(ranges, quote)
    }
}

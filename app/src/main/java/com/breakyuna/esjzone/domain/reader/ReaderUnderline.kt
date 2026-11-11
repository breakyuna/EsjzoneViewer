package com.breakyuna.esjzone.domain.reader

import com.google.gson.Gson
import java.security.MessageDigest

/**
 * Offsets cover a whole block, before page splitting. The signature names the
 * coordinate space: [ReaderUnderlines.sourceSignature] means original-text
 * offsets that survive script conversion and name replacement; any other
 * signature is a legacy range in the rendered text it was hashed from.
 */
data class ReaderUnderlineRange(val blockIndex: Int, val signature: String, val start: Int, val end: Int)

/** Optional local display context; older marks may not have it. */
data class ReaderUnderlineInfo(
    val novelId: String? = null,
    val novelName: String? = null,
    val novelUrl: String? = null,
    val chapterName: String? = null,
    val coverUrl: String? = null
) {
    fun withFallback(previous: ReaderUnderlineInfo?): ReaderUnderlineInfo = ReaderUnderlineInfo(
        novelId = novelId?.takeIf(String::isNotBlank) ?: previous?.novelId,
        novelName = novelName?.takeIf(String::isNotBlank) ?: previous?.novelName,
        novelUrl = novelUrl?.takeIf(String::isNotBlank) ?: previous?.novelUrl,
        chapterName = chapterName?.takeIf(String::isNotBlank) ?: previous?.chapterName,
        coverUrl = coverUrl?.takeIf(String::isNotBlank) ?: previous?.coverUrl
    )
}

data class ReaderUnderline(val blockIndex: Int, val signature: String, val start: Int, val end: Int,
    val quote: String? = null, val continuation: List<ReaderUnderlineRange>? = null,
    val info: ReaderUnderlineInfo? = null) {
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
    /** Equals the legacy signature of an untransformed block, whose offsets were already original. */
    fun sourceSignature(block: ReaderBlock): String = signature(block, block.sourceText())

    /**
     * Display offsets for one saved range, or null when it belongs to another text.
     * [offsets] maps the displayed block; legacy ranges match only the exact rendering.
     */
    fun displayRange(range: ReaderUnderlineRange, sourceSignature: String, renderedSignature: String,
        offsets: ReaderTextOffsets?): IntRange? {
        val (start, end) = when (range.signature) {
            sourceSignature -> offsets?.let { it.toDisplay(range.start) to it.toDisplayEnd(range.end) }
                ?: if (sourceSignature == renderedSignature) range.start to range.end else return null
            renderedSignature -> range.start to range.end
            else -> return null
        }
        return if (start < end) start until end else null
    }

    /**
     * Converts a selection made on displayed text. A boundary inside a replaced
     * name expands to the whole original span; unresolved ranges stay legacy.
     */
    fun toSource(mark: ReaderUnderline, resolve: (ReaderUnderlineRange) -> Pair<String, ReaderTextOffsets>?): ReaderUnderline {
        val ranges = mark.ranges().map { range ->
            val (source, offsets) = resolve(range) ?: return@map range
            val start = offsets.toSourceStart(range.start)
            val end = offsets.toSourceEnd(range.end)
            if (start < end) ReaderUnderlineRange(range.blockIndex, source, start, end) else range
        }
        return fromRanges(ranges, mark.quote, mark.info)
    }

    /**
     * Re-anchors legacy rendered-text ranges. [renderings] lists displayed
     * mappings the range may have been saved in; a range whose signature
     * matches none of them is kept untouched so it can migrate later.
     */
    fun migrateToSource(rows: List<ReaderUnderline>, blocks: List<ReaderBlock>,
        renderings: (ReaderBlock) -> List<ReaderTextOffsets>): List<ReaderUnderline> {
        var changed = false
        val migrated = rows.map { mark ->
            val ranges = mark.ranges().map { range ->
                val block = blocks.getOrNull(range.blockIndex) ?: return@map range
                val source = sourceSignature(block)
                if (range.signature == source) return@map range
                val rendering = renderings(block).firstOrNull {
                    range.end <= it.text.length && signature(block, it.text) == range.signature
                } ?: return@map range
                val start = rendering.toSourceStart(range.start)
                val end = rendering.toSourceEnd(range.end)
                if (start >= end) return@map range
                changed = true
                ReaderUnderlineRange(range.blockIndex, source, start, end)
            }
            fromRanges(ranges, mark.quote, mark.info)
        }
        if (!changed) return rows
        val unique = LinkedHashMap<ReaderUnderline, ReaderUnderline>()
        migrated.forEach { mark ->
            val key = mark.copy(info = null)
            val previous = unique[key]
            unique[key] = if (previous == null) mark else previous.copy(
                info = previous.info?.withFallback(mark.info) ?: mark.info)
        }
        return unique.values.toList()
    }

    /** True when any range still uses rendered-text offsets. */
    fun hasLegacyRanges(rows: List<ReaderUnderline>, blocks: List<ReaderBlock>): Boolean = rows.any { mark ->
        mark.ranges().any { range -> blocks.getOrNull(range.blockIndex)?.let { sourceSignature(it) != range.signature } == true }
    }
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

    private fun fromRanges(ranges: List<ReaderUnderlineRange>, quote: String? = null,
        info: ReaderUnderlineInfo? = null): ReaderUnderline {
        val first = ranges.first()
        return ReaderUnderline(first.blockIndex, first.signature, first.start, first.end, quote,
            ranges.drop(1).takeIf { it.isNotEmpty() }, info)
    }

    /** [quoteText] returns the displayed text of one saved range. */
    fun update(rows: List<ReaderUnderline>, selection: ReaderUnderline, remove: Boolean,
        quoteText: (ReaderUnderlineRange) -> String? = { null }): List<ReaderUnderline> {
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
        val quotes = ranges.map(quoteText)
        val quote = if (quotes.all { it != null }) quotes.joinToString("\n")
            else marks.firstOrNull { it.ranges() == ranges && it.quote != null }?.quote
        val previousInfo = compatible.firstNotNullOfOrNull { it.info }
        val info = selection.info?.withFallback(previousInfo) ?: previousInfo
        return remaining + fromRanges(ranges, quote, info)
    }
}

package com.breakyuna.esjzone.ui.reader

import com.breakyuna.esjzone.domain.reader.ReaderTextOffsets

/** Follow hard lines across styled text runs, before layout can introduce soft wraps. */
internal class ReaderLineIndent {
    private var atLineStart = true

    fun retainedRanges(text: String): List<IntRange>? {
        var ranges: MutableList<IntRange>? = null
        var retainedStart = 0
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (atLineStart && char.isIndentSpace()) {
                val kept = ranges ?: mutableListOf<IntRange>().also { ranges = it }
                if (retainedStart < index) kept.add(retainedStart until index)
                do {
                    index++
                } while (index < text.length && text[index].isIndentSpace())
                retainedStart = index
            } else {
                atLineStart = char.isHardBreak()
                index++
            }
        }
        if (retainedStart < text.length) ranges?.add(retainedStart until text.length)
        return ranges
    }

    fun trim(text: String): String {
        val ranges = retainedRanges(text) ?: return text
        return buildString { ranges.forEach { append(text, it.first, it.last + 1) } }
    }
}

private fun Char.isHardBreak(): Boolean = this == '\n' || this == '\r' || this == '\u2028' || this == '\u2029'

private fun Char.isIndentSpace(): Boolean = isWhitespace() && !isHardBreak()

/** Leave unchanged source blocks out of the display mapping cache. */
internal fun readerUnindentedOffsets(text: String, offsets: ReaderTextOffsets? = null): ReaderTextOffsets? {
    val ranges = ReaderLineIndent().retainedRanges(text) ?: return offsets
    val starts = IntArray(ranges.sumOf { it.last - it.first + 1 })
    val ends = IntArray(starts.size)
    val displayed = buildString(starts.size) {
        var index = 0
        for (range in ranges) {
            append(text, range.first, range.last + 1)
            for (source in range) {
                starts[index] = offsets?.sourceStarts?.get(source) ?: source
                ends[index] = offsets?.sourceEnds?.get(source) ?: source + 1
                index++
            }
        }
    }
    return ReaderTextOffsets(displayed, starts, ends, offsets?.sourceLength ?: text.length)
}

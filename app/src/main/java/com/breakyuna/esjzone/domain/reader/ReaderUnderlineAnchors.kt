package com.breakyuna.esjzone.domain.reader

internal class ReaderUnderlineAnchors(
    private val blocks: List<ReaderBlock>,
    private val legacyPositions: ReaderLegacyPositions? = null,
    renderings: List<(ReaderBlock) -> ReaderTextOffsets> = emptyList(),
    private val sourceTextIndex: () -> NormalizedDocumentText = { buildNormalizedDocumentText(blocks) }
) {
    private val signatures = mutableMapOf<Int, String>()
    private val renderedIndexes = renderings.map { render ->
        lazy {
            val offsets = blocks.map { block ->
                if (block is ReaderBlock.Paragraph || block is ReaderBlock.Text) render(block) else null
            }
            val stream = buildNormalizedDocumentText(blocks) { index, _ -> offsets[index]?.text.orEmpty() }
            stream to offsets
        }
    }

    fun resolve(mark: ReaderUnderline): ReaderUnderline? {
        val ranges = resolveLegacyRanges(mark) ?: resolveQuote(mark) ?: return null
        return ReaderUnderlines.fromRanges(ranges, mark.quote, mark.info)
    }

    private fun resolveLegacyRanges(mark: ReaderUnderline): List<ReaderUnderlineRange>? {
        val legacy = legacyPositions ?: return null
        val ranges = mutableListOf<ReaderUnderlineRange>()
        for (saved in mark.ranges()) {
            val old = legacy.textBlocks[saved.blockIndex]?.takeIf { it.sourceSignature == saved.signature }
                ?: return null
            val start = old.streamOffset(saved.start) ?: return null
            val end = old.streamOffset(saved.end) ?: return null
            for (range in rangesFor(sourceTextIndex(), start, end) ?: return null) {
                val previous = ranges.lastOrNull()
                if (previous == null || previous.blockIndex != range.blockIndex) {
                    ranges.add(range)
                } else {
                    val source = blocks[range.blockIndex].sourceText()
                    if (range.start > previous.end && source.substring(previous.end, range.start).isNotBlank()) {
                        return null
                    }
                    ranges[ranges.lastIndex] = previous.copy(end = maxOf(previous.end, range.end))
                }
            }
        }
        return ranges
    }

    private fun resolveQuote(mark: ReaderUnderline): List<ReaderUnderlineRange>? {
        val quote = mark.quote?.filterNot(Char::isWhitespace)?.takeIf { it.isNotEmpty() } ?: return null
        var unique: List<ReaderUnderlineRange>? = null
        val indexes = sequence {
            yield(sourceTextIndex() to emptyList<ReaderTextOffsets?>())
            for (index in renderedIndexes) yield(index.value)
        }
        for ((stream, offsets) in indexes) {
            var start = stream.text.indexOf(quote)
            while (start >= 0) {
                val ranges = rangesFor(stream, start, start + quote.length, offsets)
                if (ranges != null) {
                    if (unique != null && unique != ranges) return null
                    unique = ranges
                }
                start = stream.text.indexOf(quote, start + 1)
            }
        }
        return unique
    }

    private fun rangesFor(
        stream: NormalizedDocumentText,
        start: Int,
        end: Int,
        offsets: List<ReaderTextOffsets?> = emptyList()
    ): List<ReaderUnderlineRange>? {
        if (start !in stream.text.indices || end !in (start + 1)..stream.text.length) return null
        val ranges = mutableListOf<ReaderUnderlineRange>()
        var cursor = start
        while (cursor < end) {
            val index = stream.blockIndices[cursor]
            var next = cursor + 1
            while (next < end && stream.blockIndices[next] == index) next++
            val first = stream.sourceOffsets[cursor]
            val lastExclusive = stream.sourceOffsets[next - 1] + 1
            val mapping = offsets.getOrNull(index)
            val sourceStart = mapping?.toSourceStart(first) ?: first
            val sourceEnd = mapping?.toSourceEnd(lastExclusive) ?: lastExclusive
            if (sourceEnd <= sourceStart) return null
            val signature = signatures.getOrPut(index) { ReaderUnderlines.sourceSignature(blocks[index]) }
            ranges += ReaderUnderlineRange(index, signature, sourceStart, sourceEnd)
            cursor = next
        }
        return ranges
    }
}

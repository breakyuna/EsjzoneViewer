package com.breakyuna.esjzone.domain.reader

import com.google.gson.Gson
import java.io.Serializable

/** A position belongs to one immutable chapter snapshot. Offsets are UTF-16. */
data class ReaderAnchor(
    val chapterKey: String,
    val fingerprint: String,
    val blockIndex: Int,
    val offset: Int = 0,
    val kind: String = "text",
    val fraction: Float = 0f,
    val version: Int = 1,
    /** Normalized text excerpt starting at this anchor for cross-parser re-anchoring. */
    val textQuote: String? = null,
    /** Normalized text immediately preceding this anchor to disambiguate repeated phrases. */
    val textPrefix: String? = null,
    /** Normalized character fraction [0, 1] in the chapter text stream. */
    val normalizedOffset: Float? = null
) {
    fun matches(key: String, document: ReaderChapterDocument): Boolean =
        chapterKey == key && fingerprint.isNotBlank() && fingerprint == document.contentFingerprint &&
            when (kind) {
                "heading" -> blockIndex == -1
                "end" -> true
                "text" -> document.blocks.getOrNull(blockIndex).let { it is ReaderBlock.Paragraph || it is ReaderBlock.Text } &&
                    offset in 0..(document.textPrefixLengths[blockIndex + 1] - document.textPrefixLengths[blockIndex])
                "image" -> document.blocks.getOrNull(blockIndex).let { it is ReaderBlock.Image || it == ReaderBlock.LineBreak }
                else -> false
            }

    /** Captures layout-independent text context so this anchor survives future parser upgrades. */
    fun withTextContext(document: ReaderChapterDocument): ReaderAnchor {
        if (kind != "text" || !matches(chapterKey, document)) return this
        val stream = document.normalizedTextIndex
        if (stream.text.isEmpty()) return this
        val streamPos = stream.findStreamIndex(blockIndex, offset)
        val quote = stream.text.substring(streamPos, (streamPos + QUOTE_LENGTH).coerceAtMost(stream.text.length))
            .takeIf { it.isNotEmpty() } ?: return this
        val prefix = stream.text.substring((streamPos - PREFIX_LENGTH).coerceAtLeast(0), streamPos)
            .takeIf { it.isNotEmpty() }
        val ratio = (streamPos.toFloat() / stream.text.length).coerceIn(0f, 1f)
        return copy(textQuote = quote, textPrefix = prefix, normalizedOffset = ratio)
    }

    /**
     * Resolves an exact match first; if a parser upgrade changed the snapshot
     * fingerprint, re-anchors via saved text context without losing position.
     */
    fun resolveIn(key: String, document: ReaderChapterDocument): ReaderAnchor? {
        if (matches(key, document)) return this
        if (chapterKey != key || document.contentFingerprint.isBlank()) return null
        if (kind == "heading") {
            return copy(fingerprint = document.contentFingerprint, blockIndex = -1, offset = 0)
        }
        if (kind == "end") {
            return copy(fingerprint = document.contentFingerprint)
        }
        val legacy = document.legacyPositions?.takeIf { it.fingerprint == fingerprint }
        if (kind == "image") {
            val imageIndex = legacy?.imageBlocks?.get(blockIndex) ?: return null
            if (document.blocks.getOrNull(imageIndex) !is ReaderBlock.Image) return null
            return copy(fingerprint = document.contentFingerprint, blockIndex = imageIndex)
        }
        if (kind != "text") return null

        val oldBlock = legacy?.textBlocks?.get(blockIndex)
        if (oldBlock == null && textQuote.isNullOrBlank()) return null
        val stream = document.normalizedTextIndex
        if (stream.text.isEmpty()) return null
        oldBlock?.let { block ->
            val position = block.streamOffset(offset) ?: return@let
            val atEnd = position == block.normalizedEnd
            val index = if (atEnd) position - 1 else position
            if (index in stream.text.indices) {
                return copy(
                    fingerprint = document.contentFingerprint,
                    blockIndex = stream.blockIndices[index],
                    offset = stream.sourceOffsets[index] + if (atEnd) 1 else 0
                )
            }
        }
        val quote = textQuote?.filterNot(Char::isWhitespace)?.takeIf { it.isNotEmpty() } ?: return null
        val candidates = findQuotePositions(stream.text, quote)
            .ifEmpty {
                if (quote.length > SHORT_QUOTE_LENGTH) {
                    findQuotePositions(stream.text, quote.substring(0, SHORT_QUOTE_LENGTH))
                } else emptyList()
            }
        if (candidates.isEmpty()) return null

        val prefix = textPrefix?.filterNot(Char::isWhitespace).orEmpty()
        val expectedPos = normalizedOffset?.let { (it * stream.text.length).toInt() }
        val best = candidates.maxWithOrNull(
            compareBy<Int> { pos ->
                if (prefix.isEmpty()) 0 else matchingPrefixLength(stream.text, pos, prefix)
            }.thenBy { pos ->
                if (expectedPos == null) 0 else -kotlin.math.abs(pos - expectedPos)
            }
        ) ?: return null

        val resolvedBlock = stream.blockIndices[best]
        val resolvedOffset = stream.sourceOffsets[best]
        return copy(
            fingerprint = document.contentFingerprint,
            blockIndex = resolvedBlock,
            offset = resolvedOffset
        )
    }

    companion object {
        private const val QUOTE_LENGTH = 32
        private const val SHORT_QUOTE_LENGTH = 12
        private const val PREFIX_LENGTH = 16
        private val FINGERPRINT_PATTERN = Regex("[0-9a-f]{64}")
        private val VALID_KINDS = setOf("heading", "text", "image", "end")
        private val gson = Gson()
        fun encode(anchor: ReaderAnchor?): String? = anchor?.let(gson::toJson)
        fun decode(value: String?): ReaderAnchor? {
            if (value.isNullOrBlank()) return null
            val anchor = runCatching { gson.fromJson(value, ReaderAnchor::class.java) }.getOrNull()
                ?: return null
            val chapterKey: String? = anchor.chapterKey
            val fingerprint: String? = anchor.fingerprint
            val kind: String? = anchor.kind
            if (anchor.version != 1 || chapterKey.isNullOrBlank() ||
                fingerprint == null || !FINGERPRINT_PATTERN.matches(fingerprint)) {
                return null
            }
            if (anchor.blockIndex < -1 || anchor.offset < 0 ||
                !anchor.fraction.isFinite() || anchor.fraction !in 0f..1f) {
                return null
            }
            if (kind == null || kind !in VALID_KINDS) return null
            if (kind == "heading" && anchor.blockIndex != -1) return null
            if (kind != "heading" && kind != "end" && anchor.blockIndex < 0) return null
            val normalized = anchor.normalizedOffset
            if (normalized != null && (!normalized.isFinite() || normalized !in 0f..1f)) return null
            return anchor
        }

        private fun findQuotePositions(haystack: String, needle: String): List<Int> = buildList {
            var from = 0
            while (from <= haystack.length - needle.length) {
                val found = haystack.indexOf(needle, from)
                if (found < 0) break
                add(found)
                from = found + 1
            }
        }

        private fun matchingPrefixLength(haystack: String, pos: Int, prefix: String): Int {
            var matched = 0
            var h = pos - 1
            var p = prefix.length - 1
            while (h >= 0 && p >= 0 && haystack[h] == prefix[p]) {
                matched++
                h--
                p--
            }
            return matched
        }
    }
}

/** Positions from the saved body before re-parsing. */
data class ReaderLegacyPositions(
    val fingerprint: String,
    val textBlocks: Map<Int, ReaderLegacyTextBlock>,
    val imageBlocks: Map<Int, Int>? = null
) : Serializable

data class ReaderLegacyTextBlock(
    val normalizedStart: Int,
    val sourceLength: Int,
    val whitespaceOffsets: List<Int>,
    val sourceSignature: String
) : Serializable {
    internal val normalizedEnd: Int get() = normalizedStart + sourceLength - whitespaceOffsets.size

    internal fun streamOffset(offset: Int): Int? {
        if (offset !in 0..sourceLength) return null
        val whitespaceIndex = whitespaceOffsets.binarySearch(offset)
        val precedingWhitespace = if (whitespaceIndex >= 0) whitespaceIndex else -whitespaceIndex - 1
        return normalizedStart + offset - precedingWhitespace
    }
}

internal class NormalizedDocumentText(
    val text: String,
    val blockIndices: IntArray,
    val sourceOffsets: IntArray
) {
    fun findStreamIndex(targetBlock: Int, targetOffset: Int): Int {
        var start = 0
        var end = blockIndices.size
        while (start < end) {
            val middle = start + (end - start) / 2
            val block = blockIndices[middle]
            if (block < targetBlock || (block == targetBlock && sourceOffsets[middle] < targetOffset)) {
                start = middle + 1
            } else {
                end = middle
            }
        }
        return start
    }
}

internal fun buildNormalizedDocumentText(
    blocks: List<ReaderBlock>,
    textForBlock: (Int, ReaderBlock) -> String = { _, block -> block.sourceText() }
): NormalizedDocumentText {
    val sb = StringBuilder()
    val blockList = mutableListOf<Int>()
    val offsetList = mutableListOf<Int>()
    blocks.forEachIndexed { blockIndex, block ->
        if (block !is ReaderBlock.Paragraph && block !is ReaderBlock.Text) return@forEachIndexed
        val raw = textForBlock(blockIndex, block)
        raw.forEachIndexed { offset, ch ->
            if (!ch.isWhitespace()) {
                sb.append(ch)
                blockList.add(blockIndex)
                offsetList.add(offset)
            }
        }
    }
    return NormalizedDocumentText(
        text = sb.toString(),
        blockIndices = blockList.toIntArray(),
        sourceOffsets = offsetList.toIntArray()
    )
}

fun ReaderBlock.sourceText(): String = when (this) {
    is ReaderBlock.Paragraph -> parts.joinToString("") { it.value }
    is ReaderBlock.Text -> value
    ReaderBlock.LineBreak -> "\n"
    is ReaderBlock.Image -> ""
}

/** Original character boundaries for each displayed character, recorded during conversion. */
data class ReaderTextOffsets(val text: String, val sourceStarts: IntArray, val sourceEnds: IntArray, val sourceLength: Int) {
    fun toSource(displayOffset: Int): Int = when {
        displayOffset <= 0 -> 0
        displayOffset >= text.length -> sourceLength
        else -> sourceStarts[displayOffset]
    }
    fun toDisplay(sourceOffset: Int): Int {
        val index = sourceEnds.indexOfFirst { it > sourceOffset.coerceIn(0, sourceLength) }
        return if (index < 0) text.length else index
    }
    /**
     * Source start of the displayed character at [displayStart]. Unlike [toSource],
     * text removed before the first visible character is not included.
     */
    fun toSourceStart(displayStart: Int): Int =
        if (displayStart in text.indices) sourceStarts[displayStart] else toSource(displayStart)
    /** Exclusive source end; a display end inside a replaced span extends to the whole span. */
    fun toSourceEnd(displayEnd: Int): Int = when {
        displayEnd <= 0 || text.isEmpty() -> 0
        else -> sourceEnds[minOf(displayEnd, text.length) - 1]
    }
    /** Exclusive display end: every displayed character that starts before [sourceEnd]. */
    fun toDisplayEnd(sourceEnd: Int): Int {
        val end = sourceEnd.coerceIn(0, sourceLength)
        var index = text.length
        while (index > 0 && sourceStarts[index - 1] >= end) index--
        return index
    }
    companion object {
        fun identity(text: String) = ReaderTextOffsets(text, IntArray(text.length) { it }, IntArray(text.length) { it + 1 }, text.length)
        fun join(parts: List<ReaderTextOffsets>): ReaderTextOffsets {
            if (parts.size == 1) return parts[0]
            val text = parts.joinToString("") { it.text }
            val starts = IntArray(text.length)
            val ends = IntArray(text.length)
            var source = 0
            var display = 0
            parts.forEach { part ->
                part.text.indices.forEach { i -> starts[display + i] = source + part.sourceStarts[i]; ends[display + i] = source + part.sourceEnds[i] }
                source += part.sourceLength
                display += part.text.length
            }
            return ReaderTextOffsets(text, starts, ends, source)
        }
    }
}

/** Shared fallback calculation for every book-progress surface. */
fun readerBookProgress(chapterIndex: Int, totalChapters: Int, chapterProgress: Float): Float {
    val fraction = chapterProgress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    if (chapterIndex < 0 || totalChapters <= 0) return fraction
    return ((chapterIndex + fraction) / totalChapters).coerceIn(0f, 1f)
}

fun readerChapterProgress(document: ReaderChapterDocument, anchor: ReaderAnchor): Float {
    if (anchor.kind == "end") return 1f
    if (anchor.kind == "heading") return 0f
    val prefix = document.textPrefixLengths
    val total = prefix.last()
    if (total > 0) {
        val index = anchor.blockIndex.coerceIn(0, document.blocks.size)
        val before = prefix[index]
        val length = if (index < document.blocks.size) prefix[index + 1] - before else 0
        return ((before + anchor.offset.coerceIn(0, length)).toFloat() / total).coerceIn(0f, 1f)
    }
    return ((anchor.blockIndex + anchor.fraction) / document.blocks.size.coerceAtLeast(1)).coerceIn(0f, 1f)
}

/** Approximate layout fallback follows the same source boundary as the precise anchor. */
fun readerContentPosition(document: ReaderChapterDocument, anchor: ReaderAnchor): Float {
    if (anchor.kind == "heading") return -1f
    if (anchor.kind == "end") return document.blocks.size.toFloat()
    val index = anchor.blockIndex.coerceIn(0, document.blocks.size)
    val prefix = document.textPrefixLengths
    val length = if (index < document.blocks.size) prefix[index + 1] - prefix[index] else 0
    val fraction = if (length > 0) anchor.offset.coerceIn(0, length).toFloat() / length
        else anchor.fraction
    return index + fraction
}

/** Highlight ranges refer to source blocks, before pagination or script conversion. */
data class ReaderHighlight(val blockIndex: Int, val start: Int, val end: Int)
data class ReaderSearchHit(val anchor: ReaderAnchor, val highlights: List<ReaderHighlight>, val snippet: String)

fun searchReaderDocument(
    document: ReaderChapterDocument,
    chapterKey: String,
    query: String,
    mapping: (ReaderBlock) -> ReaderTextOffsets = { ReaderTextOffsets.identity(it.sourceText()) }
): List<ReaderSearchHit> {
    if (query.isBlank()) return emptyList()
    val mapped = document.blocks.map(mapping)
    val starts = mutableListOf<Int>()
    val text = buildString {
        mapped.forEachIndexed { index, part ->
            if (index > 0) append('\n')
            starts += length
            append(part.text)
        }
    }
    return buildList {
        var from = 0
        while (from <= text.length - query.length) {
            val start = text.indexOf(query, from, ignoreCase = true)
            if (start < 0) break
            val end = start + query.length
            val ranges = mapped.mapIndexedNotNull { index, part ->
                if (document.blocks[index] !is ReaderBlock.Paragraph && document.blocks[index] !is ReaderBlock.Text) return@mapIndexedNotNull null
                val a = maxOf(start, starts[index]) - starts[index]
                val b = minOf(end, starts[index] + part.text.length) - starts[index]
                if (a >= b) null else ReaderHighlight(index, part.toSource(a),
                    if (b == 0) 0 else part.sourceEnds[b - 1])
            }
            ranges.firstOrNull()?.let { first ->
                add(ReaderSearchHit(ReaderAnchor(chapterKey, document.contentFingerprint, first.blockIndex, first.start), ranges,
                    text.substring((start - 24).coerceAtLeast(0), (end + 24).coerceAtMost(text.length))))
            }
            from = end
        }
    }
}

package com.breakyuna.esjzone.domain.reader

import com.google.gson.Gson

/** A position belongs to one immutable chapter snapshot. Offsets are UTF-16. */
data class ReaderAnchor(
    val chapterKey: String,
    val fingerprint: String,
    val blockIndex: Int,
    val offset: Int = 0,
    val kind: String = "text",
    val fraction: Float = 0f,
    val version: Int = 1
) {
    fun matches(key: String, document: ReaderChapterDocument): Boolean =
        chapterKey == key && fingerprint.isNotBlank() && fingerprint == document.contentFingerprint &&
            when (kind) {
                "heading" -> blockIndex == -1
                "end" -> true
                "text" -> document.blocks.getOrNull(blockIndex).let { it is ReaderBlock.Paragraph || it is ReaderBlock.Text } &&
                    offset in 0..document.blocks[blockIndex].sourceText().length
                "image" -> document.blocks.getOrNull(blockIndex).let { it is ReaderBlock.Image || it == ReaderBlock.LineBreak }
                else -> false
            }

    companion object {
        private val gson = Gson()
        fun encode(anchor: ReaderAnchor?): String? = anchor?.let(gson::toJson)
        fun decode(value: String?): ReaderAnchor? = runCatching {
            if (value == null) return@runCatching null
            requireNotNull(gson.fromJson(value, ReaderAnchor::class.java)).also {
                require(it.version == 1 && it.chapterKey.isNotBlank() && it.fingerprint.matches(Regex("[0-9a-f]{64}")))
                require(it.blockIndex >= -1 && it.offset >= 0 && it.fraction.isFinite() && it.fraction in 0f..1f)
                require(it.kind in setOf("heading", "text", "image", "end"))
                require(it.kind != "heading" || it.blockIndex == -1)
                require(it.kind in setOf("heading", "end") || it.blockIndex >= 0)
            }
        }.getOrNull()
    }
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
    companion object {
        fun identity(text: String) = ReaderTextOffsets(text, IntArray(text.length) { it }, IntArray(text.length) { it + 1 }, text.length)
        fun join(parts: List<ReaderTextOffsets>): ReaderTextOffsets {
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
    val lengths = document.blocks.map { if (it is ReaderBlock.LineBreak) 0 else it.sourceText().length }
    val total = lengths.sum()
    if (total > 0) {
        val before = lengths.take(anchor.blockIndex).sum()
        return ((before + anchor.offset.coerceIn(0, lengths.getOrElse(anchor.blockIndex) { 0 })).toFloat() / total).coerceIn(0f, 1f)
    }
    return ((anchor.blockIndex + anchor.fraction) / document.blocks.size.coerceAtLeast(1)).coerceIn(0f, 1f)
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

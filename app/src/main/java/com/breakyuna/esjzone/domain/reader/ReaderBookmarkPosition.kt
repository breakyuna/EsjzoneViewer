package com.breakyuna.esjzone.domain.reader

/**
 * Bookmarks reuse [ReaderAnchor]: a source block index plus a UTF-16 offset in
 * the original text, so script conversion, name replacement, indentation and
 * pagination never move the saved paragraph.
 */
object ReaderBookmarkPosition {
    const val EXCERPT_LENGTH = 40
    private val whitespace = Regex("\\s+")

    /** Displayed text from the anchor onward; skips images and blank blocks. */
    fun excerpt(
        document: ReaderChapterDocument,
        anchor: ReaderAnchor,
        displayText: (ReaderBlock) -> ReaderTextOffsets?,
        maxLength: Int = EXCERPT_LENGTH
    ): String? {
        val start = if (anchor.kind == "heading") 0 else anchor.blockIndex.coerceAtLeast(0)
        for (index in start until document.blocks.size) {
            val block = document.blocks[index]
            if (block !is ReaderBlock.Paragraph && block !is ReaderBlock.Text) continue
            val mapped = displayText(block) ?: ReaderTextOffsets.identity(block.sourceText())
            val from = if (index == anchor.blockIndex && anchor.kind == "text") mapped.toDisplay(anchor.offset) else 0
            val text = mapped.text.substring(from.coerceIn(0, mapped.text.length)).replace(whitespace, " ").trim()
            if (text.isEmpty()) continue
            if (text.codePointCount(0, text.length) <= maxLength) return text
            return text.substring(0, text.offsetByCodePoints(0, maxLength)).trimEnd() + "…"
        }
        return null
    }

    /**
     * A changed chapter body invalidates the exact anchor. Keep its paragraph
     * index instead, clamped to the current body; null means the exact anchor applies.
     */
    fun fallbackContentPosition(document: ReaderChapterDocument, chapterKey: String, anchor: ReaderAnchor?): Float? {
        if (anchor == null || anchor.matches(chapterKey, document) || document.blocks.isEmpty()) return null
        return when (anchor.kind) {
            "heading" -> -1f
            "end" -> document.blocks.lastIndex.toFloat()
            else -> {
                val index = anchor.blockIndex.coerceIn(0, document.blocks.lastIndex)
                val prefix = document.textPrefixLengths
                val length = prefix[index + 1] - prefix[index]
                val fraction = if (anchor.kind == "text" && length > 0) {
                    anchor.offset.coerceIn(0, length).toFloat() / length
                } else 0f
                index + fraction.coerceAtMost(0.999f)
            }
        }
    }
}

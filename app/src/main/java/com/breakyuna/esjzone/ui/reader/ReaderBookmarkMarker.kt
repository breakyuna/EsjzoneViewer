package com.breakyuna.esjzone.ui.reader

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.domain.reader.ReaderAnchor
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderChapterDocument
import com.breakyuna.esjzone.domain.reader.sourceText

/**
 * A bookmarked paragraph shows a small red ribbon beside its first line. The
 * ribbon is drawn in the horizontal page margin, so it never covers text and
 * never takes part in measurement or pagination.
 */
internal object ReaderBookmarkMarker {
    private val color = Color(0xFFE53935)

    /**
     * The paragraph a bookmark pins: the first non-blank text block from its
     * anchor onward, the same paragraph its saved excerpt starts with (a
     * heading anchor starts at the first block). Chapter-only and stale
     * anchors show no marker.
     */
    fun paragraph(document: ReaderChapterDocument, chapterKey: String, anchor: ReaderAnchor?): Int? {
        if (anchor == null || anchor.kind == "end") return null
        if (!anchor.copy(chapterKey = chapterKey).matches(chapterKey, document)) return null
        for (index in anchor.blockIndex.coerceAtLeast(0) until document.blocks.size) {
            val block = document.blocks[index]
            if ((block is ReaderBlock.Paragraph || block is ReaderBlock.Text) && block.sourceText().isNotBlank()) return index
        }
        return null
    }

    /** Only the text piece that starts the paragraph carries the marker, so a split paragraph shows it once. */
    fun showsOn(blockIndex: Int, startOffset: Int, bookmarkedBlocks: Set<Int>): Boolean =
        blockIndex in bookmarkedBlocks && startOffset == 0

    /**
     * Ribbon bounds in text-local pixels. [textWidth] is the text column width
     * and [marginPx] the free space beyond it; the ribbon keeps [gapPx] from
     * both the text and the screen edge and is centred on the glyphs of the line.
     */
    fun bounds(textWidth: Float, lineTop: Float, lineBottom: Float, baseline: Float, fontSizePx: Float,
        marginPx: Float, gapPx: Float, rightToLeft: Boolean = false): Rect? {
        val width = minOf(fontSizePx * 0.42f, marginPx - 2 * gapPx)
        if (width < 1f || lineBottom <= lineTop || fontSizePx <= 0f) return null
        val height = minOf(width * 1.6f, fontSizePx * 0.8f, lineBottom - lineTop)
        // CJK glyph boxes sit about 0.88em above the baseline; centre on that body, not the spaced line box.
        val center = (baseline - fontSizePx * 0.38f).coerceIn(lineTop + height / 2, lineBottom - height / 2)
        val left = if (rightToLeft) -gapPx - width else textWidth + gapPx
        return Rect(left, center - height / 2, left + width, center + height / 2)
    }

    fun DrawScope.drawFirstLineMarker(layout: TextLayoutResult, marginPx: Float) {
        if (layout.lineCount == 0) return
        val style = layout.layoutInput.style
        val fontSize = if (style.fontSize.isSp) style.fontSize.toPx() else layout.getLineBottom(0) - layout.getLineTop(0)
        val constraints = layout.layoutInput.constraints
        val textWidth = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else layout.size.width.toFloat()
        val rect = bounds(textWidth, layout.getLineTop(0), layout.getLineBottom(0), layout.getLineBaseline(0),
            fontSize, marginPx, 3.dp.toPx(),
            layout.getParagraphDirection(0) == ResolvedTextDirection.Rtl) ?: return
        val notch = rect.height * 0.28f
        val path = Path().apply {
            moveTo(rect.left, rect.top)
            lineTo(rect.right, rect.top)
            lineTo(rect.right, rect.bottom)
            lineTo(rect.center.x, rect.bottom - notch)
            lineTo(rect.left, rect.bottom)
            close()
        }
        drawPath(path, color)
    }
}

package com.breakyuna.esjzone.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import com.breakyuna.esjzone.domain.reader.ReaderUnderline

internal val LocalReaderUnderlineSelection = staticCompositionLocalOf<ReaderUnderlineSelection?> { null }
internal val LocalReaderUnderlineChapter = staticCompositionLocalOf { "" }

/** Shares the drag across mounted paragraphs; each paged canvas owns a separate instance. */
class ReaderUnderlineSelection {
    internal class Segment(
        val chapter: String,
        val block: Int,
        val signature: String,
        val offset: Int,
        val text: String,
        val save: (ReaderUnderline, Boolean) -> Unit
    ) {
        var coordinates: LayoutCoordinates? = null
        var layout: TextLayoutResult? = null
    }

    private val segments = mutableSetOf<Segment>()
    private var anchor: Pair<Segment, TextRange>? = null
    internal var ranges by mutableStateOf<Map<Segment, TextRange>>(emptyMap())
        private set
    internal var target by mutableStateOf<Pair<Segment, Offset>?>(null)
        private set

    internal fun register(segment: Segment) { segments += segment }
    internal fun unregister(segment: Segment) {
        segments -= segment
        if (anchor?.first == segment || target?.first == segment) cancel()
    }

    internal fun start(segment: Segment, point: Offset) {
        val layout = segment.layout ?: return
        if (segment.text.isEmpty()) return
        cancel()
        anchor = segment to readerUnderlineCharacterAt(layout, point)
        move(segment, point)
    }

    internal fun move(origin: Segment, point: Offset) {
        val (first, firstRange) = anchor ?: return
        val originCoordinates = origin.coordinates?.takeIf { it.isAttached } ?: return
        val windowPoint = originCoordinates.localToWindow(point)
        val candidates = segments.filter {
            it.chapter == first.chapter && it.text.isNotEmpty() && it.layout != null &&
                it.coordinates?.isAttached == true && !it.coordinates!!.boundsInWindow().isEmpty
        }
        // Paragraphs are stacked vertically. A short line must still receive a
        // drag in its trailing whitespace instead of snapping to a longer line above.
        val last = candidates.minWithOrNull(compareBy<Segment> {
            val bounds = it.coordinates!!.boundsInWindow()
            val dy = windowPoint.y - windowPoint.y.coerceIn(bounds.top, bounds.bottom)
            dy * dy
        }.thenBy {
            val bounds = it.coordinates!!.boundsInWindow()
            val dx = windowPoint.x - windowPoint.x.coerceIn(bounds.left, bounds.right)
            dx * dx
        }) ?: return
        val local = last.coordinates!!.windowToLocal(windowPoint)
        val lastRange = readerUnderlineCharacterAt(last.layout!!, local)
        val ordered = candidates.sortedWith(compareBy({ it.block }, { it.offset }))
        val firstIndex = ordered.indexOf(first)
        val lastIndex = ordered.indexOf(last)
        if (firstIndex < 0) return
        val forward = firstIndex <= lastIndex
        ranges = ordered.slice(minOf(firstIndex, lastIndex)..maxOf(firstIndex, lastIndex))
            .associateWith { segment ->
                when {
                    first == last -> TextRange(minOf(firstRange.min, lastRange.min), maxOf(firstRange.max, lastRange.max))
                    segment == first -> if (forward) TextRange(firstRange.min, segment.text.length) else TextRange(0, firstRange.max)
                    segment == last -> if (forward) TextRange(0, lastRange.max) else TextRange(lastRange.min, segment.text.length)
                    else -> TextRange(0, segment.text.length)
                }
            }
        // Snap the lens to the touched glyph so its preview and selection agree.
        target = last to last.layout!!.getBoundingBox(lastRange.min).center
    }

    internal fun finish() {
        val selected = ranges.entries.toList()
        cancel()
        // Page fragments use full-paragraph offsets and must persist as one range.
        selected.groupBy { it.key.block to it.key.signature }.values.forEach { group ->
            val segment = group.first().key
            segment.save(ReaderUnderline(segment.block, segment.signature,
                group.minOf { it.key.offset + it.value.min },
                group.maxOf { it.key.offset + it.value.max }), false)
        }
    }

    internal fun cancel() {
        anchor = null
        ranges = emptyMap()
        target = null
    }
}

internal fun readerUnderlineCharacterAt(layout: TextLayoutResult, point: Offset): TextRange {
    val text = layout.layoutInput.text.text
    if (text.isEmpty()) return TextRange.Zero
    var index = layout.getOffsetForPosition(point).coerceIn(0, text.lastIndex)
    if (index > 0 && !layout.getBoundingBox(index).contains(point) &&
        layout.getBoundingBox(index - 1).contains(point)) index--
    return readerUnderlineCharacterRange(text, index)
}

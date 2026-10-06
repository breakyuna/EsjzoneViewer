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
import com.breakyuna.esjzone.domain.reader.ReaderUnderlineRange
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines

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
    private var orderedSegments: List<Segment>? = null
    private val candidates = mutableListOf<Segment>()
    private val previousCandidates = mutableListOf<Segment>()
    private var anchor: Pair<Segment, TextRange>? = null
    private var lastSegment: Segment? = null
    private var lastRange: TextRange? = null
    internal var ranges by mutableStateOf<Map<Segment, TextRange>>(emptyMap())
        private set
    internal var target by mutableStateOf<Pair<Segment, Offset>?>(null)
        private set

    internal fun register(segment: Segment) {
        if (segments.add(segment)) orderedSegments = null
    }
    internal fun unregister(segment: Segment) {
        if (segments.remove(segment)) orderedSegments = null
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
        candidates.clear()
        var nearest: Segment? = null
        var nearestY = Float.POSITIVE_INFINITY
        var nearestX = Float.POSITIVE_INFINITY
        // Paragraphs are stacked vertically. A short line must still receive a
        // drag in its trailing whitespace instead of snapping to a longer line above.
        // Query each current bound once, retaining registration order for tied distances.
        for (segment in segments) {
            if (segment.chapter != first.chapter || segment.text.isEmpty() || segment.layout == null) continue
            val coordinates = segment.coordinates?.takeIf { it.isAttached } ?: continue
            val bounds = coordinates.boundsInWindow()
            if (bounds.isEmpty) continue
            candidates += segment
            val dy = windowPoint.y - windowPoint.y.coerceIn(bounds.top, bounds.bottom)
            val dx = windowPoint.x - windowPoint.x.coerceIn(bounds.left, bounds.right)
            val distanceY = dy * dy
            val distanceX = dx * dx
            if (nearest == null || distanceY < nearestY || distanceY == nearestY && distanceX < nearestX) {
                nearest = segment
                nearestY = distanceY
                nearestX = distanceX
            }
        }
        val last = nearest ?: return
        val local = last.coordinates!!.windowToLocal(windowPoint)
        val endRange = readerUnderlineCharacterAt(last.layout!!, local)
        val candidatesChanged = orderedSegments == null || candidates != previousCandidates
        if (last != lastSegment || endRange != lastRange || candidatesChanged) {
            // Visibility may change during scrolling, so cache only the ordering, not bounds.
            val ordered = if (candidatesChanged) candidates.sortedWith(compareBy({ it.block }, { it.offset }))
                .also { orderedSegments = it } else orderedSegments!!
            val firstIndex = ordered.indexOf(first)
            val lastIndex = ordered.indexOf(last)
            if (firstIndex < 0) return
            val forward = firstIndex <= lastIndex
            ranges = buildMap {
                for (index in minOf(firstIndex, lastIndex)..maxOf(firstIndex, lastIndex)) {
                    val segment = ordered[index]
                    put(segment, when {
                        first == last -> TextRange(minOf(firstRange.min, endRange.min), maxOf(firstRange.max, endRange.max))
                        segment == first -> if (forward) TextRange(firstRange.min, segment.text.length) else TextRange(0, firstRange.max)
                        segment == last -> if (forward) TextRange(0, endRange.max) else TextRange(endRange.min, segment.text.length)
                        else -> TextRange(0, segment.text.length)
                    })
                }
            }
            lastSegment = last
            lastRange = endRange
            if (candidatesChanged) {
                previousCandidates.clear()
                previousCandidates.addAll(candidates)
            }
        }
        // Snap the lens to the touched glyph so its preview and selection agree.
        val center = last.layout!!.getBoundingBox(endRange.min).center
        if (target?.first != last || target?.second != center) target = last to center
    }

    internal fun finish() {
        val selected = ranges.entries.toList()
        cancel()
        // Reassemble page fragments before finding blank lines and joining adjacent paragraphs.
        val parts = selected.groupBy { it.key.block to it.key.signature }.values.map { group ->
            val segment = group.first().key
            ReaderUnderlineRange(segment.block, segment.signature,
                group.minOf { it.key.offset + it.value.min },
                group.maxOf { it.key.offset + it.value.max }) to
                group.sortedBy { it.key.offset }.joinToString("") { it.key.text.substring(it.value.min, it.value.max) }
        }
        ReaderUnderlines.selection(parts).forEach { mark ->
            selected.first { it.key.block == mark.blockIndex }.key.save(mark, false)
        }
    }

    internal fun cancel() {
        anchor = null
        lastSegment = null
        lastRange = null
        candidates.clear()
        previousCandidates.clear()
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

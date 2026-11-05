package com.breakyuna.esjzone.ui.reader

import kotlin.math.ceil
import kotlin.math.floor

internal const val READER_GLOW_TILE_HEIGHT = 512
private const val MAX_GLOW_PREFETCH = 1024

internal data class ReaderGlowTileWindow(val visible: IntRange, val buffered: IntRange) {
    fun inPriorityOrder(): List<Int> = visible.toList() + buffered.filter { it !in visible }
}

/** Tile numbers in text-local coordinates, including a bounded margin for scrolling. */
internal fun readerGlowTileWindow(textHeight: Int, viewportHeight: Int, textTop: Float): ReaderGlowTileWindow {
    if (textHeight <= 0 || viewportHeight <= 0 || !textTop.isFinite()) {
        return ReaderGlowTileWindow(IntRange.EMPTY, IntRange.EMPTY)
    }
    fun tiles(start: Float, end: Float): IntRange {
        val first = start.coerceAtLeast(0f)
        val last = end.coerceAtMost(textHeight.toFloat())
        if (first >= last) return IntRange.EMPTY
        return floor(first / READER_GLOW_TILE_HEIGHT).toInt()..
            (ceil(last / READER_GLOW_TILE_HEIGHT).toInt() - 1)
    }
    val top = -textTop
    val bottom = top + viewportHeight
    val margin = (viewportHeight / 2).coerceAtMost(MAX_GLOW_PREFETCH)
    return ReaderGlowTileWindow(tiles(top, bottom), tiles(top - margin, bottom + margin))
}

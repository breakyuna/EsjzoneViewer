package com.breakyuna.esjzone.ui.page

import kotlin.math.floor

/** Matches the former 112dp adaptive cells, including the gap between columns. */
internal fun bookshelfColumnCount(availableWidth: Float, gap: Float): Int {
    if (!availableWidth.isFinite() || availableWidth <= 0f) return 1
    return floor((availableWidth + gap) / (112f + gap)).toInt().coerceAtLeast(1)
}

package com.breakyuna.esjzone.ui.page

import kotlin.math.floor

/** Matches adaptive cells with a minimum column count of 3 for standard phone layouts. */
internal fun bookshelfColumnCount(
    availableWidth: Float,
    gap: Float,
    minColumns: Int = 3
): Int {
    if (!availableWidth.isFinite() || availableWidth <= 0f) return minColumns
    return floor((availableWidth + gap) / (96f + gap)).toInt().coerceAtLeast(minColumns)
}

/** Header occupies item zero; preserve the book identity when row grouping changes. */
internal fun bookshelfAnchorRow(bookKeys: List<String>, firstBookKey: String?, columns: Int, headerVisible: Boolean): Int {
    if (headerVisible || firstBookKey == null) return 0
    val index = bookKeys.indexOf(firstBookKey)
    return if (index < 0) 0 else 1 + index / columns.coerceAtLeast(1)
}

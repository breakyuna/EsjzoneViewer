package com.breakyuna.esjzone.ui.page

/** Pure conversion from a visible lazy-list item's geometry to chapter progress. */
internal fun chapterProgressFor(itemOffset: Int?, itemSize: Int?): Float {
    if (itemOffset == null || itemSize == null || itemSize <= 0) return 0f
    return (-itemOffset.toFloat() / itemSize.toFloat()).coerceIn(0f, 1f)
}

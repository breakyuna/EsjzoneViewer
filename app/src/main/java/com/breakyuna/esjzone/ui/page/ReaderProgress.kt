package com.breakyuna.esjzone.ui.page

/** Pure conversion from visible geometry; null means the layout is unavailable. */
internal fun chapterProgressFor(itemOffset: Int?, itemSize: Int?): Float? {
    if (itemOffset == null || itemSize == null || itemSize <= 0) return null
    return (-itemOffset.toFloat() / itemSize.toFloat()).coerceIn(0f, 1f)
}

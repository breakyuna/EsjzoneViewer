package com.breakyuna.esjzone.ui.tab

internal data class HomeCoverRow(val key: String, val urls: List<String> = emptyList())

/** Visible cards load themselves; prefetch only the next six distinct covers. */
internal fun upcomingHomeCovers(rows: List<HomeCoverRow>, visibleKeys: Set<String>): List<String> {
    val lastVisible = rows.indexOfLast { it.key in visibleKeys }
    if (visibleKeys.isNotEmpty() && lastVisible < 0) return emptyList()
    val visibleUrls = rows.filter { it.key in visibleKeys }.flatMap { it.urls }.toHashSet()
    return rows.asSequence().drop(lastVisible + 1).flatMap { it.urls.asSequence() }
        .filter { it.isNotBlank() && it !in visibleUrls }.distinct().take(6).toList()
}

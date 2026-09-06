package com.breakyuna.esjzone.domain.reader

data class ReaderScrollSnapshot(
    val firstVisibleIndex: Int,
    val firstVisibleOffset: Int,
    val firstVisibleChapterKey: String?,
    val lastVisibleChapterKey: String?,
    val distanceToLoadedTail: Int,
    val loadedChapterKeys: List<String>,
    val layoutMatchesLoadedWindow: Boolean,
    val isScrollInProgress: Boolean,
    val isProgrammaticScroll: Boolean
)

data class ReaderWindowAnchor(
    val visibleChapterKeys: Set<String>,
    val activeChapterKey: String?,
    val layoutReady: Boolean
) {
    val protectedChapterKeys: Set<String>
        get() = if (layoutReady) {
            buildSet {
                addAll(visibleChapterKeys)
                activeChapterKey?.takeIf(String::isNotBlank)?.let(::add)
            }
        } else {
            emptySet()
        }
}

fun shouldLoadNextChapter(
    previous: ReaderScrollSnapshot?,
    current: ReaderScrollSnapshot,
    threshold: Int
): Boolean {
    val before = previous ?: return false
    if (!current.layoutMatchesLoadedWindow || !current.isScrollInProgress || current.isProgrammaticScroll) return false
    if (current.lastVisibleChapterKey == null || current.lastVisibleChapterKey != current.loadedChapterKeys.lastOrNull()) return false
    if (current.distanceToLoadedTail > threshold) return false
    return dataWindowStable(before, current) && scrollingTowardsEnd(before, current)
}

fun shouldLoadPreviousChapter(
    previous: ReaderScrollSnapshot?,
    current: ReaderScrollSnapshot,
    threshold: Int
): Boolean {
    val before = previous ?: return false
    if (!current.layoutMatchesLoadedWindow || !current.isScrollInProgress || current.isProgrammaticScroll) return false
    if (current.firstVisibleChapterKey == null || current.firstVisibleChapterKey != current.loadedChapterKeys.firstOrNull()) return false
    if (current.firstVisibleOffset > threshold) return false
    return dataWindowStable(before, current) && scrollingTowardsStart(before, current)
}

fun dataWindowStable(previous: ReaderScrollSnapshot, current: ReaderScrollSnapshot): Boolean =
    previous.loadedChapterKeys == current.loadedChapterKeys

fun scrollingTowardsEnd(previous: ReaderScrollSnapshot, current: ReaderScrollSnapshot): Boolean =
    current.firstVisibleIndex > previous.firstVisibleIndex ||
        (current.firstVisibleIndex == previous.firstVisibleIndex && current.firstVisibleOffset > previous.firstVisibleOffset)

fun scrollingTowardsStart(previous: ReaderScrollSnapshot, current: ReaderScrollSnapshot): Boolean =
    current.firstVisibleIndex < previous.firstVisibleIndex ||
        (current.firstVisibleIndex == previous.firstVisibleIndex && current.firstVisibleOffset < previous.firstVisibleOffset)

fun trimReaderWindowKeys(
    keys: List<String>,
    trimFromStart: Boolean,
    maxSize: Int,
    protectedKeys: Set<String>
): List<String> {
    if (maxSize < 1 || protectedKeys.isEmpty()) return keys
    val retained = keys.toMutableList()
    while (retained.size > maxSize) {
        val edge = if (trimFromStart) retained.firstOrNull() else retained.lastOrNull()
        if (edge == null || edge in protectedKeys) break
        if (trimFromStart) retained.removeAt(0) else retained.removeAt(retained.lastIndex)
    }
    return retained
}

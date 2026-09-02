package com.breakyuna.esjzone.ui.page

/**
 * A layout snapshot used to distinguish a real user scroll from a list update.
 * The chapter keys are the loaded data window, so a prepend, append, or trim
 * invalidates the previous motion sample.
 */
internal data class ReaderScrollSnapshot(
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

internal data class ReaderWindowAnchor(
    val visibleChapterKeys: Set<String>,
    val activeChapterKey: String?,
    val layoutReady: Boolean
) {
    /** Empty or unavailable layout information must never authorize trimming. */
    val protectedChapterKeys: Set<String>
        get() = if (layoutReady) {
            buildSet {
                addAll(visibleChapterKeys)
                activeChapterKey?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
        } else {
            emptySet()
        }
}

internal fun shouldLoadNextChapter(
    previous: ReaderScrollSnapshot?,
    current: ReaderScrollSnapshot,
    threshold: Int
): Boolean {
    val previousSnapshot = previous ?: return false
    if (!current.layoutMatchesLoadedWindow) return false
    if (!current.isScrollInProgress || current.isProgrammaticScroll) return false
    if (current.lastVisibleChapterKey == null ||
        current.lastVisibleChapterKey != current.loadedChapterKeys.lastOrNull()
    ) {
        return false
    }
    if (current.distanceToLoadedTail > threshold) return false
    return dataWindowStable(previousSnapshot, current) && scrollingTowardsEnd(
        previousSnapshot,
        current
    )
}

internal fun shouldLoadPreviousChapter(
    previous: ReaderScrollSnapshot?,
    current: ReaderScrollSnapshot,
    threshold: Int
): Boolean {
    val previousSnapshot = previous ?: return false
    if (!current.layoutMatchesLoadedWindow) return false
    if (!current.isScrollInProgress || current.isProgrammaticScroll) return false
    if (current.firstVisibleChapterKey == null ||
        current.firstVisibleChapterKey != current.loadedChapterKeys.firstOrNull()
    ) {
        return false
    }
    if (current.firstVisibleOffset > threshold) return false
    return dataWindowStable(previousSnapshot, current) && scrollingTowardsStart(
        previousSnapshot,
        current
    )
}

internal fun dataWindowStable(
    previous: ReaderScrollSnapshot,
    current: ReaderScrollSnapshot
): Boolean = previous.loadedChapterKeys == current.loadedChapterKeys

internal fun scrollingTowardsEnd(
    previous: ReaderScrollSnapshot,
    current: ReaderScrollSnapshot
): Boolean = current.firstVisibleIndex > previous.firstVisibleIndex ||
    (current.firstVisibleIndex == previous.firstVisibleIndex &&
        current.firstVisibleOffset > previous.firstVisibleOffset)

internal fun scrollingTowardsStart(
    previous: ReaderScrollSnapshot,
    current: ReaderScrollSnapshot
): Boolean = current.firstVisibleIndex < previous.firstVisibleIndex ||
    (current.firstVisibleIndex == previous.firstVisibleIndex &&
        current.firstVisibleOffset < previous.firstVisibleOffset)

/**
 * Trim only a contiguous, unprotected edge. If the layout anchor is unknown,
 * the input is retained in full until a later completed load has an anchor.
 */
internal fun trimReaderWindowKeys(
    keys: List<String>,
    trimFromStart: Boolean,
    maxSize: Int,
    protectedKeys: Set<String>
): List<String> {
    if (maxSize < 1 || protectedKeys.isEmpty()) return keys
    val retained = keys.toMutableList()
    while (retained.size > maxSize) {
        val edgeKey = if (trimFromStart) retained.firstOrNull() else retained.lastOrNull()
        if (edgeKey == null || edgeKey in protectedKeys) break
        if (trimFromStart) retained.removeAt(0) else retained.removeAt(retained.lastIndex)
    }
    return retained
}

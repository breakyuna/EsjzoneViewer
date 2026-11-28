package com.breakyuna.esjzone.ui.reader

import com.breakyuna.esjzone.domain.reader.ReaderAnchor
import com.breakyuna.esjzone.domain.reader.ReaderChapterDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** A failed exact seek is only recoverable after a measured fallback succeeds. */
internal suspend fun restoreReaderPosition(
    exact: suspend () -> Boolean,
    fallback: suspend () -> Boolean
): Boolean {
    suspend fun attempt(timeoutMs: Long, action: suspend () -> Boolean): Boolean = try {
        withTimeoutOrNull(timeoutMs) { action() } == true
    } catch (_: CancellationException) {
        // The list or pager can cancel its own scroll mutation while the seek is still active.
        currentCoroutineContext().ensureActive()
        false
    } catch (_: Exception) {
        false
    }
    return attempt(500L, exact) || attempt(250L, fallback)
}

/** Seed the first body measure from a validated anchor, or the legacy progress. */
internal fun readerInitialBodyIndex(document: ReaderChapterDocument, chapterKey: String,
    progress: Float?, anchor: ReaderAnchor?): Int {
    val lastIndex = document.blocks.size
    val exact = anchor?.resolveIn(chapterKey, document)
    val fallback = ((progress?.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, 1f) *
        (lastIndex + 1)).toInt()
    return (if (exact == null) fallback else when (exact.kind) {
        "heading" -> 0
        "end" -> lastIndex
        else -> exact.blockIndex + 1
    }).coerceIn(0, lastIndex)
}

/** Page starts use source block indices plus the fraction within a text block. */
internal fun readerItemForContentPosition(starts: List<Float>, position: Float): Int? =
    starts.indexOfLast { it <= position }.takeIf { it >= 0 }
        ?: starts.indices.firstOrNull()

internal fun readerBodyListIndex(
    bodyIndex: Int,
    hasPreviousVerification: Boolean,
    hasPreviousRetry: Boolean
): Int = bodyIndex + (if (hasPreviousVerification) 1 else 0) + (if (hasPreviousRetry) 1 else 0)

internal data class ReaderScrollAnchor(val key: String, val index: Int, val offset: Int)

/** Preserve the visible paragraph even when a whole chapter moves it beyond LazyColumn's key map. */
internal fun readerRebasedScrollAnchor(
    previousHeadKey: String?,
    currentHeadKey: String?,
    visible: ReaderScrollAnchor?,
    bodyIndices: Map<String, Int>,
    bodyStartOffset: Int
): ReaderScrollAnchor? {
    if (previousHeadKey == null || previousHeadKey == currentHeadKey || visible == null) return null
    val bodyIndex = bodyIndices[visible.key] ?: return null
    return visible.copy(index = bodyIndex + bodyStartOffset)
}

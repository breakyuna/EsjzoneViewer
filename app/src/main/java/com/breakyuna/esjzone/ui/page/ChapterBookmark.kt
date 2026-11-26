package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.lazy.LazyListItemInfo
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.ui.reader.ReaderPageSegment

internal data class ReaderBookmarkTarget(
    val item: ReaderDisplayItem,
    val blockIndex: Int,
    val startsInViewport: Boolean,
    val displayOffset: Int? = null
)

internal fun scrollBookmarkTarget(
    visibleItems: List<LazyListItemInfo>,
    viewportStart: Int,
    viewportEnd: Int,
    itemsByKey: Map<String, ReaderDisplayItem>
): ReaderBookmarkTarget? = preferredBookmarkTarget(visibleItems.asSequence().mapNotNull { visible ->
    if (visible.size <= 0 || visible.offset >= viewportEnd || visible.offset + visible.size <= viewportStart) {
        return@mapNotNull null
    }
    val item = itemsByKey[visible.key.toString()] ?: return@mapNotNull null
    item.bookmarkTarget(item.ordinal - 1, startsInViewport = visible.offset >= viewportStart)
})

internal fun pageBookmarkTarget(item: ReaderDisplayItem): ReaderBookmarkTarget? =
    preferredBookmarkTarget(item.page?.segments.orEmpty().asSequence().filterIsInstance<ReaderPageSegment.TextLine>()
        .mapNotNull { line ->
            if (line.text.text.isBlank()) null
            else item.bookmarkTarget(line.blockIndex, line.startOffset == 0, line.startOffset)
        })

private fun ReaderDisplayItem.bookmarkTarget(
    blockIndex: Int,
    startsInViewport: Boolean,
    displayOffset: Int? = null
): ReaderBookmarkTarget? {
    if (entry.requiresPassword || entry.document.contentFingerprint.isBlank()) return null
    val hasText = when (val block = entry.document.blocks.getOrNull(blockIndex)) {
        is ReaderBlock.Paragraph -> block.parts.any { it.value.isNotBlank() }
        is ReaderBlock.Text -> block.value.isNotBlank()
        else -> false
    }
    return if (hasText) ReaderBookmarkTarget(this, blockIndex, startsInViewport, displayOffset) else null
}

private fun preferredBookmarkTarget(candidates: Sequence<ReaderBookmarkTarget>): ReaderBookmarkTarget? {
    var continuation: ReaderBookmarkTarget? = null
    for (candidate in candidates) {
        if (candidate.startsInViewport) return candidate
        if (continuation == null) continuation = candidate
    }
    return continuation
}

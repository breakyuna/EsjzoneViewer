package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextLayoutResult
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.domain.reader.ReaderAnchor
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderTextOffsets
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.ui.reader.ReaderPage
import com.breakyuna.esjzone.ui.reader.ReaderPageSegment
import com.breakyuna.esjzone.ui.reader.readerItemForContentPosition
import com.breakyuna.esjzone.ui.reader.restoreReaderPosition
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal data class ChapterResume(val chapter: Chapter, val progress: Float, val anchor: ReaderAnchor?)

internal suspend fun lookupChapterResume(
    novelId: String,
    novelUrl: String,
    requested: Chapter,
    restoreLatest: Boolean
): ChapterResume? {
    val saved = withContext(Dispatchers.IO) {
        PresentationAccess.database.localReadingActivityDao().getLatestForIdentity(novelId, novelUrl)
    } ?: return null
    val savedChapter = Chapter(saved.chapterName, saved.chapterUrl, true)
    val savedKey = chapterIdentity(savedChapter)
    val requestedKey = chapterIdentity(requested)
    if (!restoreLatest && savedKey != requestedKey) return null
    val target = if (restoreLatest && savedKey.isNotBlank() && savedKey != requestedKey) savedChapter else requested
    return ChapterResume(target, saved.chapterProgress, ReaderAnchor.decode(saved.anchor))
}

internal suspend fun restoreChapterPosition(
    target: ReaderBookLocation,
    startIndex: Int,
    displayItems: List<ReaderDisplayItem>,
    pagedMode: Boolean,
    scrollState: LazyListState,
    horizontalPagerState: PagerState,
    bodyStartOffset: Int,
    textLayouts: Map<String, TextLayoutResult>,
    identityOffsets: Boolean,
    textOffsets: (ReaderBlock) -> ReaderTextOffsets?
): Boolean {
    val targetKey = chapterIdentity(target.chapter)
    fun readerListIndex(bodyIndex: Int) = bodyIndex + bodyStartOffset
    val itemCount = displayItems[startIndex].itemCount
    val scaled = (target.chapterProgress.coerceIn(0f, 1f) * itemCount)
        .coerceAtMost(itemCount - 0.001f)
    val contentPosition = target.contentPosition
    val chapterItems = displayItems.subList(startIndex, startIndex + itemCount)
    val exactAnchor = target.anchor?.takeIf { it.matches(targetKey, displayItems[startIndex].entry.document) }
    val exactIndex = exactAnchor?.let { anchor ->
        when (anchor.kind) {
            "heading" -> 0
            "end" -> chapterItems.lastIndex
            else -> if (!pagedMode) anchor.blockIndex + 1 else {
                val block = displayItems[startIndex].entry.document.blocks[anchor.blockIndex]
                val offsets = if (identityOffsets) null else textOffsets(block)
                readerPageIndexForAnchor(chapterItems.map { it.page }, anchor) { offsets?.toSource(it) ?: it }
            }
        }
    }
    val sourceIndex = exactIndex ?: contentPosition?.let { position ->
        readerItemForContentPosition(
            chapterItems.map { it.page?.contentPosition ?: (it.ordinal - 1f) }, position
        )
    }
    val targetIndex = startIndex + (sourceIndex ?: scaled.toInt())
    val targetItemKey = displayItems[targetIndex].key

    suspend fun restoreParagraph(): Boolean {
        if (pagedMode) horizontalPagerState.scrollToPage(targetIndex)
        else scrollState.scrollToItem(readerListIndex(targetIndex))
        return snapshotFlow {
            if (pagedMode) horizontalPagerState.currentPage == targetIndex &&
                horizontalPagerState.layoutInfo.visiblePagesInfo.any { it.index == targetIndex && it.key == targetItemKey }
            else scrollState.layoutInfo.visibleItemsInfo.any {
                it.index == readerListIndex(targetIndex) && it.key == targetItemKey
            }
        }.first { it }
    }
    return restoreReaderPosition(exact = {
        restoreParagraph()
        val itemFraction = if (exactAnchor?.kind == "image") exactAnchor.fraction else if (contentPosition != null) {
            (contentPosition - (displayItems[targetIndex].ordinal - 1f)).coerceIn(0f, 1f)
        } else scaled - scaled.toInt()
        if (exactAnchor?.kind == "text" && !pagedMode) {
            val layout = withTimeoutOrNull(1500L) {
                snapshotFlow { textLayouts[targetItemKey] }.first { it != null }
            }
            val block = displayItems[targetIndex].entry.document.blocks.getOrNull(exactAnchor.blockIndex)
            if (layout != null && block != null) {
                val offset = if (identityOffsets) exactAnchor.offset
                    else textOffsets(block)?.toDisplay(exactAnchor.offset) ?: exactAnchor.offset
                val line = layout.getLineForOffset(offset.coerceIn(0, layout.layoutInput.text.length))
                scrollState.scrollToItem(
                readerListIndex(targetIndex), layout.getLineTop(line).roundToInt().coerceAtLeast(0)
            )
            }
        } else if (exactAnchor?.kind == "end" && !pagedMode) {
            val visible = scrollState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == targetItemKey }
            if (visible != null) scrollState.scrollToItem(visible.index, visible.size)
        } else if (itemFraction > 0f && !pagedMode) {
            val itemSize = withTimeoutOrNull(1000L) {
                snapshotFlow {
                    scrollState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.key == targetItemKey }
                        ?.let { it.index to it.size }
                }.first { layout -> layout?.second?.let { it > 0 } == true }
            }
            if (itemSize != null) {
                scrollState.scrollToItem(
                    itemSize.first,
                    (itemSize.second * itemFraction)
                        .roundToInt()
                        .coerceAtLeast(0)
                )
            }
        }
        true
    }, fallback = { restoreParagraph() })
}

/** Paged mode: the last page starting at or before the anchor; page offsets are display text. */
internal fun readerPageIndexForAnchor(
    pages: List<ReaderPage?>,
    anchor: ReaderAnchor,
    toSource: (Int) -> Int
): Int = pages.indexOfLast { candidate ->
    candidate?.let { page ->
        val blockIndex = page.contentPosition.toInt()
        val startOffset = (page.segments.firstOrNull() as? ReaderPageSegment.TextLine)?.startOffset ?: 0
        blockIndex < anchor.blockIndex || blockIndex == anchor.blockIndex && toSource(startOffset) <= anchor.offset
    } == true
}.coerceAtLeast(0)

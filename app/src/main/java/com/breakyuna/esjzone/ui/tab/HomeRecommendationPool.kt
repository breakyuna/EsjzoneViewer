package com.breakyuna.esjzone.ui.tab

import com.breakyuna.esjzone.network.PageableRequester
import com.breakyuna.esjzone.network.cancellablePageRequest
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Bounded, non-personalized sources. Unused cards remain available for later batches. */
internal class HomeRecommendationPool(
    private val openSource: (sortType: Int) -> Pair<PageableRequester<CoveredNovel>, List<CoveredNovel>>
) {
    private data class Source(
        val sortType: Int,
        val quota: Int,
        val pageLimit: Int,
        var requester: PageableRequester<CoveredNovel>? = null,
        val visitedPages: MutableSet<Int> = mutableSetOf(),
        val pending: MutableList<CoveredNovel> = mutableListOf()
    ) {
        val pageCount: Int get() = minOf(requester?.pages() ?: 0, pageLimit)

        fun copyForBatch() = copy(
            visitedPages = visitedPages.toMutableSet(),
            pending = pending.toMutableList()
        )
    }

    private fun newSources() = listOf(
        Source(sortType = 4, quota = 6, pageLimit = 5), // Most viewed
        Source(sortType = 1, quota = 12, pageLimit = 20), // Recently updated
        Source(sortType = 2, quota = 6, pageLimit = 5) // Recently added
    )

    private var sources = newSources()
    private var seenNovelKeys = mutableSetOf<String>()

    val hasMore: Boolean
        get() = sources.any { source ->
            source.requester == null || source.visitedPages.size < source.pageCount ||
                source.pending.any { novelKey(it) !in seenNovelKeys }
        }

    suspend fun nextBatch(adult: Boolean, restartIfExhausted: Boolean): List<CoveredNovel> {
        val restart = restartIfExhausted && !hasMore
        // Commit only a successful batch, so a failed/cancelled request cannot consume books.
        val workingSources = if (restart) newSources() else sources.map { it.copyForBatch() }
        val workingSeen = if (restart) mutableSetOf<String>() else seenNovelKeys.toMutableSet()
        val collected = mutableListOf<CoveredNovel>()

        for (source in workingSources) {
            var selected = 0
            var requestedPages = 0
            while (selected < source.quota) {
                currentCoroutineContext().ensureActive()
                if (source.pending.isNotEmpty()) {
                    val novel = source.pending.removeAt(0)
                    if ((adult || !novel.isAdult) && workingSeen.add(novelKey(novel))) {
                        collected += novel
                        selected += 1
                    }
                    continue
                }
                // Bound requests even when many cards are filtered or overlap across sources.
                if (requestedPages >= 3) break
                val requester = source.requester
                val novels = if (requester == null) {
                    val (opened, firstPage) = cancellablePageRequest { openSource(source.sortType) }
                    source.requester = opened
                    source.visitedPages += 1
                    firstPage
                } else {
                    val page = (1..source.pageCount)
                        .filterNot { it in source.visitedPages }
                        .randomOrNull() ?: break
                    val result = cancellablePageRequest { requester.more(page) }
                    source.visitedPages += page
                    result
                }
                requestedPages += 1
                source.pending += novels.filter { adult || !it.isAdult }.shuffled()
            }
        }

        currentCoroutineContext().ensureActive()
        sources = workingSources
        seenNovelKeys = workingSeen
        return collected.shuffled()
    }
}

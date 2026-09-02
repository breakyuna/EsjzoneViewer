package com.breakyuna.esjzone.ui.page

import androidx.compose.runtime.MutableState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.network.features.getChapterDetail
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.novellibrary.novel.FavoriteNovel
import com.breakyuna.esjzone.util.AppLogger

data class ReaderChapter(
    val chapter: Chapter,
    val detail: DetailedChapter
)

class ChapterPageModel(
    private val authorization: Authorization,
    private val requestedChapter: MutableState<Chapter>,
    private val novelId: String,
    chapterOrder: List<Chapter>
) : StateScreenModel<ChapterPageModel.State>(State.Loading) {

    private companion object {
        /** Keep a small bidirectional reading window instead of the whole book in RAM. */
        const val MAX_LOADED_CHAPTERS = 9
    }

    sealed class State {
        data object Loading : State()
        data class Error(val failure: LoadFailureKind) : State()
        data class Result(
            val chapters: List<ReaderChapter>,
            val previous: Chapter?,
            val next: Chapter?,
            val isLoadingNext: Boolean,
            val isLoadingPrevious: Boolean = false,
            val chapterOrder: List<Chapter> = emptyList()
        ) : State()
    }

    private val lock = Any()
    private val loadedChapters = mutableListOf<ReaderChapter>()
    private val prefetchedDetails = mutableMapOf<String, DetailedChapter>()
    private val prefetchJobs = mutableMapOf<String, Job>()
    private var orderedChapters = normalizeChapterOrder(chapterOrder)
    private var orderResolved = orderedChapters.isNotEmpty()
    private var sessionId = 0L
    private var initialJob: Job? = null
    private var appendJob: Job? = null
    private var prependJob: Job? = null
    private var orderJob: Job? = null
    private var orderRequestId = 0L
    private var loadingNext = false
    private var loadingPrevious = false
    private var orderLoading = false
    private var pendingNextRequest = false
    private var pendingPreviousRequest = false
    private var initialLoadStarted = false
    /** Latest completed reader layout anchor; null means no safe trim point. */
    private var windowAnchor: ReaderWindowAnchor? = null

    /**
     * Called from the reader after layout has settled. It is intentionally
     * just an atomic snapshot: an in-flight request reads the newest anchor
     * when it completes, rather than protecting the item visible at request
     * start.
     */
    internal fun updateWindowAnchor(anchor: ReaderWindowAnchor) {
        synchronized(lock) {
            windowAnchor = anchor
        }
    }

    fun getDetail() {
        synchronized(lock) {
            if (initialLoadStarted) return
            initialLoadStarted = true
        }
        openChapter(requestedChapter.value)
    }

    fun openChapter(chapter: Chapter) {
        requestedChapter.value = chapter
        var currentSession = 0L
        var jobsToCancel = emptyList<Job>()
        synchronized(lock) {
            sessionId += 1
            currentSession = sessionId
            jobsToCancel = buildList {
                initialJob?.let(::add)
                appendJob?.let(::add)
                prependJob?.let(::add)
                orderJob?.let(::add)
                addAll(prefetchJobs.values)
            }.distinct()
            initialJob = null
            appendJob = null
            prependJob = null
            orderJob = null
            orderRequestId += 1
            loadedChapters.clear()
            prefetchedDetails.clear()
            loadingNext = false
            loadingPrevious = false
            orderLoading = false
            pendingNextRequest = false
            pendingPreviousRequest = false
            windowAnchor = null
        }
        // Cancel outside the model lock: cancellation handlers may publish or
        // remove their own entries while unwinding.
        jobsToCancel.forEach(Job::cancel)
        mutableState.value = State.Loading

        initialJob = screenModelScope.launch(Dispatchers.IO) {
            val detail = try {
                loadDetail(chapter)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentSession(currentSession)) {
                    mutableState.value = State.Error(error.loadFailureKind())
                }
                AppLogger.e(
                    "ChapterPageModel",
                    "Failed to load initial chapter ${chapter.name}",
                    error
                )
                return@launch
            }
            if (!isCurrentSession(currentSession)) return@launch
            if (detail == null) {
                mutableState.value = State.Error(LoadFailureKind.CLIENT)
                return@launch
            }

            synchronized(lock) {
                if (isCurrentSessionLocked(currentSession)) {
                    loadedChapters += ReaderChapter(chapter, detail)
                }
            }
            publish(currentSession)

            val hasCanonicalOrder = synchronized(lock) { orderResolved }
            if (hasCanonicalOrder || novelId.isBlank()) {
                prefetchNext(chapter, detail)
            } else {
                requestChapterOrder(currentSession, chapter, detail)
            }
        }
    }

    /** Loads the next canonical TOC chapter when the reader reaches the end buffer. */
    fun loadNextChapter() {
        val shouldWaitForOrder = synchronized(lock) {
            !orderResolved && novelId.isNotBlank()
        }
        if (shouldWaitForOrder) {
            synchronized(lock) {
                pendingNextRequest = true
            }
            requestChapterOrder(sessionId, null, null)
            return
        }

        var currentSession: Long? = null
        var nextChapter: Chapter? = null
        synchronized(lock) {
            if (loadingNext || loadedChapters.isEmpty()) return
            val last = loadedChapters.last()
            val candidate = adjacentChapter(last.chapter, 1, last.detail) ?: return
            if (loadedChapters.any { sameChapter(it.chapter, candidate) }) return
            nextChapter = candidate
            loadingNext = true
            currentSession = sessionId
        }
        val chapterToLoad = nextChapter ?: return
        val session = currentSession ?: return
        publish(session)

        appendJob = screenModelScope.launch(Dispatchers.IO) {
            try {
                val detail = loadDetail(chapterToLoad)
                if (detail != null && isCurrentSession(session)) {
                    synchronized(lock) {
                        if (isCurrentSessionLocked(session) &&
                            loadedChapters.none { sameChapter(it.chapter, chapterToLoad) }
                        ) {
                            loadedChapters += ReaderChapter(chapterToLoad, detail)
                            trimLoadedChaptersFromStart()
                        }
                    }
                    publish(session)
                    prefetchNext(chapterToLoad, detail)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(
                    "ChapterPageModel",
                    "Failed to append chapter ${chapterToLoad.name}",
                    e
                )
            } finally {
                synchronized(lock) {
                    if (isCurrentSessionLocked(session)) {
                        loadingNext = false
                    }
                }
                publish(session)
            }
        }
    }

    /** Loads the previous canonical TOC chapter when the reader reaches the top buffer. */
    fun loadPreviousChapter() {
        val shouldWaitForOrder = synchronized(lock) {
            !orderResolved && novelId.isNotBlank()
        }
        if (shouldWaitForOrder) {
            synchronized(lock) {
                pendingPreviousRequest = true
            }
            requestChapterOrder(sessionId, null, null)
            return
        }

        var currentSession: Long? = null
        var previousChapter: Chapter? = null
        synchronized(lock) {
            if (loadingPrevious || loadedChapters.isEmpty()) return
            val first = loadedChapters.first()
            val candidate = adjacentChapter(first.chapter, -1, first.detail) ?: return
            if (loadedChapters.any { sameChapter(it.chapter, candidate) }) return
            previousChapter = candidate
            loadingPrevious = true
            currentSession = sessionId
        }
        val chapterToLoad = previousChapter ?: return
        val session = currentSession ?: return
        publish(session)

        prependJob = screenModelScope.launch(Dispatchers.IO) {
            try {
                val detail = loadDetail(chapterToLoad)
                if (detail != null && isCurrentSession(session)) {
                    synchronized(lock) {
                        if (isCurrentSessionLocked(session) &&
                            loadedChapters.none { sameChapter(it.chapter, chapterToLoad) }
                        ) {
                            loadedChapters.add(0, ReaderChapter(chapterToLoad, detail))
                            trimLoadedChaptersFromEnd()
                        }
                    }
                    publish(session)
                    prefetchPrevious(chapterToLoad, detail)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(
                    "ChapterPageModel",
                    "Failed to prepend chapter ${chapterToLoad.name}",
                    e
                )
            } finally {
                synchronized(lock) {
                    if (isCurrentSessionLocked(session)) {
                        loadingPrevious = false
                    }
                }
                publish(session)
            }
        }
    }

    private fun requestChapterOrder(
        currentSession: Long,
        chapter: Chapter?,
        detail: DetailedChapter?
    ) {
        var requestId = 0L
        synchronized(lock) {
            if (orderResolved || orderLoading) return
            orderLoading = true
            orderRequestId += 1
            requestId = orderRequestId
            orderJob = screenModelScope.launch(Dispatchers.IO) {
                try {
                    ensureChapterOrder()
                    if (!isCurrentSession(currentSession)) return@launch
                    publish(currentSession)
                    if (chapter != null && detail != null) {
                        prefetchNext(chapter, detail)
                    }
                    val pendingLoads = synchronized(lock) {
                        val requested = pendingNextRequest
                        val requestedPrevious = pendingPreviousRequest
                        pendingNextRequest = false
                        pendingPreviousRequest = false
                        requestedPrevious to requested
                    }
                    if (pendingLoads.first) loadPreviousChapter()
                    if (pendingLoads.second) loadNextChapter()
                } finally {
                    synchronized(lock) {
                        if (orderRequestId == requestId) {
                            orderLoading = false
                            orderJob = null
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadDetail(chapter: Chapter): DetailedChapter? {
        val key = chapterKey(chapter)
        val prefetched = synchronized(lock) { prefetchedDetails.remove(key) }
        if (prefetched != null) return prefetched

        val prefetchJob = synchronized(lock) { prefetchJobs[key] }
        if (prefetchJob != null) {
            try {
                prefetchJob.join()
            } catch (e: CancellationException) {
                throw e
            }
            synchronized(lock) { prefetchedDetails.remove(key) }?.let { return it }
        }

        // The prefetch can finish between the first cache check and job lookup.
        // Check one more time before issuing a duplicate request.
        synchronized(lock) { prefetchedDetails.remove(key) }?.let { return it }

        return try {
            EsjzoneClient.getChapterDetail(authorization, chapter)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(
                "ChapterPageModel",
                "Failed to load chapter detail for ${chapter.name}",
                e
            )
            throw e
        }
    }

    private fun prefetchNext(chapter: Chapter, detail: DetailedChapter) {
        adjacentChapter(chapter, 1, detail)?.let(::prefetch)
    }

    private fun prefetchPrevious(chapter: Chapter, detail: DetailedChapter) {
        adjacentChapter(chapter, -1, detail)?.let(::prefetch)
    }

    private fun prefetch(chapter: Chapter) {
        val key = chapterKey(chapter)
        if (key.isBlank()) return
        synchronized(lock) {
            if (loadedChapters.any { sameChapter(it.chapter, chapter) } ||
                prefetchedDetails.containsKey(key) || prefetchJobs.containsKey(key)
            ) {
                return
            }
            prefetchJobs[key] = screenModelScope.launch(Dispatchers.IO) {
                val detail = try {
                    EsjzoneClient.getChapterDetail(authorization, chapter)
                } catch (e: CancellationException) {
                    synchronized(lock) {
                        prefetchJobs.remove(key)
                    }
                    throw e
                } catch (e: Exception) {
                    AppLogger.w(
                        "ChapterPageModel",
                        "Prefetch failed for chapter ${chapter.name}",
                        e
                    )
                    null
                }
                synchronized(lock) {
                    if (detail != null) prefetchedDetails[key] = detail
                    prefetchJobs.remove(key)
                }
            }
        }
    }

    private suspend fun ensureChapterOrder() {
        synchronized(lock) {
            if (orderResolved) return
            if (novelId.isBlank()) {
                orderResolved = true
                return
            }
        }

        val source = FavoriteNovel(
            name = "",
            url = "${EsjzoneUrls.Base}/detail/$novelId.html"
        )
        val fetchedOrder = try {
            EsjzoneClient.getNovelDetail(authorization, source)
                .chapterList
                .orderedChapters
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(
                "ChapterPageModel",
                "Failed to load canonical chapter order for novel $novelId",
                e
            )
            emptyList()
        }

        synchronized(lock) {
            if (fetchedOrder.isNotEmpty()) {
                orderedChapters = normalizeChapterOrder(fetchedOrder)
            }
            orderResolved = true
        }
    }

    private fun adjacentChapter(
        chapter: Chapter,
        offset: Int,
        fallback: DetailedChapter?
    ): Chapter? {
        val (adjacent, currentInCanonicalOrder) = synchronized(lock) {
            val index = orderedChapters.indexOfFirst { sameChapter(it, chapter) }
            (if (index >= 0) orderedChapters.getOrNull(index + offset) else null) to
                (index >= 0)
        }
        if (adjacent != null) return adjacent
        // A history record can point to a valid chapter that the refreshed TOC
        // no longer contains.  In that case the live chapter's previous/next
        // link is the only usable continuation.  If the chapter is present in
        // the canonical TOC, keep its boundary authoritative and do not follow
        // unrelated site navigation links.
        if (currentInCanonicalOrder) return null
        return if (offset > 0) fallback?.next else fallback?.previous
    }

    private fun publish(currentSession: Long? = null) {
        synchronized(lock) {
            if (currentSession != null && !isCurrentSessionLocked(currentSession)) return
            val snapshot = loadedChapters.toList()
            if (snapshot.isEmpty()) return
            val first = snapshot.firstOrNull()
            val last = snapshot.lastOrNull()
            // Publish while holding the same lock used for the snapshot. This
            // prevents a slower completion from overwriting a newer window
            // after another append/prepend has already published it.
            mutableState.value = State.Result(
                chapters = snapshot,
                previous = first?.let { adjacentChapter(it.chapter, -1, it.detail) },
                next = last?.let { adjacentChapter(it.chapter, 1, it.detail) },
                isLoadingNext = loadingNext,
                isLoadingPrevious = loadingPrevious,
                chapterOrder = orderedChapters.toList()
            )
        }
    }

    private fun isCurrentSession(currentSession: Long): Boolean =
        synchronized(lock) { isCurrentSessionLocked(currentSession) }

    private fun isCurrentSessionLocked(currentSession: Long): Boolean = sessionId == currentSession

    private fun sameChapter(first: Chapter, second: Chapter): Boolean =
        chapterKey(first) == chapterKey(second)

    private fun chapterKey(chapter: Chapter): String = chapterIdentity(chapter)

    private fun normalizeChapterOrder(chapters: List<Chapter>): List<Chapter> =
        chapters.asSequence()
            .filter { chapterKey(it).isNotBlank() }
            .distinctBy { chapterKey(it) }
            .toList()

    /** Called only while [lock] is held after reading forward near the list end. */
    private fun trimLoadedChaptersFromStart() {
        val protectedKeys = windowAnchor?.protectedChapterKeys.orEmpty()
        val retainedKeys = trimReaderWindowKeys(
            keys = loadedChapters.map { chapterKey(it.chapter) },
            trimFromStart = true,
            maxSize = MAX_LOADED_CHAPTERS,
            protectedKeys = protectedKeys
        ).toSet()
        loadedChapters.retainAll { chapterKey(it.chapter) in retainedKeys }
    }

    /** Called only while [lock] is held after reading backward near the list start. */
    private fun trimLoadedChaptersFromEnd() {
        val protectedKeys = windowAnchor?.protectedChapterKeys.orEmpty()
        val retainedKeys = trimReaderWindowKeys(
            keys = loadedChapters.map { chapterKey(it.chapter) },
            trimFromStart = false,
            maxSize = MAX_LOADED_CHAPTERS,
            protectedKeys = protectedKeys
        ).toSet()
        loadedChapters.retainAll { chapterKey(it.chapter) in retainedKeys }
    }
}

internal fun chapterIdentity(chapter: Chapter): String =
    EsjzoneUrls.canonicalPageKey(chapter.url).takeIf { it.isNotBlank() && it != "/" }
        ?: chapter.name.trim()

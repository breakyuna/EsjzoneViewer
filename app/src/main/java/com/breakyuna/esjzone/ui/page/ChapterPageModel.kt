package com.breakyuna.esjzone.ui.page

import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.app.PresentationAccess

import androidx.compose.runtime.MutableState
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.cancellablePageRequest
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.network.features.getChapterDetail
import com.breakyuna.esjzone.network.features.unlockPasswordProtectedChapter
import com.breakyuna.esjzone.network.features.ChapterPasswordRejectedException
import com.breakyuna.esjzone.network.features.ChapterPasswordRequiredException
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.network.external.CloudflareChallengeRequiredException
import com.breakyuna.esjzone.network.external.CloudflareWebViewUnavailableException
import com.breakyuna.esjzone.network.external.ExternalChapterParseException
import com.breakyuna.esjzone.network.external.WenkuCookieStoreUnavailableException
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.novellibrary.novel.FavoriteNovel
import com.breakyuna.esjzone.ui.reader.toReaderDocument
import com.breakyuna.esjzone.util.AppLogger

data class ReaderChapter(
    val chapter: Chapter,
    /** Reader presentation consumes the stable domain AST, not HTML components. */
    val document: com.breakyuna.esjzone.domain.reader.ReaderChapterDocument,
    val fallbackPrevious: Chapter? = null,
    val fallbackNext: Chapter? = null,
    val isOffline: Boolean = false
)

class ChapterPageModel(
    private val authorization: Authorization,
    private val requestedChapter: MutableState<Chapter>,
    private val novelId: String,
    chapterOrder: List<Chapter>,
    private val novelName: String = "",
    private val novelUrl: String = "",
    private val novelCoverUrl: String = ""
) : AppStateViewModel<ChapterPageModel.State>(State.Loading) {

    override fun onCleared() {
        com.breakyuna.esjzone.network.EsjzoneClient.closeWenkuBrowserSession()
        super.onCleared()
    }

    private companion object {
        /** Keep a small bidirectional reading window instead of the whole book in RAM. */
        const val MAX_LOADED_CHAPTERS = 9
        const val APPEND_RETRY_COOLDOWN_MILLIS = 3_000L
        /** A consumed chapter must finish saving even after the reader entry is popped. */
        val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val persistenceSemaphore = Semaphore(2)
    }

    sealed class State {
        data object Loading : State()
        data object SecurityCheck : State()
        data object Empty : State()
        data class PasswordRequired(val chapter: Chapter, val message: String? = null) : State()
        data object UnsupportedExternalLink : State()
        data object VerificationRequired : State()
        data object WebViewUnavailable : State()
        data object ExternalParseError : State()
        data object ExternalStorageUnavailable : State()
        data class Error(val failure: LoadFailureKind) : State()
        data class Result(
            val chapters: List<ReaderChapter>,
            val previous: Chapter?,
            val next: Chapter?,
            val isLoadingNext: Boolean,
            val isLoadingPrevious: Boolean = false,
            val chapterOrder: List<Chapter> = emptyList(),
            val isOffline: Boolean = false,
            val verificationChapter: Chapter? = null,
            val verificationWebViewUnavailable: Boolean = false,
            val verificationStorageUnavailable: Boolean = false,
            val verificationOffset: Int = 0
        ) : State()
    }

    private val lock = Any()
    private val loadedChapters = mutableListOf<ReaderChapter>()
    private val prefetchedDetails = mutableMapOf<String, DetailedChapter>()
    private val offlineChapterKeys = mutableSetOf<String>()
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
    private var failedAppendChapterKey: String? = null
    private var failedAppendAtMillis = 0L
    private var failedPrependChapterKey: String? = null
    private var failedPrependAtMillis = 0L
    private var verificationChapter: Chapter? = null
    private var verificationOffset = 0
    private var verificationWebViewUnavailable = false
    private var verificationStorageUnavailable = false
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
            offlineChapterKeys.clear()
            failedAppendChapterKey = null
            failedPrependChapterKey = null
            failedPrependAtMillis = 0L
            verificationChapter = null
            verificationOffset = 0
            verificationWebViewUnavailable = false
            verificationStorageUnavailable = false
            windowAnchor = null
        }
        // Cancel outside the model lock: cancellation handlers may publish or
        // remove their own entries while unwinding.
        jobsToCancel.forEach(Job::cancel)

        if (chapter.isExternal) {
            mutableState.value = State.UnsupportedExternalLink
            return
        }

        mutableState.value = State.Loading

        initialJob = viewModelScope.launch(Dispatchers.IO) {
            val detail = try {
                loadDetail(chapter, showSecurityCheck = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: ChapterPasswordRequiredException) {
                if (isCurrentSession(currentSession)) {
                    mutableState.value = State.PasswordRequired(chapter)
                }
                return@launch
            } catch (error: CloudflareChallengeRequiredException) {
                if (isCurrentSession(currentSession)) mutableState.value = State.VerificationRequired
                return@launch
            } catch (error: CloudflareWebViewUnavailableException) {
                if (isCurrentSession(currentSession)) mutableState.value = State.WebViewUnavailable
                return@launch
            } catch (error: ExternalChapterParseException) {
                if (isCurrentSession(currentSession)) mutableState.value = State.ExternalParseError
                return@launch
            } catch (error: WenkuCookieStoreUnavailableException) {
                if (isCurrentSession(currentSession)) mutableState.value = State.ExternalStorageUnavailable
                return@launch
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
            if (detail.content.isEmpty()) {
                mutableState.value = State.Empty
                return@launch
            }

            synchronized(lock) {
                if (isCurrentSessionLocked(currentSession)) {
                    val loadedOffline = offlineChapterKeys.remove(chapterKey(chapter))
                    loadedChapters += ReaderChapter(
                        chapter = chapter,
                        document = detail.toReaderDocument(chapter),
                        fallbackPrevious = detail.previous,
                        fallbackNext = detail.next,
                        isOffline = loadedOffline
                    )
                }
            }
            publish(currentSession)

            val hasCanonicalOrder = synchronized(lock) { orderResolved }
            if (hasCanonicalOrder || novelId.isBlank()) {
                prefetchNext(chapter, detail.next)
            } else {
                requestChapterOrder(currentSession, chapter, detail.next)
            }
        }
    }

    /** Submits a password once; it is not kept in state, storage, logs, or navigation. */
    fun submitChapterPassword(password: String) {
        val protected = mutableState.value as? State.PasswordRequired ?: return
        if (password.isBlank()) {
            mutableState.value = protected
            return
        }
        val target = protected.chapter
        val currentSession = synchronized(lock) { sessionId }
        mutableState.value = State.Loading
        initialJob = viewModelScope.launch(Dispatchers.IO) {
            val detail = try {
                cancellablePageRequest {
                    PresentationAccess.client.unlockPasswordProtectedChapter(authorization, target, password)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: ChapterPasswordRejectedException) {
                if (isCurrentSession(currentSession)) {
                    mutableState.value = State.PasswordRequired(target, error.message)
                }
                return@launch
            } catch (error: Exception) {
                if (isCurrentSession(currentSession)) {
                    mutableState.value = State.Error(error.loadFailureKind())
                }
                AppLogger.e("ChapterPageModel", "Failed to unlock password-protected chapter", error)
                return@launch
            }
            if (!isCurrentSession(currentSession)) return@launch
            synchronized(lock) {
                if (isCurrentSessionLocked(currentSession)) {
                    loadedChapters += ReaderChapter(
                        chapter = target,
                        document = detail.toReaderDocument(target),
                        fallbackPrevious = detail.previous,
                        fallbackNext = detail.next
                    )
                }
            }
            queueChapterPersistence(target, detail)
            publish(currentSession)
            prefetchNext(target, detail.next)
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
            if (loadingNext || loadedChapters.isEmpty() ||
                (verificationChapter != null && verificationOffset > 0)) return
            val last = loadedChapters.last()
            val candidate = adjacentChapter(last.chapter, 1, last.fallbackNext) ?: return
            if (chapterKey(candidate) == failedAppendChapterKey &&
                System.currentTimeMillis() - failedAppendAtMillis < APPEND_RETRY_COOLDOWN_MILLIS) return
            if (loadedChapters.any { sameChapter(it.chapter, candidate) }) return
            nextChapter = candidate
            loadingNext = true
            currentSession = sessionId
        }
        val chapterToLoad = nextChapter ?: return
        val session = currentSession ?: return
        publish(session)

        appendJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val detail = loadDetail(chapterToLoad)
                if (detail != null && isCurrentSession(session)) {
                    synchronized(lock) {
                        if (isCurrentSessionLocked(session) &&
                            loadedChapters.none { sameChapter(it.chapter, chapterToLoad) }
                        ) {
                            val loadedOffline = offlineChapterKeys.remove(chapterKey(chapterToLoad))
                            loadedChapters += ReaderChapter(
                                chapter = chapterToLoad,
                                document = detail.toReaderDocument(chapterToLoad),
                                fallbackPrevious = detail.previous,
                                fallbackNext = detail.next,
                                isOffline = loadedOffline
                            )
                            failedAppendChapterKey = null
                            failedAppendAtMillis = 0L
                            clearVerificationLocked()
                            trimLoadedChaptersFromStart()
                        }
                    }
                    publish(session)
                    prefetchNext(chapterToLoad, detail.next)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: CloudflareChallengeRequiredException) {
                rememberVerification(session, chapterToLoad, 1)
            } catch (e: CloudflareWebViewUnavailableException) {
                rememberVerification(session, chapterToLoad, 1, unavailable = true)
            } catch (e: WenkuCookieStoreUnavailableException) {
                rememberVerification(session, chapterToLoad, 1, storageUnavailable = true)
            } catch (e: Exception) {
                synchronized(lock) {
                    if (isCurrentSessionLocked(session)) {
                        failedAppendChapterKey = chapterKey(chapterToLoad)
                        failedAppendAtMillis = System.currentTimeMillis()
                    }
                }
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
            if (loadingPrevious || loadedChapters.isEmpty() ||
                (verificationChapter != null && verificationOffset < 0)) return
            val first = loadedChapters.first()
            val candidate = adjacentChapter(first.chapter, -1, first.fallbackPrevious) ?: return
            if (chapterKey(candidate) == failedPrependChapterKey &&
                System.currentTimeMillis() - failedPrependAtMillis < APPEND_RETRY_COOLDOWN_MILLIS) return
            if (loadedChapters.any { sameChapter(it.chapter, candidate) }) return
            previousChapter = candidate
            loadingPrevious = true
            currentSession = sessionId
        }
        val chapterToLoad = previousChapter ?: return
        val session = currentSession ?: return
        publish(session)

        prependJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val detail = loadDetail(chapterToLoad)
                if (detail != null && isCurrentSession(session)) {
                    synchronized(lock) {
                        if (isCurrentSessionLocked(session) &&
                            loadedChapters.none { sameChapter(it.chapter, chapterToLoad) }
                        ) {
                            val loadedOffline = offlineChapterKeys.remove(chapterKey(chapterToLoad))
                            loadedChapters.add(
                                0,
                                ReaderChapter(
                                    chapter = chapterToLoad,
                                    document = detail.toReaderDocument(chapterToLoad),
                                    fallbackPrevious = detail.previous,
                                    fallbackNext = detail.next,
                                    isOffline = loadedOffline
                                )
                            )
                            failedPrependChapterKey = null
                            failedPrependAtMillis = 0L
                            clearVerificationLocked()
                            trimLoadedChaptersFromEnd()
                        }
                    }
                    publish(session)
                    prefetchPrevious(chapterToLoad, detail.previous)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: CloudflareChallengeRequiredException) {
                rememberVerification(session, chapterToLoad, -1)
            } catch (e: CloudflareWebViewUnavailableException) {
                rememberVerification(session, chapterToLoad, -1, unavailable = true)
            } catch (e: WenkuCookieStoreUnavailableException) {
                rememberVerification(session, chapterToLoad, -1, storageUnavailable = true)
            } catch (e: Exception) {
                synchronized(lock) {
                    if (isCurrentSessionLocked(session)) {
                        failedPrependChapterKey = chapterKey(chapterToLoad)
                        failedPrependAtMillis = System.currentTimeMillis()
                    }
                }
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
        fallbackNext: Chapter?
    ) {
        var requestId = 0L
        synchronized(lock) {
            if (orderResolved || orderLoading) return
            orderLoading = true
            orderRequestId += 1
            requestId = orderRequestId
            orderJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    ensureChapterOrder()
                    if (!isCurrentSession(currentSession)) return@launch
                    publish(currentSession)
                    if (chapter != null && fallbackNext != null) {
                        prefetchNext(chapter, fallbackNext)
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

    private suspend fun loadDetail(chapter: Chapter, showSecurityCheck: Boolean = false): DetailedChapter? {
        val key = chapterKey(chapter)
        val prefetched = synchronized(lock) { prefetchedDetails.remove(key) }
        if (prefetched != null) {
            queueChapterPersistence(chapter, prefetched)
            return prefetched
        }

        val prefetchJob = synchronized(lock) { prefetchJobs[key] }
        if (prefetchJob != null) {
            try {
                prefetchJob.join()
            } catch (e: CancellationException) {
                throw e
            }
            synchronized(lock) { prefetchedDetails.remove(key) }?.let {
                queueChapterPersistence(chapter, it)
                return it
            }
        }

        // The prefetch can finish between the first cache check and job lookup.
        // Check one more time before issuing a duplicate request.
        synchronized(lock) { prefetchedDetails.remove(key) }?.let {
            queueChapterPersistence(chapter, it)
            return it
        }

        val detail = try {
            cancellablePageRequest {
                PresentationAccess.client.getChapterDetail(authorization, chapter,
                    onSecurityCheck = if (showSecurityCheck) {
                        { mutableState.value = State.SecurityCheck }
                    } else null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.loadFailureKind() == LoadFailureKind.NETWORK) {
                val downloaded = runCatching {
                    PresentationAccess.downloads.readChapter(chapter.url)
                }.getOrNull()
                if (downloaded != null) {
                    synchronized(lock) { offlineChapterKeys += key }
                    AppLogger.i(
                        "ChapterPageModel",
                        "Using downloaded chapter while offline: ${chapter.name}"
                    )
                    queueChapterPersistence(chapter, downloaded)
                    return downloaded
                }
            }
            AppLogger.e(
                "ChapterPageModel",
                "Failed to load chapter detail for ${chapter.name}",
                e
            )
            throw e
        }
        queueChapterPersistence(chapter, detail)
        return detail
    }

    /** Disk manifest work must not hold up publication of the reader window. */
    private fun queueChapterPersistence(chapter: Chapter, detail: DetailedChapter) {
        if (!PresentationAccess.settings.readerAutoSaveFlow.value) return
        if (detail.content.isEmpty()) return
        val orderSnapshot = synchronized(lock) { orderedChapters.toList() }
        val novelIdSnapshot = novelId
        val novelNameSnapshot = novelName
        val novelUrlSnapshot = novelUrl
        val novelCoverUrlSnapshot = novelCoverUrl
        val authorizationSnapshot = authorization
        persistenceScope.launch {
            persistenceSemaphore.withPermit {
                persistLoadedChapter(
                    chapter, detail, orderSnapshot, novelIdSnapshot, novelNameSnapshot,
                    novelUrlSnapshot, novelCoverUrlSnapshot, authorizationSnapshot
                )
            }
        }
    }

    private fun prefetchNext(chapter: Chapter, fallbackNext: Chapter?) {
        adjacentChapter(chapter, 1, fallbackNext)?.let(::prefetch)
    }

    private fun prefetchPrevious(chapter: Chapter, fallbackPrevious: Chapter?) {
        adjacentChapter(chapter, -1, fallbackPrevious)?.let(::prefetch)
    }

    private fun prefetch(chapter: Chapter) {
        if (chapter.isExternal) return
        val key = chapterKey(chapter)
        if (key.isBlank()) return
        synchronized(lock) {
            if (loadedChapters.any { sameChapter(it.chapter, chapter) } ||
                prefetchedDetails.containsKey(key) || prefetchJobs.containsKey(key)
            ) {
                return
            }
            prefetchJobs[key] = viewModelScope.launch(Dispatchers.IO) {
                val detail = try {
                    cancellablePageRequest { PresentationAccess.client.getChapterDetail(authorization, chapter) }
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
            cancellablePageRequest { PresentationAccess.client.getNovelDetail(authorization, source) }
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
        fallback: Chapter?
    ): Chapter? {
        val (adjacent, currentInCanonicalOrder) = synchronized(lock) {
            val index = orderedChapters.indexOfFirst { sameChapter(it, chapter) }
            (if (index >= 0) orderedChapters.getOrNull(index + offset) else null) to
                (index >= 0)
        }
        if (adjacent != null) return adjacent.takeUnless { it.isExternal }
        // A history record can point to a valid chapter that the refreshed TOC
        // no longer contains.  In that case the live chapter's previous/next
        // link is the only usable continuation.  If the chapter is present in
        // the canonical TOC, keep its boundary authoritative and do not follow
        // unrelated site navigation links.
        if (currentInCanonicalOrder) return null
        return fallback?.takeUnless { it.isExternal }
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
                previous = first?.let { adjacentChapter(it.chapter, -1, it.fallbackPrevious) },
                next = last?.let { adjacentChapter(it.chapter, 1, it.fallbackNext) },
                isLoadingNext = loadingNext,
                isLoadingPrevious = loadingPrevious,
                chapterOrder = orderedChapters.toList(),
                isOffline = snapshot.any(ReaderChapter::isOffline),
                verificationChapter = verificationChapter,
                verificationWebViewUnavailable = verificationWebViewUnavailable,
                verificationStorageUnavailable = verificationStorageUnavailable,
                verificationOffset = verificationOffset
            )
        }
    }

    private fun rememberVerification(
        session: Long, chapter: Chapter, offset: Int,
        unavailable: Boolean = false,
        storageUnavailable: Boolean = false
    ) {
        synchronized(lock) {
            if (!isCurrentSessionLocked(session)) return
            verificationChapter = chapter
            verificationOffset = offset
            verificationWebViewUnavailable = unavailable
            verificationStorageUnavailable = storageUnavailable
        }
    }

    private fun clearVerificationLocked() {
        verificationChapter = null
        verificationOffset = 0
        verificationWebViewUnavailable = false
        verificationStorageUnavailable = false
    }

    fun markPendingStorageUnavailable() {
        synchronized(lock) {
            if (verificationChapter == null) return
            verificationStorageUnavailable = true
        }
        publish()
    }

    fun retryPendingVerification() {
        val offset = synchronized(lock) {
            val pending = verificationChapter ?: return
            val direction = verificationOffset
            failedAppendChapterKey = null
            failedPrependChapterKey = null
            clearVerificationLocked()
            direction.takeIf { it != 0 } ?: return
        }
        if (offset > 0) loadNextChapter() else loadPreviousChapter()
    }

    private fun isCurrentSession(currentSession: Long): Boolean =
        synchronized(lock) { isCurrentSessionLocked(currentSession) }

    private fun isCurrentSessionLocked(currentSession: Long): Boolean = sessionId == currentSession

    private fun sameChapter(first: Chapter, second: Chapter): Boolean =
        chapterKey(first) == chapterKey(second)

    private fun chapterKey(chapter: Chapter): String = chapterIdentity(chapter)

    private fun normalizeChapterOrder(chapters: List<Chapter>): List<Chapter> =
        chapters.asSequence()
            .filter { !it.isExternal && chapterKey(it).isNotBlank() }
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

/** Saves a value snapshot without retaining the reader ViewModel after navigation. */
private fun persistLoadedChapter(
    chapter: Chapter,
    detail: DetailedChapter,
    orderSnapshot: List<Chapter>,
    novelId: String,
    novelName: String,
    novelUrl: String,
    novelCoverUrl: String,
    authorization: Authorization
) {
    val targetNovelUrl = novelUrl.trim().takeIf { it.isNotBlank() }
        ?.let { EsjzoneUrls.resolve(it) }
        ?: if (novelId.isBlank()) "" else "${EsjzoneUrls.Base}/detail/$novelId.html"
    if (targetNovelUrl.isBlank()) return
    runCatching {
        PresentationAccess.downloads.saveChapter(
            novelName = novelName,
            novelUrl = targetNovelUrl,
            coverUrl = novelCoverUrl,
            chapterOrder = orderSnapshot,
            chapter = chapter,
            detail = detail,
            authorization = authorization
        )
    }.onFailure { error ->
        AppLogger.w("ChapterPageModel", "Failed to auto-save chapter ${chapter.name}", error)
    }
}

internal fun chapterIdentity(chapter: Chapter): String =
    EsjzoneUrls.canonicalPageKey(chapter.url).takeIf { it.isNotBlank() && it != "/" }
        ?: chapter.name.trim()

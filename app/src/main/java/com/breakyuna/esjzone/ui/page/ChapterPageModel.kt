package com.breakyuna.esjzone.ui.page

import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.database.NovelTitleRepository
import com.breakyuna.esjzone.novellibrary.novel.validNovelTitle

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.ui.reader.toReaderDocument
import com.breakyuna.esjzone.util.AppLogger

class ChapterPageModel(
    private val authorization: Authorization,
    private val requestedChapter: MutableState<Chapter>,
    private val novelId: String,
    chapterOrder: List<Chapter>,
    private val novelName: String = "",
    private val novelUrl: String = "",
    private val novelCoverUrl: String = "",
    chapterOrderComplete: Boolean = true
) : AppStateViewModel<ChapterPageModel.State>(State.Loading) {

    val novelNameState = mutableStateOf(validNovelTitle(novelName, novelId, novelUrl))

    private val downloadNovelUrl = novelUrl.takeIf(String::isNotBlank)
        ?: novelId.takeIf(String::isNotBlank)?.let { "/detail/$it.html" }

    // Navigation can retain this model while another page covers the reader.
    private val storageReaderLease = PresentationAccess.storage.also { it.retainReader() }

    override fun onCleared() {
        storageReaderLease.releaseReader()
        com.breakyuna.esjzone.network.EsjzoneClient.closeWenkuBrowserSession()
        super.onCleared()
    }

    private companion object {
        /** Keep a small bidirectional reading window instead of the whole book in RAM. */
        const val MAX_LOADED_CHAPTERS = 9
        /** A consumed chapter must finish saving even after the reader entry is popped. */
        val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val persistenceSemaphore = Semaphore(2)
    }

    sealed class State {
        data object Loading : State()
        data object SecurityCheck : State()
        data object Empty : State()
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
            val passwordStates: Map<String, ReaderChapterPasswordState> = emptyMap(),
            val isOffline: Boolean = false,
            val verificationChapter: Chapter? = null,
            val verificationWebViewUnavailable: Boolean = false,
            val verificationStorageUnavailable: Boolean = false,
            val verificationOffset: Int = 0,
            val nextFailed: Boolean = false,
            val previousFailed: Boolean = false
        ) : State()
    }

    private val lock = Any()
    private val loadedChapters = mutableListOf<ReaderChapter>()
    private val prefetchedDetails = mutableMapOf<String, DetailedChapter>()
    private val offlineChapterKeys = mutableSetOf<String>()
    private val prefetchJobs = mutableMapOf<String, Job>()
    private val passwordJobs = mutableMapOf<String, Job>()
    private val passwordStates = mutableMapOf<String, ReaderChapterPasswordState>()
    private var catalog = ReaderChapterCatalog(chapterOrder, complete = chapterOrderComplete)
    private val orderedChapters: List<Chapter> get() = catalog.chapters
    val chapterOrderState = mutableStateOf(orderedChapters)
    private var sessionId = 0L
    private var initialJob: Job? = null
    private var appendJob: Job? = null
    private var prependJob: Job? = null
    private var orderJob: Job? = null
    private var orderRequestId = 0L
    private var loadingNext = false
    private var loadingPrevious = false
    private var orderLoading = false
    private var orderFailed = false
    private var pendingNextRequest = false
    private var pendingPreviousRequest = false
    private var initialLoadStarted = false
    private var failedAppendChapterKey: String? = null
    private var failedPrependChapterKey: String? = null
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

    fun getDetail(initialChapter: Chapter = requestedChapter.value) {
        synchronized(lock) {
            if (initialLoadStarted) return
            initialLoadStarted = true
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val title = NovelTitleRepository.resolve(authorization, novelId, downloadNovelUrl.orEmpty(), novelNameState.value)
                if (title.isNotBlank()) {
                    novelNameState.value = title
                    NovelTitleRepository.repair(novelId, downloadNovelUrl.orEmpty(), title)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.w("ChapterPageModel", "Unable to resolve novel title", error)
            }
        }
        openChapter(initialChapter)
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
                addAll(passwordJobs.values)
            }.distinct()
            initialJob = null
            appendJob = null
            prependJob = null
            orderJob = null
            orderRequestId += 1
            loadedChapters.clear()
            passwordJobs.clear()
            passwordStates.clear()
            prefetchedDetails.clear()
            loadingNext = false
            loadingPrevious = false
            orderLoading = false
            orderFailed = false
            pendingNextRequest = false
            pendingPreviousRequest = false
            offlineChapterKeys.clear()
            failedAppendChapterKey = null
            failedPrependChapterKey = null
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
            val entry = try {
                loadReaderChapter(chapter, showSecurityCheck = true)
            } catch (error: CancellationException) {
                throw error
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
            if (entry == null) {
                mutableState.value = State.Error(LoadFailureKind.CLIENT)
                return@launch
            }
            if (!entry.requiresPassword && entry.document.blocks.isEmpty()) {
                mutableState.value = State.Empty
                return@launch
            }

            synchronized(lock) {
                if (isCurrentSessionLocked(currentSession)) loadedChapters += entry
            }
            publish(currentSession)

            if (!synchronized(lock) { catalog.isComplete }) {
                val targetNovelUrl = novelUrl.takeIf { it.isNotBlank() }
                    ?: novelId.takeIf { it.isNotBlank() }
                        ?.let { "${EsjzoneUrls.Base}/detail/$it.html" }
                val manifest = targetNovelUrl?.let { url ->
                    runCatching { PresentationAccess.downloads.manifest(url) }.getOrNull()
                }
                if (manifest != null) {
                    val localCatalog = ReaderChapterCatalog(
                        manifest.chapters.filterNot { it.localOnly }.sortedBy { it.index }
                            .map { Chapter(it.name, it.url, false) },
                        complete = manifest.catalogComplete || manifest.complete
                    )
                    synchronized(lock) {
                        if (isCurrentSessionLocked(currentSession) &&
                            (localCatalog.isComplete || orderedChapters.isEmpty())) {
                            catalog = localCatalog
                            chapterOrderState.value = orderedChapters
                        }
                    }
                }
            }
            if (!isCurrentSession(currentSession)) return@launch
            publish(currentSession)

            val hasCanonicalOrder = synchronized(lock) { catalog.isComplete }
            if (hasCanonicalOrder) {
                prefetchNext(chapter)
            } else {
                requestChapterOrder(currentSession, chapter)
            }
        }
    }

    /** Submit for one inline gate without replacing the rest of the reading window. */
    fun submitChapterPassword(target: Chapter, password: String) {
        if (password.isBlank()) return
        val key = chapterKey(target)
        val currentSession = synchronized(lock) {
            if (loadedChapters.none { sameChapter(it.chapter, target) && it.requiresPassword } ||
                passwordStates[key]?.submitting == true) return
            passwordStates[key] = ReaderChapterPasswordState(submitting = true)
            sessionId
        }
        publish(currentSession)
        val job = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            var failureState: ReaderChapterPasswordState? = null
            try {
                val detail = cancellablePageRequest {
                    PresentationAccess.client.unlockPasswordProtectedChapter(authorization, target, password)
                }
                if (detail.content.isEmpty()) throw java.io.IOException("Unlocked chapter content is empty")
                val replaced = synchronized(lock) {
                    if (!isCurrentSessionLocked(currentSession)) return@synchronized false
                    loadedChapters.replacePasswordChapter(target, detail)
                }
                if (replaced) {
                    queueChapterPersistence(target, detail)
                    publish(currentSession)
                    prefetchNext(target)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failureState = if (error is ChapterPasswordRejectedException) {
                    ReaderChapterPasswordState(message = error.message)
                } else ReaderChapterPasswordState(failure = error.loadFailureKind())
                if (error !is ChapterPasswordRejectedException) {
                    AppLogger.e("ChapterPageModel", "Failed to unlock password-protected chapter", error)
                }
            } finally {
                synchronized(lock) {
                    if (isCurrentSessionLocked(currentSession)) {
                        passwordJobs.remove(key)
                        passwordStates.finishPasswordSubmission(key,
                            loadedChapters.any { sameChapter(it.chapter, target) && it.requiresPassword },
                            failureState)
                        publish(currentSession)
                    }
                }
            }
        }
        synchronized(lock) {
            if (isCurrentSessionLocked(currentSession)) passwordJobs[key] = job else job.cancel()
        }
        job.start()
    }

    /** Loads the next canonical TOC chapter when the reader reaches the end buffer. */
    fun retryBoundary(forward: Boolean) {
        synchronized(lock) {
            orderFailed = false
            if (forward) failedAppendChapterKey = null else failedPrependChapterKey = null
        }
        if (forward) loadNextChapter() else loadPreviousChapter()
    }

    fun loadNextChapter() {
        val orderSession = synchronized(lock) {
            if (catalog.isComplete) null else {
                pendingNextRequest = true
                sessionId
            }
        }
        if (orderSession != null) {
            requestChapterOrder(orderSession)
            return
        }

        var currentSession: Long? = null
        var nextChapter: Chapter? = null
        synchronized(lock) {
            if (loadingNext || loadedChapters.isEmpty() ||
                (verificationChapter != null && verificationOffset > 0)) return
            val last = loadedChapters.last()
            val candidate = adjacentChapter(last.chapter, 1) ?: return
            if (chapterKey(candidate) == failedAppendChapterKey) return
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
                val entry = loadReaderChapter(chapterToLoad)
                if (entry != null && isCurrentSession(session)) {
                    synchronized(lock) {
                        if (isCurrentSessionLocked(session) &&
                            loadedChapters.none { sameChapter(it.chapter, chapterToLoad) }
                        ) {
                            loadedChapters += entry
                            failedAppendChapterKey = null
                            clearVerificationLocked()
                            trimLoadedChaptersFromStart()
                        }
                    }
                    publish(session)
                    prefetchNext(chapterToLoad)
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
        val orderSession = synchronized(lock) {
            if (catalog.isComplete) null else {
                pendingPreviousRequest = true
                sessionId
            }
        }
        if (orderSession != null) {
            requestChapterOrder(orderSession)
            return
        }

        var currentSession: Long? = null
        var previousChapter: Chapter? = null
        synchronized(lock) {
            if (loadingPrevious || loadedChapters.isEmpty() ||
                (verificationChapter != null && verificationOffset < 0)) return
            val first = loadedChapters.first()
            val candidate = adjacentChapter(first.chapter, -1) ?: return
            if (chapterKey(candidate) == failedPrependChapterKey) return
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
                val entry = loadReaderChapter(chapterToLoad)
                if (entry != null && isCurrentSession(session)) {
                    synchronized(lock) {
                        if (isCurrentSessionLocked(session) &&
                            loadedChapters.none { sameChapter(it.chapter, chapterToLoad) }
                        ) {
                            loadedChapters.add(0, entry)
                            failedPrependChapterKey = null
                            clearVerificationLocked()
                            trimLoadedChaptersFromEnd()
                        }
                    }
                    publish(session)
                    prefetchPrevious(chapterToLoad)
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
        chapter: Chapter? = null
    ) {
        var requestId = 0L
        synchronized(lock) {
            if (!isCurrentSessionLocked(currentSession) || catalog.isComplete || orderLoading || orderFailed) return
            orderLoading = true
            orderRequestId += 1
            requestId = orderRequestId
            orderJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val resolved = ensureChapterOrder(currentSession)
                    if (!isCurrentSession(currentSession)) return@launch
                    if (!resolved) {
                        synchronized(lock) {
                            if (isCurrentSessionLocked(currentSession)) {
                                orderFailed = true
                                pendingNextRequest = false
                                pendingPreviousRequest = false
                            }
                        }
                        return@launch
                    }
                    publish(currentSession)
                    queueChapterCatalogPersistence()
                    if (chapter != null) {
                        prefetchNext(chapter)
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
                            publish(currentSession)
                        }
                    }
                }
            }
            publish(currentSession)
        }
    }

    private suspend fun loadReaderChapter(chapter: Chapter, showSecurityCheck: Boolean = false): ReaderChapter? {
        val detail = try {
            loadDetail(chapter, showSecurityCheck)
        } catch (_: ChapterPasswordRequiredException) {
            return ReaderChapter.passwordRequired(chapter)
        } ?: return null
        val offline = synchronized(lock) { offlineChapterKeys.remove(chapterKey(chapter)) }
        return ReaderChapter(chapter, detail.toReaderDocument(chapter), detail.previous, detail.next, offline)
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
            PresentationAccess.downloads.readChapter(chapter.url, downloadNovelUrl)?.let { downloaded ->
                queueChapterPersistence(chapter, downloaded)
                return downloaded
            }
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
                    } else null, novelUrl = downloadNovelUrl)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ChapterPasswordRequiredException) {
            throw e
        } catch (e: Exception) {
            if (e.loadFailureKind() == LoadFailureKind.NETWORK) {
                val downloaded = runCatching {
                    PresentationAccess.downloads.readChapter(chapter.url, downloadNovelUrl)
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

    private fun queueChapterCatalogPersistence() {
        if (!PresentationAccess.settings.readerAutoSaveFlow.value) return
        val targetNovelUrl = downloadNovelUrl ?: return
        val orderSnapshot = synchronized(lock) { orderedChapters.toList() }
        val savedName = novelNameState.value
        persistenceScope.launch {
            persistenceSemaphore.withPermit {
                runCatching {
                    PresentationAccess.downloads.saveChapterCatalog(
                        savedName, targetNovelUrl, novelCoverUrl, orderSnapshot
                    )
                }.onFailure { AppLogger.w("ChapterPageModel", "Unable to save resolved chapter catalog", it) }
            }
        }
    }

    /** Disk manifest work must not hold up publication of the reader window. */
    private fun queueChapterPersistence(chapter: Chapter, detail: DetailedChapter) {
        if (!PresentationAccess.settings.readerAutoSaveFlow.value) return
        if (detail.content.isEmpty()) return
        val (orderSnapshot, orderCompleteSnapshot) = synchronized(lock) {
            orderedChapters.toList() to catalog.isComplete
        }
        val novelIdSnapshot = novelId
        val novelNameStateSnapshot = novelNameState
        val novelUrlSnapshot = novelUrl
        val novelCoverUrlSnapshot = novelCoverUrl
        val authorizationSnapshot = authorization
        persistenceScope.launch {
            persistenceSemaphore.withPermit {
                val savedName = novelNameStateSnapshot.value
                persistLoadedChapter(
                    chapter, detail, orderSnapshot, orderCompleteSnapshot, novelIdSnapshot, savedName,
                    novelUrlSnapshot, novelCoverUrlSnapshot, authorizationSnapshot
                )
                // A title lookup can finish while chapter assets are being saved.
                if (novelNameStateSnapshot.value != savedName) {
                    runCatching {
                        PresentationAccess.downloads.repairNovelName(
                            novelUrlSnapshot.ifBlank { "/detail/$novelIdSnapshot.html" }, novelNameStateSnapshot.value
                        )
                    }.onFailure { AppLogger.w("ChapterPageModel", "Unable to repair saved chapter title", it) }
                }
            }
        }
    }

    private fun prefetchNext(chapter: Chapter) {
        adjacentChapter(chapter, 1)?.let(::prefetch)
    }

    private fun prefetchPrevious(chapter: Chapter) {
        adjacentChapter(chapter, -1)?.let(::prefetch)
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
                    cancellablePageRequest {
                        PresentationAccess.client.getChapterDetail(authorization, chapter, novelUrl = downloadNovelUrl)
                    }
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

    private suspend fun ensureChapterOrder(currentSession: Long): Boolean {
        synchronized(lock) {
            if (!isCurrentSessionLocked(currentSession)) return false
            if (catalog.isComplete) return true
        }

        val source = FavoriteNovel(
            name = "",
            url = EsjzoneUrls.resolve(downloadNovelUrl ?: return false)
        )
        val fetchedOrder = try {
            cancellablePageRequest { PresentationAccess.client.getNovelDetail(authorization, source) }
                .also {
                    com.breakyuna.esjzone.database.ReadingStatisticsRecorder.recordTags(it)
                    val title = validNovelTitle(it.name, novelId, it.url)
                    if (title.isNotBlank()) {
                        novelNameState.value = title
                        NovelTitleRepository.repair(novelId, it.url, title)
                    }
                }
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

        val fetchedCatalog = fetchedOrder.takeIf { it.isNotEmpty() }?.let { ReaderChapterCatalog(it) }
        return synchronized(lock) {
            if (!isCurrentSessionLocked(currentSession) || fetchedCatalog?.isComplete != true) return@synchronized false
            catalog = fetchedCatalog
            chapterOrderState.value = orderedChapters
            true
        }
    }

    private fun adjacentChapter(
        chapter: Chapter,
        offset: Int
    ): Chapter? = synchronized(lock) { catalog.adjacent(chapter, offset) }

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
                previous = first?.let { adjacentChapter(it.chapter, -1) },
                next = last?.let { adjacentChapter(it.chapter, 1) },
                isLoadingNext = loadingNext || (orderLoading && pendingNextRequest),
                isLoadingPrevious = loadingPrevious || (orderLoading && pendingPreviousRequest),
                chapterOrder = orderedChapters,
                passwordStates = passwordStates.filterKeys { key -> snapshot.any { chapterKey(it.chapter) == key } },
                isOffline = snapshot.any(ReaderChapter::isOffline),
                verificationChapter = verificationChapter,
                verificationWebViewUnavailable = verificationWebViewUnavailable,
                verificationStorageUnavailable = verificationStorageUnavailable,
                verificationOffset = verificationOffset,
                nextFailed = orderFailed || failedAppendChapterKey != null,
                previousFailed = orderFailed || failedPrependChapterKey != null
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
    orderCompleteSnapshot: Boolean,
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
            chapterOrderComplete = orderCompleteSnapshot,
            chapter = chapter,
            detail = detail,
            authorization = authorization
        )
    }.onFailure { error ->
        AppLogger.w("ChapterPageModel", "Failed to auto-save chapter ${chapter.name}", error)
    }
}

internal fun chapterIdentity(chapter: Chapter): String =
    NovelDownloadStore.chapterKey(chapter.url).takeIf { it.isNotBlank() && it != "/" }
        ?: chapter.name.trim()

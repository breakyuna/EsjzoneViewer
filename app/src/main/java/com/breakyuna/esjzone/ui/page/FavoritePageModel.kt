package com.breakyuna.esjzone.ui.page

import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.app.PresentationAccess

import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.database.BookshelfSyncResult
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.network.EsjzoneUrls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Owns bookshelf synchronization and deletion jobs for the page. */
class FavoritePageModel(private val authorization: Authorization) :
    AppStateViewModel<FavoritePageModel.State>(State.Idle) {
    /** Hot snapshots prevent an empty Room frame from resetting the restored shelf position. */
    val entries: StateFlow<List<BookshelfEntry>> = BookshelfRepository.observe(authorization)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Latest local activity for the showcase, grid progress labels, and reactive ordering. */
    data class ReadingIndex(
        val novelIds: Set<String> = emptySet(),
        val bookKeys: Set<String> = emptySet(),
        private val latestByNovelId: Map<String, LocalReadingActivity> = emptyMap(),
        private val latestByBookKey: Map<String, LocalReadingActivity> = emptyMap()
    ) {
        operator fun contains(entry: BookshelfEntry): Boolean =
            entry.novelId in novelIds || entry.bookKey in bookKeys ||
                (entry.url.isNotBlank() && BookshelfRepository.keyFor(entry.url) in bookKeys)

        fun activityFor(entry: BookshelfEntry): LocalReadingActivity? =
            latestByNovelId[entry.novelId]
                ?: latestByBookKey[entry.bookKey]
                ?: entry.url.takeIf(String::isNotBlank)
                    ?.let(BookshelfRepository::keyFor)
                    ?.let(latestByBookKey::get)
    }

    // Exclude unread recent additions from the showcase; no remote history or extra requests.
    val readingIndex: StateFlow<ReadingIndex> =
        PresentationAccess.database.localReadingActivityDao().observeAll()
        .map { activities ->
            val latestByNovelId = HashMap<String, LocalReadingActivity>()
            val latestByBookKey = HashMap<String, LocalReadingActivity>()
            activities.forEach { activity ->
                activity.novelId.takeIf(String::isNotBlank)?.let { novelId ->
                    val current = latestByNovelId[novelId]
                    if (current == null || activity.lastReadAt > current.lastReadAt) {
                        latestByNovelId[novelId] = activity
                    }
                }
                activity.novelUrl.takeIf(String::isNotBlank)
                    ?.let(BookshelfRepository::keyFor)
                    ?.takeIf(String::isNotBlank)
                    ?.let { bookKey ->
                        val current = latestByBookKey[bookKey]
                        if (current == null || activity.lastReadAt > current.lastReadAt) {
                            latestByBookKey[bookKey] = activity
                        }
                    }
            }
            ReadingIndex(
                novelIds = latestByNovelId.keys,
                bookKeys = latestByBookKey.keys,
                latestByNovelId = latestByNovelId,
                latestByBookKey = latestByBookKey
            )
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingIndex())

    private val _downloadedBookKeys = MutableStateFlow<Set<String>>(emptySet())
    val downloadedBookKeys: StateFlow<Set<String>> = _downloadedBookKeys
    private val isRefreshingDownloaded = AtomicBoolean(false)

    /** Refreshes the local-download index without blocking bookshelf composition. */
    fun refreshDownloaded() {
        if (isRefreshingDownloaded.getAndSet(true)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _downloadedBookKeys.value = PresentationAccess.downloads.listDownloadedNovels()
                    .mapTo(LinkedHashSet()) { summary ->
                        BookshelfRepository.keyFor(summary.novelUrl)
                            .ifBlank { EsjzoneUrls.canonicalPageKey(summary.novelUrl) }
                    }
            } finally {
                isRefreshingDownloaded.set(false)
            }
        }
    }

    sealed class State {
        data object Idle : State()
        data object Syncing : State()
        data class Completed(
            val result: BookshelfSyncResult,
            val eventId: Long = nextEventId.getAndIncrement()
        ) : State()
        data class Failed(
            val failure: LoadFailureKind?,
            val eventId: Long = nextEventId.getAndIncrement()
        ) : State()
    }

    sealed class DeleteState {
        data object Idle : DeleteState()
        data object Deleting : DeleteState()
        data class Completed(
            val count: Int,
            val eventId: Long = nextEventId.getAndIncrement()
        ) : DeleteState()
        data class Failed(
            val eventId: Long = nextEventId.getAndIncrement()
        ) : DeleteState()
    }

    private val _deleteState = MutableStateFlow<DeleteState>(DeleteState.Idle)
    val deleteState: StateFlow<DeleteState> = _deleteState
    private val _legacyBookshelfCount = MutableStateFlow(0)
    val legacyBookshelfCount: StateFlow<Int> = _legacyBookshelfCount
    private val _legacyRecoveryFailed = MutableStateFlow(false)
    val legacyRecoveryFailed: StateFlow<Boolean> = _legacyRecoveryFailed

    fun sync(manualRetry: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = State.Syncing
            try {
                val result = BookshelfRepository.sync(authorization, manualRetry = manualRetry)
                mutableState.value = if (result.success) {
                    State.Completed(result)
                } else {
                    State.Failed(result.loadFailure ?: LoadFailureKind.NETWORK)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (error: Exception) {
                mutableState.value = State.Failed(error.loadFailureKind())
            }
        }
    }
    private var initialized = false

    fun initShelf() {
        if (initialized) return
        initialized = true
        viewModelScope.launch(Dispatchers.IO) {
            BookshelfRepository.migrateLegacyScopeIfNeeded(authorization)
            _legacyBookshelfCount.value = BookshelfRepository.pendingLegacyBookshelfCount(authorization)
        }
        scheduleMetadataSupplement()
        refreshDownloaded()
        autoCheck()
    }

    fun claimLegacyBookshelf() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                BookshelfRepository.claimLegacyBookshelf(authorization)
                _legacyBookshelfCount.value = 0
                _legacyRecoveryFailed.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _legacyRecoveryFailed.value = true
            }
        }
    }

    fun autoCheck() {
        val scope = BookshelfRepository.scopeFor(authorization)
        val now = System.currentTimeMillis()
        synchronized(autoCheckTimes) {
            if (now - (autoCheckTimes[scope] ?: 0L) < AUTO_CHECK_COOLDOWN_MILLIS) return
            autoCheckTimes[scope] = now
        }
        sync()
    }

    fun scheduleMetadataSupplement() {
        BookshelfRepository.scheduleMetadataSupplement(authorization)
    }

    fun delete(entries: List<BookshelfEntry>) {
        if (entries.isEmpty() || _deleteState.value is DeleteState.Deleting) return
        _deleteState.value = DeleteState.Deleting
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = BookshelfRepository.removeBatch(authorization, entries)
                _deleteState.value = DeleteState.Completed(count)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _deleteState.value = DeleteState.Failed()
            }
        }
    }

    private companion object {
        const val AUTO_CHECK_COOLDOWN_MILLIS = 30 * 60 * 1000L
        val autoCheckTimes = mutableMapOf<String, Long>()
        val nextEventId = AtomicLong(1L)
    }
}

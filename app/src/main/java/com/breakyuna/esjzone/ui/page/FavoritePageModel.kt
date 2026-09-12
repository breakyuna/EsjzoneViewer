package com.breakyuna.esjzone.ui.page

import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.app.PresentationAccess

import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.database.BookshelfSyncResult
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.network.EsjzoneUrls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Owns bookshelf synchronization and deletion jobs for the page. */
class FavoritePageModel(private val authorization: Authorization) :
    AppStateViewModel<FavoritePageModel.State>(State.Idle) {
    val entries = BookshelfRepository.observe(authorization)

    /** Presence only: entries already reactively sort by lastReadAt in BookshelfRepository. */
    data class ReadingIndex(
        val novelIds: Set<String> = emptySet(),
        val bookKeys: Set<String> = emptySet()
    ) {
        operator fun contains(entry: BookshelfEntry): Boolean =
            entry.novelId in novelIds || entry.bookKey in bookKeys ||
                (entry.url.isNotBlank() && BookshelfRepository.keyFor(entry.url) in bookKeys)
    }

    // Exclude unread recent additions from the showcase; no remote history or extra requests.
    val readingIndex = PresentationAccess.database.localReadingActivityDao().observeAll()
        .map { activities ->
            ReadingIndex(
                novelIds = activities.mapNotNullTo(HashSet()) { it.novelId.takeIf(String::isNotBlank) },
                bookKeys = activities.mapNotNullTo(HashSet()) { activity ->
                    activity.novelUrl.takeIf(String::isNotBlank)
                        ?.let(BookshelfRepository::keyFor)?.takeIf(String::isNotBlank)
                }
            )
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    private val _downloadedBookKeys = MutableStateFlow<Set<String>>(emptySet())
    val downloadedBookKeys: StateFlow<Set<String>> = _downloadedBookKeys

    /** Refreshes the local-download index without blocking bookshelf composition. */
    fun refreshDownloaded() {
        viewModelScope.launch(Dispatchers.IO) {
            _downloadedBookKeys.value = PresentationAccess.downloads.listDownloadedNovels()
                .mapTo(LinkedHashSet()) { summary ->
                    BookshelfRepository.keyFor(summary.novelUrl)
                        .ifBlank { EsjzoneUrls.canonicalPageKey(summary.novelUrl) }
                }
        }
    }

    sealed class State {
        data object Idle : State()
        data object Syncing : State()
        data class Completed(val result: BookshelfSyncResult) : State()
        data class Failed(val failure: LoadFailureKind?) : State()
    }

    sealed class DeleteState {
        data object Idle : DeleteState()
        data object Deleting : DeleteState()
        data class Completed(val count: Int) : DeleteState()
        data object Failed : DeleteState()
    }

    private val _deleteState = MutableStateFlow<DeleteState>(DeleteState.Idle)
    val deleteState: StateFlow<DeleteState> = _deleteState

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = State.Syncing
            try {
                val result = BookshelfRepository.sync(authorization)
                mutableState.value = if (result.success) {
                    State.Completed(result)
                } else {
                    State.Failed(result.loadFailure)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (error: Exception) {
                mutableState.value = State.Failed(error.loadFailureKind())
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
                _deleteState.value = DeleteState.Failed
            }
        }
    }

    private companion object {
        const val AUTO_CHECK_COOLDOWN_MILLIS = 30 * 60 * 1000L
        val autoCheckTimes = mutableMapOf<String, Long>()
    }
}

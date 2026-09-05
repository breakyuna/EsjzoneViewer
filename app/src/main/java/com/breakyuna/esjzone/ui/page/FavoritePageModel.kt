package com.breakyuna.esjzone.ui.page

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.database.BookshelfSyncResult
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.offline.NovelDownloadStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Owns bookshelf synchronization and deletion jobs for the page. */
class FavoritePageModel(private val authorization: Authorization) :
    StateScreenModel<FavoritePageModel.State>(State.Idle) {
    val entries = BookshelfRepository.observe(authorization)

    private val _downloadedBookKeys = MutableStateFlow<Set<String>>(emptySet())
    val downloadedBookKeys: StateFlow<Set<String>> = _downloadedBookKeys

    /** Refreshes the local-download index without blocking bookshelf composition. */
    fun refreshDownloaded() {
        screenModelScope.launch(Dispatchers.IO) {
            _downloadedBookKeys.value = NovelDownloadStore.listDownloadedNovels()
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
        screenModelScope.launch(Dispatchers.IO) {
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

    fun scheduleMetadataSupplement() {
        BookshelfRepository.scheduleMetadataSupplement(authorization)
    }

    fun delete(entries: List<BookshelfEntry>) {
        if (entries.isEmpty() || _deleteState.value is DeleteState.Deleting) return
        _deleteState.value = DeleteState.Deleting
        screenModelScope.launch(Dispatchers.IO) {
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
}

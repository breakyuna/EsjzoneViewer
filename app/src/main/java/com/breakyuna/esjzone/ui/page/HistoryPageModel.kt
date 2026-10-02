package com.breakyuna.esjzone.ui.page

import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.breakyuna.esjzone.app.PresentationAccess

import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.features.HistoryDataCache
import com.breakyuna.esjzone.network.features.getHistories
import com.breakyuna.esjzone.network.features.removeHistories
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.novellibrary.novel.HistoryNovel
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

/** Cloud history loader isolated from the tab and its paging presentation. */
class HistoryPageModel(
    private val authorization: Authorization
) : AppStateViewModel<HistoryPageModel.State>(
    HistoryDataCache.readSnapshot(authorization)?.let { State.Result(it, isSyncSuccess = false) } ?: State.Loading
) {

    private var loadJob: Job? = null
    private var loadStarted = false
    @Volatile private var loadGeneration = 0L
    var deleting by mutableStateOf(false)
        private set

    sealed class State {
        data object Loading : State()
        data class Error(val failure: LoadFailureKind) : State()
        data class Result(
            val historyNovels: List<HistoryNovel>,
            val isSyncing: Boolean = false,
            val isSyncSuccess: Boolean = false,
            val lastSyncFailure: LoadFailureKind? = null
        ) : State()
    }

    fun getNovels(forceRefresh: Boolean = false) {
        if (loadStarted || deleting) return
        loadStarted = true
        val generation = ++loadGeneration
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val visibleData = mutableState.value as? State.Result
            mutableState.value = visibleData?.copy(
                isSyncing = true,
                isSyncSuccess = false,
                lastSyncFailure = null
            ) ?: State.Loading
            try {
                val histories = com.breakyuna.esjzone.network.cancellablePageRequest {
                    PresentationAccess.client.getHistories(
                        authorization,
                        forceRefresh = forceRefresh
                    )
                }
                ensureActive()
                HistoryDataCache.writeSnapshot(authorization, histories)
                mutableState.value = State.Result(
                    historyNovels = histories,
                    isSyncing = false,
                    isSyncSuccess = true,
                    lastSyncFailure = null
                )
            } catch (e: CancellationException) {
                if (generation != loadGeneration) throw e
                val current = mutableState.value
                if (current is State.Result) {
                    mutableState.value = current.copy(
                        isSyncing = false,
                        isSyncSuccess = false,
                        lastSyncFailure = LoadFailureKind.NETWORK
                    )
                } else if (current is State.Loading) {
                    mutableState.value = State.Error(LoadFailureKind.NETWORK)
                }
                throw e
            } catch (e: Exception) {
                if (generation != loadGeneration) return@launch
                if (visibleData == null) {
                    mutableState.value = State.Error(e.loadFailureKind())
                } else {
                    mutableState.value = visibleData.copy(
                        isSyncing = false,
                        isSyncSuccess = false,
                        lastSyncFailure = e.loadFailureKind()
                    )
                }
                AppLogger.e("HistoryPageModel", "Failed to load cloud histories", e)
            } finally {
                if (generation == loadGeneration) loadStarted = false
            }
        }
    }

    fun reload() {
        if (deleting) return
        loadJob?.cancel()
        loadStarted = false
        getNovels(forceRefresh = true)
    }

    fun delete(viewIds: Set<String>, onComplete: (Set<String>) -> Unit) {
        if (deleting) return
        val current = mutableState.value as? State.Result ?: return
        val ids = viewIds.intersect(current.historyNovels.map { it.vid }.toSet()).filterTo(linkedSetOf()) { it.isNotBlank() }
        if (ids.isEmpty()) {
            onComplete(emptySet())
            return
        }
        deleting = true
        ++loadGeneration
        viewModelScope.launch {
            try {
                // Prevent a refresh started before deletion from restoring removed rows.
                loadJob?.cancelAndJoin()
                loadStarted = false
                val visible = (mutableState.value as? State.Result) ?: current
                mutableState.value = visible.copy(isSyncing = false)
                val epoch = PresentationAccess.client.sessionEpoch()
                val deleted = runInterruptible(Dispatchers.IO) {
                    PresentationAccess.client.removeHistories(authorization, ids)
                }
                ensureActive()
                if (epoch != PresentationAccess.client.sessionEpoch()) return@launch
                val remaining = visible.historyNovels.filterNot { it.vid in deleted }
                mutableState.value = visible.copy(historyNovels = remaining, isSyncing = false)
                // An uncertain response requires a fresh read; do not persist it as a known snapshot.
                if (deleted == ids) withContext(Dispatchers.IO) {
                    HistoryDataCache.writeSnapshot(authorization, remaining)
                }
                onComplete(deleted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("HistoryPageModel", "Failed to delete cloud histories", e)
                onComplete(emptySet())
            } finally {
                deleting = false
            }
        }
    }

}

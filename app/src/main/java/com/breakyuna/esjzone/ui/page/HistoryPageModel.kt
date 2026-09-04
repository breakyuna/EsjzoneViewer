package com.breakyuna.esjzone.ui.page

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.features.getHistories
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.novellibrary.novel.HistoryNovel
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Cloud history loader isolated from the tab and its paging presentation. */
class HistoryPageModel(
    private val authorization: Authorization
) : StateScreenModel<HistoryPageModel.State>(State.Loading) {

    private var loadJob: Job? = null
    private var loadStarted = false
    private val _deletedIds = MutableStateFlow<Set<String>>(emptySet())
    val deletedIds: StateFlow<Set<String>> = _deletedIds.asStateFlow()

    sealed class State {
        data object Loading : State()
        data class Error(val failure: LoadFailureKind) : State()
        data class Result(val historyNovels: List<HistoryNovel>) : State()
    }

    fun getNovels(forceRefresh: Boolean = false) {
        if (loadStarted) return
        loadStarted = true
        loadJob?.cancel()
        loadJob = screenModelScope.launch(Dispatchers.IO) {
            mutableState.value = State.Loading
            try {
                val histories = EsjzoneClient.getHistories(
                    authorization,
                    forceRefresh = forceRefresh
                )
                ensureActive()
                mutableState.value = State.Result(histories)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = State.Error(e.loadFailureKind())
                loadStarted = false
                AppLogger.e("HistoryPageModel", "Failed to load cloud histories", e)
            }
        }
    }

    fun reload() {
        loadStarted = false
        getNovels(forceRefresh = true)
    }

    fun deleteHistory(vid: String, name: String) {
        screenModelScope.launch(Dispatchers.IO) {
            try {
                EsjzoneClient.removeHistory(authorization, vid)
                _deletedIds.value = _deletedIds.value + vid
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("HistoryPageModel", "Failed to remove history for $name", e)
            }
        }
    }
}

package com.breakyuna.esjzone.ui.tab

import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.app.PresentationAccess

import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.database.entity.SearchHistory
import com.breakyuna.esjzone.util.AppLogger
import com.breakyuna.esjzone.util.currentDateString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Owns local search-history persistence so the tab remains a presentation layer. */
class SearchHistoryModel : AppStateViewModel<SearchHistoryModel.State>(State()) {

    data class State(
        val histories: List<SearchHistory> = emptyList(),
        val loading: Boolean = true
    )

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val histories = PresentationAccess.database.searchHistoryDao().getAll()
                mutableState.value = State(histories = histories, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SearchHistoryModel", "Failed to load search history", e)
                mutableState.value = mutableState.value.copy(loading = false)
            }
        }
    }

    fun save(keyword: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dao = PresentationAccess.database.searchHistoryDao()
                val history = dao.findByKeyword(keyword)
                    ?: SearchHistory(keyword = keyword, time = currentDateString())
                history.time = currentDateString()
                dao.insertAll(history)
                mutableState.value = State(dao.getAll(), loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SearchHistoryModel", "Failed to persist search history", e)
            }
        }
    }

    fun delete(history: SearchHistory) {
        mutableState.value = mutableState.value.copy(
            histories = mutableState.value.histories.filterNot { it.index == history.index }
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                PresentationAccess.database.searchHistoryDao().delete(history)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SearchHistoryModel", "Failed to delete search history entry", e)
            }
        }
    }

    fun clear() {
        val items = mutableState.value.histories
        mutableState.value = mutableState.value.copy(histories = emptyList())
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dao = PresentationAccess.database.searchHistoryDao()
                items.forEach { dao.delete(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SearchHistoryModel", "Failed to clear search history", e)
            }
        }
    }
}

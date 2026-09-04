package com.breakyuna.esjzone.ui.tab

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.breakyuna.esjzone.MainActivity
import com.breakyuna.esjzone.database.entity.SearchHistory
import com.breakyuna.esjzone.util.AppLogger
import com.breakyuna.esjzone.util.currentDateString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Owns local search-history persistence so the tab remains a presentation layer. */
class SearchHistoryModel : StateScreenModel<SearchHistoryModel.State>(State()) {

    data class State(
        val histories: List<SearchHistory> = emptyList(),
        val loading: Boolean = true
    )

    fun load() {
        screenModelScope.launch(Dispatchers.IO) {
            try {
                val histories = MainActivity.database.searchHistoryDao().getAll()
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
        screenModelScope.launch(Dispatchers.IO) {
            try {
                val dao = MainActivity.database.searchHistoryDao()
                val history = if (dao.exists(keyword)) {
                    dao.findByKeyword(keyword)
                } else {
                    SearchHistory(keyword = keyword, time = currentDateString())
                }
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
        screenModelScope.launch(Dispatchers.IO) {
            try {
                MainActivity.database.searchHistoryDao().delete(history)
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
        screenModelScope.launch(Dispatchers.IO) {
            try {
                val dao = MainActivity.database.searchHistoryDao()
                items.forEach { dao.delete(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SearchHistoryModel", "Failed to clear search history", e)
            }
        }
    }
}

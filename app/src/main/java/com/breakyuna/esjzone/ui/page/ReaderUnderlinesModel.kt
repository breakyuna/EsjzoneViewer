package com.breakyuna.esjzone.ui.page

import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ReaderUnderlinesModel(
    private val database: GeneralDatabase = PresentationAccess.database
) : AppStateViewModel<Boolean>(false) {
    val bookmarks = database.bookmarkDao().observeAll()
        .flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val underlines = database.cacheDao().observeReaderUnderlines().map { rows ->
        rows.associate { row ->
            row.key.removePrefix(ReaderUnderlines.KEY_PREFIX) to
                runCatching { ReaderUnderlines.decode(row.value) }.getOrDefault(emptyList())
        }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun update(chapterKey: String, selection: ReaderUnderline, remove: Boolean,
        renderedText: (Int) -> String? = { null }) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                database.withTransaction {
                    val dao = database.cacheDao()
                    val key = ReaderUnderlines.KEY_PREFIX + chapterKey
                    val updated = ReaderUnderlines.update(ReaderUnderlines.decode(dao.findByKey(key)?.value), selection, remove, renderedText)
                    if (updated.isEmpty()) dao.deleteByKey(key) else dao.putAtomic(key, ReaderUnderlines.encode(updated))
                }
                mutableState.value = false
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { mutableState.value = true }
        }
    }
}

package com.breakyuna.esjzone.ui.page

import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ReaderUnderlinesModel(
    private val database: GeneralDatabase = PresentationAccess.database
) : AppStateViewModel<Int>(0) {
    val bookmarks = database.bookmarkDao().observeAll()
        .flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val underlines = database.cacheDao().observeReaderUnderlines().map { rows ->
        rows.associate { row ->
            val chapterKey = row.key.removePrefix(ReaderUnderlines.KEY_PREFIX)
            chapterKey to runCatching { decode(chapterKey, row.value) }.getOrElse { error ->
                AppLogger.w("ReaderUnderlinesModel",
                    "Could not read saved underlines; chapter=${chapterKey.hashCode().toUInt().toString(16)}", error)
                emptyList()
            }
        }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun update(chapterKey: String, selection: ReaderUnderline, remove: Boolean,
        renderedText: (Int) -> String? = { null }) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                database.withTransaction {
                    val dao = database.cacheDao()
                    val key = ReaderUnderlines.KEY_PREFIX + chapterKey
                    val updated = ReaderUnderlines.update(decode(chapterKey, dao.findByKey(key)?.value), selection, remove, renderedText)
                    if (updated.isEmpty()) dao.deleteByKey(key) else dao.putAtomic(key, ReaderUnderlines.encode(updated))
                }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                AppLogger.e("ReaderUnderlinesModel",
                    "Could not ${if (remove) "remove" else "save"} underline; " +
                        "chapter=${chapterKey.hashCode().toUInt().toString(16)}, " +
                        "block=${selection.blockIndex}, start=${selection.start}, end=${selection.end}, " +
                        "ranges=${selection.ranges().size}", error)
                mutableState.update { it + 1 }
            }
        }
    }

    private fun decode(chapterKey: String, value: String?): List<ReaderUnderline> = ReaderUnderlines.decode(value) {
        AppLogger.w("ReaderUnderlinesModel",
            "Recovered mixed-source underline ranges; chapter=${chapterKey.hashCode().toUInt().toString(16)}")
    }
}

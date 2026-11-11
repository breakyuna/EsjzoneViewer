package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.database.BookmarkCoverStore
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.database.entity.Bookmark
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.ChapterSource
import com.breakyuna.esjzone.novellibrary.novel.CoverSource
import com.breakyuna.esjzone.novellibrary.novel.validNovelTitle
import com.breakyuna.esjzone.offline.DownloadedNovelManifest
import com.breakyuna.esjzone.offline.DownloadedChapterRecord
import com.breakyuna.esjzone.ui.component.AppNovelCover
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class UnderlineListRow(
    val chapterKey: String,
    val mark: ReaderUnderline,
    val novelId: String,
    val novelName: String,
    val novelUrl: String,
    val chapterName: String,
    val coverUrl: String,
    val displayCoverUrl: String = coverUrl
) {
    val id: String = buildString {
        append(chapterKey.length).append(':').append(chapterKey)
        mark.ranges().forEach { range ->
            append(':').append(range.blockIndex).append(',').append(range.signature)
                .append(',').append(range.start).append(',').append(range.end)
        }
    }
}

/** Resolve old records from local metadata without requesting the website. */
internal fun resolveUnderlineRows(
    marks: Map<String, List<ReaderUnderline>>,
    bookmarks: List<Bookmark>,
    history: List<LocalReadingActivity>,
    shelf: List<BookshelfEntry>,
    downloads: List<DownloadedNovelManifest>
): List<UnderlineListRow> {
    val bookmarkByChapter = bookmarks.associateBy(::bookmarkChapterIdentity)
    val bookmarkByNovel = bookmarks.filter { it.novelId.isNotBlank() }.associateBy { it.novelId }
    val historyByChapter = history.associateBy { chapterIdentity(Chapter(it.chapterName, it.chapterUrl, true)) }
    val historyByNovel = history.filter { it.novelId.isNotBlank() }.associateBy { it.novelId }
    val shelfByNovel = shelf.filter { it.novelId.isNotBlank() }.associateBy { it.novelId }
    val downloadByChapter = buildMap<String, Pair<DownloadedNovelManifest, DownloadedChapterRecord>> {
        downloads.forEach { manifest -> manifest.chapters.forEach { chapter ->
            val key = chapterIdentity(Chapter(chapter.name, chapter.url, false))
            if (key !in this) put(key, manifest to chapter)
        } }
    }
    return marks.flatMap { (chapterKey, chapterMarks) ->
        val bookmark = bookmarkByChapter[chapterKey]
        val chapterHistory = historyByChapter[chapterKey]
        val download = downloadByChapter[chapterKey]
        val sharedInfo = chapterMarks.mapNotNull { it.info }.reduceOrNull { current, next -> current.withFallback(next) }
        // The same saved range can occur twice in older data; it represents one visible underline.
        val uniqueMarks = chapterMarks.groupBy { it.ranges() }.values.map { duplicates ->
            duplicates.firstOrNull { !it.quote.isNullOrBlank() } ?: duplicates.first()
        }
        uniqueMarks.map { mark ->
            val info = mark.info?.withFallback(sharedInfo) ?: sharedInfo
            val chapter = Chapter("", chapterKey, false)
            val id = sequenceOf(info?.novelId, bookmark?.novelId, chapterHistory?.novelId, chapter.novelId())
                .filterNotNull().firstOrNull(String::isNotBlank).orEmpty()
            val bookHistory = historyByNovel[id]
            val bookBookmark = bookmarkByNovel[id]
            val bookShelf = shelfByNovel[id]
            val chapterName = sequenceOf(info?.chapterName, bookmark?.chapterName, chapterHistory?.chapterName,
                download?.second?.name).filterNotNull().firstOrNull(String::isNotBlank).orEmpty()
            val novelUrl = sequenceOf(info?.novelUrl, chapterHistory?.novelUrl, download?.first?.url,
                bookHistory?.novelUrl, bookShelf?.url).filterNotNull().firstOrNull(String::isNotBlank)
                ?: if (id.isNotBlank() && chapter.source == ChapterSource.ESJ_ZONE) "/detail/$id.html" else ""
            val novelName = sequenceOf(info?.novelName, bookmark?.novelName, chapterHistory?.novelName,
                download?.first?.name, bookHistory?.novelName, bookShelf?.title, bookBookmark?.novelName)
                .filterNotNull().map { validNovelTitle(it, id, novelUrl, chapterName) }
                .firstOrNull(String::isNotBlank).orEmpty()
            val coverUrl = sequenceOf(info?.coverUrl, chapterHistory?.novelCoverUrl, download?.first?.coverUrl,
                bookHistory?.novelCoverUrl, bookShelf?.coverUrl).filterNotNull().firstOrNull(String::isNotBlank).orEmpty()
            UnderlineListRow(chapterKey, mark, id, novelName, novelUrl, chapterName, coverUrl)
        }
    }.sortedWith(compareBy<UnderlineListRow>({ it.novelName.lowercase() }, { it.chapterKey },
        { it.mark.blockIndex }, { it.mark.start }))
}

/** Delete only the selected ranges, using the latest stored data in one transaction. */
internal suspend fun deleteUnderlineRows(database: GeneralDatabase, rows: List<UnderlineListRow>) {
    database.withTransaction {
        val dao = database.cacheDao()
        rows.groupBy { it.chapterKey }.forEach { (chapterKey, selected) ->
            val key = ReaderUnderlines.KEY_PREFIX + chapterKey
            val existing = ReaderUnderlines.decode(dao.findByKey(key)?.value)
            val ranges = selected.map { it.mark.ranges() }.toSet()
            val remaining = existing.filterNot { it.ranges() in ranges }
            if (remaining.isEmpty()) dao.deleteByKey(key)
            else if (remaining != existing) dao.putAtomic(key, ReaderUnderlines.encode(remaining))
        }
    }
}

internal class UnderlinesPageModel : AppStateViewModel<UnderlinesPageModel.State>(State.Loading) {
    sealed class State {
        data object Loading : State()
        data class Result(val rows: List<UnderlineListRow>) : State()
        data object Error : State()
    }

    private var started = false

    fun load() {
        if (started) return
        started = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val database = PresentationAccess.database
                combine(database.cacheDao().observeReaderUnderlines(), database.bookmarkDao().observeAll(),
                    database.localReadingActivityDao().observeAll()) { marks, bookmarks, history ->
                    Triple(marks, bookmarks, history)
                }.collectLatest { (cached, bookmarks, history) ->
                    var unreadable = false
                    val marks = cached.associate { row ->
                        row.key.removePrefix(ReaderUnderlines.KEY_PREFIX) to runCatching {
                            ReaderUnderlines.decode(row.value)
                        }.getOrElse { error ->
                            unreadable = true
                            AppLogger.w("UnderlinesPage", "Could not read a saved underline", error)
                            emptyList()
                        }
                    }
                    if (marks.values.all { it.isEmpty() }) {
                        check(!unreadable) { "Could not read stored underlines" }
                        mutableState.value = State.Result(emptyList())
                        return@collectLatest
                    }
                    val shelf = database.bookshelfDao().getAllEntries()
                    val downloads = try {
                        PresentationAccess.downloads.listDownloadedMetadata().map { it.manifest }
                    } catch (error: CancellationException) { throw error
                    } catch (error: Exception) {
                        AppLogger.w("UnderlinesPage", "Could not read downloaded chapter information", error)
                        emptyList()
                    }
                    val localCovers = mutableMapOf<String, String?>()
                    val rows = resolveUnderlineRows(marks, bookmarks, history, shelf, downloads).map { row ->
                        val local = if (row.novelId.isBlank()) null else {
                            if (row.novelId !in localCovers) localCovers[row.novelId] = runCatching {
                                BookmarkCoverStore.getCoverUri(row.novelId, row.chapterKey)
                            }.getOrNull()
                            localCovers[row.novelId]
                        }
                        row.copy(displayCoverUrl = local ?: row.coverUrl)
                    }
                    mutableState.value = State.Result(rows)
                }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                started = false
                mutableState.value = State.Error
                AppLogger.e("UnderlinesPage", "Failed to load underlines", error)
            }
        }
    }

    fun retry() {
        started = false
        mutableState.value = State.Loading
        load()
    }

    fun delete(rows: List<UnderlineListRow>, onComplete: (Boolean) -> Unit) {
        if (rows.isEmpty()) { onComplete(true); return }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                deleteUnderlineRows(PresentationAccess.database, rows)
                withContext(Dispatchers.Main) { onComplete(true) }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                AppLogger.e("UnderlinesPage", "Failed to delete underlines", error)
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }
}

@Composable
internal fun UnderlineCard(row: UnderlineListRow, editing: Boolean, selected: Boolean,
    onOpen: () -> Unit, onDelete: () -> Unit) {
    val title = row.novelName.ifBlank { stringResource(R.string.download_unknown_novel) }
    val cover = CoverSource.fromUrl(row.displayCoverUrl)
    Row(modifier = Modifier.fillMaxWidth().clip(AppShapes.standard)
        .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surface, AppShapes.standard)
        .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, AppShapes.standard) else Modifier)
        .clickable(onClick = onOpen).semantics { role = Role.Button }.padding(AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        AppNovelCover(coverUrl = cover.url, coverState = cover.state, title = title,
            modifier = Modifier.size(width = 56.dp, height = 76.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(title, style = AppTypography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.chapterName.ifBlank { stringResource(R.string.reader_underline_unknown_chapter) },
                style = AppTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.mark.quote?.takeIf { it.isNotBlank() } ?: stringResource(R.string.reader_underline_position,
                row.mark.blockIndex + 1, row.mark.start + 1, row.mark.end),
                style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (editing) {
            Icon(if (selected) Icons.Filled.Check else Icons.Filled.BookmarkBorder, contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.reader_remove_underline))
            }
        }
    }
}

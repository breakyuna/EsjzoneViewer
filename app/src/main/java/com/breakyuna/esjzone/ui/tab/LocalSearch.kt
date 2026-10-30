package com.breakyuna.esjzone.ui.tab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.novellibrary.novel.*
import com.breakyuna.esjzone.ui.component.AppNovelCover
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.designsystem.wideContentWidth
import com.breakyuna.esjzone.ui.navigation.*
import com.breakyuna.esjzone.ui.page.*
import kotlinx.coroutines.*

internal data class LocalSearchEntry(val name: String, val url: String, val cover: String = "",
    val adult: Boolean = false, val downloaded: Boolean = false, val history: LocalReadingActivity? = null)

/** Downloads win the destination; history supplies the resume position; adult flags are never lost. */
internal fun matchLocalBooks(entries: List<LocalSearchEntry>, keyword: String, adult: Boolean): List<LocalSearchEntry> =
    entries.filter { it.url.isNotBlank() }.groupBy { EsjzoneUrls.canonicalPageKey(it.url) }.values.map { copies ->
        val first = copies.first()
        first.copy(name = copies.firstOrNull { it.name.isNotBlank() }?.name.orEmpty(),
            cover = copies.firstOrNull { it.cover.isNotBlank() }?.cover.orEmpty(),
            downloaded = copies.any { it.downloaded }, adult = copies.any { it.adult },
            history = copies.mapNotNull { it.history }.maxByOrNull { it.lastReadAt })
    }.filter { (adult || !it.adult) && it.name.contains(keyword.trim(), ignoreCase = true) }

private class LocalSearchModel(private val authorization: Authorization) : AppStateViewModel<LocalSearchModel.State>(State()) {
    data class State(val items: List<LocalSearchEntry> = emptyList(), val loading: Boolean = false, val failed: Boolean = false)
    private var job: Job? = null
    fun search(query: String, adult: Boolean) {
        job?.cancel()
        mutableState.value = State(loading = true)
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val downloads = PresentationAccess.downloads.listDownloadedNovels().map {
                    LocalSearchEntry(it.novelName, it.novelUrl, it.coverUrl, it.manifest.isAdult, downloaded = true)
                }
                val history = PresentationAccess.database.localReadingActivityDao().getAll().map {
                    LocalSearchEntry(validNovelTitle(it.novelName, it.novelId, it.novelUrl, it.chapterName),
                        it.novelUrl, it.novelCoverUrl, history = it)
                }
                val shelf = PresentationAccess.database.bookshelfDao().getAll(BookshelfRepository.scopeFor(authorization))
                    .filter { it.visible }.map { LocalSearchEntry(it.title, it.url, it.coverUrl, it.isAdult) }
                val result = matchLocalBooks(downloads + history + shelf, query, adult)
                ensureActive()
                mutableState.value = State(result)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.value = State(failed = true) }
        }
    }
}

@Composable
internal fun LocalSearchResults(query: String, modifier: Modifier = Modifier) {
    val authorization = LocalAuthorization.current
    val navigator = LocalBaseNavigator.current
    val model = rememberAppViewModel { LocalSearchModel(authorization) }
    val state by model.state.collectAsStateWithLifecycle()
    val adult by PresentationAccess.settings.adult
    LaunchedEffect(query, adult) { model.search(query, adult) }
    LazyColumn(modifier.wideContentWidth(), contentPadding = PaddingValues(16.dp)) {
        item {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.failed) {
                Text(stringResource(R.string.load_client_error))
                TextButton(onClick = { model.search(query, adult) }) { Text(stringResource(R.string.retry)) }
            } else if (!state.loading && state.items.isEmpty()) Text(stringResource(R.string.local_search_empty))
        }
        items(state.items, key = { EsjzoneUrls.canonicalPageKey(it.url) }) { book ->
            Row(Modifier.fillMaxWidth().clickable {
                val history = book.history
                val destination: AppDestination = when {
                    book.downloaded -> DownloadedNovelPage(book.url)
                    history != null -> {
                        val chapter = Chapter(history.chapterName, history.chapterUrl, true)
                        ChapterPage(history.novelId, chapter, ChapterStateHolder(chapter), novelName = book.name,
                            novelUrl = book.url, novelCoverUrl = book.cover, restoreFromLocalHistory = true)
                    }
                    else -> NovelPage(CoveredNovelImpl(name = book.name, url = book.url, coverUrl = book.cover, isAdult = book.adult))
                }
                navigator?.pushIfNotCurrent(destination)
            }.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppNovelCover(book.cover, book.name, Modifier.size(54.dp, 76.dp), isAdult = book.adult, elevated = false)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(book.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    Text(stringResource(when {
                        book.downloaded -> R.string.downloads
                        book.history != null -> R.string.history
                        else -> R.string.bookshelf
                    }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

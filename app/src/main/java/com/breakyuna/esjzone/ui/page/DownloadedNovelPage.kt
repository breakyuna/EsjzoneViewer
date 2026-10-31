package com.breakyuna.esjzone.ui.page

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
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.cancellablePageRequest
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.FavoriteNovel
import com.breakyuna.esjzone.novellibrary.novel.novelTitleId
import com.breakyuna.esjzone.offline.DownloadedNovelManifest
import com.breakyuna.esjzone.offline.DownloadedChapterRecord
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.offline.NovelDownloadManager
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.designsystem.wideContentWidth
import com.breakyuna.esjzone.ui.navigation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

/** A local catalog: opening it never requests a detail page. */
class DownloadedNovelPage(private val url: String) : AppDestination {
    override val key = "DownloadedNovelPage:$url"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val model = rememberAppViewModel { DownloadedNovelModel(url) }
        val state by model.state.collectAsStateWithLifecycle()
        val manifest = state.manifest
        val work by remember(url) { NovelDownloadManager.statusFlow(
            com.breakyuna.esjzone.EsjzoneApplication.instance, url) }.collectAsStateWithLifecycle(initialValue = null)
        val migrating by PresentationAccess.downloads.migrating.collectAsStateWithLifecycle()
        val active = work?.let { it.running || it.waiting } == true
        val records = remember(manifest) { manifest?.chapters.orEmpty().sortedBy { it.index } }
        val history = state.history
        val resume = remember(records, history) { localResumeRecord(records, history?.chapterUrl) }
        fun open(record: DownloadedChapterRecord) {
            val book = manifest ?: return
            val chapter = Chapter(record.name, record.url, true)
            navigator?.pushIfNotCurrent(ChapterPage(
                novelId = novelTitleId("", book.url), chapter = chapter, history = ChapterStateHolder(chapter),
                chapterOrder = records.filterNot { it.localOnly }.map { Chapter(it.name, it.url, true) },
                chapterOrderComplete = book.catalogComplete || book.complete,
                novelName = book.name, novelUrl = book.url, novelCoverUrl = book.coverUrl,
                restoreFromLocalHistory = state.history?.chapterUrl?.let {
                    NovelDownloadStore.chapterKey(it) == NovelDownloadStore.chapterKey(record.url)
                } == true
            ))
        }
        Scaffold(topBar = { TopAppBar(
            title = { Text(manifest?.name.orEmpty().ifBlank { stringResource(R.string.downloads) }, maxLines = 1) },
            navigationIcon = { BackIconButton { navigator?.pop() } }
        ) }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).wideContentWidth(),
                contentPadding = PaddingValues(16.dp)) {
                item {
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (state.failed) {
                        Text(stringResource(R.string.load_client_error))
                        TextButton(onClick = model::reload) { Text(stringResource(R.string.retry)) }
                    }
                    if (!state.loading && !state.failed && manifest == null) Text(stringResource(R.string.download_empty))
                    if (manifest != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (resume != null) Button(onClick = { open(resume) }, enabled = !migrating) {
                                Text(stringResource(if (history != null &&
                                    NovelDownloadStore.chapterKey(history.chapterUrl) == NovelDownloadStore.chapterKey(resume.url))
                                    R.string.continue_reading else R.string.local_start_reading))
                            }
                            TextButton(onClick = { if (active) model.pause() else model.resume(authorization) },
                                enabled = !migrating && !state.busy) {
                                Text(stringResource(if (active) R.string.local_pause_download else R.string.novel_download_continue))
                            }
                        }
                        work?.progress?.let { Text(stringResource(R.string.local_download_count, it.completed, it.total)) }
                        if (work?.waiting == true) Text(stringResource(R.string.local_download_waiting))
                        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                items(records, key = { NovelDownloadStore.chapterKey(it.url) }) { record ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = !migrating && (record.bodyAvailable || record.downloaded)) {
                        open(record)
                    }.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(record.name, Modifier.weight(1f), color = if (record.bodyAvailable || record.downloaded)
                            MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(if (record.downloaded) R.string.local_downloaded else if (record.bodyAvailable)
                            R.string.local_text_saved else R.string.local_not_downloaded), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

internal fun localResumeRecord(records: List<DownloadedChapterRecord>, chapterUrl: String?): DownloadedChapterRecord? {
    val readable = records.filter { it.bodyAvailable || it.downloaded }
    return readable.firstOrNull { chapterUrl != null && NovelDownloadStore.chapterKey(it.url) == NovelDownloadStore.chapterKey(chapterUrl) }
        ?: readable.minByOrNull { it.index }
}

private class DownloadedNovelModel(private val url: String) : AppStateViewModel<DownloadedNovelModel.State>(State()) {
    data class State(val manifest: DownloadedNovelManifest? = null, val history: LocalReadingActivity? = null,
        val loading: Boolean = true, val failed: Boolean = false, val busy: Boolean = false)
    private var loadJob: Job? = null
    init { reload() }
    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            combine(PresentationAccess.downloads.changes, PresentationAccess.database.localReadingActivityDao().observeAll()) {
                _, histories -> histories
            }.collectLatest { histories ->
                try {
                    val manifest = PresentationAccess.downloads.manifest(url)
                    val history = histories.firstOrNull { NovelDownloadStore.chapterKey(it.novelUrl) == NovelDownloadStore.chapterKey(url) ||
                        it.novelId.isNotBlank() && it.novelId == novelTitleId("", url) }
                    mutableState.value = State(manifest, history, loading = false, busy = mutableState.value.busy)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { mutableState.value = mutableState.value.copy(loading = false, failed = true) }
            }
        }
    }
    fun pause() { NovelDownloadManager.cancel(com.breakyuna.esjzone.EsjzoneApplication.instance, url) }
    fun resume(authorization: Authorization) {
        if (mutableState.value.busy) return
        val manifest = mutableState.value.manifest ?: return
        mutableState.value = mutableState.value.copy(busy = true, failed = false)
        viewModelScope.launch {
            try {
                val detail = cancellablePageRequest {
                    PresentationAccess.client.getNovelDetail(authorization, FavoriteNovel(manifest.name, url))
                }
                withContext(Dispatchers.IO) {
                    if (!PresentationAccess.downloads.migrating.value) NovelDownloadManager.enqueue(
                        com.breakyuna.esjzone.EsjzoneApplication.instance, authorization, detail)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.value = mutableState.value.copy(failed = true) }
            finally { mutableState.value = mutableState.value.copy(busy = false) }
        }
    }
}

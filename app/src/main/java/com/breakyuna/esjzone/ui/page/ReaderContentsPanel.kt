package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.database.entity.Bookmark
import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSideSheet
import com.breakyuna.esjzone.ui.designsystem.AppSideSheetEdge
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ReaderContentsSheet(
    visible: Boolean,
    chapters: List<Chapter>,
    currentChapter: Chapter,
    novelId: String,
    bookmarks: List<Bookmark>,
    underlines: Map<String, List<ReaderUnderline>>,
    onChapterSelected: (Chapter) -> Unit,
    onUnderlineSelected: (Chapter, ReaderUnderline) -> Unit,
    onDismiss: () -> Unit,
    novelUrl: String = ""
) {
    AppSideSheet(visible = visible, edge = AppSideSheetEdge.START, onDismissRequest = onDismiss) {
        ReaderContentsContent(chapters, currentChapter, novelId, bookmarks, underlines,
            onChapterSelected, onUnderlineSelected, onDismiss, novelUrl)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderContentsContent(
    chapters: List<Chapter>,
    currentChapter: Chapter,
    novelId: String,
    bookmarks: List<Bookmark>,
    underlines: Map<String, List<ReaderUnderline>>,
    onChapterSelected: (Chapter) -> Unit,
    onUnderlineSelected: (Chapter, ReaderUnderline) -> Unit,
    onDismiss: () -> Unit,
    novelUrl: String = ""
) {
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val catalogState = rememberLazyListState()
    val bookmarkState = rememberLazyListState()
    val underlineState = rememberLazyListState()
    val currentKey = chapterIdentity(currentChapter)
    var query by rememberSaveable(novelId) { mutableStateOf("") }
    var locate by remember { mutableIntStateOf(0) }
    val catalogModel = rememberAppViewModel { ReaderCatalogModel(novelUrl) }
    val savedChapters by catalogModel.state.collectAsStateWithLifecycle()
    val filteredChapters by produceState(chapters, chapters, query) {
        value = withContext(Dispatchers.Default) { filterReaderCatalog(chapters, query) }
    }

    val bookBookmarks by produceState<List<Chapter>>(emptyList(), chapters, bookmarks, novelId) {
        value = withContext(Dispatchers.Default) {
            val catalog = chapters.associateBy(::chapterIdentity)
            bookmarks.filter { (novelId.isNotBlank() && it.novelId == novelId) ||
                chapterIdentity(Chapter(it.chapterName, it.chapterUrl, true)) in catalog }
                .map { row ->
                    val chapter = Chapter(row.chapterName, row.chapterUrl, true)
                    catalog[chapterIdentity(chapter)] ?: chapter
                }.distinctBy(::chapterIdentity)
        }
    }
    val bookUnderlines by produceState<List<Pair<Chapter, ReaderUnderline>>>(emptyList(), chapters, currentChapter, underlines) {
        value = withContext(Dispatchers.Default) {
            (chapters + currentChapter).distinctBy(::chapterIdentity).flatMap { chapter ->
                underlines[chapterIdentity(chapter)].orEmpty()
                    .sortedWith(compareBy<ReaderUnderline> { it.blockIndex }.thenBy { it.start })
                    .map { chapter to it }
            }
        }
    }
    LaunchedEffect(filteredChapters, currentKey, locate) {
        val index = filteredChapters.indexOfFirst { chapterIdentity(it) == currentKey }
        if (index >= 0) catalogState.scrollToItem(index)
    }
    val labels = listOf(R.string.reader_contents, R.string.reader_bookmark_action, R.string.reader_underline_tab)
    Column(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top))
            .navigationBarsPadding()
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TabRow(selectedTabIndex = pager.currentPage, modifier = Modifier.weight(1f)) {
                labels.forEachIndexed { index, label ->
                    Tab(selected = pager.currentPage == index,
                        onClick = { scope.launch { pager.animateScrollToPage(index) } },
                        text = { Text(stringResource(label), maxLines = 1) })
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, stringResource(R.string.close))
            }
        }
        if (pager.currentPage == 0) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text(stringResource(R.string.reader_catalog_search)) }, modifier = Modifier.weight(1f))
            IconButton(onClick = { query = ""; locate++ }) {
                Icon(Icons.Filled.MyLocation, stringResource(R.string.reader_catalog_current))
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
            val description = stringResource(labels[page])
            val isEmpty = when (page) {
                0 -> filteredChapters.isEmpty()
                1 -> bookBookmarks.isEmpty()
                else -> bookUnderlines.isEmpty()
            }
            if (isEmpty) {
                Box(Modifier.fillMaxSize().semantics { contentDescription = description }) {
                    Text(stringResource(when (page) {
                        0 -> R.string.reader_contents_empty
                        1 -> R.string.bookmarks_empty
                        else -> R.string.reader_underlines_empty
                    }), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp))
                }
            } else {
                LazyColumn(
                    state = when (page) { 0 -> catalogState; 1 -> bookmarkState; else -> underlineState },
                    modifier = Modifier.fillMaxSize().semantics { contentDescription = description }
                ) {
                    when (page) {
                        0, 1 -> items(if (page == 0) filteredChapters else bookBookmarks, key = ::chapterIdentity) { chapter ->
                            ReaderContentsRow(chapter.name, quote = savedChapters[NovelDownloadStore.chapterKey(chapter.url)]?.let {
                                stringResource(if (it) R.string.local_downloaded else R.string.local_text_saved)
                            }, selected = chapterIdentity(chapter) == currentKey,
                                onClick = { onChapterSelected(chapter) })
                        }
                        else -> items(bookUnderlines, key = { (chapter, mark) ->
                            "${chapterIdentity(chapter)}:${mark.blockIndex}:${mark.signature}:${mark.start}:${mark.end}"
                        }) { (chapter, mark) ->
                            ReaderContentsRow(chapter.name,
                                quote = mark.quote?.takeIf { it.isNotBlank() } ?: stringResource(
                                    R.string.reader_underline_position, mark.blockIndex + 1, mark.start + 1, mark.end),
                                selected = chapterIdentity(chapter) == currentKey,
                                onClick = { onUnderlineSelected(chapter, mark) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderContentsRow(title: String, quote: String? = null, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xxs),
        shape = AppShapes.standard,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent) {
        Column(Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)) {
            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            if (quote != null) Text(quote, maxLines = 3, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun filterReaderCatalog(chapters: List<Chapter>, query: String): List<Chapter> =
    query.trim().let { term -> if (term.isEmpty()) chapters else chapters.filter { it.name.contains(term, ignoreCase = true) } }

private class ReaderCatalogModel(novelUrl: String) : AppStateViewModel<Map<String, Boolean>>(emptyMap()) {
    init {
        if (novelUrl.isNotBlank()) viewModelScope.launch(Dispatchers.IO) {
            PresentationAccess.downloads.changes.collectLatest {
                try {
                    mutableState.value = PresentationAccess.downloads.manifest(novelUrl)?.chapters.orEmpty()
                        .filter { it.bodyAvailable || it.downloaded }
                        .associate { NovelDownloadStore.chapterKey(it.url) to it.downloaded }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { mutableState.value = emptyMap() }
            }
        }
    }
}

package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.features.getCachedNovelChapterList
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
import com.breakyuna.esjzone.domain.reader.ReaderAnchor
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

/** Old rows may have lost a URL fragment, but their saved anchor retains the chapter identity. */
internal fun bookmarkChapterIdentity(bookmark: Bookmark): String =
    chapterIdentity(Chapter(bookmark.chapterName, ReaderAnchor.decode(bookmark.anchor)?.chapterKey ?: bookmark.chapterUrl, true))

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
    onBookmarkSelected: (Chapter, Bookmark) -> Unit,
    onDismiss: () -> Unit,
    novelUrl: String = ""
) {
    AppSideSheet(visible = visible, edge = AppSideSheetEdge.START, onDismissRequest = onDismiss) {
        ReaderContentsContent(chapters, currentChapter, novelId, bookmarks, underlines,
            onChapterSelected, onUnderlineSelected, onDismiss, novelUrl, onBookmarkSelected)
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
    novelUrl: String = "",
    onBookmarkSelected: (Chapter, Bookmark) -> Unit = { chapter, _ -> onChapterSelected(chapter) }
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
    val authorization = LocalAuthorization.current
    val tocModel = rememberAppViewModel { ReaderTocVolumesModel() }
    val tocUrl = novelUrl.ifBlank { novelId.takeIf(String::isNotBlank)?.let { "/detail/$it.html" }.orEmpty() }
    // Reload when the reader's order changes: a detail fetch may have refreshed the local cache.
    LaunchedEffect(tocUrl, chapters.size) { tocModel.load(authorization, tocUrl, chapters.size) }
    val tocSources by tocModel.state.collectAsStateWithLifecycle()
    val descending by PresentationAccess.readerSettings.tocDescending.collectAsStateWithLifecycle()
    val toc by produceState(ReaderToc(listOf(ReaderTocVolume("all", null, chapters))), chapters, tocSources) {
        value = withContext(Dispatchers.Default) { buildReaderToc(chapters, tocSources) }
    }
    val currentVolumeKey = remember(toc, currentKey) { toc.volumeKeyOf(currentKey) }
    var expandedVolumes by remember(novelId) { mutableStateOf(emptySet<String>()) }
    // The current chapter's volume opens by default; other volumes keep the user's choice.
    LaunchedEffect(currentVolumeKey) { currentVolumeKey?.let { expandedVolumes = expandedVolumes + it } }
    val tocSnapshot by produceState(TocRowsSnapshot(toc, descending, expandedVolumes, emptyList()),
        toc, descending, expandedVolumes, query) {
        val input = TocRowsSnapshot(toc, descending, expandedVolumes, emptyList())
        value = input.copy(rows = withContext(Dispatchers.Default) {
            readerTocRows(input.toc, input.descending, input.expanded, query)
        })
    }
    val tocRows = tocSnapshot.rows

    val bookBookmarks by produceState<List<Pair<Chapter, Bookmark>>>(emptyList(), chapters, bookmarks, novelId) {
        value = withContext(Dispatchers.Default) {
            val catalog = chapters.associateBy(::chapterIdentity)
            bookmarks.filter { (novelId.isNotBlank() && it.novelId == novelId) ||
                bookmarkChapterIdentity(it) in catalog }
                .map { row ->
                    val key = bookmarkChapterIdentity(row)
                    val chapter = Chapter(row.chapterName, key, true)
                    (catalog[key] ?: chapter) to row
                }
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
    // Scroll once per open, current-chapter change, order flip or locate tap; plain expand or
    // collapse taps must not make the list jump.
    var pendingLocate by remember { mutableStateOf(true) }
    LaunchedEffect(toc, currentKey, locate, descending) { pendingLocate = true }
    LaunchedEffect(tocSnapshot, pendingLocate) {
        if (!pendingLocate) return@LaunchedEffect
        // Rows are computed off the main thread; skip a snapshot that predates the change.
        val snapshot = tocSnapshot
        if (snapshot.toc !== toc || snapshot.descending != descending) return@LaunchedEffect
        if (currentVolumeKey != null && currentVolumeKey !in snapshot.expanded &&
            currentVolumeKey in expandedVolumes) return@LaunchedEffect
        val index = readerTocIndexOf(tocRows, currentKey, currentVolumeKey)
        if (index >= 0) {
            catalogState.scrollToItem(index)
            pendingLocate = false
        }
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
            IconButton(onClick = {
                scope.launch { PresentationAccess.readerSettings.saveTocDescending(!descending) }
            }) {
                Icon(Icons.Filled.SwapVert, stringResource(
                    if (descending) R.string.reader_toc_sort_ascending else R.string.reader_toc_sort_descending))
            }
            IconButton(onClick = {
                query = ""
                currentVolumeKey?.let { expandedVolumes = expandedVolumes + it }
                locate++
            }) {
                Icon(Icons.Filled.MyLocation, stringResource(R.string.reader_catalog_current))
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
            val description = stringResource(labels[page])
            val isEmpty = when (page) {
                0 -> tocRows.isEmpty()
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
                        0 -> items(tocRows, key = ReaderTocRow::key, contentType = { it is ReaderTocRow.Volume }) { row ->
                            when (row) {
                                is ReaderTocRow.Volume -> ReaderTocVolumeRow(row,
                                    current = row.volume.key == currentVolumeKey,
                                    onToggle = {
                                        expandedVolumes = if (row.expanded) expandedVolumes - row.volume.key
                                        else expandedVolumes + row.volume.key
                                    })
                                is ReaderTocRow.Entry -> ReaderContentsRow(row.chapter.name,
                                    quote = savedChapters[NovelDownloadStore.chapterKey(row.chapter.url)]?.let {
                                        stringResource(if (it) R.string.local_downloaded else R.string.local_text_saved)
                                    }, selected = row.chapterKey == currentKey,
                                    onClick = { onChapterSelected(row.chapter) })
                            }
                        }
                        1 -> items(bookBookmarks, key = { it.second.id }) { (chapter, bookmark) ->
                            ReaderContentsRow(chapter.name, quote = bookmark.excerpt?.takeIf { it.isNotBlank() }
                                ?: savedChapters[NovelDownloadStore.chapterKey(chapter.url)]?.let {
                                    stringResource(if (it) R.string.local_downloaded else R.string.local_text_saved)
                                }, selected = chapterIdentity(chapter) == currentKey,
                                onClick = { onBookmarkSelected(chapter, bookmark) })
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

private data class TocRowsSnapshot(
    val toc: ReaderToc,
    val descending: Boolean,
    val expanded: Set<String>,
    val rows: List<ReaderTocRow>
)

@Composable
private fun ReaderTocVolumeRow(row: ReaderTocRow.Volume, current: Boolean, onToggle: () -> Unit) {
    val title = row.volume.title ?: stringResource(R.string.reader_toc_untitled_volume)
    // A query keeps every matching volume open, so the header cannot collapse it.
    val description = stringResource(if (row.expanded) R.string.chapter_group_collapse else R.string.chapter_group_expand)
    Surface(onClick = onToggle, modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.xs, bottom = AppSpacing.xxs),
        shape = AppShapes.standard,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)) {
        Row(Modifier.padding(start = AppSpacing.md, end = AppSpacing.sm, top = AppSpacing.sm, bottom = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (current) FontWeight.Bold else FontWeight.SemiBold,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.reader_toc_volume_count, row.count),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(if (row.expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, description,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** Volume headings from the locally cached detail page only; a miss never fetches the site. */
private class ReaderTocVolumesModel : AppStateViewModel<List<ReaderTocSourceVolume>>(emptyList()) {
    private var loadedFor: Pair<String, Int>? = null

    fun load(authorization: Authorization, novelUrl: String, chapterCount: Int) {
        val request = novelUrl to chapterCount
        if (novelUrl.isBlank() || loadedFor == request) return
        loadedFor = request
        viewModelScope.launch(Dispatchers.IO) {
            val volumes = try {
                PresentationAccess.client.getCachedNovelChapterList(authorization, novelUrl)
                    ?.let { readerTocSourceVolumes(it.items) }.orEmpty()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { emptyList() }
            if (loadedFor == request) mutableState.value = volumes
        }
    }
}

internal fun filterReaderCatalog(chapters: List<Chapter>, query: String): List<Chapter> =
    query.trim().let { term -> if (term.isEmpty()) chapters else chapters.filter { it.name.contains(term, ignoreCase = true) } }

private class ReaderCatalogModel(novelUrl: String) : AppStateViewModel<Map<String, Boolean>>(emptyMap()) {
    init {
        if (novelUrl.isNotBlank()) viewModelScope.launch(Dispatchers.IO) {
            PresentationAccess.downloads.observeNovelChanges(novelUrl).collectLatest {
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

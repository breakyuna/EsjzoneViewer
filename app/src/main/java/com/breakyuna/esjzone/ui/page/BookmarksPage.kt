package com.breakyuna.esjzone.ui.page

import com.breakyuna.esjzone.ui.designsystem.wideListColumns
import com.breakyuna.esjzone.ui.designsystem.PreserveAdaptiveListAnchor
import com.breakyuna.esjzone.ui.designsystem.adaptiveItems
import com.breakyuna.esjzone.novellibrary.novel.validNovelTitle

import androidx.lifecycle.viewModelScope

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.designsystem.accountContentWidth
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.database.BookmarkCoverStore
import com.breakyuna.esjzone.database.entity.Bookmark as LocalBookmark
import com.breakyuna.esjzone.domain.reader.ReaderAnchor
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.CoverSource
import com.breakyuna.esjzone.novellibrary.novel.CoverState
import com.breakyuna.esjzone.novellibrary.novel.CoverFailure
import com.breakyuna.esjzone.novellibrary.novel.coverFailure
import com.breakyuna.esjzone.ui.component.AppNovelCover
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.navigation.AppNavigator
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.ui.navigation.ChapterStateHolder
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import com.breakyuna.esjzone.ui.product.EmptyState
import com.breakyuna.esjzone.ui.product.ErrorState
import com.breakyuna.esjzone.ui.product.LoadingSkeleton
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Device-only bookmarks; opening one restores its saved paragraph in the reader. */
object BookmarksPage : AppDestination {
    private fun readResolve(): Any = BookmarksPage
    override val key: String = "BookmarksPage"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content(navigator: AppNavigator) {
        val columns = wideListColumns()
        val model = rememberAppViewModel { BookmarksPageModel() }
        val underlineModel = rememberAppViewModel { UnderlinesPageModel() }
        val state by model.state.collectAsStateWithLifecycle()
        val underlineState by underlineModel.state.collectAsStateWithLifecycle()
        var tab by rememberSaveable { mutableStateOf(0) }
        var editing by remember { mutableStateOf(false) }
        var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
        var pendingDelete by remember { mutableStateOf<List<LocalBookmark>>(emptyList()) }
        var pendingUnderlineDelete by remember { mutableStateOf<List<UnderlineListRow>>(emptyList()) }
        var deletingUnderlines by remember { mutableStateOf(false) }
        var showDeleteDialog by remember { mutableStateOf(false) }
        var deleteError by remember { mutableStateOf(false) }
        var deleting by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { model.load() }
        LaunchedEffect(tab) { if (tab == 1) underlineModel.load() }

        val bookmarks = (state as? BookmarksPageModel.State.Result)?.bookmarks.orEmpty()
        val underlines = (underlineState as? UnderlinesPageModel.State.Result)?.rows.orEmpty()
        val bookmarkIds = remember(bookmarks) { bookmarks.mapTo(LinkedHashSet()) { it.id } }
        val underlineIds = remember(underlines) { underlines.mapTo(LinkedHashSet()) { it.id } }
        val visibleIds = if (tab == 0) bookmarkIds else underlineIds
        LaunchedEffect(visibleIds, tab) { selected = selected.intersect(visibleIds) }

        fun requestDelete(bookmarksToDelete: List<LocalBookmark>) {
            pendingDelete = bookmarksToDelete
            deletingUnderlines = false
            deleteError = false
            showDeleteDialog = bookmarksToDelete.isNotEmpty()
        }

        fun requestUnderlineDelete(rows: List<UnderlineListRow>) {
            pendingUnderlineDelete = rows
            deletingUnderlines = true
            deleteError = false
            showDeleteDialog = rows.isNotEmpty()
        }

        fun exitEditing() {
            editing = false
            selected = emptySet()
        }

        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(stringResource(if (tab == 0) R.string.bookmarks else R.string.reader_underlines),
                            style = AppTypography.titleLarge) },
                        navigationIcon = { BackIconButton { if (editing) exitEditing() else navigator.pop() } },
                        actions = {
                            if (editing) {
                                IconButton(
                                    onClick = { selected = if (selected == visibleIds) emptySet() else visibleIds },
                                    enabled = visibleIds.isNotEmpty()
                                ) {
                                    Icon(Icons.Filled.SelectAll, contentDescription = stringResource(
                                        if (tab == 0) R.string.bookmark_select_all else R.string.underline_select_all))
                                }
                                IconButton(
                                    onClick = {
                                        if (tab == 0) requestDelete(bookmarks.filter { it.id in selected })
                                        else requestUnderlineDelete(underlines.filter { it.id in selected })
                                    },
                                    enabled = selected.isNotEmpty()
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(
                                        if (tab == 0) R.string.bookmark_delete_selected else R.string.underline_delete_selected))
                                }
                                IconButton(onClick = ::exitEditing) {
                                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.bookmark_edit_done))
                                }
                            } else {
                                IconButton(onClick = { editing = true }) {
                                    Icon(Icons.Filled.Edit, contentDescription = stringResource(
                                        if (tab == 0) R.string.bookmark_edit else R.string.underline_edit))
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                    TabRow(selectedTabIndex = tab) {
                        listOf(R.string.reader_bookmark_action, R.string.reader_underline_tab).forEachIndexed { index, label ->
                            Tab(selected = tab == index, onClick = { tab = index; exitEditing() },
                                text = { Text(stringResource(label)) })
                        }
                    }
                }
            }
        ) { padding ->
            if (tab == 0) when (val current = state) {
                BookmarksPageModel.State.Loading -> LoadingSkeleton(Modifier.fillMaxWidth().padding(padding), stringResource(R.string.bookmarks))
                is BookmarksPageModel.State.Error -> ErrorState(
                    title = stringResource(R.string.load_client_error),
                    message = stringResource(R.string.history_local_load_failed),
                    onRetry = model::retry,
                    modifier = Modifier.fillMaxSize().accountContentWidth().padding(padding)
                )
                is BookmarksPageModel.State.Result -> if (current.bookmarks.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.bookmarks_empty),
                        message = stringResource(R.string.bookmarks_description),
                        modifier = Modifier.fillMaxSize().accountContentWidth().padding(padding)
                    )
                } else {
                    val listState = rememberLazyListState()
                    PreserveAdaptiveListAnchor(listState, current.bookmarks, columns, key = { "bookmark:${it.id}" })
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().accountContentWidth().padding(padding),
                        contentPadding = PaddingValues(AppSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        adaptiveItems(
                            current.bookmarks,
                            columns = columns,
                            key = { "bookmark:${it.id}" },
                            contentType = { "bookmark" }
                        ) { bookmark ->
                            BookmarkCard(
                                bookmark = bookmark,
                                cover = model.coverFor(bookmark),
                                onOpen = {
                                    if (editing) {
                                        selected = if (bookmark.id in selected) selected - bookmark.id else selected + bookmark.id
                                    } else {
                                        val chapter = Chapter(bookmark.chapterName, bookmarkChapterIdentity(bookmark), false)
                                        val cleanId = BookmarkCoverStore.cleanNovelId(bookmark.novelId, bookmark.chapterUrl)
                                        navigator.pushIfNotCurrent(
                                            ChapterPage(
                                                novelId = cleanId,
                                                chapter = chapter,
                                                history = ChapterStateHolder(chapter),
                                                novelName = validNovelTitle(bookmark.novelName, bookmark.novelId, bookmark.chapterUrl, bookmark.chapterName),
                                                novelUrl = "/detail/$cleanId.html",
                                                novelCoverUrl = model.coverFor(bookmark).url,
                                                bookmarkAnchor = ReaderAnchor.decode(bookmark.anchor)
                                            )
                                        )
                                    }
                                },
                                onDelete = { requestDelete(listOf(bookmark)) },
                                editing = editing,
                                selected = bookmark.id in selected
                            )
                        }
                    }
                }
            } else when (val current = underlineState) {
                UnderlinesPageModel.State.Loading -> LoadingSkeleton(Modifier.fillMaxWidth().padding(padding),
                    stringResource(R.string.reader_underlines))
                UnderlinesPageModel.State.Error -> ErrorState(
                    title = stringResource(R.string.load_client_error),
                    message = stringResource(R.string.underline_load_failed),
                    onRetry = underlineModel::retry,
                    modifier = Modifier.fillMaxSize().accountContentWidth().padding(padding)
                )
                is UnderlinesPageModel.State.Result -> if (current.rows.isEmpty()) {
                    EmptyState(title = stringResource(R.string.reader_underlines_empty),
                        modifier = Modifier.fillMaxSize().accountContentWidth().padding(padding))
                } else {
                    val listState = rememberLazyListState()
                    PreserveAdaptiveListAnchor(listState, current.rows, columns, key = { "underline:${it.id}" })
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().accountContentWidth().padding(padding),
                        contentPadding = PaddingValues(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        adaptiveItems(current.rows, columns = columns, key = { "underline:${it.id}" },
                            contentType = { "underline" }) { row ->
                            UnderlineCard(row, editing, row.id in selected, onOpen = {
                                if (editing) selected = if (row.id in selected) selected - row.id else selected + row.id
                                else {
                                    val chapter = Chapter(row.chapterName, row.chapterKey, false)
                                    navigator.pushIfNotCurrent(ChapterPage(
                                        novelId = row.novelId, chapter = chapter, history = ChapterStateHolder(chapter),
                                        novelName = row.novelName, novelUrl = row.novelUrl, novelCoverUrl = row.coverUrl,
                                        initialUnderline = row.mark
                                    ))
                                }
                            }, onDelete = { requestUnderlineDelete(listOf(row)) })
                        }
                    }
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(stringResource(
                    if (deletingUnderlines) R.string.underline_delete_title else R.string.bookmark_delete_title,
                    if (deletingUnderlines) pendingUnderlineDelete.size else pendingDelete.size)) },
                text = {
                    Column {
                        Text(stringResource(R.string.bookmark_delete_confirm))
                        if (deleteError) Text(
                            stringResource(if (deletingUnderlines) R.string.underline_delete_failed else R.string.bookmark_delete_failed),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                confirmButton = {
                    TextButton(enabled = !deleting, onClick = {
                        deleting = true
                        deleteError = false
                        val complete: (Boolean) -> Unit = { succeeded ->
                            deleting = false
                            if (succeeded) {
                                showDeleteDialog = false
                                exitEditing()
                            } else deleteError = true
                        }
                        if (deletingUnderlines) underlineModel.delete(pendingUnderlineDelete, complete)
                        else model.delete(pendingDelete, complete)
                    }) {
                        Text(stringResource(R.string.bookmark_delete_action), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(android.R.string.cancel)) } }
            )
        }
    }
}

@Composable
private fun BookmarkCard(
    bookmark: LocalBookmark,
    cover: CoverSource,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    editing: Boolean,
    selected: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.standard)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.surface,
                AppShapes.standard
            )
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, AppShapes.standard) else Modifier)
            .clickable(onClick = onOpen)
            .semantics { role = Role.Button }
            .padding(AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        AppNovelCover(
            coverUrl = cover.url,
            coverState = cover.state,
            title = validNovelTitle(bookmark.novelName, bookmark.novelId, bookmark.chapterUrl, bookmark.chapterName).ifBlank { stringResource(R.string.download_unknown_novel) },
            modifier = Modifier.size(width = 56.dp, height = 76.dp)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(validNovelTitle(bookmark.novelName, bookmark.novelId, bookmark.chapterUrl, bookmark.chapterName).ifBlank { stringResource(R.string.download_unknown_novel) }, style = AppTypography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val excerpt = bookmark.excerpt?.takeIf { it.isNotBlank() }
            Text(bookmark.chapterName, style = AppTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (excerpt == null) 2 else 1, overflow = TextOverflow.Ellipsis)
            if (excerpt != null) Text(excerpt, style = AppTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (editing) {
            Icon(
                if (selected) Icons.Filled.Check else Icons.Filled.BookmarkBorder,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.bookmark_remove))
            }
        }
    }
}

private class BookmarksPageModel : AppStateViewModel<BookmarksPageModel.State>(State.Loading) {
    sealed class State {
        data object Loading : State()
        data class Result(val bookmarks: List<LocalBookmark>) : State()
        data object Error : State()
    }

    private var loadStarted = false
    private val resolvedCovers = mutableStateMapOf<String, CoverSource>()

    fun load() {
        if (loadStarted) return
        loadStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                coroutineScope {
                    val coverRows = MutableStateFlow<List<LocalBookmark>?>(null)
                    launch {
                        // Cancellation waits for an in-progress synchronous copy to leave before
                        // starting the next batch, independently of the database observer.
                        coverRows.filterNotNull().collectLatest { ensureCovers(it) }
                    }
                    PresentationAccess.database.bookmarkDao().observeAll().collect { bookmarks ->
                        mutableState.value = State.Result(bookmarks)
                        coverRows.value = bookmarks
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadStarted = false
                mutableState.value = State.Error
                AppLogger.e("BookmarksPageModel", "Failed to load local bookmarks", e)
            }
        }
    }

    private suspend fun ensureCovers(bookmarks: List<LocalBookmark>) {
        for (bookmark in bookmarks.distinctBy {
            BookmarkCoverStore.cleanNovelId(it.novelId, it.chapterUrl)
        }) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val cleanId = BookmarkCoverStore.cleanNovelId(bookmark.novelId, bookmark.chapterUrl)
            val cover = try {
                val uri = BookmarkCoverStore.getCoverUri(bookmark.novelId, bookmark.chapterUrl)
                    ?: BookmarkCoverStore.saveCoverFromCacheOrDownload(
                        novelId = bookmark.novelId,
                        chapterUrl = bookmark.chapterUrl
                    )
                // A missing local copy says nothing about the site's actual cover.
                CoverSource.fromUrl(uri, CoverState.Failed(CoverFailure.UNAVAILABLE))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                CoverSource(state = CoverState.Failed(error.coverFailure()))
            }
            withContext(Dispatchers.Main) {
                resolvedCovers[cleanId] = cover
            }
        }
    }

    fun coverFor(bookmark: LocalBookmark): CoverSource {
        val cleanId = BookmarkCoverStore.cleanNovelId(bookmark.novelId, bookmark.chapterUrl)
        return resolvedCovers[cleanId] ?: CoverSource(state = CoverState.Resolving)
    }

    fun retry() {
        loadStarted = false
        mutableState.value = State.Loading
        load()
    }

    fun delete(bookmarks: List<LocalBookmark>, onComplete: (Boolean) -> Unit) {
        if (bookmarks.isEmpty()) {
            onComplete(true)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                PresentationAccess.database.bookmarkDao().deleteAll(bookmarks)
                bookmarks.forEach { bookmark ->
                    runCatching { BookmarkCoverStore.cleanupIfUnused(bookmark.novelId, bookmark.chapterUrl) }
                        .onFailure { AppLogger.w("BookmarksPageModel", "Failed to clean up bookmark cover", it) }
                }
                withContext(Dispatchers.Main) { onComplete(true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("BookmarksPageModel", "Failed to delete local bookmark", e)
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }
}

package com.breakyuna.esjzone.ui.page

import com.breakyuna.esjzone.network.wenku8.novelDetailUrlForId
import androidx.lifecycle.viewModelScope

import androidx.compose.ui.draw.clip
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
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.ui.component.AppNovelCover
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.ui.navigation.ChapterStateHolder
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import com.breakyuna.esjzone.ui.product.EmptyState
import com.breakyuna.esjzone.ui.product.ErrorState
import com.breakyuna.esjzone.ui.product.LoadingSkeleton
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Device-only chapter bookmarks; opening one delegates to the reader shell. */
object BookmarksPage : AppDestination {
    private fun readResolve(): Any = BookmarksPage
    override val key: String = "BookmarksPage"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val model = rememberAppViewModel { BookmarksPageModel() }
        val state by model.state.collectAsState()
        var editing by remember { mutableStateOf(false) }
        var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
        var pendingDelete by remember { mutableStateOf<List<LocalBookmark>>(emptyList()) }
        var showDeleteDialog by remember { mutableStateOf(false) }
        var deleteError by remember { mutableStateOf(false) }
        var deleting by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { model.load() }

        val bookmarks = (state as? BookmarksPageModel.State.Result)?.bookmarks.orEmpty()
        val bookmarkUrls = remember(bookmarks) { bookmarks.mapTo(LinkedHashSet()) { it.chapterUrl } }
        LaunchedEffect(bookmarkUrls) { selected = selected.intersect(bookmarkUrls) }

        fun requestDelete(bookmarksToDelete: List<LocalBookmark>) {
            pendingDelete = bookmarksToDelete
            deleteError = false
            showDeleteDialog = bookmarksToDelete.isNotEmpty()
        }

        fun exitEditing() {
            editing = false
            selected = emptySet()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.bookmarks), style = AppTypography.titleLarge) },
                    navigationIcon = { BackIconButton { if (editing) exitEditing() else navigator?.pop() } },
                    actions = {
                        if (editing) {
                            IconButton(
                                onClick = { selected = if (selected == bookmarkUrls) emptySet() else bookmarkUrls },
                                enabled = bookmarkUrls.isNotEmpty()
                            ) {
                                Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.bookmark_select_all))
                            }
                            IconButton(
                                onClick = { requestDelete(bookmarks.filter { it.chapterUrl in selected }) },
                                enabled = selected.isNotEmpty()
                            ) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.bookmark_delete_selected))
                            }
                            IconButton(onClick = ::exitEditing) {
                                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.bookmark_edit_done))
                            }
                        } else {
                            IconButton(onClick = { editing = true }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.bookmark_edit))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { padding ->
            when (val current = state) {
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().accountContentWidth().padding(padding),
                        contentPadding = PaddingValues(AppSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        items(
                            current.bookmarks,
                            key = { "bookmark:${it.chapterUrl}" },
                            contentType = { "bookmark" }
                        ) { bookmark ->
                            BookmarkCard(
                                bookmark = bookmark,
                                coverUrl = model.coverUrlFor(bookmark),
                                onOpen = {
                                    if (editing) {
                                        selected = if (bookmark.chapterUrl in selected) selected - bookmark.chapterUrl else selected + bookmark.chapterUrl
                                    } else {
                                        val chapter = Chapter(bookmark.chapterName, bookmark.chapterUrl, false)
                                        val cleanId = BookmarkCoverStore.cleanNovelId(bookmark.novelId, bookmark.chapterUrl)
                                        navigator?.pushIfNotCurrent(
                                            ChapterPage(
                                                novelId = cleanId,
                                                chapter = chapter,
                                                history = ChapterStateHolder(chapter),
                                                novelName = bookmark.novelName,
                                                novelUrl = novelDetailUrlForId(cleanId),
                                                novelCoverUrl = model.coverUrlFor(bookmark)
                                            )
                                        )
                                    }
                                },
                                onDelete = { requestDelete(listOf(bookmark)) },
                                editing = editing,
                                selected = bookmark.chapterUrl in selected
                            )
                        }
                    }
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(stringResource(R.string.bookmark_delete_title, pendingDelete.size)) },
                text = {
                    Column {
                        Text(stringResource(R.string.bookmark_delete_confirm))
                        if (deleteError) Text(
                            stringResource(R.string.bookmark_delete_failed),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                confirmButton = {
                    TextButton(enabled = !deleting, onClick = {
                        deleting = true
                        deleteError = false
                        model.delete(pendingDelete) { succeeded ->
                            deleting = false
                            if (succeeded) {
                                showDeleteDialog = false
                                exitEditing()
                            } else deleteError = true
                        }
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
    coverUrl: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    editing: Boolean,
    selected: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.standard)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, AppShapes.standard) else Modifier)
            .clickable(onClick = onOpen)
            .semantics { role = Role.Button }
            .padding(vertical = AppSpacing.sm, horizontal = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        AppNovelCover(
            coverUrl = coverUrl,
            title = bookmark.novelName.ifBlank { bookmark.novelId },
            modifier = Modifier.size(width = 56.dp, height = 76.dp)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(bookmark.novelName.ifBlank { bookmark.novelId }, style = AppTypography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(bookmark.chapterName, style = AppTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    val resolvedCovers = mutableStateMapOf<String, String>()

    fun load() {
        if (loadStarted) return
        loadStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                PresentationAccess.database.bookmarkDao().observeAll().collect { bookmarks ->
                    mutableState.value = State.Result(bookmarks)
                    ensureCovers(bookmarks)
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
        for (bookmark in bookmarks) {
            val cleanId = BookmarkCoverStore.cleanNovelId(bookmark.novelId, bookmark.chapterUrl)
            val uri = BookmarkCoverStore.getCoverUri(bookmark.novelId, bookmark.chapterUrl)
            if (uri != null) {
                withContext(Dispatchers.Main) {
                    resolvedCovers[cleanId] = uri
                }
            } else {
                // If the cover file wasn't created yet, copy from existing Coil cache or download store (0 network)
                val copied = BookmarkCoverStore.saveCoverFromCacheOrDownload(
                    novelId = bookmark.novelId,
                    chapterUrl = bookmark.chapterUrl
                )
                if (copied != null) {
                    withContext(Dispatchers.Main) {
                        resolvedCovers[cleanId] = copied
                    }
                }
            }
        }
    }

    fun coverUrlFor(bookmark: LocalBookmark): String {
        val cleanId = BookmarkCoverStore.cleanNovelId(bookmark.novelId, bookmark.chapterUrl)
        return resolvedCovers[cleanId].orEmpty()
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

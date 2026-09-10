package com.breakyuna.esjzone.ui.page

import androidx.lifecycle.viewModelScope

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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.database.entity.Bookmark as LocalBookmark
import com.breakyuna.esjzone.novellibrary.novel.Chapter
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
        LaunchedEffect(Unit) { model.load() }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.bookmarks), style = AppTypography.titleLarge) },
                    navigationIcon = { BackIconButton { navigator?.pop() } }
                )
            }
        ) { padding ->
            when (val current = state) {
                BookmarksPageModel.State.Loading -> LoadingSkeleton(Modifier.fillMaxWidth().padding(padding), stringResource(R.string.bookmarks))
                is BookmarksPageModel.State.Error -> ErrorState(
                    title = stringResource(R.string.load_client_error),
                    message = stringResource(R.string.history_local_load_failed),
                    onRetry = model::retry,
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
                is BookmarksPageModel.State.Result -> if (current.bookmarks.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.bookmarks_empty),
                        message = stringResource(R.string.bookmarks_description),
                        modifier = Modifier.fillMaxSize().padding(padding)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(AppSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                    ) {
                        item(key = "bookmark-header") {
                            Text(
                                stringResource(R.string.bookmarks_description),
                                style = AppTypography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = AppSpacing.sm)
                            )
                        }
                        items(
                            current.bookmarks,
                            key = { "bookmark:${it.chapterUrl}" },
                            contentType = { "bookmark" }
                        ) { bookmark ->
                            BookmarkCard(
                                bookmark = bookmark,
                                onOpen = {
                                    val chapter = Chapter(bookmark.chapterName, bookmark.chapterUrl, false)
                                    navigator?.pushIfNotCurrent(
                                        ChapterPage(
                                            novelId = bookmark.novelId,
                                            chapter = chapter,
                                            history = ChapterStateHolder(chapter),
                                            novelName = bookmark.novelName
                                        )
                                    )
                                },
                                onDelete = { model.delete(bookmark) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkCard(bookmark: LocalBookmark, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .semantics { role = Role.Button }
            .padding(vertical = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        Icon(Icons.Filled.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(bookmark.novelName.ifBlank { bookmark.novelId }, style = AppTypography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(bookmark.chapterName, style = AppTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.bookmark_remove))
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

    fun load() {
        if (loadStarted) return
        loadStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                PresentationAccess.database.bookmarkDao().observeAll().collect { mutableState.value = State.Result(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadStarted = false
                mutableState.value = State.Error
                AppLogger.e("BookmarksPageModel", "Failed to load local bookmarks", e)
            }
        }
    }

    fun retry() {
        loadStarted = false
        mutableState.value = State.Loading
        load()
    }

    fun delete(bookmark: LocalBookmark) {
        val current = mutableState.value as? State.Result ?: return
        mutableState.value = State.Result(current.bookmarks.filterNot { it.chapterUrl == bookmark.chapterUrl })
        viewModelScope.launch(Dispatchers.IO) {
            try {
                PresentationAccess.database.bookmarkDao().delete(bookmark)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("BookmarksPageModel", "Failed to delete local bookmark", e)
            }
        }
    }
}

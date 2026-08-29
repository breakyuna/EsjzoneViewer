package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.breakyuna.esjzone.MainActivity
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.database.entity.Bookmark as LocalBookmark
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.ui.component.AppBar
import com.breakyuna.esjzone.ui.component.Loading
import com.breakyuna.esjzone.ui.navigation.ChapterStateHolder
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent
import com.breakyuna.esjzone.util.AppLogger

object BookmarksPage : Screen {

    private fun readResolve(): Any = BookmarksPage

    override val key: ScreenKey = "BookmarksPage"

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val model = rememberScreenModel { BookmarksPageModel() }
        val state by model.state.collectAsState()

        Column(modifier = Modifier.fillMaxSize()) {
            AppBar(
                title = stringResource(id = R.string.bookmarks),
                onBack = { navigator?.pop() }
            )

            when (val current = state) {
                BookmarksPageModel.State.Loading -> Loading()
                is BookmarksPageModel.State.Result -> {
                    if (current.bookmarks.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bookmark,
                                contentDescription = null,
                                modifier = Modifier.size(42.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                            )
                            Text(
                                text = stringResource(id = R.string.bookmarks_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(
                                horizontal = 12.dp,
                                vertical = 12.dp
                            )
                        ) {
                            items(
                                items = current.bookmarks,
                                key = { it.chapterUrl }
                            ) { bookmark ->
                                BookmarkRow(
                                    bookmark = bookmark,
                                    onOpen = {
                                        val chapter = Chapter(
                                            bookmark.chapterName,
                                            bookmark.chapterUrl,
                                            false
                                        )
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

        LaunchedEffect(Unit) {
            model.load()
        }
    }
}

@Composable
private fun BookmarkRow(
    bookmark: LocalBookmark,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Bookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = bookmark.novelName.ifBlank { bookmark.novelId },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = bookmark.chapterName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(id = R.string.bookmark_remove)
                )
            }
        }
    }
}

private class BookmarksPageModel : StateScreenModel<BookmarksPageModel.State>(State.Loading) {

    sealed class State {
        data object Loading : State()
    data class Result(val bookmarks: List<LocalBookmark>) : State()
    }

    private var loadStarted = false

    fun load() {
        if (loadStarted) return
        loadStarted = true
        screenModelScope.launch(Dispatchers.IO) {
            try {
                MainActivity.database.bookmarkDao().observeAll().collect { bookmarks ->
                    mutableState.value = State.Result(bookmarks)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("BookmarksPageModel", "Failed to load local bookmarks", e)
                mutableState.value = State.Result(emptyList())
            }
        }
    }

    fun delete(bookmark: LocalBookmark) {
        val current = mutableState.value as? State.Result ?: return
        mutableState.value = State.Result(
            current.bookmarks.filterNot { it.chapterUrl == bookmark.chapterUrl }
        )
        screenModelScope.launch(Dispatchers.IO) {
            try {
                MainActivity.database.bookmarkDao().delete(bookmark)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("BookmarksPageModel", "Failed to delete local bookmark", e)
            }
        }
    }
}

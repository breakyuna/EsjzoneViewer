package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.breakyuna.esjzone.GlobalSettings
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.database.BookshelfSyncResult
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovelImpl
import com.breakyuna.esjzone.novellibrary.novel.FavoriteNovel
import com.breakyuna.esjzone.ui.component.AppBar
import com.breakyuna.esjzone.ui.component.Novel
import com.breakyuna.esjzone.ui.navigation.BooleanStateHolder
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent

/** The shelf is rendered entirely from the local Room flow. */
object FavoritePage : Screen {
    private fun readResolve(): Any = FavoritePage

    @Composable
    override fun Content() = Content(showBack = true)

    @Composable
    fun Content(showBack: Boolean) {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val model = rememberScreenModel { FavoritePageModel(authorization) }
        val entries by model.entries.collectAsState(initial = emptyList())
        val syncState by model.state.collectAsState()
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val listState = rememberLazyListState()
        var refreshing by rememberSaveable { mutableStateOf(false) }
        val adult by remember { GlobalSettings.adult }
        val syncDoneText = stringResource(R.string.bookshelf_sync_done)
        val syncAddedPattern = stringResource(R.string.bookshelf_sync_added)
        val syncFailedText = stringResource(R.string.bookshelf_sync_failed)

        fun refresh() {
            if (!refreshing) {
                refreshing = true
                model.sync()
            }
        }

        LaunchedEffect(syncState) {
            when (val result = syncState) {
                is FavoritePageModel.State.Completed -> {
                    refreshing = false
                    snackbar.showSnackbar(
                        if (result.result.added > 0) {
                            syncAddedPattern.format(result.result.added)
                        } else {
                            syncDoneText
                        }
                    )
                }
                FavoritePageModel.State.Failed -> {
                    refreshing = false
                    snackbar.showSnackbar(syncFailedText)
                }
                else -> Unit
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (showBack) {
                    AppBar(title = stringResource(R.string.bookshelf), onBack = { navigator?.pop() })
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.bookshelf),
                                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = stringResource(R.string.bookshelf_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = ::refresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.sync_bookshelf))
                        }
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().bookshelfPullToRefresh(
                        listState = listState,
                        enabled = !refreshing,
                        onRefresh = ::refresh
                    )
                ) {
                    if (entries.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(stringResource(R.string.bookshelf_empty))
                                Text(
                                    stringResource(R.string.bookshelf_empty_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                    items(entries, key = { it.bookKey }) { entry ->
                        if (adult || !entry.isAdultHint()) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Novel(
                                    covered = entry.asCoveredNovel(),
                                    onClick = {
                                        navigator?.pushIfNotCurrent(
                                            NovelPage(
                                                FavoriteNovel(entry.title, entry.url),
                                                favorite = BooleanStateHolder(true)
                                            )
                                        )
                                    }
                                )
                                IconButton(
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            BookshelfRepository.setFavorite(
                                                authorization,
                                                FavoriteNovel(entry.title, entry.url),
                                                false
                                            )
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 8.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_bookshelf))
                                }
                            }
                        }
                    }
                }
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.TopCenter).padding(8.dp))
                }
            }
        }
    }
}

private fun BookshelfEntry.asCoveredNovel() = CoveredNovelImpl(
    coverUrl = coverUrl,
    name = title,
    url = url,
    views = 0,
    likes = 0,
    isAdult = isAdultHint()
)

/** Remote favorite rows do not expose adult metadata; only explicit local hints are hidden. */
private fun BookshelfEntry.isAdultHint(): Boolean = isAdult

private fun Modifier.bookshelfPullToRefresh(
    listState: androidx.compose.foundation.lazy.LazyListState,
    enabled: Boolean,
    onRefresh: () -> Unit
): Modifier = pointerInput(enabled) {
    awaitPointerEventScope {
        var distance = 0f
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: continue
            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                // A pull must start at the absolute top; never carry distance
                // from a gesture that began inside the first item.
                distance = 0f
            }
            if (!change.pressed) {
                if (
                    enabled &&
                    listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0 &&
                    distance > 72f
                ) onRefresh()
                distance = 0f
            } else if (listState.firstVisibleItemIndex == 0 && change.positionChange().y > 0f) {
                distance += change.positionChange().y
            }
        }
    }
}

class FavoritePageModel(private val authorization: Authorization) :
    StateScreenModel<FavoritePageModel.State>(State.Idle) {
    val entries = BookshelfRepository.observe(authorization)

    sealed class State {
        data object Idle : State()
        data object Syncing : State()
        data class Completed(val result: BookshelfSyncResult) : State()
        data object Failed : State()
    }

    fun sync() {
        screenModelScope.launch(Dispatchers.IO) {
            mutableState.value = State.Syncing
            try {
                val result = BookshelfRepository.sync(authorization)
                mutableState.value = if (result.success) State.Completed(result) else State.Failed
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.value = State.Failed
            }
        }
    }
}

package com.breakyuna.esjzone.ui.tab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.breakyuna.esjzone.MainActivity
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.database.entity.SearchHistory
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.ui.component.QuietEmptyState
import com.breakyuna.esjzone.ui.component.QuietSearchHeader
import com.breakyuna.esjzone.ui.page.SearchPageModel
import com.breakyuna.esjzone.ui.page.searchResultItems
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.theme.QuietEditorial
import com.breakyuna.esjzone.util.currentDateString
import com.breakyuna.esjzone.util.formattedDate

object SearchTab : Tab {

    private fun readResolve(): Any = SearchTab

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 5u,
            title = stringResource(R.string.screen_main_tab_search),
            icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(image = Icons.Filled.Search)
        )

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val scope = rememberCoroutineScope()
        val searchModel = rememberScreenModel { SearchPageModel(authorization) }
        val searchState by searchModel.state.collectAsState()

        var loadingHistory by remember { mutableStateOf(true) }
        val histories = remember { mutableStateListOf<SearchHistory>() }
        var keyword by rememberSaveable { mutableStateOf("") }
        var activeSearchKeyword by rememberSaveable { mutableStateOf<String?>(null) }

        fun performSearch(query: String) {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return
            activeSearchKeyword = trimmed
            scope.launch(Dispatchers.IO) {
                try {
                    val dao = MainActivity.database.searchHistoryDao()
                    val history = if (dao.exists(trimmed)) {
                        dao.findByKeyword(trimmed)
                    } else {
                        SearchHistory(keyword = trimmed, time = currentDateString())
                    }
                    history.time = currentDateString()
                    dao.insertAll(history)
                    val updated = dao.getAll()
                    withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                        histories.clear()
                        histories.addAll(updated)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    com.breakyuna.esjzone.util.AppLogger.e(
                        "SearchTab",
                        "Failed to persist search history",
                        e
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "search-header") {
                QuietSearchHeader(
                    value = keyword,
                    onValueChange = { keyword = it },
                    onSearch = { performSearch(keyword) },
                    onClear = { keyword = "" },
                    onBack = { navigator?.pop() },
                    modifier = Modifier.widthInContent()
                )
            }

            item(key = "search-history") {
                Column(
                    modifier = Modifier
                        .widthInContent()
                        .padding(horizontal = QuietEditorial.pagePadding, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.search_local_history),
                            style = QuietEditorial.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                        )
                        if (histories.isNotEmpty()) {
                                TextButton(
                                onClick = {
                                    val items = histories.toList()
                                    histories.clear()
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            items.forEach { MainActivity.database.searchHistoryDao().delete(it) }
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            com.breakyuna.esjzone.util.AppLogger.e(
                                                "SearchTab",
                                                "Failed to clear search history",
                                                e
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = stringResource(R.string.search_clear_history),
                                    style = QuietEditorial.label,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }

                    when {
                        loadingHistory -> {
                            com.breakyuna.esjzone.ui.component.QuietLoadingState(
                                modifier = Modifier.padding(horizontal = 0.dp)
                            )
                        }
                        histories.isEmpty() -> {
                            QuietEmptyState(
                                title = stringResource(R.string.search_no_history),
                                message = stringResource(R.string.search_history_privacy),
                                icon = Icons.Filled.Search,
                                modifier = Modifier.padding(horizontal = 0.dp)
                            )
                        }
                        else -> {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                histories
                                    .toList()
                                    .sortedByDescending { it.time.formattedDate() }
                                    .forEach { history ->
                                        SearchHistoryPill(
                                            history = history,
                                            onSelect = {
                                                keyword = history.keyword
                                                performSearch(history.keyword)
                                            },
                                            onDelete = {
                                                histories.remove(history)
                                                scope.launch(Dispatchers.IO) {
                                                    try {
                                                        MainActivity.database.searchHistoryDao().delete(history)
                                                    } catch (e: CancellationException) {
                                                        throw e
                                                    } catch (e: Exception) {
                                                        com.breakyuna.esjzone.util.AppLogger.e(
                                                            "SearchTab",
                                                            "Failed to delete search history entry",
                                                            e
                                                        )
                                                    }
                                                }
                                            }
                                        )
                                    }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.search_history_privacy),
                            style = QuietEditorial.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            activeSearchKeyword?.let { currentKeyword ->
                searchResultItems(
                    model = searchModel,
                    state = searchState,
                    onRetry = { searchModel.search(currentKeyword) },
                    keyword = currentKeyword
                )
            }
            item(key = "search-bottom-space") {
                Spacer(Modifier.height(24.dp))
            }
        }

        LaunchedEffect(Unit) {
            try {
                val dbHistories = withContext(Dispatchers.IO) {
                    MainActivity.database.searchHistoryDao().getAll()
                }
                histories.clear()
                histories.addAll(dbHistories)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.breakyuna.esjzone.util.AppLogger.e(
                    "SearchTab",
                    "Failed to load search history",
                    e
                )
            } finally {
                loadingHistory = false
            }
        }

        LaunchedEffect(activeSearchKeyword) {
            activeSearchKeyword?.let { searchModel.search(it) }
        }
    }
}

@Composable
private fun SearchHistoryPill(
    history: SearchHistory,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = history.keyword,
                style = QuietEditorial.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onSelect)
                    .padding(vertical = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Close,
                    contentDescription = stringResource(R.string.remove),
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun Modifier.widthInContent(): Modifier = this
    .fillMaxWidth()
    .widthIn(max = QuietEditorial.contentMaxWidth)

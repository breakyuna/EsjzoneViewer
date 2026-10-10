@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.breakyuna.esjzone.ui.tab

import com.breakyuna.esjzone.ui.discovery.*
import com.breakyuna.esjzone.ui.page.WenkuFeed
import com.breakyuna.esjzone.network.wenku8.Wenku8SearchType
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Row

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.database.entity.SearchHistory
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import androidx.compose.material3.Scaffold
import com.breakyuna.esjzone.ui.discovery.DiscoveryEmptyState
import com.breakyuna.esjzone.ui.discovery.DiscoveryLoadingState
import com.breakyuna.esjzone.ui.discovery.DiscoverySearchTopBar
import com.breakyuna.esjzone.ui.navigation.AppTab
import com.breakyuna.esjzone.ui.navigation.AppTabOptions
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import com.breakyuna.esjzone.ui.page.DiscoverySearchResults
import com.breakyuna.esjzone.ui.page.SearchPageModel
import com.breakyuna.esjzone.util.formattedDate

object SearchTab : AppTab {
    override val key: String = "SearchTab"
    private fun readResolve(): Any = SearchTab

    override val options: AppTabOptions
        @Composable
        get() = AppTabOptions(
            index = 5,
            title = stringResource(R.string.screen_main_tab_search),
            icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(image = Icons.Filled.Search)
        )

    @Composable
    override fun Content() = SearchContent()

    @Composable
    internal fun SearchContent(
        initialSource: LibrarySource = LocalDiscoverySource.current.value,
        initialKeyword: String = "",
        initialType: Wenku8SearchType = Wenku8SearchType.TITLE
    ) {
        val navigator = LocalBaseNavigator.current
        val discoverySource = LocalDiscoverySource.current
        var source by rememberSaveable { mutableStateOf(initialSource) }
        val holder = rememberSaveableStateHolder()
        val authorization = LocalAuthorization.current
        val searchModel = rememberAppViewModel { SearchPageModel(authorization) }
        val searchState by searchModel.state.collectAsStateWithLifecycle()
        val historyModel = rememberAppViewModel { SearchHistoryModel() }
        val historyState by historyModel.state.collectAsStateWithLifecycle()
        var query by rememberSaveable { mutableStateOf(initialKeyword) }
        var activeKeyword by rememberSaveable { mutableStateOf(initialKeyword.takeIf { it.isNotBlank() }) }
        var category by rememberSaveable { mutableIntStateOf(0) }
        var sort by rememberSaveable { mutableIntStateOf(1) }
        var searchType by rememberSaveable { mutableStateOf(initialType) }
        var refreshRequest by rememberSaveable { mutableIntStateOf(0) }

        fun submit(value: String) {
            value.trim().takeIf { it.isNotBlank() }?.let {
                query = it
                if (activeKeyword == it) {
                    if (source == LibrarySource.ESJZONE) searchModel.search(it, category, sort, forceRefresh = true)
                    else refreshRequest++
                } else activeKeyword = it
                historyModel.save(it)
            }
        }

        Scaffold(
            topBar = {
                Column {
                    DiscoverySearchTopBar(query, { query = it }, { submit(query) }, {
                        query = ""; activeKeyword = null
                    }, { navigator?.pop() })
                    SourceSelector(source, { selected -> selected?.let {
                        source = it; discoverySource.value = it
                    } })
                    if (source == LibrarySource.WENKU8) Row(
                        Modifier.padding(horizontal = AppSpacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        Wenku8SearchType.entries.forEach { type ->
                            FilterChip(selected = searchType == type, onClick = { searchType = type },
                                label = { Text(stringResource(if (type == Wenku8SearchType.TITLE) R.string.wenku8_search_title else R.string.author)) })
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            val keyword = activeKeyword
            if (keyword == null) SearchHistoryList(historyState, historyModel::clear, ::submit,
                Modifier.fillMaxSize().padding(padding))
            else holder.SaveableStateProvider("${source.name}:$keyword:${if (source == LibrarySource.WENKU8) searchType.name else ""}") {
                if (source == LibrarySource.WENKU8) {
                    WenkuFeed(keyword = keyword, type = searchType, refreshRequest = refreshRequest,
                        modifier = Modifier.fillMaxSize().padding(padding))
                } else PullToRefreshBox(
                    isRefreshing = searchState is SearchPageModel.State.Loading,
                    onRefresh = { searchModel.refresh(keyword, category, sort) },
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    DiscoverySearchResults(model = searchModel, state = searchState, keyword = keyword,
                        category = category, sort = sort, onCategoryChange = { category = it },
                        onSortChange = { sort = it },
                        onRetry = { searchModel.search(keyword, category, sort, forceRefresh = true) },
                        navigator = navigator, modifier = Modifier.fillMaxSize())
                }
            }
        }
        LaunchedEffect(Unit) { historyModel.load() }
        LaunchedEffect(source, activeKeyword, category, sort) {
            if (source == LibrarySource.ESJZONE) activeKeyword?.let { searchModel.search(it, category, sort) }
        }
    }

}

@Composable
private fun SearchHistoryList(
    state: SearchHistoryModel.State,
    onClear: () -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier
) {
    if (state.loading) {
        DiscoveryLoadingState(modifier = modifier)
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = AppSpacing.lg, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        item(key = "history-heading", contentType = "heading") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.History, contentDescription = null)
                Text(
                    text = stringResource(R.string.search_local_history),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = AppSpacing.sm)
                )
                if (state.histories.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                        Text(stringResource(R.string.search_clear_history))
                    }
                }
            }
        }
        if (state.histories.isEmpty()) {
            item(key = "history-empty", contentType = "empty") {
                DiscoveryEmptyState(
                    title = stringResource(R.string.search_no_history),
                    message = stringResource(R.string.search_no_results_message)
                )
            }
        } else {
            item(key = "history-chips", contentType = "chips") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    val sortedHistories = remember(state.histories) {
                        state.histories.sortedByDescending { it.time }
                    }
                    sortedHistories.forEach { history ->
                        key("history:${history.index}") {
                            SearchHistoryChip(
                                keyword = history.keyword,
                                onSelect = { onSelect(history.keyword) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHistoryChip(
    keyword: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    SuggestionChip(
        onClick = onSelect,
        label = {
            Text(
                text = keyword,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        shape = AppShapes.pill,
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurface
        ),
        border = null,
        modifier = modifier.semantics {
            contentDescription = "搜索历史：$keyword"
        }
    )
}

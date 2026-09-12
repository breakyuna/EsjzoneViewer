package com.breakyuna.esjzone.ui.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.database.entity.SearchHistory
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.ui.discovery.DiscoveryEmptyState
import com.breakyuna.esjzone.ui.discovery.DiscoveryLoadingState
import com.breakyuna.esjzone.ui.discovery.DiscoverySearchField
import com.breakyuna.esjzone.ui.discovery.DiscoveryScaffold
import com.breakyuna.esjzone.ui.navigation.AppTab
import com.breakyuna.esjzone.ui.navigation.AppTabOptions
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import com.breakyuna.esjzone.ui.page.DiscoverySearchResults
import com.breakyuna.esjzone.ui.page.SearchPageModel
import com.breakyuna.esjzone.util.formattedDate

object SearchTab : AppTab {
    private fun readResolve(): Any = SearchTab

    override val options: AppTabOptions
        @Composable
        get() = AppTabOptions(
            index = 5,
            title = stringResource(R.string.screen_main_tab_search),
            icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(image = Icons.Filled.Search)
        )

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val searchModel = rememberAppViewModel { SearchPageModel(authorization) }
        val searchState by searchModel.state.collectAsState()
        val historyModel = rememberAppViewModel { SearchHistoryModel() }
        val historyState by historyModel.state.collectAsState()
        var query by rememberSaveable { mutableStateOf("") }
        var activeKeyword by rememberSaveable { mutableStateOf<String?>(null) }
        var category by rememberSaveable { mutableIntStateOf(0) }
        var sort by rememberSaveable { mutableIntStateOf(1) }

        fun submit(value: String) {
            value.trim().takeIf { it.isNotBlank() }?.let {
                query = it
                if (activeKeyword == it) {
                    searchModel.search(it, category, sort)
                } else {
                    activeKeyword = it
                }
                historyModel.save(it)
            }
        }

        DiscoveryScaffold(
            title = stringResource(R.string.search_result),
            onRefresh = {
                activeKeyword?.let { searchModel.refresh(it, category, sort) }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DiscoverySearchField(
                    value = query,
                    onValueChange = { query = it },
                    onSearch = { submit(query) },
                    onClear = {
                        query = ""
                        activeKeyword = null
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                activeKeyword?.let { current ->
                    DiscoverySearchResults(
                        model = searchModel,
                        state = searchState,
                        keyword = current,
                        category = category,
                        sort = sort,
                        onCategoryChange = { category = it },
                        onSortChange = { sort = it },
                        onRetry = { searchModel.search(current, category, sort) },
                        navigator = navigator,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                } ?: SearchHistoryList(
                    state = historyState,
                    onClear = historyModel::clear,
                    onSelect = ::submit,
                    onDelete = historyModel::delete,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }

        LaunchedEffect(Unit) { historyModel.load() }
        LaunchedEffect(activeKeyword, category, sort) {
            activeKeyword?.let { searchModel.search(it, category, sort) }
        }
    }
}

@Composable
private fun SearchHistoryList(
    state: SearchHistoryModel.State,
    onClear: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (SearchHistory) -> Unit,
    modifier: Modifier
) {
    if (state.loading) {
        DiscoveryLoadingState(modifier = modifier)
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
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
            items(
                items = state.histories.sortedByDescending { it.time.formattedDate() },
                key = { history -> "history:${history.index}" },
                contentType = { "search-history" }
            ) { history ->
                SearchHistoryRow(
                    history = history,
                    onSelect = { onSelect(history.keyword) },
                    onDelete = { onDelete(history) }
                )
            }
        }
    }
}

@Composable
private fun SearchHistoryRow(history: SearchHistory, onSelect: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "搜索历史：${history.keyword}" },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(history.keyword, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(history.time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove))
            }
        }
    }
}

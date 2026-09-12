package com.breakyuna.esjzone.ui.page

import androidx.lifecycle.viewModelScope

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.PageableRequester
import com.breakyuna.esjzone.network.features.search
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.discovery.DiscoveryEmptyState
import com.breakyuna.esjzone.ui.discovery.DiscoveryErrorState
import com.breakyuna.esjzone.ui.discovery.DiscoveryFilterMenu
import com.breakyuna.esjzone.ui.discovery.DiscoveryFilterOption
import com.breakyuna.esjzone.ui.discovery.DiscoveryNovelCard
import com.breakyuna.esjzone.ui.discovery.DiscoveryOfflineBanner
import com.breakyuna.esjzone.ui.discovery.DiscoveryScaffold
import com.breakyuna.esjzone.ui.discovery.DiscoverySearchField
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun searchTypeResource(type: Int): Int = when (type) {
    1 -> R.string.novel_list_japanese
    2 -> R.string.novel_list_original
    3 -> R.string.novel_list_korean
    else -> R.string.novel_list_all
}

private fun searchSortResource(type: Int): Int = when (type) {
    2 -> R.string.novel_filter_recentlyupload
    3 -> R.string.novel_filter_highestrating
    4 -> R.string.novel_filter_mostviews
    5 -> R.string.novel_filter_mostchapters
    6 -> R.string.novel_filter_mostcomments
    7 -> R.string.novel_filter_mostfavorites
    8 -> R.string.novel_filter_mostwords
    else -> R.string.novel_filter_recentlyupdate
}

class SearchPage(private val keyword: String) : AppDestination {
    override val key: String = "SearchPage:${keyword.trim()}"

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val model = rememberAppViewModel { SearchPageModel(authorization) }
        val state by model.state.collectAsState()
        var query by rememberSaveable { mutableStateOf(keyword.trim()) }
        var activeQuery by rememberSaveable { mutableStateOf(keyword.trim()) }
        var category by rememberSaveable { mutableIntStateOf(0) }
        var sort by rememberSaveable { mutableIntStateOf(1) }

        DiscoveryScaffold(
            title = stringResource(R.string.search_result),
            onBack = { navigator?.pop() },
            onRefresh = {
                activeQuery.takeIf { it.isNotBlank() }?.let { model.refresh(it, category, sort) }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DiscoverySearchField(
                    value = query,
                    onValueChange = { query = it },
                    onSearch = {
                        val trimmed = query.trim()
                        if (trimmed.isNotBlank()) {
                            if (activeQuery == trimmed) {
                                model.search(trimmed, category, sort)
                            } else {
                                activeQuery = trimmed
                            }
                        }
                    },
                    onClear = { query = "" },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                DiscoverySearchResults(
                    model = model,
                    state = state,
                    keyword = activeQuery,
                    category = category,
                    sort = sort,
                    onCategoryChange = { category = it },
                    onSortChange = { sort = it },
                    onRetry = { model.search(activeQuery, category, sort) },
                    navigator = navigator,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }

        LaunchedEffect(activeQuery, category, sort) {
            activeQuery.takeIf { it.isNotBlank() }?.let { model.search(it, category, sort) }
        }
    }
}

@Composable
fun DiscoverySearchResults(
    model: SearchPageModel,
    state: SearchPageModel.State,
    keyword: String,
    category: Int,
    sort: Int,
    onCategoryChange: (Int) -> Unit,
    onSortChange: (Int) -> Unit,
    onRetry: () -> Unit,
    navigator: com.breakyuna.esjzone.ui.navigation.AppNavigator?,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
        item(key = "search-filters", contentType = "filters") {
            SearchFilters(
                category = category,
                sort = sort,
                onCategoryChange = onCategoryChange,
                onSortChange = onSortChange
            )
        }
        when (val snapshot = state) {
            SearchPageModel.State.Loading -> item(key = "search-loading", contentType = "loading") {
                com.breakyuna.esjzone.ui.discovery.DiscoveryLoadingState()
            }
            is SearchPageModel.State.Error -> item(key = "search-error", contentType = "error") {
                if (snapshot.failure == LoadFailureKind.NETWORK) {
                    DiscoveryOfflineBanner(modifier = Modifier.padding(bottom = 8.dp))
                }
                DiscoveryErrorState(
                    message = stringResource(searchFailureMessage(snapshot.failure)),
                    onRetry = onRetry
                )
            }
            is SearchPageModel.State.Result -> {
                val visible = model.visibleItems
                if (visible.isEmpty()) {
                    item(key = "search-empty", contentType = "empty") {
                        DiscoveryEmptyState(
                            title = stringResource(R.string.search_no_results),
                            message = stringResource(R.string.search_no_results_message)
                        )
                    }
                }
                items(
                    items = visible,
                    key = { novel -> "search:${novelKey(novel)}" },
                    contentType = { "novel" }
                ) { novel ->
                    DiscoveryNovelCard(
                        novel = novel,
                        onClick = { navigator?.pushIfNotCurrent(NovelPage(novel)) }
                    )
                }
                item(key = "search-pagination", contentType = "pagination") {
                    SearchPagination(model, snapshot.requester)
                }
            }
        }
    }

    LaunchedEffect(listState, model.currentPage, model.moreFailed, model.visibleItems.size, state) {
        if (state !is SearchPageModel.State.Result) return@LaunchedEffect
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            last >= total - 3
        }.collect { nearEnd ->
            if (nearEnd && !model.loadingMore && !model.moreFailed) {
                model.loadMore(state.requester)
            }
        }
    }
}

@Composable
private fun SearchFilters(
    category: Int,
    sort: Int,
    onCategoryChange: (Int) -> Unit,
    onSortChange: (Int) -> Unit
) {
    val categories = listOf(0, 2, 1, 3).map { DiscoveryFilterOption(it, stringResource(searchTypeResource(it))) }
    val sorts = (1..8).map { DiscoveryFilterOption(it, stringResource(searchSortResource(it))) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DiscoveryFilterMenu(
            label = stringResource(R.string.novel_list_type),
            selected = categories.firstOrNull { it.value == category } ?: categories.first(),
            options = categories,
            onSelected = onCategoryChange,
            modifier = Modifier.weight(1f)
        )
        DiscoveryFilterMenu(
            label = stringResource(R.string.novel_list_sort),
            selected = sorts.firstOrNull { it.value == sort } ?: sorts.first(),
            options = sorts,
            onSelected = onSortChange,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SearchPagination(model: SearchPageModel, requester: PageableRequester<CoveredNovel>) {
    val hasMore = model.currentPage <= requester.pages()
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        when {
            model.moreFailed -> {
                Text(
                    text = stringResource(searchFailureMessage(model.moreFailure ?: LoadFailureKind.CLIENT)),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { model.loadMore(requester) }) { Text(stringResource(R.string.retry)) }
            }
            model.loadingMore -> CircularProgressIndicator(modifier = Modifier.padding(10.dp))
            hasMore -> Text(
                stringResource(R.string.search_load_more),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> Text(
                stringResource(R.string.search_end),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun novelKey(novel: CoveredNovel): String = novel.url.trim().ifBlank { novel.name.trim() }

private fun searchFailureMessage(failure: LoadFailureKind): Int = when (failure) {
    LoadFailureKind.NETWORK -> R.string.load_network_error
    LoadFailureKind.CLIENT -> R.string.load_client_error
}

class SearchPageModel(
    private val authorization: Authorization
) : AppStateViewModel<SearchPageModel.State>(State.Loading) {
    private val pageItems = mutableStateListOf<CoveredNovel>()
    val visibleItems: List<CoveredNovel> by derivedStateOf {
        pageItems
            .filter { !it.isAdult || PresentationAccess.settings.adult.value }
            .distinctBy { novelKey(it) }
    }
    var currentPage by mutableIntStateOf(2)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var moreFailed by mutableStateOf(false)
        private set
    var moreFailure by mutableStateOf<LoadFailureKind?>(null)
        private set
    private var requestJob: Job? = null
    private var moreJob: Job? = null
    private var generation = 0L
    private var activeKeyword: String? = null
    private var activeCategory: Int? = null
    private var activeSort: Int? = null

    sealed class State {
        data object Loading : State()
        data class Error(val failure: LoadFailureKind) : State()
        data class Result(val requester: PageableRequester<CoveredNovel>) : State()
    }

    fun search(keyword: String, category: Int = 0, sort: Int = 1) {
        val normalizedKeyword = keyword.trim()
        if (normalizedKeyword.isBlank()) return
        if (activeKeyword == normalizedKeyword && activeCategory == category && activeSort == sort &&
            (requestJob?.isActive == true || mutableState.value is State.Result)
        ) return

        generation += 1
        val token = generation
        activeKeyword = normalizedKeyword
        activeCategory = category
        activeSort = sort
        requestJob?.cancel()
        moreJob?.cancel()
        pageItems.clear()
        currentPage = 2
        loadingMore = false
        moreFailed = false
        moreFailure = null
        mutableState.value = State.Loading
        requestJob = viewModelScope.launch {
            try {
                val (requester, novels) = withContext(Dispatchers.IO) {
                    PresentationAccess.client.search(authorization, normalizedKeyword, category, sort)
                }
                ensureActive()
                if (token != generation) return@launch
                pageItems.addAll(novels)
                mutableState.value = State.Result(requester)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (token == generation) mutableState.value = State.Error(error.loadFailureKind())
                com.breakyuna.esjzone.util.AppLogger.e("SearchPageModel", "Failed to search for keyword", error)
            }
        }
    }

    fun refresh(keyword: String, category: Int = 0, sort: Int = 1) {
        activeKeyword = null
        activeCategory = null
        activeSort = null
        search(keyword, category, sort)
    }

    fun loadMore(requester: PageableRequester<CoveredNovel>) {
        if ((mutableState.value as? State.Result)?.requester !== requester || loadingMore || currentPage > requester.pages()) return
        val page = currentPage
        val token = generation
        loadingMore = true
        moreFailed = false
        moreFailure = null
        moreJob = viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { requester.more(page) }
                ensureActive()
                if (token != generation) return@launch
                val keys = pageItems.mapTo(mutableSetOf()) { novelKey(it) }
                pageItems.addAll(loaded.filter { keys.add(novelKey(it)) })
                currentPage = page + 1
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (token == generation) {
                    moreFailed = true
                    moreFailure = error.loadFailureKind()
                }
                com.breakyuna.esjzone.util.AppLogger.e("SearchPageModel", "Failed to load search page $page", error)
            } finally {
                if (token == generation) loadingMore = false
            }
        }
    }
}

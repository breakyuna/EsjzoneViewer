package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import com.breakyuna.esjzone.GlobalSettings
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.network.PageableRequester
import com.breakyuna.esjzone.network.features.search
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.component.QuietMetric
import com.breakyuna.esjzone.ui.component.QuietEmptyState
import com.breakyuna.esjzone.ui.component.QuietErrorState
import com.breakyuna.esjzone.ui.component.QuietLoadingState
import com.breakyuna.esjzone.ui.component.QuietNovelCover
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent
import com.breakyuna.esjzone.ui.theme.QuietEditorial
import com.breakyuna.esjzone.ui.theme.quietEditorialColors
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Small compatibility screen for any previously saved search destination. */
class SearchPage(private val keyword: String) : Screen {
    override val key: ScreenKey = "SearchPage:" + keyword.trim()

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val model = rememberScreenModel { SearchPageModel(authorization) }
        val state by model.state.collectAsState()
        var query by rememberSaveable { mutableStateOf(keyword.trim()) }
        var activeQuery by rememberSaveable { mutableStateOf(keyword.trim()) }
        var category by rememberSaveable { mutableIntStateOf(0) }
        var sort by rememberSaveable { mutableIntStateOf(1) }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
        ) {
            item(key = "search-app-bar") {
                com.breakyuna.esjzone.ui.component.QuietSearchHeader(
                    value = query,
                    onValueChange = { query = it },
                    onSearch = {
                        query.trim().takeIf { it.isNotBlank() }?.let { activeQuery = it }
                    },
                    onClear = { query = "" },
                    onBack = { navigator?.pop() }
                )
            }
            searchResultItems(
                model = model,
                state = state,
                onRetry = { model.search(activeQuery, category, sort) },
                keyword = activeQuery,
                category = category,
                sort = sort,
                onCategoryChange = { category = it },
                onSortChange = { sort = it }
            )
        }
        LaunchedEffect(activeQuery, category, sort) {
            activeQuery.takeIf { it.isNotBlank() }?.let {
                model.search(it, category, sort)
            }
        }
    }
}

/** Adds individual rows to the caller's list, without nesting a scroll container. */
fun LazyListScope.searchResultItems(
    model: SearchPageModel,
    state: SearchPageModel.State,
    onRetry: () -> Unit,
    keyword: String? = null,
    category: Int = 0,
    sort: Int = 1,
    onCategoryChange: (Int) -> Unit = {},
    onSortChange: (Int) -> Unit = {}
) {
    item(key = "search-results-title") {
        SearchResultsHeader(
            keyword = keyword,
            loadedCount = (state as? SearchPageModel.State.Result)?.let {
                model.visibleItems.size
            }
        )
    }
    item(key = "search-filters") {
        SearchFilterBar(
            category = category,
            sort = sort,
            onCategoryChange = onCategoryChange,
            onSortChange = onSortChange
        )
    }
    when (state) {
        SearchPageModel.State.Loading -> item(key = "search-loading") {
            QuietLoadingState(modifier = Modifier.padding(horizontal = 16.dp))
        }
        is SearchPageModel.State.Error -> item(key = "search-error") {
            QuietErrorState(
                onRetry = onRetry,
                failure = state.failure,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        is SearchPageModel.State.Result -> {
            val visible = model.visibleItems
            if (visible.isEmpty()) {
                item(key = "search-empty") {
                    QuietEmptyState(
                        title = stringResource(R.string.search_no_results),
                        message = stringResource(R.string.search_no_results_message),
                        icon = androidx.compose.material.icons.Icons.Filled.Search,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            items(visible, key = { novel ->
                "search-book:${novel.url.trim().ifBlank { novel.name.trim() }}"
            }) { novel ->
                SearchResultRow(
                    novel = novel,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                )
            }
            if (model.currentPage <= state.requester.pages()) {
                item(key = "search-more") {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        TextButton(
                            enabled = !model.loadingMore,
                            onClick = { model.loadMore(state.requester) }
                        ) {
                            if (model.loadingMore) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(if (model.moreFailed) R.string.retry else R.string.search_load_more))
                            }
                        }
                        if (model.moreFailed) {
                            Text(
                                text = stringResource(
                                    if (model.moreFailure == LoadFailureKind.NETWORK) {
                                        R.string.load_network_error
                                    } else {
                                        R.string.load_client_error
                                    }
                                ),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
            else {
                item(key = "search-end") {
                    Text(
                        text = stringResource(R.string.search_end),
                        style = QuietEditorial.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 22.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private data class SearchFilterOption(
    val value: Int,
    val label: String
)

@Composable
private fun SearchFilterBar(
    category: Int,
    sort: Int,
    onCategoryChange: (Int) -> Unit,
    onSortChange: (Int) -> Unit
) {
    val categoryOptions = listOf(
        SearchFilterOption(0, stringResource(R.string.novel_list_all)),
        SearchFilterOption(2, stringResource(R.string.novel_list_original)),
        SearchFilterOption(1, stringResource(R.string.novel_list_japanese)),
        SearchFilterOption(3, stringResource(R.string.novel_list_korean))
    )
    val sortOptions = listOf(
        SearchFilterOption(1, stringResource(R.string.novel_filter_recentlyupdate)),
        SearchFilterOption(2, stringResource(R.string.novel_filter_recentlyupload)),
        SearchFilterOption(3, stringResource(R.string.novel_filter_highestrating)),
        SearchFilterOption(4, stringResource(R.string.novel_filter_mostviews)),
        SearchFilterOption(5, stringResource(R.string.novel_filter_mostchapters)),
        SearchFilterOption(6, stringResource(R.string.novel_filter_mostcomments)),
        SearchFilterOption(7, stringResource(R.string.novel_filter_mostfavorites)),
        SearchFilterOption(8, stringResource(R.string.novel_filter_mostwords))
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = QuietEditorial.pagePadding)
            .padding(top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SearchFilterMenu(
            label = stringResource(R.string.novel_list_type),
            options = categoryOptions,
            selectedValue = category,
            onSelected = onCategoryChange,
            modifier = Modifier.weight(1f)
        )
        SearchFilterMenu(
            label = stringResource(R.string.novel_list_sort),
            options = sortOptions,
            selectedValue = sort,
            onSelected = onSortChange,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SearchFilterMenu(
    label: String,
    options: List<SearchFilterOption>,
    selectedValue: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.value == selectedValue } ?: options.first()
    val editorialColors = quietEditorialColors()

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            shape = QuietEditorial.controlShape,
            color = editorialColors.softSurface
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = QuietEditorial.smallLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = selected.label,
                        style = QuietEditorial.body,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            style = QuietEditorial.body,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(option.value)
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchResultsHeader(
    keyword: String?,
    loadedCount: Int?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = QuietEditorial.pagePadding)
            .padding(top = 14.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.search_result),
                style = QuietEditorial.sectionTitle,
                color = MaterialTheme.colorScheme.primary
            )
            keyword?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = stringResource(R.string.search_results_for, it),
                    style = QuietEditorial.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        loadedCount?.let {
            Text(
                text = stringResource(R.string.search_results_loaded, it),
                style = QuietEditorial.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

/** A result row intentionally renders only fields guaranteed by CoveredNovel. */
@Composable
private fun SearchResultRow(
    novel: CoveredNovel,
    modifier: Modifier = Modifier
) {
    val navigator = LocalBaseNavigator.current
    val editorialColors = quietEditorialColors()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                navigator?.pushIfNotCurrent(NovelPage(novel))
            },
        shape = QuietEditorial.cardShape,
        color = editorialColors.cardSurface
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            QuietNovelCover(
                coverUrl = novel.coverUrl,
                title = novel.name,
                modifier = Modifier.size(width = 88.dp, height = 120.dp),
                isAdult = novel.isAdult
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 120.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = novel.name,
                    style = QuietEditorial.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuietMetric(
                        icon = Icons.Filled.RemoveRedEye,
                        value = formatSearchCount(novel.views),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    QuietMetric(
                        icon = Icons.Filled.ThumbUp,
                        value = formatSearchCount(novel.likes),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

private fun formatSearchCount(count: Int): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

class SearchPageModel(
    private val authorization: Authorization
) : StateScreenModel<SearchPageModel.State>(State.Loading) {
    private val pageItems = mutableStateListOf<CoveredNovel>()
    val visibleItems: List<CoveredNovel> by derivedStateOf {
        pageItems.filter { !it.isAdult || GlobalSettings.adult.value }
            .distinctBy { it.url.ifBlank { it.name } }
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
        if (activeKeyword == normalizedKeyword &&
            activeCategory == category &&
            activeSort == sort &&
            (requestJob?.isActive == true || mutableState.value is State.Result)
        ) return

        // Invalidate both request families before starting a new keyword.
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
        requestJob = screenModelScope.launch {
            try {
                val (requester, novels) = withContext(Dispatchers.IO) {
                    EsjzoneClient.search(
                        authorization = authorization,
                        keyword = normalizedKeyword,
                        category = category,
                        sort = sort
                    )
                }
                ensureActive()
                if (token != generation) return@launch
                pageItems.addAll(novels)
                mutableState.value = State.Result(requester)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ensureActive()
                if (token == generation) mutableState.value = State.Error(error.loadFailureKind())
                AppLogger.e("SearchPageModel", "Failed to search for keyword", error)
            }
        }
    }

    fun loadMore(requester: PageableRequester<CoveredNovel>) {
        if ((mutableState.value as? State.Result)?.requester !== requester ||
            loadingMore || currentPage > requester.pages()
        ) return
        val page = currentPage
        val token = generation
        loadingMore = true
        moreFailed = false
        moreFailure = null
        moreJob = screenModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { requester.more(page) }
                ensureActive()
                if (token != generation) return@launch
                val keys = pageItems.mapTo(mutableSetOf()) { it.url.ifBlank { it.name } }
                pageItems.addAll(loaded.filter { keys.add(it.url.ifBlank { it.name }) })
                currentPage = page + 1
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ensureActive()
                if (token == generation) {
                    moreFailed = true
                    moreFailure = error.loadFailureKind()
                }
                AppLogger.e("SearchPageModel", "Failed to load search page $page", error)
            } finally {
                if (token == generation) loadingMore = false
            }
        }
    }
}

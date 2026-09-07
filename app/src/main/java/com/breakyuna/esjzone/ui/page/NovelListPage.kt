package com.breakyuna.esjzone.ui.page

import androidx.lifecycle.viewModelScope

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
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
import com.breakyuna.esjzone.network.features.novels
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.discovery.DiscoveryErrorState
import com.breakyuna.esjzone.ui.discovery.DiscoveryFilterMenu
import com.breakyuna.esjzone.ui.discovery.DiscoveryFilterOption
import com.breakyuna.esjzone.ui.discovery.DiscoveryNovelCard
import com.breakyuna.esjzone.ui.discovery.DiscoveryOfflineBanner
import com.breakyuna.esjzone.ui.discovery.DiscoveryScaffold
import com.breakyuna.esjzone.ui.discovery.DiscoveryLoadingState
import com.breakyuna.esjzone.ui.discovery.discoveryLoadingFooter
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun typeResource(type: Int): Int = when (type) {
    1 -> R.string.novel_list_japanese
    2 -> R.string.novel_list_original
    3 -> R.string.novel_list_korean
    else -> R.string.novel_list_all
}

private fun sortResource(type: Int): Int = when (type) {
    2 -> R.string.novel_filter_recentlyupload
    3 -> R.string.novel_filter_highestrating
    4 -> R.string.novel_filter_mostviews
    5 -> R.string.novel_filter_mostchapters
    6 -> R.string.novel_filter_mostcomments
    7 -> R.string.novel_filter_mostfavorites
    8 -> R.string.novel_filter_mostwords
    else -> R.string.novel_filter_recentlyupdate
}

class NovelListPage(
    private val initializedNovelType: Int,
    private val initializedSortType: Int,
    private val initializedAdultOnly: Boolean
) : AppDestination {
    override val key: String = "NovelListPage:$initializedNovelType:$initializedSortType:$initializedAdultOnly"

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val novelType = rememberSaveable { mutableIntStateOf(initializedNovelType) }
        val sortType = rememberSaveable { mutableIntStateOf(initializedSortType) }
        var adultOnly by rememberSaveable { mutableStateOf(initializedAdultOnly) }
        val model = rememberAppViewModel { NovelListPageModel(authorization, novelType, sortType) }
        val state by model.state.collectAsState()
        val adult by PresentationAccess.settings.adult

        DiscoveryScaffold(
            title = stringResource(R.string.novel_list),
            onBack = { navigator?.pop() },
            onRefresh = model::retry
        ) { padding ->
            when (val snapshot = state) {
                NovelListPageModel.State.Loading -> DiscoveryLoadingState(
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
                is NovelListPageModel.State.Error -> {
                    val contentModifier = Modifier.fillMaxSize().padding(padding)
                    if (snapshot.failure == LoadFailureKind.NETWORK) {
                        DiscoveryOfflineBanner(modifier = contentModifier.padding(16.dp))
                    } else {
                        DiscoveryErrorState(
                            message = stringResource(listFailureMessage(snapshot.failure)),
                            onRetry = model::retry,
                            modifier = contentModifier
                        )
                    }
                }
                is NovelListPageModel.State.Result -> {
                    NovelListResult(
                        result = snapshot,
                        model = model,
                        novelType = novelType,
                        sortType = sortType,
                        adult = adult,
                        adultOnly = adultOnly,
                        onAdultOnlyChange = { adultOnly = it },
                        onFilterChanged = { model.getRequester(forceRefresh = true) },
                        navigator = navigator,
                        modifier = Modifier.fillMaxSize().padding(padding)
                    )
                }
            }
        }

        LaunchedEffect(Unit) { model.getRequester() }
    }
}

@Composable
private fun NovelListResult(
    result: NovelListPageModel.State.Result,
    model: NovelListPageModel,
    novelType: androidx.compose.runtime.MutableIntState,
    sortType: androidx.compose.runtime.MutableIntState,
    adult: Boolean,
    adultOnly: Boolean,
    onAdultOnlyChange: (Boolean) -> Unit,
    onFilterChanged: () -> Unit,
    navigator: com.breakyuna.esjzone.ui.navigation.AppNavigator?,
    modifier: Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val items = remember(result.requester) {
        mutableStateListOf<CoveredNovel>().apply { addAll(result.firstPage) }
    }
    val visibleItems by remember(items, adult, adultOnly) {
        derivedStateOf {
            items.asSequence()
                .filter { adult && (!adultOnly || it.isAdult) || !adult && !it.isAdult }
                .distinctBy { it.url.trim().ifBlank { it.name.trim() } }
                .toList()
        }
    }
    var page by remember(result.requester) { mutableIntStateOf(2) }
    var loading by remember(result.requester) { mutableStateOf(false) }
    var pageFailure by remember(result.requester) { mutableStateOf<LoadFailureKind?>(null) }
    val maxPage = result.requester.pages()
    val pageErrorMessage = pageFailure?.let { stringResource(listFailureMessage(it)) }

    fun loadMore() {
        if (loading || page > maxPage) return
        val targetPage = page
        loading = true
        pageFailure = null
        scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { result.requester.more(targetPage) }
                val existing = items.mapTo(mutableSetOf()) { novelKey(it) }
                loaded.forEach { novel -> if (existing.add(novelKey(novel))) items.add(novel) }
                page = targetPage + 1
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                pageFailure = error.loadFailureKind()
                com.breakyuna.esjzone.util.AppLogger.e(
                    "NovelListPage",
                    "Failed to load novel page $targetPage",
                    error
                )
            } finally {
                loading = false
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "novel-list-filters", contentType = "filters") {
            NovelListFilters(
                novelType = novelType,
                sortType = sortType,
                adult = adult,
                adultOnly = adultOnly,
                onAdultOnlyChange = onAdultOnlyChange,
                onFilterChanged = onFilterChanged
            )
        }
        if (visibleItems.isEmpty()) {
            item(key = "novel-list-empty", contentType = "empty") {
                com.breakyuna.esjzone.ui.discovery.DiscoveryEmptyState(
                    title = stringResource(R.string.search_no_results),
                    message = stringResource(R.string.search_no_results_message)
                )
            }
        }
        items(
            items = visibleItems,
            key = { novel -> "novel-list:${novelKey(novel)}" },
            contentType = { "novel" }
        ) { novel ->
            DiscoveryNovelCard(
                novel = novel,
                onClick = { navigator?.pushIfNotCurrent(NovelPage(novel)) }
            )
        }
        discoveryLoadingFooter(
            loading = loading,
            hasMore = page <= maxPage,
            onRetry = if (pageFailure != null) ::loadMore else null,
            errorMessage = pageErrorMessage
        )
    }

    LaunchedEffect(listState, visibleItems.size, page, pageFailure) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            last >= total - 3
        }.collect { nearEnd ->
            if (nearEnd && pageFailure == null) loadMore()
        }
    }
}

@Composable
private fun NovelListFilters(
    novelType: androidx.compose.runtime.MutableIntState,
    sortType: androidx.compose.runtime.MutableIntState,
    adult: Boolean,
    adultOnly: Boolean,
    onAdultOnlyChange: (Boolean) -> Unit,
    onFilterChanged: () -> Unit
) {
    val typeOptions = listOf(0, 2, 1, 3).map {
        DiscoveryFilterOption(it, stringResource(typeResource(it)))
    }
    val sortOptions = (1..8).map {
        DiscoveryFilterOption(it, stringResource(sortResource(it)))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DiscoveryFilterMenu(
            label = stringResource(R.string.novel_list_type),
            selected = typeOptions.firstOrNull { it.value == novelType.intValue } ?: typeOptions.first(),
            options = typeOptions,
            onSelected = {
                novelType.intValue = it
                onFilterChanged()
            },
            modifier = Modifier.weight(1f)
        )
        DiscoveryFilterMenu(
            label = stringResource(R.string.novel_list_sort),
            selected = sortOptions.firstOrNull { it.value == sortType.intValue } ?: sortOptions.first(),
            options = sortOptions,
            onSelected = {
                sortType.intValue = it
                onFilterChanged()
            },
            modifier = Modifier.weight(1f)
        )
        if (adult) {
            FilterChip(
                selected = adultOnly,
                onClick = { onAdultOnlyChange(!adultOnly) },
                label = { Text(stringResource(R.string.novel_list_adultonly)) }
            )
        }
    }
}

private fun novelKey(novel: CoveredNovel): String = novel.url.trim().ifBlank { novel.name.trim() }

private fun listFailureMessage(failure: LoadFailureKind): Int = when (failure) {
    LoadFailureKind.NETWORK -> R.string.load_network_error
    LoadFailureKind.CLIENT -> R.string.load_client_error
}

class NovelListPageModel(
    private val authorization: Authorization,
    private val novelType: androidx.compose.runtime.MutableIntState,
    private val sortType: androidx.compose.runtime.MutableIntState
) : AppStateViewModel<NovelListPageModel.State>(State.Loading) {
    private var requestJob: kotlinx.coroutines.Job? = null
    private var initialRequestStarted = false

    sealed class State {
        data object Loading : State()
        data class Error(val failure: LoadFailureKind) : State()
        data class Result(
            val requester: PageableRequester<CoveredNovel>,
            val firstPage: List<CoveredNovel>
        ) : State()
    }

    fun getRequester(forceRefresh: Boolean = false) {
        if (!forceRefresh && initialRequestStarted) return
        initialRequestStarted = true
        requestJob?.cancel()
        requestJob = viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = State.Loading
            try {
                val (requester, firstPage) = PresentationAccess.client.novels(
                    authorization = authorization,
                    novelType = novelType.intValue,
                    sortType = sortType.intValue,
                    forceRefresh = forceRefresh
                )
                ensureActive()
                mutableState.value = State.Result(requester, firstPage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                initialRequestStarted = false
                mutableState.value = State.Error(e.loadFailureKind())
                com.breakyuna.esjzone.util.AppLogger.e(
                    "NovelListPageModel",
                    "Failed to load novel list for type=${novelType.intValue}, sort=${sortType.intValue}",
                    e
                )
            }
        }
    }

    fun retry() = getRequester(forceRefresh = true)
}

package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import com.breakyuna.esjzone.GlobalSettings
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.PageableRequester
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.network.features.novels
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.novellibrary.novel.preview
import com.breakyuna.esjzone.ui.component.AppBar
import com.breakyuna.esjzone.ui.component.DropdownSelection
import com.breakyuna.esjzone.ui.component.LoadError
import com.breakyuna.esjzone.ui.component.QuietEmptyState
import com.breakyuna.esjzone.ui.component.QuietErrorState
import com.breakyuna.esjzone.ui.component.QuietLoadingState
import com.breakyuna.esjzone.ui.component.QuietNovelListItem
import com.breakyuna.esjzone.ui.component.QuietSectionHeader
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator

private fun typeResource(type: Int): Int {
    return when (type) {
        0 -> R.string.novel_list_all
        1 -> R.string.novel_list_japanese
        2 -> R.string.novel_list_original
        3 -> R.string.novel_list_korean
        else -> R.string.novel_list_all
    }
}

private fun sortResource(type: Int): Int {
    return when (type) {
        1 -> R.string.novel_filter_recentlyupdate
        2 -> R.string.novel_filter_recentlyupload
        3 -> R.string.novel_filter_highestrating
        4 -> R.string.novel_filter_mostviews
        5 -> R.string.novel_filter_mostchapters
        6 -> R.string.novel_filter_mostcomments
        7 -> R.string.novel_filter_mostfavorites
        8 -> R.string.novel_filter_mostwords
        else -> R.string.novel_filter_recentlyupdate
    }
}

class NovelListPage(
    private val initializedNovelType: Int,
    private val initializedSortType: Int,
    private val initializedAdultOnly: Boolean
) : Screen {

    override val key: ScreenKey =
        "NovelListPage:" +
            initializedNovelType + ":" +
            initializedSortType + ":" +
            initializedAdultOnly

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current

        val authorization = LocalAuthorization.current

        val novelType = rememberSaveable {
            mutableIntStateOf(initializedNovelType)
        }

        val sortType = rememberSaveable {
            mutableIntStateOf(initializedSortType)
        }

        var adultOnly by rememberSaveable {
            mutableStateOf(initializedAdultOnly)
        }

        val novelListModel =
            rememberScreenModel { NovelListPageModel(authorization, novelType, sortType) }
        val state by novelListModel.state.collectAsState()

        val adult by remember {
            GlobalSettings.adult
        }

        Column(modifier = Modifier.fillMaxSize()) {
            AppBar(
                title = stringResource(id = R.string.novel_list),
                onBack = {
                    navigator?.pop()
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                ) {
                    var typeExposed by remember { mutableStateOf(false) }
                    var sortExposed by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DropdownSelection(
                            label = stringResource(id = R.string.novel_list_type),
                            items = listOf(0, 2, 1, 3),
                            current = novelType.intValue,
                            onChange = {
                                novelType.intValue = it
                                novelListModel.getRequester(forceRefresh = true)
                            },
                            exposed = typeExposed,
                            onExposeChanged = { typeExposed = it },
                            modifier = Modifier.weight(1f),
                            nameProvider = { stringResource(id = typeResource(this)) }
                        )
                        if (adult) {
                            FilterChip(
                                selected = adultOnly,
                                onClick = { adultOnly = !adultOnly },
                                label = { Text(stringResource(id = R.string.novel_list_adultonly)) }
                            )
                        }
                    }
                    DropdownSelection(
                        label = stringResource(id = R.string.novel_list_sort),
                        items = listOf(1, 2, 3, 4, 5, 6, 7, 8),
                        current = sortType.intValue,
                        onChange = {
                            sortType.intValue = it
                            novelListModel.getRequester(forceRefresh = true)
                        },
                        exposed = sortExposed,
                        onExposeChanged = { sortExposed = it },
                        modifier = Modifier.fillMaxWidth(),
                        nameProvider = { stringResource(id = sortResource(this)) }
                    )
                }
            }

            when (state) {
                is NovelListPageModel.State.Loading -> QuietLoadingState(modifier = Modifier.fillMaxSize())

                is NovelListPageModel.State.Error -> QuietErrorState(
                    onRetry = novelListModel::retry,
                    failure = (state as NovelListPageModel.State.Error).failure,
                    modifier = Modifier.fillMaxSize()
                )

                is NovelListPageModel.State.Result -> {
                    val result = (state as NovelListPageModel.State.Result)
                    val requester = result.requester

                    var current by remember(result) {
                        mutableIntStateOf(2)
                    }
                    var pageFailed by remember(result) {
                        mutableStateOf(false)
                    }
                    var pageFailure by remember(result) {
                        mutableStateOf<LoadFailureKind?>(null)
                    }
                    var pageRetry by remember(result) {
                        mutableIntStateOf(0)
                    }

                    val max = requester.pages()

                    val items = remember(result) {
                        mutableStateListOf<CoveredNovel>().apply {
                            addAll(result.firstPage)
                        }
                    }

                    val visibleItems by remember(items, adult, adultOnly) {
                        derivedStateOf {
                            items.asSequence()
                                .filter { adult && (!adultOnly || it.isAdult) || !adult && !it.isAdult }
                                .distinctBy { it.url.ifBlank { it.name } }
                                .toList()
                        }
                    }

                    val listState = rememberLazyListState()
                    var isLoadingPage by remember(result) { mutableStateOf(false) }

                    LazyColumn(
                        state = listState,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
                    ) {
                        item {
                            QuietSectionHeader(
                                title = stringResource(R.string.novel_list),
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        if (visibleItems.isEmpty()) {
                            item {
                                QuietEmptyState(
                                    title = stringResource(R.string.search_no_results),
                                    message = stringResource(R.string.home_collection_empty_message)
                                )
                            }
                        }
                        items(visibleItems, key = { novel ->
                            "novel-list-${novel.url.trim().ifBlank { novel.name.trim() }}"
                        }) { novel ->
                            val summaryKey = novel.url.ifBlank { novel.name }
                            val summary = novelListModel.summaries[summaryKey]

                            QuietNovelListItem(
                                novel = novel,
                                summary = summary,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            if (summary == null) {
                                LaunchedEffect(summaryKey) {
                                    novelListModel.loadSummary(novel)
                                }
                            }
                        }

                        item {
                            if (current <= max && max > 1) {
                                if (pageFailed) {
                                    LoadError(
                                        onRetry = {
                                            pageFailed = false
                                            pageFailure = null
                                            pageRetry += 1
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        failure = pageFailure ?: LoadFailureKind.CLIENT
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (current > max || max <= 1) {
                            item(key = "novel-list-end") {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(text = stringResource(id = R.string.the_end), modifier = Modifier.padding(16.dp))
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // The filtered-empty state is a real lazy item. Include it
                    // when locating the footer so it is not mistaken for the
                    // load trigger on every recomposition.
                    val footerIndex = 1 + visibleItems.size +
                        if (visibleItems.isEmpty()) 1 else 0
                    LaunchedEffect(listState, footerIndex, current, pageRetry, pageFailed) {
                        snapshotFlow {
                            listState.layoutInfo.visibleItemsInfo.any { it.index >= footerIndex }
                        }.distinctUntilChanged().collect { footerVisible ->
                            if (footerVisible && !pageFailed && !isLoadingPage && current <= max && max > 1) {
                                isLoadingPage = true
                                try {
                                    val newlyLoaded = withContext(Dispatchers.IO) { requester.more(current) }
                                    newlyLoaded.forEach { item ->
                                        val itemKey = item.url.trim().ifBlank { item.name.trim() }
                                        if (items.none { it.url.trim().ifBlank { it.name.trim() } == itemKey }) {
                                            items.add(item)
                                        }
                                    }
                                    pageFailed = false
                                    pageFailure = null
                                    current += 1
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    pageFailed = true
                                    pageFailure = e.loadFailureKind()
                                    com.breakyuna.esjzone.util.AppLogger.e("NovelListPage", "Failed to load novel page $current", e)
                                } finally {
                                    isLoadingPage = false
                                }
                            }
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            novelListModel.getRequester()
        }
    }

}

class NovelListPageModel(
    private val authorization: Authorization,
    private val novelType: MutableIntState,
    private val sortType: MutableIntState,
) : StateScreenModel<NovelListPageModel.State>(State.Loading) {

    private var requestJob: Job? = null
    private var initialRequestStarted = false

    val summaries = mutableStateMapOf<String, String>()
    private val summaryLock = Any()
    private val summaryRequests = mutableSetOf<String>()
    private val summaryFailures = mutableSetOf<String>()

    sealed class State {
        data object Loading : State()
        data class Error(val failure: LoadFailureKind) : State()
        data class Result(
            val requester: PageableRequester<CoveredNovel>,
            val firstPage: List<CoveredNovel>
        ) : State()
    }

    fun loadSummary(novel: CoveredNovel) {
        val key = novel.url.ifBlank { novel.name }
        if (key.isBlank()) return

        synchronized(summaryLock) {
            if (summaries.containsKey(key) ||
                key in summaryFailures ||
                !summaryRequests.add(key)
            ) {
                return
            }
        }

        screenModelScope.launch(Dispatchers.IO) {
            try {
                val detail = EsjzoneClient.getNovelDetail(authorization, novel)
                val preview = detail.description.preview()
                if (preview.isBlank()) {
                    synchronized(summaryLock) {
                        summaryFailures.add(key)
                    }
                } else {
                    summaries[key] = preview
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                synchronized(summaryLock) {
                    summaryFailures.add(key)
                }
                com.breakyuna.esjzone.util.AppLogger.w(
                    "NovelListPageModel",
                    "Failed to load summary for ${novel.name}",
                    e
                )
            } finally {
                synchronized(summaryLock) {
                    summaryRequests.remove(key)
                }
            }
        }
    }

    fun getRequester(forceRefresh: Boolean = false) {
        if (!forceRefresh && initialRequestStarted) return
        initialRequestStarted = true
        requestJob?.cancel()
        requestJob = screenModelScope.launch(Dispatchers.IO) {
            mutableState.value = State.Loading
            try {
                val (requester, novels) = EsjzoneClient.novels(
                    authorization,
                    novelType.intValue,
                    sortType.intValue,
                    forceRefresh = forceRefresh
                )
                ensureActive()
                mutableState.value = State.Result(requester, novels)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = State.Error(e.loadFailureKind())
                // Allow a later lifecycle/network recovery request to try again.
                initialRequestStarted = false
                com.breakyuna.esjzone.util.AppLogger.e("NovelListPageModel", "Failed to load novel list for type=${novelType.intValue}, sort=${sortType.intValue}", e)
            }
        }
    }

    fun retry() {
        getRequester(forceRefresh = true)
    }

}

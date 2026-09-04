package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.breakyuna.esjzone.ui.component.QuietBackHeader
import com.breakyuna.esjzone.ui.component.QuietEmptyState
import com.breakyuna.esjzone.ui.component.QuietErrorState
import com.breakyuna.esjzone.ui.component.QuietLoadingState
import com.breakyuna.esjzone.ui.component.QuietNovelListItem
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
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
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .imePadding()
        ) {
            item(key = "search-app-bar") {
                QuietBackHeader(
                    title = stringResource(R.string.search_result),
                    onBack = { navigator?.pop() }
                )
            }
            searchResultItems(
                model = model,
                state = state,
                onRetry = { model.search(keyword) },
                keyword = keyword
            )
        }
        LaunchedEffect(keyword) { model.search(keyword) }
    }
}

/** Adds individual rows to the caller's list, without nesting a scroll container. */
fun LazyListScope.searchResultItems(
    model: SearchPageModel,
    state: SearchPageModel.State,
    onRetry: () -> Unit,
    keyword: String? = null
) {
    item(key = "search-results-title") {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.search_result),
                style = com.breakyuna.esjzone.ui.theme.QuietEditorial.sectionTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
            keyword?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = stringResource(R.string.search_results_for, it),
                    style = com.breakyuna.esjzone.ui.theme.QuietEditorial.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (state is SearchPageModel.State.Result) {
                Text(
                    text = stringResource(R.string.search_results_loaded, model.visibleItems.size),
                    style = com.breakyuna.esjzone.ui.theme.QuietEditorial.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
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
            items(visible, key = { "search-book:${it.url.ifBlank { it.name }}" }) {
                QuietNovelListItem(
                    novel = it,
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
                        style = com.breakyuna.esjzone.ui.theme.QuietEditorial.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 22.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
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

    sealed class State {
        data object Loading : State()
        data class Error(val failure: LoadFailureKind) : State()
        data class Result(val requester: PageableRequester<CoveredNovel>) : State()
    }

    fun search(keyword: String) {
        val normalizedKeyword = keyword.trim()
        if (normalizedKeyword.isBlank()) return
        if (activeKeyword == normalizedKeyword &&
            (requestJob?.isActive == true || mutableState.value is State.Result)
        ) return

        // Invalidate both request families before starting a new keyword.
        generation += 1
        val token = generation
        activeKeyword = normalizedKeyword
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
                    EsjzoneClient.search(authorization, normalizedKeyword)
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

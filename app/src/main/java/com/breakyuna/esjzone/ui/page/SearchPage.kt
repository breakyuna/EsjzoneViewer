package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.breakyuna.esjzone.GlobalSettings
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.PageableRequester
import com.breakyuna.esjzone.network.features.search
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.component.AppBar
import com.breakyuna.esjzone.ui.component.Loading
import com.breakyuna.esjzone.ui.component.Novel
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator

class SearchPage(private val keyword: String) : Screen {

    override val key: ScreenKey =
        "SearchPage:" + keyword.trim()

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current

        val authorization = LocalAuthorization.current

        val searchModel = rememberScreenModel { SearchPageModel(authorization) }
        val state by searchModel.state.collectAsState()

        Column {
            AppBar(
                title = "${stringResource(id = R.string.search_result)}: $keyword",
                onBack = {
                    navigator?.pop()
                }
            )

            when (state) {
                is SearchPageModel.State.Loading -> Loading()

                is SearchPageModel.State.Result -> {
                    val result = (state as SearchPageModel.State.Result)
                    val requester = result.requester

                    var current by rememberSaveable(result) {
                        mutableIntStateOf(2)
                    }

                    val max = requester.pages()

                    val items = remember(result) {
                        mutableStateListOf<CoveredNovel>().apply {
                            addAll(result.firstPage)
                        }
                    }

                    val listState = rememberLazyListState()

                    val adult by remember {
                        GlobalSettings.adult
                    }

                    LazyColumn(state = listState) {
                        items(
                            items = items.toList().filter {
                                if (!it.isAdult)
                                    true
                                else
                                    adult
                            }.distinct(),
                            key = { item -> item.url.ifBlank { item.name } }
                        ) { novel ->
                            Novel(covered = novel)
                        }

                        item {
                            if (current <= max && max > 1) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                                LaunchedEffect(current) {
                                    try {
                                        val newlyLoaded = withContext(Dispatchers.IO) {
                                            requester.more(current)
                                        }
                                        for (item in newlyLoaded) {
                                            if (items.contains(item))
                                                continue
                                            items.add(item)
                                        }
                                        current += 1
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        com.breakyuna.esjzone.util.AppLogger.e(
                                            "SearchPage",
                                            "Failed to load search page $current",
                                            e
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = stringResource(id = R.string.the_end),
                                    modifier = Modifier.padding(16.dp)
                                )
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                SearchPageModel.State.Error -> Text(
                    text = stringResource(id = R.string.search_failed),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }

        LaunchedEffect(keyword) {
            searchModel.search(keyword)
        }
    }

}

/**
 * Renders search results directly inside the search screen. The result area
 * uses a regular Column so it can live below the search field's scrollable
 * content without creating a nested, unbounded LazyColumn.
 */
@Composable
fun InlineSearchResults(
    authorization: Authorization,
    keyword: String,
    modifier: Modifier = Modifier
) {
    val searchModel = rememberScreenModel { SearchPageModel(authorization) }
    val state by searchModel.state.collectAsState()

    LaunchedEffect(keyword) {
        searchModel.search(keyword)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(id = R.string.search_result),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        when (val currentState = state) {
            SearchPageModel.State.Loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(strokeWidth = 2.5.dp)
            }

            SearchPageModel.State.Error -> Text(
                text = stringResource(id = R.string.search_failed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
            )

            is SearchPageModel.State.Result -> {
                val requester = currentState.requester
                val items = remember(currentState) {
                    mutableStateListOf<CoveredNovel>().apply {
                        addAll(currentState.firstPage)
                    }
                }
                var currentPage by remember(currentState) {
                    mutableIntStateOf(2)
                }
                var loadingMore by remember(currentState) {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                val scope = rememberCoroutineScope()
                val adult by remember {
                    GlobalSettings.adult
                }
                val visibleItems = items.toList()
                    .filter { !it.isAdult || adult }
                    .distinct()

                if (visibleItems.isEmpty()) {
                    Text(
                        text = stringResource(id = R.string.search_no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                    )
                } else {
                    visibleItems.forEach { novel ->
                        Novel(covered = novel)
                    }
                }

                if (currentPage <= requester.pages() && requester.pages() > 1) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(
                            enabled = !loadingMore,
                            onClick = {
                                if (!loadingMore) {
                                    loadingMore = true
                                    val pageToLoad = currentPage
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val newlyLoaded = requester.more(pageToLoad)
                                            withContext(Dispatchers.Main.immediate) {
                                                newlyLoaded.forEach { novel ->
                                                    if (novel !in items) items.add(novel)
                                                }
                                                currentPage = pageToLoad + 1
                                                loadingMore = false
                                            }
                                        } catch (e: CancellationException) {
                                            withContext(Dispatchers.Main.immediate) {
                                                loadingMore = false
                                            }
                                            throw e
                                        } catch (e: Exception) {
                                            com.breakyuna.esjzone.util.AppLogger.e(
                                                "SearchPage",
                                                "Failed to load search page $pageToLoad",
                                                e
                                            )
                                            withContext(Dispatchers.Main.immediate) {
                                                loadingMore = false
                                            }
                                        }
                                    }
                                }
                            }
                        ) {
                            if (loadingMore) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(text = stringResource(id = R.string.search_load_more))
                            }
                        }
                    }
                }
            }
        }
    }
}

class SearchPageModel(
    private val authorization: Authorization
) : StateScreenModel<SearchPageModel.State>(State.Loading) {

    private var requestJob: Job? = null
    private var activeKeyword: String? = null

    sealed class State {
        data object Loading : State()
        data object Error : State()
        data class Result(
            val requester: PageableRequester<CoveredNovel>,
            val firstPage: List<CoveredNovel>
        ) : State()
    }

    fun search(keyword: String) {
        val normalizedKeyword = keyword.trim()
        if (normalizedKeyword.isBlank()) return
        if (activeKeyword == normalizedKeyword &&
            (requestJob?.isActive == true || mutableState.value is State.Result)
        ) {
            return
        }

        activeKeyword = normalizedKeyword
        requestJob?.cancel()
        requestJob = screenModelScope.launch(Dispatchers.IO) {
            mutableState.value = State.Loading
            try {
                val (requester, novels) = EsjzoneClient.search(authorization, normalizedKeyword)
                ensureActive()
                mutableState.value = State.Result(requester, novels)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = State.Error
                com.breakyuna.esjzone.util.AppLogger.e(
                    "SearchPageModel",
                    "Failed to search for keyword: $normalizedKeyword",
                    e
                )
            }
        }
    }

}

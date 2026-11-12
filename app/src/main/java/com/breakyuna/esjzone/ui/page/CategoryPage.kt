@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.breakyuna.esjzone.ui.page

import com.breakyuna.esjzone.ui.designsystem.AppStateContent
import com.breakyuna.esjzone.ui.designsystem.wideContentWidth
import com.breakyuna.esjzone.ui.designsystem.wideListColumns
import com.breakyuna.esjzone.ui.designsystem.PreserveAdaptiveListAnchor
import com.breakyuna.esjzone.ui.designsystem.adaptiveItems
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.lifecycle.viewModelScope

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.features.listNovels
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.novellibrary.novel.Category
import com.breakyuna.esjzone.novellibrary.novel.CategoryNovel
import com.breakyuna.esjzone.ui.discovery.DiscoveryCategoryNovelCard
import com.breakyuna.esjzone.ui.discovery.DiscoveryEmptyState
import com.breakyuna.esjzone.ui.discovery.DiscoveryErrorState
import com.breakyuna.esjzone.ui.discovery.DiscoveryLoadingState
import com.breakyuna.esjzone.ui.discovery.DiscoveryOfflineBanner
import com.breakyuna.esjzone.ui.discovery.DiscoveryScaffold
import com.breakyuna.esjzone.ui.navigation.AppNavKey
import com.breakyuna.esjzone.ui.navigation.LegacyRoute
import com.breakyuna.esjzone.ui.navigation.token
import com.breakyuna.esjzone.ui.navigation.AppNavigator
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Novel names in a category are already available from the list endpoint. */
class CategoryPage(private val category: Category) : AppDestination {
    override val navKey = AppNavKey.Legacy(LegacyRoute.Category(category.url.trim().ifBlank { category.name.trim() }, category.name.trim()))

    override val key: String get() = navKey.route.token()

    @Composable
    override fun Content(navigator: AppNavigator) {
        val columns = wideListColumns(maxWidth = 1280f)
        val authorization = LocalAuthorization.current
        val model = rememberAppViewModel { CategoryPageModel(authorization, category) }
        val listState = rememberLazyListState()
        DiscoveryScaffold(
            title = category.name,
            onBack = { navigator.pop() }
        ) { padding ->
            val state by model.state.collectAsStateWithLifecycle()
            PullToRefreshBox(
                isRefreshing = state is CategoryPageModel.State.Loading,
                onRefresh = model::retry,
                modifier = Modifier.fillMaxSize()
            ) {
            AppStateContent(state) { snapshot ->
                when (snapshot) {
                    CategoryPageModel.State.Loading ->
                        DiscoveryLoadingState(modifier = Modifier.fillMaxSize().padding(padding))
                    is CategoryPageModel.State.Error -> {
                        val contentModifier = Modifier.fillMaxSize().padding(padding)
                        Column(modifier = contentModifier) {
                            if (snapshot.failure == LoadFailureKind.NETWORK) {
                                DiscoveryOfflineBanner(modifier = Modifier.padding(16.dp))
                            }
                            DiscoveryErrorState(
                                message = stringResource(categoryPageFailure(snapshot.failure)),
                                onRetry = model::retry,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    is CategoryPageModel.State.Result -> {
                        if (snapshot.categoryNovels.isEmpty()) {
                            DiscoveryEmptyState(
                                title = stringResource(R.string.search_no_results),
                                message = stringResource(R.string.search_no_results_message),
                                modifier = Modifier.fillMaxSize().padding(padding),
                            )
                        } else {
                            val novels =
                                snapshot.categoryNovels.distinctBy {
                                    it.url.trim().ifBlank { it.name.trim() }
                                }
                            PreserveAdaptiveListAnchor(listState, novels, columns, key = ::categoryNovelKey)
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize().wideContentWidth().padding(padding),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                adaptiveItems(
                                    columns = columns,
                                    items = novels,
                                    key = { novel -> categoryNovelKey(novel) },
                                    contentType = { "category-novel" },
                                ) { novel ->
                                    DiscoveryCategoryNovelCard(
                                        novel = novel,
                                        onClick = { navigator.pushIfNotCurrent(NovelPage(novel)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }
        LaunchedEffect(Unit) { model.getNovels() }
    }
}

private fun categoryNovelKey(novel: CategoryNovel): String =
    "category-novel:${novel.url.trim().ifBlank { novel.name.trim() }}"

private fun categoryPageFailure(failure: LoadFailureKind): Int = when (failure) {
    LoadFailureKind.NETWORK -> R.string.load_network_error
    LoadFailureKind.CLIENT -> R.string.load_client_error
}

class CategoryPageModel(
    private val authorization: Authorization,
    private val category: Category
) : AppStateViewModel<CategoryPageModel.State>(State.Loading) {
    private var loadStarted = false

    sealed class State {
        data object Loading : State()
        data class Error(val failure: LoadFailureKind) : State()
        data class Result(val categoryNovels: List<CategoryNovel>) : State()
    }

    fun getNovels(forceRefresh: Boolean = false) {
        if (!forceRefresh && loadStarted) return
        loadStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = State.Loading
            try {
                val novels = PresentationAccess.client.listNovels(
                    authorization,
                    category,
                    forceRefresh = forceRefresh
                )
                ensureActive()
                mutableState.value = State.Result(novels)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = State.Error(e.loadFailureKind())
                loadStarted = false
                com.breakyuna.esjzone.util.AppLogger.e(
                    "CategoryPageModel",
                    "Failed to list novels for category: ${category.name}",
                    e
                )
            }
        }
    }

    fun retry() {
        loadStarted = false
        getNovels(forceRefresh = true)
    }
}

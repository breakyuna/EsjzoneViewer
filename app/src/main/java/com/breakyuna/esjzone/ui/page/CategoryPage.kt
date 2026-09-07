package com.breakyuna.esjzone.ui.page

import androidx.lifecycle.viewModelScope

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Novel names in a category are already available from the list endpoint. */
class CategoryPage(private val category: Category) : AppDestination {
    override val key: String = "CategoryPage:" + category.url.trim().ifBlank { category.name.trim() }

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val model = rememberAppViewModel { CategoryPageModel(authorization, category) }
        DiscoveryScaffold(
            title = category.name,
            onBack = { navigator?.pop() },
            onRefresh = model::retry
        ) { padding ->
            val state by model.state.collectAsState()
            when (val snapshot = state) {
                CategoryPageModel.State.Loading -> DiscoveryLoadingState(
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
                is CategoryPageModel.State.Error -> {
                    val contentModifier = Modifier.fillMaxSize().padding(padding)
                    Column(modifier = contentModifier) {
                        if (snapshot.failure == LoadFailureKind.NETWORK) {
                            DiscoveryOfflineBanner(modifier = Modifier.padding(16.dp))
                        }
                        DiscoveryErrorState(
                            message = stringResource(categoryPageFailure(snapshot.failure)),
                            onRetry = model::retry,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                is CategoryPageModel.State.Result -> {
                    if (snapshot.categoryNovels.isEmpty()) {
                        DiscoveryEmptyState(
                            title = stringResource(R.string.search_no_results),
                            message = stringResource(R.string.search_no_results_message),
                            modifier = Modifier.fillMaxSize().padding(padding)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = snapshot.categoryNovels.distinctBy {
                                    it.url.trim().ifBlank { it.name.trim() }
                                },
                                key = { novel -> categoryNovelKey(novel) },
                                contentType = { "category-novel" }
                            ) { novel ->
                                DiscoveryCategoryNovelCard(
                                    novel = novel,
                                    onClick = { navigator?.pushIfNotCurrent(NovelPage(novel)) }
                                )
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

    fun getNovels() {
        if (loadStarted) return
        loadStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = State.Loading
            try {
                val novels = PresentationAccess.client.listNovels(authorization, category)
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
        getNovels()
    }
}

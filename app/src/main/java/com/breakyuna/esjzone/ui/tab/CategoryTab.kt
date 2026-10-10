@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.breakyuna.esjzone.ui.tab

import com.breakyuna.esjzone.ui.discovery.LibrarySource
import com.breakyuna.esjzone.ui.discovery.LocalDiscoverySource
import com.breakyuna.esjzone.ui.discovery.SourceSelector
import com.breakyuna.esjzone.ui.page.WenkuFeed
import com.breakyuna.esjzone.network.wenku8.Wenku8Browse
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.lifecycle.viewModelScope

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.MaterialTheme
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.features.getCategories
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.novellibrary.novel.Category
import com.breakyuna.esjzone.ui.discovery.DiscoveryCategoryCard
import com.breakyuna.esjzone.ui.discovery.DiscoveryEmptyState
import com.breakyuna.esjzone.ui.discovery.DiscoveryErrorState
import com.breakyuna.esjzone.ui.discovery.DiscoveryOfflineBanner
import com.breakyuna.esjzone.ui.discovery.DiscoveryScaffold
import com.breakyuna.esjzone.ui.discovery.DiscoveryLoadingState
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.ui.navigation.AppTab
import com.breakyuna.esjzone.ui.navigation.AppTabOptions
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import com.breakyuna.esjzone.ui.page.CategoryPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Full-screen category browser opened from Home. */
class CategoryBrowserPage : AppDestination {
    override val key: String = "CategoryBrowserPage"

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val model = rememberAppViewModel { CategoryModel(authorization) }
        DiscoveryScaffold(
            title = stringResource(R.string.categories),
            onBack = { navigator?.pop() }
        ) { padding ->
            IntegratedCategoryContent(model, Modifier.fillMaxSize().padding(padding))
        }
    }
}

object CategoryTab : AppTab {
    private fun readResolve(): Any = CategoryTab

    override val options: AppTabOptions
        @Composable
        get() = AppTabOptions(
            index = 1,
            title = stringResource(R.string.screen_main_tab_category),
            icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(image = Icons.Filled.Category)
        )

    @Composable
    override fun Content() {
        val authorization = LocalAuthorization.current
        val model = rememberAppViewModel { CategoryModel(authorization) }
        DiscoveryScaffold(
            title = stringResource(R.string.categories)
        ) { padding ->
            IntegratedCategoryContent(model, Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
private fun IntegratedCategoryContent(model: CategoryModel, modifier: Modifier) {
    val discoverySource = LocalDiscoverySource.current
    var source by rememberSaveable { mutableStateOf(discoverySource.value) }
    val holder = rememberSaveableStateHolder()
    Column(modifier) {
        SourceSelector(source, { selected -> selected?.let { source = it; discoverySource.value = it } })
        holder.SaveableStateProvider(source.name) {
            if (source == LibrarySource.WENKU8) WenkuFeed(initialBrowse = Wenku8Browse.ALL,
                showBrowse = true, modifier = Modifier.weight(1f))
            else CategoryBrowserContent(model, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CategoryBrowserContent(model: CategoryModel, modifier: Modifier) {
    val navigator = LocalBaseNavigator.current
    val state by model.state.collectAsStateWithLifecycle()
    val adult by PresentationAccess.settings.adult

    PullToRefreshBox(
        isRefreshing = state is CategoryModel.State.Loading,
        onRefresh = model::reload,
        modifier = modifier
    ) {
        when (val snapshot = state) {
        CategoryModel.State.Loading -> DiscoveryLoadingState(Modifier.fillMaxSize())
        is CategoryModel.State.Error -> {
            Column(modifier = Modifier.fillMaxSize()) {
                if (snapshot.failure == LoadFailureKind.NETWORK) {
                    DiscoveryOfflineBanner(modifier = Modifier.padding(16.dp))
                }
                DiscoveryErrorState(
                    message = stringResource(categoryFailureMessage(snapshot.failure)),
                    onRetry = model::retry,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        is CategoryModel.State.Result -> {
            val categories = snapshot.categories.filterNot { it.isAdult && !adult }
            if (categories.isEmpty()) {
                DiscoveryEmptyState(
                    title = stringResource(R.string.categories_empty),
                    message = stringResource(R.string.home_adult_hidden),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyVerticalGrid(
                    modifier = Modifier.fillMaxSize(),
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "category-heading", contentType = "heading") {
                        Text(
                            text = stringResource(R.string.categories),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    itemsIndexed(
                        items = categories,
                        key = { _, category -> categoryKey(category) },
                        contentType = { _, _ -> "category" }
                    ) { index, category ->
                        DiscoveryCategoryCard(
                            title = category.name,
                            isAdult = category.isAdult,
                            index = index,
                            onClick = { navigator?.pushIfNotCurrent(CategoryPage(category)) }
                        )
                    }
                }
            }
        }
        }
    }

    LaunchedEffect(Unit) { model.getCategories() }
}

private fun categoryKey(category: Category): String =
    "category:${category.url.trim().ifBlank { category.name.trim() }}"

private fun categoryFailureMessage(failure: LoadFailureKind): Int = when (failure) {
    LoadFailureKind.NETWORK -> R.string.load_network_error
    LoadFailureKind.SESSION_OR_NODE -> R.string.load_session_or_node_error
    LoadFailureKind.CLIENT -> R.string.load_client_error
}

class CategoryModel(
    private val authorization: Authorization
) : AppStateViewModel<CategoryModel.State>(State.Loading) {
    private var loadStarted = false

    sealed class State {
        data object Loading : State()
        data class Error(val failure: LoadFailureKind) : State()
        data class Result(val categories: List<Category>) : State()
    }

    fun getCategories(forceRefresh: Boolean = false) {
        if (!forceRefresh && loadStarted) return
        loadStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = State.Loading
            try {
                val categories = PresentationAccess.client.getCategories(
                    authorization,
                    forceRefresh = forceRefresh
                )
                ensureActive()
                mutableState.value = State.Result(categories)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadStarted = false
                mutableState.value = State.Error(e.loadFailureKind())
                com.breakyuna.esjzone.util.AppLogger.e("CategoryModel", "Failed to load categories", e)
            }
        }
    }

    fun retry() {
        loadStarted = false
        getCategories(forceRefresh = true)
    }

    fun reload() = retry()
}

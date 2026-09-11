package com.breakyuna.esjzone.ui.tab

import androidx.lifecycle.viewModelScope

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.features.getHomeData
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.novellibrary.data.HomeData
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.component.AppHomeNovelTile
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.discovery.DiscoveryEmptyState
import com.breakyuna.esjzone.ui.discovery.DiscoveryErrorState
import com.breakyuna.esjzone.ui.discovery.DiscoveryLoadingState
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import com.breakyuna.esjzone.ui.discovery.DiscoveryOfflineBanner
import com.breakyuna.esjzone.ui.discovery.DiscoveryScaffold
import com.breakyuna.esjzone.ui.navigation.AppNavigator
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.ui.navigation.AppTab
import com.breakyuna.esjzone.ui.navigation.AppTabOptions
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.LocalFloatingNavPadding
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import com.breakyuna.esjzone.ui.page.ForumPage
import com.breakyuna.esjzone.ui.page.GuestbookPage
import com.breakyuna.esjzone.ui.page.NovelListPage
import com.breakyuna.esjzone.ui.page.NovelPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

object HomeTab : AppTab {

    private fun readResolve(): Any = HomeTab

    override val options: AppTabOptions
        @Composable
        get() = AppTabOptions(
            index = 0,
            title = stringResource(R.string.screen_main_tab_home),
            icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(image = Icons.Filled.Home)
        )

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val model = rememberAppViewModel { HomeTabModel(authorization) }
        val state by model.state.collectAsState()
        val adult by PresentationAccess.settings.adult
        val editorPicksTitle = stringResource(R.string.home_editor_picks)
        val translatedTitle = stringResource(R.string.tab_home_recentlyupdate_tranlated)
        val originalTitle = stringResource(R.string.tab_home_recentlyupdate_original)
        val translatedAdultTitle = stringResource(R.string.tab_home_recentlyupdate_tranlated_r18)
        val originalAdultTitle = stringResource(R.string.tab_home_recentlyupdate_original_r18)
        val browseMoreLabel = stringResource(R.string.home_browse_more)
        val emptyCollectionTitle = stringResource(R.string.home_collection_empty_title)
        val emptyCollectionMessage = stringResource(R.string.home_collection_empty_message)

        val navPadding = LocalFloatingNavPadding.current
        val layoutDirection = LocalLayoutDirection.current

        DiscoveryScaffold(
            title = stringResource(R.string.home_discover),
            onRefresh = model::reload
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
                contentPadding = PaddingValues(
                    start = 16.dp + navPadding.calculateStartPadding(layoutDirection),
                    end = 16.dp,
                    top = AppSpacing.sm,
                    bottom = AppSpacing.sm + navPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                item(key = "home-actions", contentType = "home-actions") {
                    HomeActions(
                        onSearch = { navigator?.pushIfNotCurrent(SearchTab) },
                        onCategories = { navigator?.pushIfNotCurrent(CategoryBrowserPage()) },
                        onForum = { navigator?.pushIfNotCurrent(ForumPage) },
                        onGuestbook = { navigator?.pushIfNotCurrent(GuestbookPage) }
                    )
                }
                when (val snapshot = state) {
                    HomeTabModel.State.Loading -> item(key = "home-loading", contentType = "loading") {
                        DiscoveryLoadingState()
                    }
                    is HomeTabModel.State.Error -> item(key = "home-error", contentType = "error") {
                        Column {
                            if (snapshot.failure == LoadFailureKind.NETWORK) {
                                DiscoveryOfflineBanner(modifier = Modifier.padding(bottom = 8.dp))
                            }
                            DiscoveryErrorState(
                                message = stringResource(failureMessage(snapshot.failure)),
                                onRetry = model::reload
                            )
                        }
                    }
                    is HomeTabModel.State.Result -> {
                        homeCollection(
                            title = editorPicksTitle,
                            novels = snapshot.homeData.recommendation,
                            onMore = null,
                            adult = adult,
                            navigator = navigator,
                            browseMoreLabel = browseMoreLabel,
                            emptyTitle = emptyCollectionTitle,
                            emptyMessage = emptyCollectionMessage
                        )
                        homeCollection(
                            title = translatedTitle,
                            novels = snapshot.homeData.recentlyUpdateTranslated,
                            onMore = { navigator?.pushIfNotCurrent(NovelListPage(1, 1, false)) },
                            adult = adult,
                            navigator = navigator,
                            browseMoreLabel = browseMoreLabel,
                            emptyTitle = emptyCollectionTitle,
                            emptyMessage = emptyCollectionMessage
                        )
                        homeCollection(
                            title = originalTitle,
                            novels = snapshot.homeData.recentlyUpdateOriginal,
                            onMore = { navigator?.pushIfNotCurrent(NovelListPage(2, 1, false)) },
                            adult = adult,
                            navigator = navigator,
                            browseMoreLabel = browseMoreLabel,
                            emptyTitle = emptyCollectionTitle,
                            emptyMessage = emptyCollectionMessage
                        )
                        if (adult) {
                            homeCollection(
                                title = translatedAdultTitle,
                                novels = snapshot.homeData.recentlyUpdateTranslatedR18,
                                onMore = { navigator?.pushIfNotCurrent(NovelListPage(1, 1, true)) },
                                adult = true,
                                navigator = navigator,
                                browseMoreLabel = browseMoreLabel,
                                emptyTitle = emptyCollectionTitle,
                                emptyMessage = emptyCollectionMessage
                            )
                            homeCollection(
                                title = originalAdultTitle,
                                novels = snapshot.homeData.recentlyUpdateOriginalR18,
                                onMore = { navigator?.pushIfNotCurrent(NovelListPage(2, 1, true)) },
                                adult = true,
                                navigator = navigator,
                                browseMoreLabel = browseMoreLabel,
                                emptyTitle = emptyCollectionTitle,
                                emptyMessage = emptyCollectionMessage
                            )
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) { model.getHomeData() }
    }
}

@Composable
private fun HomeActions(
    onSearch: () -> Unit,
    onCategories: () -> Unit,
    onForum: () -> Unit,
    onGuestbook: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        HomeAction(stringResource(R.string.search_action), Icons.Filled.Search, onSearch)
        HomeAction(stringResource(R.string.categories), Icons.Filled.Category, onCategories)
        HomeAction(stringResource(R.string.forum), Icons.Filled.Forum, onForum)
        HomeAction(stringResource(R.string.guestbook), Icons.Filled.Forum, onGuestbook)
    }
}

@Composable
private fun HomeAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Icon(icon, contentDescription = null)
    }
}

private const val HOME_COLLECTION_MAX_ITEMS = 16

private fun LazyListScope.homeCollection(
    title: String,
    novels: List<CoveredNovel>,
    onMore: (() -> Unit)?,
    adult: Boolean,
    navigator: AppNavigator?,
    browseMoreLabel: String,
    emptyTitle: String,
    emptyMessage: String
) {
    val visible = novels
        .asSequence()
        .filter { adult || !it.isAdult }
        .distinctBy { it.url.trim().ifBlank { it.name.trim() } }
        .take(HOME_COLLECTION_MAX_ITEMS)
        .toList()

    item(key = "home-section-$title", contentType = "home-section") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (onMore != null) {
                TextButton(onClick = onMore) { Text(browseMoreLabel) }
            }
        }
    }
    if (visible.isEmpty()) {
        item(key = "home-empty-$title", contentType = "empty") {
            DiscoveryEmptyState(
                title = emptyTitle,
                message = emptyMessage
            )
        }
    } else {
        item(key = "home-rail-$title", contentType = "home-rail") {
            LazyRow(
                contentPadding = PaddingValues(end = AppSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = title
                    }
            ) {
                items(
                    items = visible,
                    key = { novel -> "home-novel-${novel.url.trim().ifBlank { novel.name.trim() }}" },
                    contentType = { "home-portrait-novel" }
                ) { novel ->
                    AppHomeNovelTile(
                        novel = novel,
                        onClick = { navigator?.pushIfNotCurrent(NovelPage(novel)) }
                    )
                }
            }
        }
    }
}

private fun failureMessage(failure: LoadFailureKind): Int = when (failure) {
    LoadFailureKind.NETWORK -> R.string.load_network_error
    LoadFailureKind.CLIENT -> R.string.load_client_error
}

class HomeTabModel(
    private val authorization: Authorization
) : AppStateViewModel<HomeTabModel.State>(State.Loading) {

    private var loadStarted = false

    sealed class State {
        data object Loading : State()
        data class Error(val failure: LoadFailureKind) : State()
        data class Result(val homeData: HomeData) : State()
    }

    fun getHomeData() {
        if (loadStarted) return
        loadStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = State.Loading
            try {
                val data = PresentationAccess.client.getHomeData(authorization)
                ensureActive()
                mutableState.value = State.Result(data)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = State.Error(e.loadFailureKind())
                loadStarted = false
                com.breakyuna.esjzone.util.AppLogger.e("HomeTabModel", "Failed to load home data", e)
            }
        }
    }

    fun reload() {
        loadStarted = false
        getHomeData()
    }
}

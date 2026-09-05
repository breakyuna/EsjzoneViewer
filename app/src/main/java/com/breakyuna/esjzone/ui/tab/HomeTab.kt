package com.breakyuna.esjzone.ui.tab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.breakyuna.esjzone.GlobalSettings
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.features.getHomeData
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.novellibrary.data.HomeData
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.component.QuietEmptyState
import com.breakyuna.esjzone.ui.component.QuietErrorState
import com.breakyuna.esjzone.ui.component.QuietHomeHeader
import com.breakyuna.esjzone.ui.component.QuietLoadingState
import com.breakyuna.esjzone.ui.component.QuietSectionHeader
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent
import com.breakyuna.esjzone.ui.page.ForumPage
import com.breakyuna.esjzone.ui.page.GuestbookPage
import com.breakyuna.esjzone.ui.page.NovelListPage
import com.breakyuna.esjzone.ui.theme.QuietEditorial
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

object HomeTab : Tab {

    private fun readResolve(): Any = HomeTab

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 0u,
            title = stringResource(R.string.screen_main_tab_home),
            icon = rememberVectorPainter(image = Icons.Filled.Home)
        )

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val model = rememberScreenModel { HomeTabModel(authorization) }
        val state by model.state.collectAsState()
        val adult by GlobalSettings.adult

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = QuietEditorial.contentMaxWidth),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                QuietHomeHeader(
                    domain = GlobalSettings.domain.value,
                    onSearch = { navigator?.pushIfNotCurrent(SearchTab) },
                    onCategories = { navigator?.pushIfNotCurrent(CategoryBrowserPage()) },
                    onForum = { navigator?.pushIfNotCurrent(ForumPage) },
                    onGuestbook = { navigator?.pushIfNotCurrent(GuestbookPage) }
                )

                when (val snapshot = state) {
                    HomeTabModel.State.Loading -> QuietLoadingState(
                        modifier = Modifier.padding(horizontal = QuietEditorial.pagePadding)
                    )

                    is HomeTabModel.State.Error -> QuietErrorState(
                        failure = snapshot.failure,
                        onRetry = model::retry,
                        modifier = Modifier.padding(horizontal = QuietEditorial.pagePadding)
                    )

                    is HomeTabModel.State.Result -> {
                        HomeCollection(
                            title = stringResource(R.string.home_editor_picks),
                            novels = snapshot.homeData.recommendation,
                            accent = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            featured = true,
                            onMore = null
                        )
                        HomeCollection(
                            title = stringResource(R.string.tab_home_recentlyupdate_tranlated),
                            novels = snapshot.homeData.recentlyUpdateTranslated,
                            accent = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            onMore = {
                                navigator?.pushIfNotCurrent(NovelListPage(1, 1, false))
                            }
                        )
                        HomeCollection(
                            title = stringResource(R.string.tab_home_recentlyupdate_original),
                            novels = snapshot.homeData.recentlyUpdateOriginal,
                            accent = androidx.compose.material3.MaterialTheme.colorScheme.tertiary,
                            onMore = {
                                navigator?.pushIfNotCurrent(NovelListPage(2, 1, false))
                            }
                        )

                        if (adult) {
                            HomeCollection(
                                title = stringResource(R.string.tab_home_recentlyupdate_tranlated_r18),
                                novels = snapshot.homeData.recentlyUpdateTranslatedR18,
                                accent = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                onMore = {
                                    navigator?.pushIfNotCurrent(NovelListPage(1, 1, true))
                                }
                            )
                            HomeCollection(
                                title = stringResource(R.string.tab_home_recentlyupdate_original_r18),
                                novels = snapshot.homeData.recentlyUpdateOriginalR18,
                                accent = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                onMore = {
                                    navigator?.pushIfNotCurrent(NovelListPage(2, 1, true))
                                }
                            )
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            model.getHomeData()
        }
    }
}

/** Keeps collection filtering and navigation in the presentation boundary. */
@Composable
private fun HomeCollection(
    title: String,
    novels: List<CoveredNovel>,
    accent: Color,
    featured: Boolean = false,
    onMore: (() -> Unit)?
) {
    val adult by GlobalSettings.adult
    val visible = remember(novels, adult) {
        novels
            .asSequence()
            .filter { adult || !it.isAdult }
            .distinctBy { it.url.ifBlank { it.name } }
            .take(4)
            .toList()
    }

    QuietSectionHeader(
        title = title,
        accent = accent,
        actionLabel = stringResource(R.string.home_browse_more).takeIf { onMore != null },
        onAction = onMore,
        // Keep the featured section breathing room, but tighten the gap
        // between subsequent home collections to avoid a large blank band.
        modifier = Modifier.padding(top = if (featured) 8.dp else 24.dp)
    )

    if (visible.isEmpty()) {
        QuietEmptyState(
            title = stringResource(R.string.home_collection_empty_title),
            message = stringResource(R.string.home_collection_empty_message),
            modifier = Modifier.padding(horizontal = QuietEditorial.pagePadding)
        )
    } else {
        HomePreviewRail(
            novels = visible,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

class HomeTabModel(
    private val authorization: Authorization
) : StateScreenModel<HomeTabModel.State>(State.Loading) {

    private var loadStarted = false

    sealed class State {
        data object Loading : State()
        data class Error(val failure: LoadFailureKind) : State()
        data class Result(val homeData: HomeData) : State()
    }

    fun getHomeData() {
        if (loadStarted) return
        loadStarted = true
        screenModelScope.launch(Dispatchers.IO) {
            mutableState.value = State.Loading
            try {
                val data = EsjzoneClient.getHomeData(authorization)
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

    fun retry() {
        loadStarted = false
        getHomeData()
    }
}

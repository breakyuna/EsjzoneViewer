@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.breakyuna.esjzone.ui.tab

import com.breakyuna.esjzone.ui.designsystem.wideContentWidth
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import com.breakyuna.esjzone.app.CoverLoadingPolicy
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.database.BookshelfCoverStore
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.novellibrary.community.ForumTopic
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.discovery.DiscoveryErrorState
import com.breakyuna.esjzone.ui.discovery.DiscoveryOfflineBanner
import com.breakyuna.esjzone.ui.discovery.DiscoveryScaffold
import com.breakyuna.esjzone.ui.navigation.AppTab
import com.breakyuna.esjzone.ui.navigation.AppTabOptions
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.LocalFloatingNavPadding
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import com.breakyuna.esjzone.ui.page.ForumPage
import com.breakyuna.esjzone.ui.page.ForumPostPage
import com.breakyuna.esjzone.ui.page.GuestbookPage
import com.breakyuna.esjzone.ui.page.NovelListPage
import com.breakyuna.esjzone.ui.page.NovelPage
import kotlinx.coroutines.flow.collect

object HomeTab : AppTab {

    private fun readResolve(): Any = HomeTab

    override val options: AppTabOptions
        @Composable
        get() = AppTabOptions(
            index = 0,
            title = stringResource(R.string.screen_main_tab_home),
            icon = rememberVectorPainter(image = Icons.Filled.Home)
        )

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    override fun Content() {
        val homeColumns = homeGridColumnCount()
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val model = rememberAppViewModel { HomeTabModel(authorization) }
        val state by model.state.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
        val randomState by model.randomRecommendations.collectAsStateWithLifecycle()
        val adult by PresentationAccess.settings.adult
        val hideHomeRecommendations by PresentationAccess.settings.hideHomeRecommendations
        val editorPicksTitle = stringResource(R.string.home_editor_picks)
        val translatedTitle = stringResource(R.string.tab_home_recentlyupdate_tranlated)
        val originalTitle = stringResource(R.string.tab_home_recentlyupdate_original)
        val translatedAdultTitle = stringResource(R.string.tab_home_recentlyupdate_tranlated_r18)
        val originalAdultTitle = stringResource(R.string.tab_home_recentlyupdate_original_r18)
        val browseMoreLabel = stringResource(R.string.home_browse_more)
        val emptyCollectionTitle = stringResource(R.string.home_collection_empty_title)
        val emptyCollectionMessage = stringResource(R.string.home_collection_empty_message)
        val waterCoolerTitle = stringResource(R.string.home_water_cooler)
        val randomRecommendationsTitle = stringResource(R.string.home_random_recommendations)
        val changeBatchLabel = stringResource(R.string.home_random_change_batch)
        val collapseLabel = stringResource(R.string.home_random_collapse)
        val pullToLoadLabel = stringResource(R.string.home_random_pull_to_load)

        val navPadding = LocalFloatingNavPadding.current
        val layoutDirection = LocalLayoutDirection.current
        val listState = rememberLazyListState()

        val onNovelClick: (CoveredNovel) -> Unit = remember(navigator) {
            { novel -> navigator?.pushIfNotCurrent(NovelPage(novel)) }
        }
        val onForum: () -> Unit = { navigator?.pushIfNotCurrent(ForumPage) }
        val onGuestbook: () -> Unit = { navigator?.pushIfNotCurrent(GuestbookPage) }
        val onWaterCooler: () -> Unit = {
            navigator?.pushIfNotCurrent(
                ForumPostPage(
                    ForumTopic(
                        boardId = EsjzoneUrls.WATER_COOLER_BOARD_ID,
                        id = EsjzoneUrls.WATER_COOLER_TOPIC_ID,
                        title = waterCoolerTitle,
                        author = null,
                        createdAt = null,
                        replyCount = null,
                        viewCount = null,
                        lastReplyAt = null,
                        url = EsjzoneUrls.WaterCooler
                    )
                )
            )
        }

        val weeklyDays = remember(state) {
            (state as? HomeTabModel.State.Result)?.homeData?.weeklyUpdates
                .orEmpty().sortedByDescending { it.date }
        }
        val weeklyDayKeys = remember(weeklyDays) {
            weeklyDays.map { it.date.toString() }
        }
        var selectedWeeklyDate by rememberSaveable(weeklyDayKeys) {
            mutableStateOf(weeklyDayKeys.firstOrNull().orEmpty())
        }
        val weeklyIndex = weeklyDays.indexOfFirst { it.date.toString() == selectedWeeklyDate }
            .takeIf { it >= 0 } ?: 0
        val weeklyNovelsByDay = remember(weeklyDays, adult) {
            weeklyDays.map { day ->
                day.novels.asSequence()
                    .filter { adult || !it.isAdult }
                    .distinctBy { it.url.trim().ifBlank { it.name.trim() } }
                    .take(WEEKLY_UPDATE_MAX_ITEMS)
                    .toList()
            }
        }
        val homeData = (state as? HomeTabModel.State.Result)?.homeData
        val coverPolicy by CoverLoadingPolicy.state.collectAsStateWithLifecycle()
        val heroVisible by remember { derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.key == "home-weekly-popular" }
        } }
        // Resolve layout-derived state in composition, before LazyColumn builds its item
        // provider. Reading it inside the content builder makes the provider depend on
        // its own measurement result and can loop in DerivedSnapshotState during startup.
        val popularEnrichmentEnabled = heroVisible && coverPolicy.visiblePage == "HOME" &&
            (state as? HomeTabModel.State.Result)?.isSyncing == false

        val weeklyPopularNovels = remember(homeData?.weeklyPopular, adult) {
            homeData?.weeklyPopular.orEmpty().filter { adult || !it.isAdult }
        }
        val editorPicksRows = remember(homeData?.recommendation, adult, homeColumns) {
            prepareHomeSectionRows(homeData?.recommendation.orEmpty(), adult, homeColumns)
        }
        val translatedRows = remember(homeData?.recentlyUpdateTranslated, adult, homeColumns) {
            prepareHomeSectionRows(homeData?.recentlyUpdateTranslated.orEmpty(), adult, homeColumns)
        }
        val originalRows = remember(homeData?.recentlyUpdateOriginal, adult, homeColumns) {
            prepareHomeSectionRows(homeData?.recentlyUpdateOriginal.orEmpty(), adult, homeColumns)
        }
        val translatedAdultRows = remember(homeData?.recentlyUpdateTranslatedR18, adult, homeColumns) {
            if (adult) prepareHomeSectionRows(homeData?.recentlyUpdateTranslatedR18.orEmpty(), true, homeColumns)
            else emptyList()
        }
        val originalAdultRows = remember(homeData?.recentlyUpdateOriginalR18, adult, homeColumns) {
            if (adult) prepareHomeSectionRows(homeData?.recentlyUpdateOriginalR18.orEmpty(), true, homeColumns)
            else emptyList()
        }

        val homeCoverRows = remember(
            weeklyPopularNovels, editorPicksRows, translatedRows, originalRows,
            translatedAdultRows, originalAdultRows, hideHomeRecommendations,
            weeklyNovelsByDay, weeklyIndex, randomState.items, adult, homeColumns,
            editorPicksTitle, translatedTitle, originalTitle, translatedAdultTitle, originalAdultTitle
        ) {
            fun covers(novels: List<CoveredNovel>) = novels.map { EsjzoneUrls.coverOrEmpty(it.coverUrl) }
            buildList {
                if (weeklyPopularNovels.isNotEmpty()) add(HomeCoverRow("home-weekly-popular", covers(weeklyPopularNovels.take(2))))
                add(HomeCoverRow("home-actions"))
                fun collection(title: String, rows: List<List<CoveredNovel>>) {
                    add(HomeCoverRow("home-section-$title"))
                    if (rows.isEmpty()) add(HomeCoverRow("home-empty-$title"))
                    rows.forEach { row -> add(HomeCoverRow(homeGridRowKey(title, row), covers(row))) }
                }
                if (!hideHomeRecommendations) collection(editorPicksTitle, editorPicksRows)
                collection(translatedTitle, translatedRows)
                collection(originalTitle, originalRows)
                if (adult) {
                    collection(translatedAdultTitle, translatedAdultRows)
                    collection(originalAdultTitle, originalAdultRows)
                }
                if (weeklyNovelsByDay.isNotEmpty()) {
                    add(HomeCoverRow("home-weekly-header"))
                    add(HomeCoverRow("home-weekly-content", covers(weeklyNovelsByDay.getOrNull(weeklyIndex).orEmpty())))
                }
                if (randomState.items.isNotEmpty()) {
                    add(HomeCoverRow("home-random-header"))
                    randomState.items.chunked(homeColumns).forEach { row ->
                        add(HomeCoverRow("home-random-row:${novelKey(row.first())}", covers(row)))
                    }
                }
            }
        }
        val latestHomeCoverRows by rememberUpdatedState(homeCoverRows)
        val coverDomain by PresentationAccess.settings.domain
        val refreshing = (state as? HomeTabModel.State.Result)?.isRefreshing == true
        LaunchedEffect(adult, hideHomeRecommendations, coverDomain, refreshing) {
            val successful = mutableSetOf<String>()
            CoverLoadingPolicy.state.map { it.preloadHome to it.visiblePage }.distinctUntilChanged()
                .collectLatest { (allowed, visiblePage) ->
                    if (allowed) {
                        snapshotFlow {
                            upcomingHomeCovers(latestHomeCoverRows, if (visiblePage == "HOME")
                                listState.layoutInfo.visibleItemsInfo.map { it.key.toString() }.toSet() else emptySet())
                        }.collectLatest { urls ->
                            delay(150) // Let visible requests enter the queue first; avoid fling churn.
                            successful += BookshelfCoverStore.preloadHomeCovers(urls.filterNot { it in successful })
                        }
                    }
                }
        }

        val density = LocalDensity.current
        val thresholdPx = remember(density) { with(density) { 40.dp.toPx() } }
        val maxPullPx = remember(density) { with(density) { 120.dp.toPx() } }
        var pullUpOffsetPx by remember { mutableFloatStateOf(0f) }
        var isLoadingFromPull by remember { mutableStateOf(false) }
        var lastAutoLoadSize by remember { mutableStateOf(-1) }
        val isPullingUp by remember {
            derivedStateOf { !randomState.isActivated && pullUpOffsetPx > 0f }
        }

        LaunchedEffect(randomState.isActivated, randomState.isLoading, isLoadingFromPull) {
            if (isLoadingFromPull) {
                if (randomState.isActivated && !randomState.isLoading) {
                    // Let the new content be laid out before transferring the pull offset
                    // into the list's scroll position, keeping the existing content in place.
                    try {
                        withFrameNanos { }
                        withFrameNanos { }
                        listState.scrollBy(pullUpOffsetPx)
                    } finally {
                        // A user drag can cancel scrollBy; it must still finish the pull UI.
                        pullUpOffsetPx = 0f
                        isLoadingFromPull = false
                    }
                }
                return@LaunchedEffect
            }
            if (randomState.isActivated) {
                pullUpOffsetPx = 0f
            } else {
                lastAutoLoadSize = -1
            }
        }

        LaunchedEffect(randomState.isActivated, randomState.items.size, randomState.isLoading,
            randomState.hasMore, randomState.failure, adult, isLoadingFromPull) {
            if (!isLoadingFromPull && randomState.isActivated && randomState.items.isNotEmpty() &&
                randomState.hasMore && !randomState.isLoading && randomState.failure == null) {
                snapshotFlow {
                    val layout = listState.layoutInfo
                    val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                    lastVisible >= (layout.totalItemsCount - 3).coerceAtLeast(0)
                }.collect { nearEnd ->
                    if (nearEnd && lastAutoLoadSize != randomState.items.size) {
                        lastAutoLoadSize = randomState.items.size
                        model.loadMoreRandomRecommendations(adult)
                    }
                }
            }
        }

        LaunchedEffect(listState.isScrollInProgress, isLoadingFromPull) {
            if (!listState.isScrollInProgress && !isLoadingFromPull && pullUpOffsetPx > 0f) {
                Animatable(pullUpOffsetPx).animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) {
                    pullUpOffsetPx = value
                }
            }
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> model.onHomeShown()
                    Lifecycle.Event.ON_PAUSE,
                    Lifecycle.Event.ON_STOP -> {
                        model.onHomeHidden {
                            val layout = listState.layoutInfo
                            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
                            lastVisible < (layout.totalItemsCount - 8).coerceAtLeast(0)
                        }
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        val bottomSwipeConnection = remember(randomState.isActivated, isLoadingFromPull, adult, thresholdPx, maxPullPx) {
            object : NestedScrollConnection {
                // A reversible rubber-band curve: responsive at first, then progressively
                // firmer without hitting a hard stop. 40 dp still takes about 53 dp of drag.
                private fun dragDistance(): Float {
                    val offset = pullUpOffsetPx.coerceIn(0f, maxPullPx - 1f)
                    return offset * maxPullPx / (1.125f * (maxPullPx - offset))
                }

                private fun updatePull(drag: Float) {
                    val resistedDrag = drag * 1.125f
                    pullUpOffsetPx = maxPullPx * resistedDrag / (maxPullPx + resistedDrag)
                }

                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (!randomState.isActivated && !isLoadingFromPull && pullUpOffsetPx > 0f && available.y > 0f) {
                        val drag = dragDistance()
                        val consumedY = available.y.coerceAtMost(drag)
                        updatePull(drag - consumedY)
                        return Offset(0f, consumedY)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (!randomState.isActivated && !isLoadingFromPull && source == NestedScrollSource.UserInput && available.y < 0f) {
                        updatePull(dragDistance() - available.y)
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (!randomState.isActivated && !isLoadingFromPull && pullUpOffsetPx >= thresholdPx) {
                        isLoadingFromPull = true
                        model.activateRandomRecommendations(adult)
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

        DiscoveryScaffold(
            titleContent = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = AppSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
                ) {
                    Text(
                        text = stringResource(R.string.home_discover),
                        style = AppTypography.titleLarge
                    )
                    HomeSearchBar(
                        onClick = { navigator?.pushIfNotCurrent(SearchTab) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = (state as? HomeTabModel.State.Result)?.isRefreshing == true,
                onRefresh = {
                    isLoadingFromPull = false
                    pullUpOffsetPx = 0f
                    model.reload()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .wideContentWidth()
                    .padding(top = padding.calculateTopPadding())
                    .clipToBounds()
            ) {
                if (isPullingUp || isLoadingFromPull) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = navPadding.calculateBottomPadding() + AppSpacing.md)
                            .graphicsLayer {
                                val progress = (pullUpOffsetPx / thresholdPx).coerceIn(0f, 1f)
                                alpha = progress
                                translationY = (1f - progress) * 16.dp.toPx()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingFromPull) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else Text(
                            text = if (pullUpOffsetPx >= thresholdPx) {
                                stringResource(R.string.home_random_release_to_load)
                            } else pullToLoadLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (pullUpOffsetPx >= thresholdPx) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .testTag("home-feed")
                        .fillMaxSize()
                        .nestedScroll(bottomSwipeConnection)
                        .graphicsLayer {
                            translationY = -pullUpOffsetPx
                        },
                    contentPadding = PaddingValues(
                        start = 16.dp + navPadding.calculateStartPadding(layoutDirection),
                        end = 16.dp,
                        top = AppSpacing.sm,
                        bottom = AppSpacing.sm + navPadding.calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    when (val snapshot = state) {
                        HomeTabModel.State.Loading -> {
                            homeLoadingHero()
                            homeActionsItem(onForum, onGuestbook, onWaterCooler)
                            if (!hideHomeRecommendations) homeLoadingCollection(editorPicksTitle)
                            homeLoadingCollection(translatedTitle)
                            homeLoadingCollection(originalTitle)
                            if (adult) {
                                homeLoadingCollection(translatedAdultTitle)
                                homeLoadingCollection(originalAdultTitle)
                            }
                            homeLoadingWeeklyUpdates()
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
                            if (snapshot.refreshFailure != null) {
                                item(key = "home-refresh-failed") {
                                    HomeRetryRow(stringResource(R.string.home_refresh_retained), model::reload)
                                }
                            }
                            weeklyPopularCarousel(
                                novels = weeklyPopularNovels,
                                enrichmentEnabled = popularEnrichmentEnabled,
                                onVisible = model::loadVisiblePopular,
                                onNovelClick = { novel, page ->
                                    navigator?.pushIfNotCurrent(NovelPage(novel), homeCarouselPage = page)
                                }
                            )
                            homeActionsItem(onForum, onGuestbook, onWaterCooler)
                            if (!hideHomeRecommendations) {
                                homeCollection(
                                    title = editorPicksTitle,
                                    rows = editorPicksRows,
                                    showDivider = true,
                                    onMore = null,
                                    onNovelClick = onNovelClick,
                                    browseMoreLabel = browseMoreLabel,
                                    emptyTitle = emptyCollectionTitle,
                                    emptyMessage = emptyCollectionMessage
                                )
                            }
                            homeCollection(
                                title = translatedTitle,
                                rows = translatedRows,
                                showDivider = true,
                                onMore = { navigator?.pushIfNotCurrent(NovelListPage(1, 1, false)) },
                                onNovelClick = onNovelClick,
                                browseMoreLabel = browseMoreLabel,
                                emptyTitle = emptyCollectionTitle,
                                emptyMessage = emptyCollectionMessage
                            )
                            homeCollection(
                                title = originalTitle,
                                rows = originalRows,
                                showDivider = true,
                                onMore = { navigator?.pushIfNotCurrent(NovelListPage(2, 1, false)) },
                                onNovelClick = onNovelClick,
                                browseMoreLabel = browseMoreLabel,
                                emptyTitle = emptyCollectionTitle,
                                emptyMessage = emptyCollectionMessage
                            )
                            if (adult) {
                                homeCollection(
                                    title = translatedAdultTitle,
                                    rows = translatedAdultRows,
                                    showDivider = true,
                                    onMore = { navigator?.pushIfNotCurrent(NovelListPage(1, 1, true)) },
                                    onNovelClick = onNovelClick,
                                    browseMoreLabel = browseMoreLabel,
                                    emptyTitle = emptyCollectionTitle,
                                    emptyMessage = emptyCollectionMessage
                                )
                                homeCollection(
                                    title = originalAdultTitle,
                                    rows = originalAdultRows,
                                    showDivider = true,
                                    onMore = { navigator?.pushIfNotCurrent(NovelListPage(2, 1, true)) },
                                    onNovelClick = onNovelClick,
                                    browseMoreLabel = browseMoreLabel,
                                    emptyTitle = emptyCollectionTitle,
                                    emptyMessage = emptyCollectionMessage
                                )
                            }
                            if (snapshot.homeData.weeklyFailure != null) {
                                item(key = "home-weekly-failed") {
                                    HomeRetryRow(stringResource(R.string.home_weekly_failed), model::retryWeekly,
                                        enabled = !snapshot.isSyncing)
                                }
                            }
                            weeklyUpdatesCollection(
                                days = weeklyDays,
                                selectedIndex = weeklyIndex,
                                novelsByDay = weeklyNovelsByDay,
                                showDivider = true,
                                onSelect = { selectedWeeklyDate = weeklyDays[it].date.toString() },
                                onNovelClick = onNovelClick
                            )
                            if (weeklyDays.isEmpty() && snapshot.isSyncing) {
                                homeLoadingWeeklyUpdates()
                            }
                            randomRecommendationsSection(
                                state = randomState,
                                columns = homeColumns,
                                showInitialLoading = !isLoadingFromPull,
                                title = randomRecommendationsTitle,
                                changeBatchLabel = changeBatchLabel,
                                collapseLabel = collapseLabel,
                                onChangeBatch = { model.replaceRandomRecommendations(adult) },
                                onCollapse = { model.unloadRandomRecommendations(clearDeduplication = false) },
                                onRetry = { model.retryRandomRecommendations(adult) },
                                onNovelClick = onNovelClick
                            )
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) { model.getHomeData() }
        LaunchedEffect(adult) {
            isLoadingFromPull = false
            pullUpOffsetPx = 0f
            model.onRandomAdultModeChanged(adult)
        }
    }
}

private fun LazyListScope.homeActionsItem(
    onForum: () -> Unit,
    onGuestbook: () -> Unit,
    onWaterCooler: () -> Unit
) {
    item(key = "home-actions", contentType = "home-actions") {
        HomeActions(onForum, onGuestbook, onWaterCooler)
    }
}

@Composable
private fun HomeRetryRow(message: String, onRetry: () -> Unit, enabled: Boolean = true) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
    ) {
        Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.material3.TextButton(onClick = onRetry, enabled = enabled) {
            Text(stringResource(R.string.retry))
        }
    }
}

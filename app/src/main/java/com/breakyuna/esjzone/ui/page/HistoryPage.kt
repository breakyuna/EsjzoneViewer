package com.breakyuna.esjzone.ui.page

import androidx.lifecycle.viewModelScope

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import com.breakyuna.esjzone.ui.navigation.LocalFloatingNavPadding
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.FavoriteNovel
import com.breakyuna.esjzone.novellibrary.novel.HistoryNovel
import com.breakyuna.esjzone.ui.component.AppNovelCover
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.ui.navigation.ChapterStateHolder
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppShimmerPlaceholder
import com.breakyuna.esjzone.ui.product.EmptyState
import com.breakyuna.esjzone.ui.product.ErrorState
import com.breakyuna.esjzone.ui.product.OfflineState
import com.breakyuna.esjzone.util.AppLogger
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Local and cloud history are deliberately separate sources and gestures. */
object HistoryPage : AppDestination {
    private fun readResolve(): Any = HistoryPage

    @OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() = Content(showBack = true)

    @OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
    @Composable
    fun Content(showBack: Boolean) {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val localModel = rememberAppViewModel { LocalHistoryPageModel(authorization) }
        val cloudModel = rememberAppViewModel { HistoryPageModel(authorization) }
        val localState by localModel.state.collectAsState()
        val cloudState by cloudModel.state.collectAsState()
        var selectedPage by rememberSaveable { mutableIntStateOf(0) }
        var searchOpen by rememberSaveable { mutableStateOf(false) }
        var query by rememberSaveable { mutableStateOf("") }
        val pager = rememberPagerState(initialPage = 0, pageCount = { 2 })

        LaunchedEffect(Unit) { localModel.observe() }
        LaunchedEffect(selectedPage) {
            if (pager.currentPage != selectedPage) pager.animateScrollToPage(selectedPage)
            if (selectedPage == 1) cloudModel.getNovels()
        }
        LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { selectedPage = it } }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.history), style = AppTypography.titleLarge) },
                    navigationIcon = { if (showBack) BackIconButton { navigator?.pop() } },
                    actions = {
                        IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }) {
                            Icon(if (searchOpen) Icons.Filled.Close else Icons.Filled.Search, stringResource(R.string.history_search))
                        }
                        if (selectedPage == 0) IconButton(onClick = localModel::clear) {
                            Icon(Icons.Filled.DeleteSweep, stringResource(R.string.history_local_clear))
                        }
                    }
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                if (searchOpen) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.history_search_placeholder)) },
                        leadingIcon = { Icon(Icons.Filled.Search, null) }
                    )
                }
                TabRow(selectedTabIndex = selectedPage) {
                    Tab(selected = selectedPage == 0, onClick = { selectedPage = 0 }, text = { Text(stringResource(R.string.history_local)) }, icon = { Icon(Icons.Filled.AutoStories, null) })
                    Tab(selected = selectedPage == 1, onClick = { selectedPage = 1 }, text = { Text(stringResource(R.string.history_cloud)) }, icon = { Icon(Icons.Filled.CloudSync, null) })
                }
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    if (page == 0) {
                        LocalHistoryContent(localState, query, localModel, navigator)
                    } else {
                        CloudHistoryContent(cloudState, query, cloudModel, authorization, navigator)
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalHistoryContent(
    state: LocalHistoryPageModel.State,
    query: String,
    model: LocalHistoryPageModel,
    navigator: com.breakyuna.esjzone.ui.navigation.AppNavigator?
) {
    when (val current = state) {
        LocalHistoryPageModel.State.Loading -> HistoryListSkeleton()
        is LocalHistoryPageModel.State.Error -> ErrorState(
            title = stringResource(R.string.load_client_error),
            message = stringResource(R.string.history_local_load_failed),
            onRetry = model::retry,
            modifier = Modifier.fillMaxSize()
        )
        is LocalHistoryPageModel.State.Result -> {
            val rows = current.activities.filter { it.novelName.contains(query, true) || it.chapterName.contains(query, true) || query.isBlank() }
            if (rows.isEmpty()) {
                EmptyState(
                    title = stringResource(if (query.isBlank()) R.string.history_local_empty else R.string.history_search_empty),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val navPadding = LocalFloatingNavPadding.current
                val layoutDirection = LocalLayoutDirection.current
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = AppSpacing.lg + navPadding.calculateStartPadding(layoutDirection),
                        end = AppSpacing.lg,
                        top = AppSpacing.lg,
                        bottom = AppSpacing.lg + navPadding.calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    items(rows, key = { it.activityId }, contentType = { "history" }) { activity ->
                        LocalHistoryCard(
                            activity = activity,
                            coverUrl = model.coverUrlFor(activity),
                            onOpen = {
                                val chapter = Chapter(activity.chapterName, activity.chapterUrl, true)
                                navigator?.pushIfNotCurrent(ChapterPage(
                                    novelId = activity.novelId.ifBlank { chapter.novelId() },
                                    chapter = chapter,
                                    history = ChapterStateHolder(chapter),
                                    novelName = activity.novelName,
                                    novelUrl = activity.novelUrl,
                                    novelCoverUrl = activity.novelCoverUrl
                                ))
                            },
                            onCoverNeeded = { model.loadCover(activity) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalHistoryCard(
    activity: LocalReadingActivity,
    coverUrl: String,
    onOpen: () -> Unit,
    onCoverNeeded: () -> Unit
) {
    if (coverUrl.isBlank()) LaunchedEffect(activity.activityId) { onCoverNeeded() }
    val progress = (fullBookProgress(activity.chapterIndex, activity.totalChapters, activity.chapterProgress) * 100).roundToInt()
    val position = if (activity.chapterIndex >= 0 && activity.totalChapters > 0) {
        stringResource(R.string.history_local_position, activity.chapterIndex + 1, activity.totalChapters, progress)
    } else stringResource(R.string.history_local_percent, progress)
    val relative = DateUtils.getRelativeTimeSpanString(activity.lastReadAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .semantics { role = Role.Button }
    ) {
        Row(Modifier.fillMaxWidth().padding(AppSpacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            AppNovelCover(
                coverUrl = coverUrl,
                title = activity.novelName,
                modifier = Modifier.size(width = 64.dp, height = 88.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text(activity.novelName.ifBlank { activity.novelId }, style = AppTypography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(activity.chapterName, style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                androidx.compose.material3.LinearProgressIndicator(progress = fullBookProgress(activity.chapterIndex, activity.totalChapters, activity.chapterProgress), modifier = Modifier.fillMaxWidth())
                Text(position, style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.history_local_meta, relative, localDurationText(activity.durationMs)), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CloudHistoryContent(
    state: HistoryPageModel.State,
    query: String,
    model: HistoryPageModel,
    authorization: Authorization,
    navigator: com.breakyuna.esjzone.ui.navigation.AppNavigator?
) {
    when (state) {
        HistoryPageModel.State.Loading -> HistoryListSkeleton()
        is HistoryPageModel.State.Error -> if (state.failure == LoadFailureKind.NETWORK) {
            OfflineState(
                title = stringResource(R.string.load_network_error),
                message = stringResource(R.string.history_cloud_separate),
                onRetry = model::reload,
                modifier = Modifier.fillMaxSize()
            )
        } else ErrorState(
            title = stringResource(R.string.history_load_failed),
            message = stringResource(R.string.history_cloud_separate),
            onRetry = model::reload,
            modifier = Modifier.fillMaxSize()
        )
        is HistoryPageModel.State.Result -> {
            val rows = state.historyNovels.distinctBy { it.url.ifBlank { it.name } }.filter { it.name.contains(query, true) || it.chapter.name.contains(query, true) || query.isBlank() }
            val detailLoader = rememberAppViewModel { NovelDetailLoader(authorization) }
            if (rows.isEmpty()) {
                EmptyState(stringResource(if (query.isBlank()) R.string.history_cloud_empty else R.string.history_search_empty), stringResource(R.string.history_cloud_separate), Modifier.fillMaxSize())
            } else {
                val navPadding = LocalFloatingNavPadding.current
                val layoutDirection = LocalLayoutDirection.current
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = AppSpacing.lg + navPadding.calculateStartPadding(layoutDirection),
                        end = AppSpacing.lg,
                        top = AppSpacing.lg,
                        bottom = AppSpacing.lg + navPadding.calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    items(rows, key = { "cloud-history:${detailLoader.key(it)}" }, contentType = { "history" }) { history ->
                        val key = detailLoader.key(history)
                        val detail = detailLoader.details[key]
                        if (detail == null) {
                            LaunchedEffect(key) { detailLoader.load(history) }
                            if (detailLoader.failures[key] != null) {
                                ErrorState(
                                    title = stringResource(R.string.history_load_failed),
                                    message = stringResource(R.string.load_network_error),
                                    onRetry = { detailLoader.retry(history) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else HistoryItemSkeleton()
                        } else if (PresentationAccess.settings.adult.value || !detail.isAdult) {
                            CloudHistoryCard(
                                history = history,
                                coverUrl = detail.coverUrl,
                                chapterIndex = detail.chapterList.orderedChapters.indexOfFirst {
                                    EsjzoneUrls.canonicalPageKey(it.url) == EsjzoneUrls.canonicalPageKey(history.chapter.url)
                                },
                                totalChapters = detail.chapterList.orderedChapters.size,
                                onOpen = { navigator?.pushIfNotCurrent(NovelPage(history, history = ChapterStateHolder(history.chapter))) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudHistoryCard(
    history: HistoryNovel,
    coverUrl: String,
    chapterIndex: Int,
    totalChapters: Int,
    onOpen: () -> Unit
) {
    val progress = fullBookProgress(chapterIndex, totalChapters, 1f)
    val progressPercent = (progress * 100).roundToInt()
    val position = if (chapterIndex >= 0 && totalChapters > 0) {
        stringResource(R.string.history_local_position, chapterIndex + 1, totalChapters, progressPercent)
    } else {
        stringResource(R.string.history_local_percent, progressPercent)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .semantics { role = Role.Button }
            .padding(vertical = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        AppNovelCover(
            coverUrl = coverUrl,
            title = history.name,
            modifier = Modifier.size(width = 64.dp, height = 88.dp)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(history.name, style = AppTypography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(history.chapter.name, style = AppTypography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            androidx.compose.material3.LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
            Text(position, style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryListSkeleton(modifier: Modifier = Modifier) {
    val navPadding = LocalFloatingNavPadding.current
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppSpacing.lg + navPadding.calculateStartPadding(layoutDirection),
            end = AppSpacing.lg,
            top = AppSpacing.lg,
            bottom = AppSpacing.lg + navPadding.calculateBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        userScrollEnabled = false
    ) {
        items(6) {
            HistoryItemSkeleton()
        }
    }
}

@Composable
private fun HistoryItemSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        AppShimmerPlaceholder(
            modifier = Modifier.size(width = 64.dp, height = 88.dp),
            shape = AppShapes.compact
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            AppShimmerPlaceholder(
                modifier = Modifier.fillMaxWidth(0.72f).height(18.dp),
                shape = AppShapes.compact
            )
            AppShimmerPlaceholder(
                modifier = Modifier.fillMaxWidth(0.48f).height(14.dp),
                shape = AppShapes.compact
            )
            AppShimmerPlaceholder(
                modifier = Modifier.fillMaxWidth().height(4.dp),
                shape = AppShapes.compact
            )
            AppShimmerPlaceholder(
                modifier = Modifier.fillMaxWidth(0.32f).height(12.dp),
                shape = AppShapes.compact
            )
        }
    }
}

/** Maps a chapter-local fraction to the fraction of the complete book. */
internal fun fullBookProgress(chapterIndex: Int, totalChapters: Int, chapterProgress: Float): Float {
    val fraction = chapterProgress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    if (chapterIndex < 0 || totalChapters <= 0) return fraction
    return ((chapterIndex + fraction) / totalChapters.toFloat()).coerceIn(0f, 1f)
}

private fun List<HistoryNovel>.filterByHistoryQuery(query: String): List<HistoryNovel> = filter { query.isBlank() || it.name.contains(query, true) || it.chapter.name.contains(query, true) }

@Composable
private fun localDurationText(durationMs: Long): String {
    val minutes = durationMs.coerceAtLeast(0) / 60_000
    return when {
        minutes >= 60 -> stringResource(R.string.history_local_duration_hours, minutes / 60, minutes % 60)
        minutes > 0 -> stringResource(R.string.history_local_duration_minutes, minutes)
        else -> stringResource(R.string.history_local_duration_seconds, durationMs.coerceAtLeast(0) / 1_000)
    }
}

class LocalHistoryPageModel(private val authorization: Authorization) : AppStateViewModel<LocalHistoryPageModel.State>(State.Loading) {
    sealed class State {
        data object Loading : State()
        data class Error(val failure: LoadFailureKind) : State()
        data class Result(val activities: List<LocalReadingActivity>) : State()
    }

    private var observeStarted = false
    private var observeJob: Job? = null
    private val coverLock = Any()
    private val requestedCovers = mutableSetOf<String>()
    private val resolvedCovers = mutableStateOf<Map<String, String>>(emptyMap())

    fun observe() {
        if (observeStarted) return
        observeStarted = true
        observeJob?.cancel()
        observeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                PresentationAccess.database.localReadingActivityDao().observeAll().collect { mutableState.value = State.Result(it) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { observeStarted = false; mutableState.value = State.Error(e.loadFailureKind()); AppLogger.e("LocalHistoryPageModel", "Failed to load local history", e) }
        }
    }

    fun retry() { observeStarted = false; mutableState.value = State.Loading; observe() }

    fun clear() { viewModelScope.launch(Dispatchers.IO) { runCatching { PresentationAccess.database.localReadingActivityDao().deleteAll() }.onFailure { AppLogger.e("LocalHistoryPageModel", "Failed to clear local history", it) } } }

    fun coverUrlFor(activity: LocalReadingActivity): String {
        val stored = EsjzoneUrls.coverOrEmpty(activity.novelCoverUrl)
        if (stored.isNotBlank()) return stored
        return resolvedCovers.value[coverKey(coverLookupUrl(activity))].orEmpty()
    }

    fun loadCover(activity: LocalReadingActivity) {
        if (EsjzoneUrls.coverOrEmpty(activity.novelCoverUrl).isNotBlank()) return
        val target = coverLookupUrl(activity)
        val key = coverKey(target)
        if (key.isBlank() || !synchronized(coverLock) { requestedCovers.add(key) }) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cover = EsjzoneUrls.coverOrEmpty(PresentationAccess.client.getNovelDetail(authorization, FavoriteNovel(activity.novelName, target)).coverUrl)
                if (cover.isNotBlank()) {
                    resolvedCovers.value = resolvedCovers.value + (key to cover)
                    runCatching { PresentationAccess.database.localReadingActivityDao().updateCover(activity.activityId, cover) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { AppLogger.w("LocalHistoryPageModel", "Failed to recover local history cover", e) }
        }
    }

    private fun coverLookupUrl(activity: LocalReadingActivity): String = activity.novelUrl.trim().ifBlank { activity.novelId.trim().takeIf(String::isNotBlank)?.let { "/detail/$it.html" }.orEmpty() }
    private fun coverKey(url: String): String = EsjzoneUrls.canonicalPageKey(url).ifBlank { url.trim() }
}

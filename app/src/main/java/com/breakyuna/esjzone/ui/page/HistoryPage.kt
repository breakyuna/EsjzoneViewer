package com.breakyuna.esjzone.ui.page

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.breakyuna.esjzone.GlobalSettings
import com.breakyuna.esjzone.MainActivity
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.util.AppLogger
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.FavoriteNovel
import com.breakyuna.esjzone.novellibrary.novel.HistoryNovel
import com.breakyuna.esjzone.ui.component.QuietBackHeader
import com.breakyuna.esjzone.ui.component.QuietEmptyState
import com.breakyuna.esjzone.ui.component.QuietErrorState
import com.breakyuna.esjzone.ui.component.QuietLoadingState
import com.breakyuna.esjzone.ui.component.QuietNotice
import com.breakyuna.esjzone.ui.theme.QuietEditorial
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.ChapterStateHolder
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent

object HistoryPage : Screen {

    private fun readResolve(): Any = HistoryPage

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        Content(showBack = true)
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun Content(showBack: Boolean) {
        val navigator = LocalBaseNavigator.current

        val authorization = LocalAuthorization.current
        val historyPageModel = rememberScreenModel { HistoryPageModel(authorization) }
        val state by historyPageModel.state.collectAsState()
        val deletedIds by historyPageModel.deletedIds.collectAsState()
        val localHistoryPageModel = rememberScreenModel { LocalHistoryPageModel(authorization) }
        val localState by localHistoryPageModel.state.collectAsState()
        var selectedPage by rememberSaveable { mutableIntStateOf(0) }
        var searchVisible by rememberSaveable { mutableStateOf(false) }
        var searchQuery by rememberSaveable { mutableStateOf("") }
        val pagerState = rememberPagerState(
            initialPage = 0,
            pageCount = { 2 }
        )

        LaunchedEffect(selectedPage) {
            if (pagerState.currentPage != selectedPage) {
                pagerState.animateScrollToPage(selectedPage)
            }
        }

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { page ->
                selectedPage = page
            }
        }

        LaunchedEffect(Unit) {
            localHistoryPageModel.observe()
        }

        LaunchedEffect(selectedPage) {
            if (selectedPage == 1) {
                historyPageModel.getNovels()
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            HistoryHeader(
                showBack = showBack,
                searchVisible = searchVisible,
                searchQuery = searchQuery,
                onQueryChange = { searchQuery = it },
                onToggleSearch = {
                    if (searchVisible) searchQuery = ""
                    searchVisible = !searchVisible
                },
                onBack = { navigator?.pop() },
                onClearLocal = { localHistoryPageModel.clear() },
                showClear = selectedPage == 0
            )
            HistorySourceSelector(
                selectedPage = selectedPage,
                localCount = (localState as? LocalHistoryPageModel.State.Result)
                    ?.activities
                    ?.size
                    ?: 0,
                onSelect = { selectedPage = it }
            )
            if (selectedPage == 0) {
                QuietNotice(
                    text = stringResource(R.string.history_local_device_only),
                    modifier = Modifier.padding(
                        horizontal = QuietEditorial.pagePadding,
                        vertical = 8.dp
                    )
                )
            }

            HorizontalPager(
                state = pagerState,
                // Page 0 is local history and page 1 is cloud history. Keep
                // that visual order so local -> cloud is a right swipe.
                reverseLayout = false,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                if (page == 0) {
                    LocalHistoryContent(
                        state = localState,
                        query = searchQuery,
                        onOpen = { activity ->
                            val chapter = Chapter(
                                activity.chapterName,
                                activity.chapterUrl,
                                true
                            )
                            navigator?.pushIfNotCurrent(
                                ChapterPage(
                                    novelId = activity.novelId.ifBlank { chapter.novelId() },
                                    chapter = chapter,
                                    history = ChapterStateHolder(chapter),
                                    novelName = activity.novelName,
                                    novelUrl = activity.novelUrl,
                                    novelCoverUrl = activity.novelCoverUrl
                                )
                            )
                        },
                        onDelete = localHistoryPageModel::delete,
                        onRetry = localHistoryPageModel::retry,
                        coverUrlFor = localHistoryPageModel::coverUrlFor,
                        onCoverNeeded = localHistoryPageModel::loadCover
                    )
                } else {
                    when (state) {
                        is HistoryPageModel.State.Loading -> QuietLoadingState(modifier = Modifier.fillMaxSize())

                        is HistoryPageModel.State.Result -> {
                            val result = state as HistoryPageModel.State.Result

                            val novels = result.historyNovels
                                .distinctBy { it.url.ifBlank { it.name } }
                                .filterByHistoryQuery(searchQuery)

                            val detailLoader = rememberScreenModel { NovelDetailLoader(authorization) }

                            val adult by remember {
                                GlobalSettings.adult
                            }

                            if (novels.isEmpty()) {
                                QuietEmptyState(
                                    title = stringResource(
                                        if (searchQuery.isBlank()) {
                                            R.string.history_cloud_empty
                                        } else {
                                            R.string.history_search_empty
                                        }
                                    ),
                                    message = stringResource(R.string.history_cloud_separate),
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .widthIn(max = QuietEditorial.contentMaxWidth)
                                        .align(Alignment.CenterHorizontally),
                                    contentPadding = PaddingValues(bottom = 16.dp)
                                ) {
                                    items(
                                        novels,
                                        key = { item -> detailLoader.key(item) }
                                    ) { historyNovel ->
                            val detailKey = detailLoader.key(historyNovel)

                            val historyChapter: MutableState<Chapter?> = rememberSaveable {
                                mutableStateOf(historyNovel.chapter)
                            }

                            val novel = detailLoader.details[detailKey]
                            if (novel == null) {
                                if (detailLoader.failures[detailKey] != null) {
                                    QuietErrorState(
                                        onRetry = {
                                            detailLoader.retry(historyNovel)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        failure = detailLoader.failures[detailKey]
                                            ?: LoadFailureKind.CLIENT
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                                    }
                                }

                                LaunchedEffect(detailKey) { detailLoader.load(historyNovel) }
                            } else {
                                if (adult || !novel.isAdult) {
                                    var expanded by remember { mutableStateOf(false) }

                                    if (historyNovel.vid !in deletedIds) {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                        ) {
                                            DropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = {
                                                    expanded = false
                                                }
                                            ) {
                                                DropdownMenuItem(
                                                    onClick = {
                                                        historyPageModel.deleteHistory(
                                                            historyNovel.vid,
                                                            historyNovel.name
                                                        )
                                                        expanded = false
                                                    },
                                                    text = {
                                                        Text(text = stringResource(id = R.string.delete_history))
                                                    }
                                                )
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .combinedClickable(
                                                            onClick = {
                                                                navigator?.pushIfNotCurrent(
                                                                    NovelPage(
                                                                        historyNovel,
                                                                        history = ChapterStateHolder(historyChapter)
                                                                    )
                                                                )
                                                            },
                                                            onLongClick = {
                                                                expanded = true
                                                            }
                                                        ),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(72.dp)
                                                            .height(100.dp)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(MaterialTheme.colorScheme.surface)
                                                    ) {
                                                        SubcomposeAsyncImage(
                                                            model = ImageRequest.Builder(LocalContext.current)
                                                                .data(
                                                                    EsjzoneUrls.coverOrEmpty(novel.coverUrl)
                                                                        .takeIf { it.isNotBlank() }
                                                                        ?: R.drawable.missing_cover
                                                                )
                                                                .crossfade(true)
                                                                .build(),
                                                            contentDescription = historyNovel.name,
                                                            imageLoader = MainActivity.imageLoader,
                                                            loading = {
                                                                CircularProgressIndicator(strokeWidth = 2.dp)
                                                            },
                                                            error = {
                                                                androidx.compose.foundation.Image(
                                                                    painter = androidx.compose.ui.res.painterResource(
                                                                        id = R.drawable.missing_cover
                                                                    ),
                                                                    contentDescription = historyNovel.name,
                                                                    contentScale = ContentScale.Crop,
                                                                    modifier = Modifier.fillMaxSize()
                                                                )
                                                            },
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.width(12.dp))

                                                    Column {
                                                        Text(
                                                            text = novel.name,
                                                            style = MaterialTheme.typography.titleSmall.copy(
                                                                fontWeight = FontWeight.SemiBold
                                                            ),
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        Text(
                                                            text = historyChapter.value?.name ?: "",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                FilledTonalIconButton(
                                                    onClick = {
                                                        historyChapter.value?.let { currChapter ->
                                                            navigator?.pushIfNotCurrent(
                                                                ChapterPage(
                                                                    novel.id(),
                                                                    currChapter,
                                                                    ChapterStateHolder(historyChapter),
                                                                    novel.chapterList.orderedChapters,
                                                                    novelName = novel.name,
                                                                    novelUrl = novel.url,
                                                                    novelCoverUrl = novel.coverUrl
                                                                )
                                                            )
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.PlayArrow,
                                                        contentDescription = stringResource(id = R.string.continue_reading)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
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
                        }

                        is HistoryPageModel.State.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = stringResource(
                                        if ((state as HistoryPageModel.State.Error).failure == LoadFailureKind.NETWORK) {
                                            R.string.load_network_error
                                        } else {
                                            R.string.load_client_error
                                        }
                                    ),
                                    color = MaterialTheme.colorScheme.error
                                )
                                TextButton(onClick = { historyPageModel.reload() }) {
                                    Text(text = stringResource(id = R.string.retry))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun HistoryHeader(
    showBack: Boolean,
    searchVisible: Boolean,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onBack: () -> Unit,
    onClearLocal: () -> Unit,
    showClear: Boolean
) {
    val actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        IconButton(onClick = onToggleSearch) {
            Icon(
                imageVector = if (searchVisible) Icons.Filled.Close else Icons.Filled.Search,
                contentDescription = stringResource(
                    if (searchVisible) R.string.close else R.string.history_search
                )
            )
        }
        if (showClear) {
            IconButton(onClick = onClearLocal) {
                Icon(
                    imageVector = Icons.Filled.DeleteSweep,
                    contentDescription = stringResource(R.string.history_local_clear)
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (showBack) {
            QuietBackHeader(
                title = stringResource(R.string.history),
                onBack = onBack,
                actions = actions
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .padding(
                            start = QuietEditorial.pagePadding,
                            end = QuietEditorial.pagePadding,
                            top = 16.dp,
                            bottom = 10.dp
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.history),
                            style = QuietEditorial.display,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        actions()
                    }
                    Text(
                        text = stringResource(R.string.history_description),
                        style = QuietEditorial.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        if (searchVisible) {
            TextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = QuietEditorial.pagePadding, vertical = 4.dp),
                singleLine = true,
                placeholder = {
                    Text(stringResource(R.string.history_search_placeholder))
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                },
                shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        }
    }
}

@Composable
private fun HistorySourceSelector(
    selectedPage: Int,
    localCount: Int,
    onSelect: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = QuietEditorial.pagePadding, vertical = 4.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HistorySourceItem(
                selected = selectedPage == 0,
                icon = Icons.Filled.AutoStories,
                label = if (localCount > 0) {
                    stringResource(R.string.history_local_count, localCount)
                } else {
                    stringResource(R.string.history_local)
                },
                onClick = { onSelect(0) },
                modifier = Modifier.weight(1f)
            )
            HistorySourceItem(
                selected = selectedPage == 1,
                icon = Icons.Filled.CloudSync,
                label = stringResource(R.string.history_cloud),
                onClick = { onSelect(1) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HistorySourceItem(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.surface
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = QuietEditorial.title,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

private fun List<HistoryNovel>.filterByHistoryQuery(query: String): List<HistoryNovel> {
    val normalized = query.trim()
    if (normalized.isBlank()) return this
    return filter { item ->
        item.name.contains(normalized, ignoreCase = true) ||
            item.chapter.name.contains(normalized, ignoreCase = true)
    }
}

@Composable
private fun LocalHistoryContent(
    state: LocalHistoryPageModel.State,
    query: String,
    onOpen: (LocalReadingActivity) -> Unit,
    onDelete: (String) -> Unit,
    onRetry: () -> Unit,
    coverUrlFor: (LocalReadingActivity) -> String,
    onCoverNeeded: (LocalReadingActivity) -> Unit
) {
    when (val current = state) {
    LocalHistoryPageModel.State.Loading -> QuietLoadingState(modifier = Modifier.fillMaxSize())

        is LocalHistoryPageModel.State.Error -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(
                    if (current.failure == LoadFailureKind.NETWORK) {
                        R.string.load_network_error
                    } else {
                        R.string.load_client_error
                    }
                ),
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = onRetry) {
                Text(text = stringResource(id = R.string.retry))
            }
        }

        is LocalHistoryPageModel.State.Result -> {
            val activities = current.activities.filter { activity ->
                val normalized = query.trim()
                normalized.isBlank() ||
                    activity.novelName.contains(normalized, ignoreCase = true) ||
                    activity.chapterName.contains(normalized, ignoreCase = true)
            }
            if (activities.isEmpty()) {
                QuietEmptyState(
                    title = stringResource(
                        if (query.isBlank()) {
                            R.string.history_local_empty
                        } else {
                            R.string.history_search_empty
                        }
                    ),
                    message = stringResource(R.string.history_local_device_only),
                    icon = Icons.Filled.AutoStories,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .widthIn(max = QuietEditorial.contentMaxWidth)
                            .align(Alignment.CenterHorizontally),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 8.dp,
                            bottom = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = activities,
                            key = { activity -> activity.activityId }
                        ) { activity ->
                            LocalHistoryRow(
                                activity = activity,
                                coverUrl = coverUrlFor(activity),
                                onOpen = { onOpen(activity) },
                                onDelete = { onDelete(activity.activityId) },
                                onCoverNeeded = { onCoverNeeded(activity) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalHistoryRow(
    activity: LocalReadingActivity,
    coverUrl: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onCoverNeeded: () -> Unit
) {
    if (coverUrl.isBlank()) {
        LaunchedEffect(activity.activityId) {
            onCoverNeeded()
        }
    }

    val progress = (activity.chapterProgress.coerceIn(0f, 1f) * 100f).roundToInt()
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        activity.lastReadAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    )
    val position = if (activity.chapterIndex >= 0 && activity.totalChapters > 0) {
        stringResource(
            id = R.string.history_local_position,
            activity.chapterIndex + 1,
            activity.totalChapters,
            progress
        )
    } else {
        stringResource(id = R.string.history_local_percent, progress)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(100.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(
                            EsjzoneUrls.coverOrEmpty(coverUrl)
                                .takeIf { it.isNotBlank() }
                                ?: R.drawable.missing_cover
                        )
                        .crossfade(true)
                        .build(),
                    contentDescription = activity.novelName,
                    imageLoader = MainActivity.imageLoader,
                    loading = {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    },
                    error = {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(
                                id = R.drawable.missing_cover
                            ),
                            contentDescription = activity.novelName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = activity.novelName.ifBlank { activity.novelId },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = activity.chapterName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
                androidx.compose.material3.LinearProgressIndicator(
                    progress = activity.chapterProgress.coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(4.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = position,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 5.dp)
                )
                Text(
                    text = stringResource(
                        id = R.string.history_local_meta,
                        relativeTime,
                        localDurationText(activity.durationMs)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(id = R.string.history_local_delete)
                )
            }
        }
    }
}

@Composable
private fun localDurationText(durationMs: Long): String {
    val totalMinutes = durationMs.coerceAtLeast(0L) / 60_000L
    return if (totalMinutes >= 60L) {
        stringResource(
            id = R.string.history_local_duration_hours,
            totalMinutes / 60L,
            totalMinutes % 60L
        )
    } else if (totalMinutes > 0L) {
        stringResource(id = R.string.history_local_duration_minutes, totalMinutes)
    } else {
        stringResource(
            id = R.string.history_local_duration_seconds,
            durationMs.coerceAtLeast(0L) / 1_000L
        )
    }
}

class LocalHistoryPageModel(
    private val authorization: Authorization
) : StateScreenModel<LocalHistoryPageModel.State>(State.Loading) {

    private var observeJob: Job? = null
    private var observeStarted = false
    private val coverLock = Any()
    private val requestedCoverKeys = mutableSetOf<String>()
    private val resolvedCoverUrls = mutableStateOf<Map<String, String>>(emptyMap())

    sealed class State {
        data object Loading : State()
        data class Error(val failure: LoadFailureKind) : State()
        data class Result(val activities: List<LocalReadingActivity>) : State()
    }

    fun coverUrlFor(activity: LocalReadingActivity): String {
        val storedCover = EsjzoneUrls.coverOrEmpty(activity.novelCoverUrl)
        if (storedCover.isNotBlank()) return storedCover
        return resolvedCoverUrls.value[coverKey(coverLookupUrl(activity))] ?: ""
    }

    fun loadCover(activity: LocalReadingActivity) {
        if (EsjzoneUrls.coverOrEmpty(activity.novelCoverUrl).isNotBlank()) return

        val targetUrl = coverLookupUrl(activity)
        val key = coverKey(targetUrl)
        if (key.isBlank()) return

        val shouldLoad = synchronized(coverLock) {
            if (resolvedCoverUrls.value.containsKey(key) || !requestedCoverKeys.add(key)) {
                false
            } else {
                true
            }
        }
        if (!shouldLoad) return

        screenModelScope.launch(Dispatchers.IO) {
            try {
                val cover = EsjzoneUrls.coverOrEmpty(
                    EsjzoneClient.getNovelDetail(
                        authorization,
                        FavoriteNovel(
                            name = activity.novelName,
                            url = targetUrl
                        )
                    ).coverUrl
                )
                if (cover.isNotBlank()) {
                    synchronized(coverLock) {
                        resolvedCoverUrls.value = resolvedCoverUrls.value + (key to cover)
                    }
                    runCatching {
                        MainActivity.database.localReadingActivityDao().updateCover(
                            activity.activityId,
                            cover
                        )
                    }.onFailure { error ->
                        AppLogger.w(
                            "LocalHistoryPageModel",
                            "Failed to persist recovered novel cover",
                            error
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(
                    "LocalHistoryPageModel",
                    "Failed to recover cover for ${activity.novelName}",
                    e
                )
            }
        }
    }

    private fun coverLookupUrl(activity: LocalReadingActivity): String =
        activity.novelUrl.trim().ifBlank {
            activity.novelId.trim()
                .takeIf { it.isNotBlank() }
                ?.let { "/detail/$it.html" }
                .orEmpty()
        }

    private fun coverKey(url: String): String =
        EsjzoneUrls.canonicalPageKey(url).ifBlank { url.trim() }

    fun observe() {
        if (observeStarted) return
        observeStarted = true
        observeJob?.cancel()
        observeJob = screenModelScope.launch(Dispatchers.IO) {
            try {
                MainActivity.database.localReadingActivityDao().observeAll().collect { activities ->
                    mutableState.value = State.Result(activities)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = State.Error(e.loadFailureKind())
                AppLogger.e("LocalHistoryPageModel", "Failed to load local history", e)
            }
        }
    }

    fun retry() {
        observeStarted = false
        observe()
    }

    fun delete(activityId: String) {
        screenModelScope.launch(Dispatchers.IO) {
            try {
                MainActivity.database.localReadingActivityDao().deleteById(activityId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(
                    "LocalHistoryPageModel",
                    "Failed to delete local reading activity",
                    e
                )
            }
        }
    }

    fun clear() {
        screenModelScope.launch(Dispatchers.IO) {
            try {
                MainActivity.database.localReadingActivityDao().deleteAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("LocalHistoryPageModel", "Failed to clear local history", e)
            }
        }
    }
}

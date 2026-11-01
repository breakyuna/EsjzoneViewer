package com.breakyuna.esjzone.ui.tab

import kotlinx.coroutines.launch
import com.breakyuna.esjzone.ui.designsystem.appViewportSize
import com.breakyuna.esjzone.ui.designsystem.appUsesWideLayout
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.pager.PageSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import androidx.compose.material3.TextButton
import com.breakyuna.esjzone.ui.designsystem.contentArrival
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.novellibrary.data.WeeklyPopularNovel
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.component.AppNovelCover
import com.breakyuna.esjzone.ui.component.AppSectionTitle
import com.breakyuna.esjzone.ui.designsystem.AppCoverBackdrop
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.appSemanticColors
import com.breakyuna.esjzone.ui.discovery.DiscoveryEmptyState
import com.breakyuna.esjzone.ui.navigation.ContainerTransformTarget
import com.breakyuna.esjzone.ui.navigation.HomeCarouselElement
import com.breakyuna.esjzone.ui.navigation.homeCarouselSharedElement
import com.breakyuna.esjzone.ui.navigation.containerTransformContent
import com.breakyuna.esjzone.ui.navigation.containerTransformSource
import com.breakyuna.esjzone.ui.navigation.rememberNovelCoverSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private val HOME_WEEKLY_POPULAR_COVER_WIDTH = 106.dp
private val HOME_WEEKLY_POPULAR_CONTENT_HEIGHT = 172.dp
internal val HOME_WEEKLY_POPULAR_CARD_HEIGHT = 196.dp
internal val HOME_WEEKLY_POPULAR_INDICATOR_SIZE = 7.dp
private const val HOME_WEEKLY_POPULAR_AUTO_PLAY_MS = 4_000L
private const val HOME_WEEKLY_POPULAR_ANIMATION_MS = 800
private const val HOME_COLLECTION_MAX_ITEMS = 16

internal fun prepareHomeSectionRows(
    novels: List<CoveredNovel>,
    adult: Boolean,
    columns: Int = HOME_GRID_COLUMNS
): List<List<CoveredNovel>> = novels
    .asSequence()
    .filter { adult || !it.isAdult }
    .distinctBy { it.url.trim().ifBlank { it.name.trim() } }
    .take(HOME_COLLECTION_MAX_ITEMS)
    .chunked(columns)
    .toList()

internal fun LazyListScope.weeklyPopularCarousel(
    novels: List<WeeklyPopularNovel>,
    onNovelClick: (CoveredNovel, Int) -> Unit,
    enrichmentEnabled: Boolean = false,
    onVisible: suspend (List<WeeklyPopularNovel>) -> Unit = {}
) {
    val visible = novels.take(10)
    if (visible.isEmpty()) return

    item(key = "home-weekly-popular", contentType = "home-weekly-popular") {
        val pageCount = Int.MAX_VALUE
        val initialPage = Int.MAX_VALUE / 2 - (Int.MAX_VALUE / 2 % visible.size)
        val pagerState = rememberPagerState(
            initialPage = initialPage,
            pageCount = { pageCount }
        )
        val wide = appUsesWideLayout()
        val scope = rememberCoroutineScope()
        val cardWidth = ((minOf(appViewportSize().width, 1280f) - 40f) / 2).dp
        val lifecycleOwner = LocalLifecycleOwner.current
        var enriching by remember { mutableStateOf(false) }
        LaunchedEffect(enrichmentEnabled, pagerState.settledPage, visible.map { it.url }) {
            if (enrichmentEnabled) {
                lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                    enriching = true
                    try {
                        onVisible(listOf(visible[pagerState.settledPage % visible.size],
                            visible[(pagerState.settledPage + 1) % visible.size]).distinctBy { it.url })
                    } finally { enriching = false }
                }
            }
        }

        LaunchedEffect(pagerState, visible.size, lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                // Navigation can cancel an auto-play animation between pages. The
                // saveable pager state retains that offset, so settle it on return.
                if (pagerState.currentPageOffsetFraction != 0f) {
                    pagerState.scrollToPage(pagerState.currentPage)
                }
                while (true) {
                    val manualScrollStarted = withTimeoutOrNull(HOME_WEEKLY_POPULAR_AUTO_PLAY_MS) {
                        snapshotFlow { pagerState.isScrollInProgress }
                            .filter { it }
                            .first()
                    } != null
                    if (manualScrollStarted) {
                        if (pagerState.isScrollInProgress) {
                            snapshotFlow { pagerState.isScrollInProgress }
                                .filter { !it }
                                .first()
                        }
                        continue
                    }
                    if (enriching) continue
                    try {
                        pagerState.animateScrollToPage(
                            page = pagerState.currentPage + 1,
                            animationSpec = tween(HOME_WEEKLY_POPULAR_ANIMATION_MS)
                        )
                    } catch (_: CancellationException) {
                        currentCoroutineContext().ensureActive()
                    }
                }
            }
        }
        Column(modifier = Modifier.contentArrival(), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            HorizontalPager(
                state = pagerState,
                pageSize = if (wide) PageSize.Fixed(cardWidth) else PageSize.Fill,
                pageSpacing = AppSpacing.sm,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val novel = visible[page % visible.size]
                val target = ContainerTransformTarget.homeCarousel(
                    EsjzoneUrls.canonicalPageKey(novel.url).ifBlank { novel.name.trim() },
                    page
                )
                WeeklyPopularCard(
                    novel = novel,
                    target = target,
                    modifier = Modifier.containerTransformSource(target),
                    onClick = { onNovelClick(novel, page) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (wide) {
                    IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.home_carousel_previous))
                    }
                }
                visible.indices.forEach { index ->
                    // Interpolate directly from the gesture, including the last-to-first wrap.
                    val position = (pagerState.currentPage % visible.size) + pagerState.currentPageOffsetFraction
                    val distance = kotlin.math.abs(position - index)
                    val wrappedDistance = minOf(distance, kotlin.math.abs(distance - visible.size))
                    // A single book remains selected while the infinite pager moves between copies.
                    val selectedFraction = if (visible.size == 1) 1f else (1f - wrappedDistance).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .width(HOME_WEEKLY_POPULAR_INDICATOR_SIZE + (28.dp - HOME_WEEKLY_POPULAR_INDICATOR_SIZE) * selectedFraction)
                            .height(HOME_WEEKLY_POPULAR_INDICATOR_SIZE)
                            .clip(CircleShape)
                            .background(
                                androidx.compose.ui.graphics.lerp(
                                    MaterialTheme.colorScheme.outlineVariant,
                                    MaterialTheme.colorScheme.primary,
                                    selectedFraction
                                )
                            )
                    )
                }
                if (wide) {
                    IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.home_carousel_next))
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyPopularCard(
    novel: WeeklyPopularNovel,
    target: ContainerTransformTarget,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val coverSource = rememberNovelCoverSource(
        novel.url, novel.coverUrl, novel.coverState, enableSharedElement = false
    )
    val semantics = appSemanticColors()
    Card(
        onClick = { coverSource.open(onClick) },
        modifier = modifier.fillMaxWidth().height(HOME_WEEKLY_POPULAR_CARD_HEIGHT),
        shape = AppShapes.prominent,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        AppCoverBackdrop(
            coverUrl = novel.coverUrl,
            coverState = novel.coverState,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .containerTransformContent()
                    .padding(start = 14.dp, end = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(HOME_WEEKLY_POPULAR_COVER_WIDTH)
                        .height(HOME_WEEKLY_POPULAR_CONTENT_HEIGHT)
                ) {
                    AppNovelCover(
                        coverUrl = novel.coverUrl,
                        onImageLoaded = coverSource.onImageLoaded,
                        initialImage = coverSource.initialImage,
                        coverState = novel.coverState,
                        title = novel.name,
                        modifier = Modifier
                            .homeCarouselSharedElement(target, HomeCarouselElement.COVER)
                            .fillMaxSize(),
                        isAdult = novel.isAdult
                    )
                    Text(
                        text = stringResource(R.string.home_weekly_popular_badge, novel.rank),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(HOME_WEEKLY_POPULAR_CONTENT_HEIGHT)
                ) {
                    Text(
                        text = novel.name,
                        modifier = Modifier.homeCarouselSharedElement(target, HomeCarouselElement.TITLE),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = novel.descriptionPreview.ifBlank { stringResource(R.string.home_weekly_popular_no_description) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.84f),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = novel.author
                            ?.takeIf(String::isNotBlank)
                            ?.let { stringResource(R.string.home_weekly_popular_author, it) }
                            ?: " ",
                        modifier = Modifier.homeCarouselSharedElement(target, HomeCarouselElement.AUTHOR),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = novel.type
                            .takeIf(String::isNotBlank)
                            ?.let { stringResource(R.string.home_weekly_popular_type, it) }
                            ?: " ",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = semantics.heat,
                            contentColor = semantics.onHeat
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Whatshot,
                                contentDescription = null,
                                tint = semantics.onHeat,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = stringResource(
                                    R.string.home_weekly_popular_heat,
                                    formatWeeklyHeat(novel.weeklyViews)
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = semantics.onHeat
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatWeeklyHeat(value: Int): String = when {
    value >= 10_000 -> "%.1f万".format(value / 10_000f)
    else -> value.toString()
}

internal fun homeGridRowKey(title: String, row: List<CoveredNovel>): String =
    "home-grid-$title-row:${novelKey(row.first())}"

internal fun LazyListScope.homeCollection(
    title: String,
    rows: List<List<CoveredNovel>>,
    showDivider: Boolean,
    onMore: (() -> Unit)?,
    onNovelClick: (CoveredNovel) -> Unit,
    browseMoreLabel: String,
    emptyTitle: String,
    emptyMessage: String
) {
    item(key = "home-section-$title", contentType = "home-section") {
        AppSectionTitle(
            title = title,
            actionLabel = browseMoreLabel.takeIf { onMore != null },
            onAction = onMore,
            modifier = Modifier.padding(top = if (showDivider) AppSpacing.md else AppSpacing.zero)
        )
    }
    if (rows.isEmpty()) {
        item(key = "home-empty-$title", contentType = "empty") {
            DiscoveryEmptyState(
                title = emptyTitle,
                message = emptyMessage
            )
        }
    } else {
        items(
            count = rows.size,
            key = { rowIndex -> homeGridRowKey(title, rows[rowIndex]) },
            contentType = { "home-grid-row" }
        ) { rowIndex ->
            val row = rows[rowIndex]
            NovelGridRow(
                row = row,
                showLatestTitle = false,
                onNovelClick = onNovelClick,
                modifier = Modifier.contentArrival()
                    .padding(bottom = if (rowIndex < rows.size - 1) AppSpacing.lg else AppSpacing.zero)
                    .semantics { contentDescription = "$title row $rowIndex" }
            )
        }
    }
}

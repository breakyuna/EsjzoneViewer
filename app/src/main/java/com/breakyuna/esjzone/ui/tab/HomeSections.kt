package com.breakyuna.esjzone.ui.tab

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.novellibrary.data.WeeklyPopularNovel
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.component.AppNovelCover
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.discovery.DiscoveryEmptyState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private val HOME_WEEKLY_POPULAR_COVER_WIDTH = 106.dp
private val HOME_WEEKLY_POPULAR_CONTENT_HEIGHT = 172.dp
private val HOME_WEEKLY_POPULAR_CARD_HEIGHT = 196.dp
private const val HOME_WEEKLY_POPULAR_AUTO_PLAY_MS = 4_000L
private const val HOME_WEEKLY_POPULAR_ANIMATION_MS = 800
private const val HOME_COLLECTION_MAX_ITEMS = 16

internal fun prepareHomeSectionRows(
    novels: List<CoveredNovel>,
    adult: Boolean
): List<List<CoveredNovel>> = novels
    .asSequence()
    .filter { adult || !it.isAdult }
    .distinctBy { it.url.trim().ifBlank { it.name.trim() } }
    .take(HOME_COLLECTION_MAX_ITEMS)
    .chunked(HOME_GRID_COLUMNS)
    .toList()

internal fun LazyListScope.weeklyPopularCarousel(
    novels: List<WeeklyPopularNovel>,
    onNovelClick: (CoveredNovel) -> Unit
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
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(pagerState, visible.size, lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
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
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            HorizontalPager(
                state = pagerState,
                pageSpacing = AppSpacing.sm,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val novel = visible[page % visible.size]
                WeeklyPopularCard(
                    novel = novel,
                    onClick = { onNovelClick(novel) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                visible.indices.forEach { index ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .width(if (index == pagerState.currentPage % visible.size) 28.dp else 7.dp)
                            .height(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage % visible.size) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyPopularCard(
    novel: WeeklyPopularNovel,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(HOME_WEEKLY_POPULAR_CARD_HEIGHT),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                            MaterialTheme.colorScheme.surfaceContainer,
                            MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                )
                .padding(start = 12.dp, end = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(HOME_WEEKLY_POPULAR_COVER_WIDTH)
                    .height(HOME_WEEKLY_POPULAR_CONTENT_HEIGHT)
            ) {
                AppNovelCover(
                    coverUrl = novel.coverUrl,
                    title = novel.name,
                    modifier = Modifier.fillMaxSize(),
                    isAdult = novel.isAdult
                )
                Text(
                    text = stringResource(R.string.home_weekly_popular_badge, novel.rank),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f))
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(HOME_WEEKLY_POPULAR_CONTENT_HEIGHT)
            ) {
                Text(
                    text = novel.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = novel.descriptionPreview.ifBlank { stringResource(R.string.home_weekly_popular_no_description) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = novel.author
                        ?.takeIf(String::isNotBlank)
                        ?.let { stringResource(R.string.home_weekly_popular_author, it) }
                        ?: " ",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = novel.type
                        .takeIf(String::isNotBlank)
                        ?.let { stringResource(R.string.home_weekly_popular_type, it) }
                        ?: " ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(162, 25, 23),
                        contentColor = Color.White
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Whatshot,
                            contentDescription = null,
                            tint = Color.White,
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
                            color = Color.White
                        )
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
    if (showDivider) {
        item(key = "home-divider-$title", contentType = "home-section-divider") {
            HomeSectionDividerItem()
        }
    }
    item(key = "home-section-$title", contentType = "home-section") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = homeSectionTitleText(title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (onMore != null) {
                TextButton(onClick = onMore) { Text(browseMoreLabel) }
            }
        }
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
            key = { rowIndex -> "home-grid-$title-row:${novelKey(rows[rowIndex].first())}" },
            contentType = { "home-grid-row" }
        ) { rowIndex ->
            val row = rows[rowIndex]
            NovelGridRow(
                row = row,
                showLatestTitle = false,
                onNovelClick = onNovelClick,
                modifier = Modifier
                    .padding(bottom = if (rowIndex < rows.size - 1) AppSpacing.lg else AppSpacing.zero)
                    .semantics { contentDescription = "$title row $rowIndex" }
            )
        }
    }
}

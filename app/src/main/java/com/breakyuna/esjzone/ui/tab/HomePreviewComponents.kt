package com.breakyuna.esjzone.ui.tab

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.component.QuietNovelCover
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent
import com.breakyuna.esjzone.ui.page.NovelPage
import com.breakyuna.esjzone.ui.theme.QuietEditorial
import com.breakyuna.esjzone.ui.theme.quietEditorialColors

private val HomePreviewCardHeight = 112.dp
private val HomePreviewCoverWidth = 76.dp
private val HomePreviewCoverHeight = 96.dp

/**
 * A finite, two-row home rail. Each page contains at most two cards and the
 * list snaps the page-sized cards to the centre of the viewport. Keeping the
 * rail finite is intentional: home data is a snapshot and is never a paging
 * trigger.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun HomePreviewRail(
    novels: List<CoveredNovel>,
    modifier: Modifier = Modifier
) {
    val pages = remember(novels) {
        novels
            .chunked(2)
            .filter { it.isNotEmpty() }
    }

    if (pages.isEmpty()) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // The side inset and the page width share the same value, so both the
        // first and last page can settle with their centre aligned to the rail.
        val sideInset = QuietEditorial.pagePadding
        val pageWidth = if (maxWidth > sideInset * 2) {
            maxWidth - sideInset * 2
        } else {
            maxWidth
        }
        val listState = rememberLazyListState()
        val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

        LazyRow(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(horizontal = sideInset),
            horizontalArrangement = Arrangement.spacedBy(QuietEditorial.itemGap),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(
                items = pages,
                key = { index, page ->
                    val first = page.firstOrNull()
                    first?.url?.takeIf { it.isNotBlank() }
                        ?: first?.name?.takeIf { it.isNotBlank() }
                        ?: "home-page-$index"
                }
            ) { _, page ->
                Column(
                    modifier = Modifier.width(pageWidth),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    page.forEach { novel ->
                        HomePreviewCard(novel = novel)
                    }
                }
            }
        }
    }
}

/** Compact home card: only fields guaranteed by [CoveredNovel] are rendered. */
@Composable
private fun HomePreviewCard(
    novel: CoveredNovel,
    modifier: Modifier = Modifier
) {
    val navigator = LocalBaseNavigator.current
    val editorialColors = quietEditorialColors()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { navigator?.pushIfNotCurrent(NovelPage(novel)) },
        shape = RoundedCornerShape(20.dp),
        color = editorialColors.cardSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuietNovelCover(
                coverUrl = novel.coverUrl,
                title = novel.name,
                modifier = Modifier.size(HomePreviewCoverWidth, HomePreviewCoverHeight),
                isAdult = novel.isAdult
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(HomePreviewCardHeight - 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = novel.name,
                    style = QuietEditorial.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                HomePreviewMetrics(
                    novel = novel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HomePreviewMetrics(
    novel: CoveredNovel,
    tint: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        novel.views
            .takeIf { it > 0 }
            ?.let { count ->
                HomePreviewMetric(
                    icon = Icons.Filled.RemoveRedEye,
                    value = formatHomeCount(count),
                    tint = tint
                )
            }
        novel.likes
            .takeIf { it > 0 }
            ?.let { count ->
                HomePreviewMetric(
                    icon = Icons.Filled.ThumbUp,
                    value = formatHomeCount(count),
                    tint = tint
                )
            }
    }
}

@Composable
private fun HomePreviewMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    tint: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = value,
            style = QuietEditorial.label,
            color = tint,
            maxLines = 1
        )
    }
}

private fun formatHomeCount(count: Int): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

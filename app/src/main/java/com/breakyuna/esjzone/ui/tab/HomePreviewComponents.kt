package com.breakyuna.esjzone.ui.tab

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.component.QuietNovelPreviewCard
import com.breakyuna.esjzone.ui.theme.QuietEditorial

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
                        QuietNovelPreviewCard(
                            novel = novel,
                            compact = true,
                            showLatestChapter = true
                        )
                    }
                }
            }
        }
    }
}

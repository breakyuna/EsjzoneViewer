package com.breakyuna.esjzone.ui.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.component.AppHomeNovelTile
import com.breakyuna.esjzone.ui.designsystem.AppSpacing

/**
 * A finite, single-row home rail using the same borderless portrait tile as HomeTab.
 * Keeping the rail finite is intentional: home data is a snapshot and is never a paging trigger.
 */
@Composable
internal fun HomePreviewRail(
    novels: List<CoveredNovel>,
    modifier: Modifier = Modifier
) {
    if (novels.isEmpty()) return

    LazyRow(
        contentPadding = PaddingValues(end = AppSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        modifier = modifier.fillMaxWidth()
    ) {
        items(
            items = novels,
            key = { it.url.trim().ifBlank { it.name.trim() } },
            contentType = { "home-portrait-novel" }
        ) { novel ->
            AppHomeNovelTile(novel = novel)
        }
    }
}

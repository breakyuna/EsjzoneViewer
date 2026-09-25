package com.breakyuna.esjzone.ui.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppShimmerPlaceholder
import com.breakyuna.esjzone.ui.designsystem.AppSpacing

/** Keeps the first visit laid out like the loaded home while its HTML is in flight. */
internal fun LazyListScope.homeLoadingHero() {
    item(key = "home-weekly-popular", contentType = "home-weekly-popular") {
        val loadingLabel = stringResource(R.string.loading_content)
        AppShimmerPlaceholder(
            modifier = Modifier
                .fillMaxWidth()
                .height(196.dp)
                .semantics { contentDescription = loadingLabel },
            shape = AppShapes.prominent
        )
    }
}

internal fun LazyListScope.homeLoadingCollection(title: String) {
    item(key = "home-loading-divider-$title", contentType = "home-section-divider") {
        HomeSectionDividerItem()
    }
    item(key = "home-loading-section-$title", contentType = "home-section") {
        Text(
            text = homeSectionTitleText(title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(top = AppSpacing.sm)
        )
    }
    item(key = "home-loading-grid-$title", contentType = "home-grid-row") {
        HomeLoadingGridRow()
    }
}

internal fun LazyListScope.homeLoadingWeeklyUpdates() {
    item(key = "home-weekly-loading-divider", contentType = "home-section-divider") {
        HomeSectionDividerItem()
    }
    item(key = "home-weekly-loading-header", contentType = "home-weekly-header") {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Text(
                text = stringResource(R.string.home_weekly_updates),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(top = AppSpacing.sm)
            )
            AppShimmerPlaceholder(Modifier.fillMaxWidth().height(44.dp))
        }
    }
    item(key = "home-weekly-loading-grid", contentType = "home-grid-row") {
        HomeLoadingGridRow()
    }
}

@Composable
private fun HomeLoadingGridRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md / 2)
    ) {
        repeat(HOME_GRID_COLUMNS) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                AppShimmerPlaceholder(
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.7f),
                    shape = AppShapes.compact
                )
                AppShimmerPlaceholder(Modifier.fillMaxWidth().height(12.dp))
                AppShimmerPlaceholder(Modifier.width(44.dp).height(12.dp))
            }
        }
    }
}

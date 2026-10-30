package com.breakyuna.esjzone.ui.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.component.AppSectionTitle
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppShimmerPlaceholder
import com.breakyuna.esjzone.ui.designsystem.AppSpacing

/** Keeps the first visit laid out like the loaded home while its HTML is in flight. */
internal fun LazyListScope.homeLoadingHero() {
    item(key = "home-weekly-popular", contentType = "home-weekly-popular") {
        val loadingLabel = stringResource(R.string.loading_content)
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            AppShimmerPlaceholder(
                modifier = Modifier.fillMaxWidth().height(HOME_WEEKLY_POPULAR_CARD_HEIGHT)
                    .semantics { contentDescription = loadingLabel },
                shape = AppShapes.prominent
            )
            Spacer(Modifier.height(HOME_WEEKLY_POPULAR_INDICATOR_SIZE))
        }
    }
}

internal fun LazyListScope.homeLoadingCollection(title: String) {
    item(key = "home-section-$title", contentType = "home-section") {
        AppSectionTitle(
            title = title,
            modifier = Modifier.padding(top = AppSpacing.md)
        )
    }
    item(key = "home-loading-grid-$title", contentType = "home-grid-row") {
        HomeLoadingGridRow()
    }
}

internal fun LazyListScope.homeLoadingWeeklyUpdates() {
    item(key = "home-weekly-header", contentType = "home-weekly-header") {
        Column(
            modifier = Modifier.padding(top = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            AppSectionTitle(title = stringResource(R.string.home_weekly_updates))
            AppShimmerPlaceholder(Modifier.fillMaxWidth().height(44.dp))
        }
    }
    item(key = "home-weekly-content", contentType = "home-weekly-content") {
        HomeLoadingGridRow()
    }
}

@Composable
private fun HomeLoadingGridRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        repeat(homeGridColumnCount()) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                AppShimmerPlaceholder(
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.7f),
                    shape = AppShapes.cover
                )
                AppShimmerPlaceholder(Modifier.fillMaxWidth().height(12.dp))
                AppShimmerPlaceholder(Modifier.width(44.dp).height(12.dp))
            }
        }
    }
}

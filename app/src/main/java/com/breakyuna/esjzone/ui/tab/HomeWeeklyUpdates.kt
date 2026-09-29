package com.breakyuna.esjzone.ui.tab

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.novellibrary.data.WeeklyUpdateDay
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.discovery.DiscoveryEmptyState
import java.time.DayOfWeek

internal const val WEEKLY_UPDATE_MAX_ITEMS = 24
private const val WEEKLY_UPDATE_TRANSITION_DURATION = 280

internal fun LazyListScope.weeklyUpdatesCollection(
    days: List<WeeklyUpdateDay>,
    selectedIndex: Int,
    novelsByDay: List<List<CoveredNovel>>,
    showDivider: Boolean,
    onSelect: (Int) -> Unit,
    onNovelClick: (CoveredNovel) -> Unit
) {
    if (days.isEmpty()) return
    if (showDivider) {
        item(key = "home-weekly-divider", contentType = "home-section-divider") {
            HomeSectionDividerItem()
        }
    }
    item(key = "home-weekly-header", contentType = "home-weekly-header") {
        WeeklyUpdatesHeader(days, selectedIndex, onSelect)
    }
    item(key = "home-weekly-content", contentType = "home-weekly-content") {
        AnimatedContent(
            targetState = selectedIndex,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally(animationSpec = tween(WEEKLY_UPDATE_TRANSITION_DURATION)) { fullWidth -> fullWidth } togetherWith
                        slideOutHorizontally(animationSpec = tween(WEEKLY_UPDATE_TRANSITION_DURATION)) { fullWidth -> -fullWidth }
                } else {
                    slideInHorizontally(animationSpec = tween(WEEKLY_UPDATE_TRANSITION_DURATION)) { fullWidth -> -fullWidth } togetherWith
                        slideOutHorizontally(animationSpec = tween(WEEKLY_UPDATE_TRANSITION_DURATION)) { fullWidth -> fullWidth }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppSpacing.sm)
                .weeklyDaySwipe(days, selectedIndex, onSelect),
            label = "weekly-update-date-transition"
        ) { index ->
            val novels = novelsByDay.getOrNull(index).orEmpty()
            if (novels.isEmpty()) {
                DiscoveryEmptyState(
                    title = stringResource(R.string.home_collection_empty_title),
                    message = stringResource(R.string.home_weekly_update_empty)
                )
            } else {
                HomeNovelGrid(
                    novels = novels,
                    showLatestTitle = true,
                    onNovelClick = onNovelClick
                )
            }
        }
    }
}

@Composable
private fun HomeNovelGrid(
    novels: List<CoveredNovel>,
    showLatestTitle: Boolean = false,
    onNovelClick: (CoveredNovel) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = remember(novels) { novels.chunked(HOME_GRID_COLUMNS) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
    ) {
        rows.forEach { row ->
            NovelGridRow(
                row = row,
                showLatestTitle = showLatestTitle,
                onNovelClick = onNovelClick
            )
        }
    }
}

@Composable
private fun WeeklyUpdatesHeader(
    days: List<WeeklyUpdateDay>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weeklyDaySwipe(days, selectedIndex, onSelect),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.home_weekly_updates), style = MaterialTheme.typography.titleMedium)
        }
        TabRow(selectedTabIndex = selectedIndex) {
            days.forEachIndexed { index, day ->
                Tab(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    text = { Text(weeklyDayLabel(day.date.dayOfWeek), maxLines = 1, overflow = TextOverflow.Clip) }
                )
            }
        }
    }
}

@Composable
private fun Modifier.weeklyDaySwipe(
    days: List<WeeklyUpdateDay>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
): Modifier {
    val swipeThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    return pointerInput(days, selectedIndex, swipeThreshold) {
        var dragDistance = 0f
        detectHorizontalDragGestures(
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                dragDistance += dragAmount
            },
            onDragCancel = { dragDistance = 0f },
            onDragEnd = {
                val target = when {
                    dragDistance <= -swipeThreshold -> (selectedIndex + 1).coerceAtMost(days.lastIndex)
                    dragDistance >= swipeThreshold -> (selectedIndex - 1).coerceAtLeast(0)
                    else -> selectedIndex
                }
                if (target != selectedIndex) onSelect(target)
                dragDistance = 0f
            }
        )
    }
}

@Composable
private fun weeklyDayLabel(day: DayOfWeek): String = stringResource(
    when (day) {
        DayOfWeek.MONDAY -> R.string.home_weekday_monday
        DayOfWeek.TUESDAY -> R.string.home_weekday_tuesday
        DayOfWeek.WEDNESDAY -> R.string.home_weekday_wednesday
        DayOfWeek.THURSDAY -> R.string.home_weekday_thursday
        DayOfWeek.FRIDAY -> R.string.home_weekday_friday
        DayOfWeek.SATURDAY -> R.string.home_weekday_saturday
        DayOfWeek.SUNDAY -> R.string.home_weekday_sunday
    }
)

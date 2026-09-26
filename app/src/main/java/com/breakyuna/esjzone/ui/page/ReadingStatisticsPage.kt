package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.database.ReadingTagTotal
import com.breakyuna.esjzone.database.ReadingBookTotal
import com.breakyuna.esjzone.database.ReadingStatisticsRecorder
import com.breakyuna.esjzone.database.ReadingStatisticsSummary
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.AccountIconBadge
import com.breakyuna.esjzone.ui.designsystem.AppLoadingState
import com.breakyuna.esjzone.ui.designsystem.AppEmptyState
import com.breakyuna.esjzone.ui.designsystem.AppErrorState
import com.breakyuna.esjzone.ui.designsystem.accountContentWidth
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import com.breakyuna.esjzone.util.AppLogger
import java.time.LocalDate
import java.time.format.TextStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import com.google.gson.JsonParser

object ReadingStatisticsPage : AppDestination {
    override val key: String = "ReadingStatisticsPage"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val model = rememberAppViewModel { ReadingStatisticsPageModel() }
        val state by model.state.collectAsState()
        var selectedDate by remember { mutableStateOf(LocalDate.now()) }
        var trendDays by remember { mutableStateOf(7) }
        var rankingDays by remember { mutableStateOf<Int?>(30) }
        var tagDays by remember { mutableStateOf<Int?>(30) }
        var confirmClear by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { model.observe() }

        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text(stringResource(R.string.reading_stats_clear)) },
                text = { Text(stringResource(R.string.reading_stats_clear_message)) },
                confirmButton = {
                    TextButton(onClick = { confirmClear = false; model.clear() }) {
                        Text(stringResource(R.string.clear))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        Scaffold(topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reading_stats_title), style = AppTypography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = { navigator?.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.reading_stats_back))
                    }
                },
                actions = {
                    IconButton(onClick = { confirmClear = true }, enabled = state is ReadingStatisticsPageModel.State.Ready &&
                        (state as ReadingStatisticsPageModel.State.Ready).summary.totalMs > 0L) {
                        Icon(Icons.Filled.DeleteOutline, stringResource(R.string.reading_stats_clear))
                    }
                }
            )
        }) { padding ->
            when (val current = state) {
                ReadingStatisticsPageModel.State.Loading -> AppLoadingState(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    message = stringResource(R.string.reading_stats_loading)
                )
                ReadingStatisticsPageModel.State.Error -> AppErrorState(
                    title = stringResource(R.string.load_failed),
                    onRetry = model::retry,
                    retryLabel = stringResource(R.string.retry),
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
                is ReadingStatisticsPageModel.State.Ready -> {
                    val summary = current.summary
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding).accountContentWidth(),
                        contentPadding = PaddingValues(horizontal = AppSpacing.lg, vertical = AppSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
                    ) {
                        item {
                            StatCard {
                                StatSectionTitle(Icons.Filled.QueryStats, stringResource(R.string.reading_stats_title))
                                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                                    StatValue(stringResource(R.string.reading_stats_total), formatDuration(summary.totalMs), Modifier.weight(1f), emphasized = true)
                                    StatValue(stringResource(R.string.reading_stats_today), formatDuration(summary.todayMs), Modifier.weight(1f))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                                    StatValue(stringResource(R.string.reading_stats_week), formatDuration(summary.weekMs), Modifier.weight(1f))
                                    StatValue(stringResource(R.string.reading_stats_streak), stringResource(R.string.reading_stats_days, summary.streakDays), Modifier.weight(1f))
                                }
                                Text(stringResource(R.string.reading_stats_local_note), style = AppTypography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (summary.totalMs == 0L) {
                                    AppEmptyState(title = stringResource(R.string.reading_stats_empty), icon = Icons.Filled.MenuBook)
                                }
                            }
                        }
                        item {
                            StatCard {
                                StatSectionTitle(Icons.Filled.DateRange, stringResource(R.string.reading_stats_activity))
                                ReadingHeatmap(summary, selectedDate) { selectedDate = it }
                                Text(
                                    stringResource(R.string.reading_stats_day_detail, selectedDate.toString(), formatDuration(summary.daily[selectedDate] ?: 0L)),
                                    style = AppTypography.labelLarge,
                                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, AppShapes.compact)
                                        .padding(AppSpacing.md)
                                )
                            }
                        }
                        item {
                            StatCard {
                                StatSectionTitle(Icons.Filled.ShowChart, stringResource(R.string.reading_stats_trend))
                                StatPeriodFilter(listOf(7, 30), trendDays) { trendDays = it ?: 7 }
                                TrendBars(summary, trendDays)
                            }
                        }
                        item {
                            StatCard {
                                StatSectionTitle(Icons.Filled.MenuBook, stringResource(R.string.reading_stats_ranking))
                                StatPeriodFilter(listOf(7, 30, null), rankingDays) { rankingDays = it }
                                val topBooks = current.rankings[rankingDays].orEmpty()
                                if (topBooks.isEmpty()) {
                                    Text(stringResource(R.string.reading_stats_no_books))
                                } else {
                                    topBooks.forEachIndexed { index, book -> BookRank(index + 1, book) }
                                }
                            }
                        }
                        item {
                            StatCard {
                                StatSectionTitle(Icons.Filled.Label, stringResource(R.string.reading_stats_tags))
                                Text(stringResource(R.string.reading_stats_tags_note), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                StatPeriodFilter(listOf(7, 30, null), tagDays) { tagDays = it }
                                val tags = current.tagRankings[tagDays].orEmpty()
                                if (tags.isEmpty()) {
                                    Text(stringResource(R.string.reading_stats_no_tags))
                                } else {
                                    TagDonutChart(tags)
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
private fun TagDonutChart(tags: List<ReadingTagTotal>) {
    val total = tags.sumOf { it.durationMs }.toDouble()
    val locale = LocalConfiguration.current.locales[0]
    val percentFormat = remember(locale) {
        java.text.NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }
    }
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    val colors = tags.mapIndexed { index, tag ->
        if (tag.tag == null) MaterialTheme.colorScheme.outline
        else Color.hsv((index * 137.508f + 215f) % 360f, if (dark) .55f else .65f, if (dark) .9f else .8f)
    }
    val chartBackground = MaterialTheme.colorScheme.surface
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp)) {
            val stroke = 28.dp.toPx()
            val inset = stroke / 2
            var startAngle = -90f
            tags.forEachIndexed { index, tag ->
                val sweep = (tag.durationMs / total * 360).toFloat()
                drawArc(
                    color = colors[index], startAngle = startAngle, sweepAngle = sweep,
                    useCenter = false, topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke)
                )
                if (tags.size > 1) {
                    drawArc(color = chartBackground, startAngle = startAngle, sweepAngle = minOf(1.5f, sweep / 4),
                        useCenter = false, topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                }
                startAngle += sweep
            }
        }
        Column(Modifier.width(116.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(percentFormat.format(tags.first().durationMs / total), style = AppTypography.displayMedium)
            Text(tags.first().tag ?: stringResource(R.string.reading_stats_other), style = AppTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    tags.forEachIndexed { index, tag ->
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(colors[index]))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                Text(tag.tag ?: stringResource(R.string.reading_stats_other), style = AppTypography.bodyMedium)
                Text(formatDuration(tag.durationMs), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(percentFormat.format(tag.durationMs / total), style = AppTypography.labelLarge)
        }
    }
}

@Composable
private fun StatCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.prominent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.lg), content = content)
    }
}

@Composable
private fun StatSectionTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        AccountIconBadge(icon)
        Text(title, style = AppTypography.titleMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatPeriodFilter(options: List<Int?>, selected: Int?, onSelect: (Int?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        options.forEach { days ->
            FilterChip(
                selected = selected == days,
                onClick = { onSelect(days) },
                shape = AppShapes.pill,
                label = { Text(stringResource(when (days) {
                    7 -> R.string.reading_stats_7_days
                    30 -> R.string.reading_stats_30_days
                    else -> R.string.reading_stats_all
                }), style = AppTypography.labelMedium) }
            )
        }
    }
}

@Composable
private fun StatValue(label: String, value: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Column(
        modifier.background(if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow, AppShapes.standard)
            .padding(AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        Text(label, style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = AppTypography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ReadingHeatmap(summary: ReadingStatisticsSummary, selectedDate: LocalDate, onSelect: (LocalDate) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val firstDate = summary.today.minusYears(1).plusDays(1)
    val firstWeek = firstDate.minusDays((firstDate.dayOfWeek.value - 1).toLong())
    val weekCount = ((summary.today.toEpochDay() - firstWeek.toEpochDay()) / 7 + 1).toInt()
    val scrollState = rememberScrollState(initial = Int.MAX_VALUE)
    val maximumDuration = remember(summary, firstDate) {
        (0L..(summary.today.toEpochDay() - firstDate.toEpochDay()))
            .maxOf { summary.daily[firstDate.plusDays(it)] ?: 0L }
            .coerceAtLeast(1L)
    }
    val heatColor = if (MaterialTheme.colorScheme.surface.luminance() < .5f) Color(0xFF82B1FF) else Color(0xFF2563EB)
    val shades = listOf(
        MaterialTheme.colorScheme.surfaceVariant,
        heatColor.copy(alpha = .25f),
        heatColor.copy(alpha = .5f),
        heatColor.copy(alpha = .75f),
        heatColor
    )
    Column(Modifier.fillMaxWidth().horizontalScroll(scrollState)) {
        Box(Modifier.width((weekCount * 20 - 4).dp).height(24.dp)) {
            repeat(weekCount) { week ->
                val start = firstWeek.plusWeeks(week.toLong())
                val monthStart = (0L..6L).map { start.plusDays(it) }
                    .firstOrNull { it.dayOfMonth == 1 && !it.isBefore(firstDate) && !it.isAfter(summary.today) }
                if (monthStart != null) {
                    Text(
                        monthStart.month.getDisplayName(TextStyle.SHORT, locale),
                        modifier = Modifier.offset(x = (week * 20).dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(weekCount) { week ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(7) { weekday ->
                        val date = firstWeek.plusDays((week * 7 + weekday).toLong())
                        val inRange = !date.isBefore(firstDate) && !date.isAfter(summary.today)
                        val duration = summary.daily[date] ?: 0L
                        val shade = when {
                            !inRange -> Color.Transparent
                            duration <= 0L -> MaterialTheme.colorScheme.surfaceVariant
                            else -> when (duration.toDouble() / maximumDuration) {
                                in 0.0..0.25 -> shades[1]
                                in 0.25..0.5 -> shades[2]
                                in 0.5..0.75 -> shades[3]
                                else -> shades[4]
                            }
                        }
                        val description = stringResource(R.string.reading_stats_day_detail, date.toString(), formatDuration(duration))
                        val shape = RoundedCornerShape(3.dp)
                        Box(
                            Modifier.size(16.dp).clip(shape)
                                .background(shade)
                                .then(if (inRange) Modifier
                                    .then(if (date == selectedDate) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, shape) else Modifier)
                                    .clickable { onSelect(date) }
                                    .semantics {
                                        contentDescription = description
                                        selected = date == selectedDate
                                    } else Modifier)
                        )
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.reading_stats_less), style = MaterialTheme.typography.labelSmall)
        shades.forEach { shade ->
            Box(Modifier.padding(start = 4.dp).size(12.dp).clip(RoundedCornerShape(3.dp)).background(shade))
        }
        Text(stringResource(R.string.reading_stats_more), modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TrendBars(summary: ReadingStatisticsSummary, days: Int) {
    val values = (days - 1 downTo 0).map { summary.daily[summary.today.minusDays(it.toLong())] ?: 0L }
    val maximum = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        values.forEach { value ->
            Box(Modifier.weight(1f).height((4f + 82f * value / maximum).dp)
                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                .background(if (value > 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(summary.today.minusDays(days.toLong() - 1L).toString(), style = MaterialTheme.typography.labelSmall)
        Text(summary.today.toString(), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BookRank(rank: Int, book: ReadingBookTotal) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(AppSpacing.xxl).background(
            if (rank == 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            AppShapes.compact), contentAlignment = Alignment.Center) {
            Text(rank.toString(), style = AppTypography.labelLarge)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(book.bookName, style = AppTypography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(formatDuration(book.durationMs), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun formatDuration(durationMs: Long): String {
    val minutes = durationMs / 60_000L
    return when {
        durationMs <= 0L -> stringResource(R.string.reading_stats_zero_minutes)
        minutes == 0L -> stringResource(R.string.reading_stats_less_than_minute)
        minutes < 60L -> stringResource(R.string.reading_stats_minutes, minutes)
        else -> stringResource(R.string.reading_stats_hours_minutes, minutes / 60L, minutes % 60L)
    }
}

private class ReadingStatisticsPageModel : AppStateViewModel<ReadingStatisticsPageModel.State>(State.Loading) {
    sealed interface State {
        data object Loading : State
        data object Error : State
        data class Ready(val summary: ReadingStatisticsSummary) : State {
            val rankings = listOf(7, 30, null).associateWith(summary::topBooks)
            val tagRankings = listOf(7, 30, null).associateWith(summary::tagDistribution)
        }
    }

    private var observing = false
    private var observeJob: Job? = null

    fun observe() {
        if (observing) return
        observing = true
        observeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                combine(
                    PresentationAccess.database.readingStatDao().observeAll(),
                    PresentationAccess.database.cacheDao().observeReadingTags()
                ) { rows, cachedTags ->
                    val tags = cachedTags.associate { entry ->
                        entry.key.removePrefix(ReadingStatisticsRecorder.TAGS_KEY_PREFIX) to runCatching {
                            JsonParser.parseString(entry.value).asJsonArray.map { it.asString }
                        }.getOrDefault(emptyList())
                    }
                    State.Ready(ReadingStatisticsSummary(rows, LocalDate.now(), tags))
                }.collect { mutableState.value = it }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                observing = false
                mutableState.value = State.Error
                AppLogger.e("ReadingStatistics", "Failed to load statistics", error)
            }
        }
    }

    fun retry() {
        observeJob?.cancel()
        observing = false
        mutableState.value = State.Loading
        observe()
    }

    fun clear() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ReadingStatisticsRecorder.clear()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e("ReadingStatistics", "Failed to clear statistics", error)
                mutableState.value = State.Error
            }
        }
    }
}

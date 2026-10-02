package com.breakyuna.esjzone.ui.page

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Scaffold
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
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
import com.breakyuna.esjzone.ui.designsystem.AppLoadingState
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
        val incognito by PresentationAccess.settings.readingStatisticsIncognitoFlow.collectAsState()
        var selectedDate by remember { mutableStateOf(LocalDate.now()) }
        var trendDays by remember { mutableStateOf(7) }
        var trendLineChart by rememberSaveable { mutableStateOf(false) }
        var rankingDays by remember { mutableStateOf<Int?>(30) }
        var tagDays by remember { mutableStateOf<Int?>(30) }
        var confirmClear by remember { mutableStateOf(false) }
        val incognitoDescription = stringResource(R.string.reading_stats_incognito)
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
                title = { Text(stringResource(R.string.reading_stats_title), style = AppTypography.titleMedium) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = { navigator?.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.reading_stats_back))
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.reading_stats_incognito), style = MaterialTheme.typography.labelMedium)
                        Switch(
                            checked = incognito,
                            onCheckedChange = PresentationAccess.settings::setReadingStatisticsIncognito,
                            modifier = Modifier.padding(horizontal = 8.dp).semantics {
                                contentDescription = incognitoDescription
                            }
                        )
                    }
                    if (!incognito) {
                        IconButton(onClick = { confirmClear = true }, enabled = state is ReadingStatisticsPageModel.State.Ready &&
                            (state as ReadingStatisticsPageModel.State.Ready).summary.totalMs > 0L) {
                            Icon(Icons.Filled.DeleteOutline, stringResource(R.string.reading_stats_clear))
                        }
                    }
                }
            )
        }) { padding ->
            if (incognito) {
                ReadingStatisticsIncognitoState(Modifier.fillMaxSize().padding(padding))
                return@Scaffold
            }
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
                        contentPadding = PaddingValues(start = AppSpacing.lg, end = AppSpacing.lg, top = AppSpacing.sm, bottom = AppSpacing.xl),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                    ) {
                        item {
                            StatCard {
                                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                                    StatValue(stringResource(R.string.reading_stats_total), formatDuration(summary.totalMs), Modifier.weight(1f), emphasized = true)
                                    StatValue(stringResource(R.string.reading_stats_today), formatDuration(summary.todayMs), Modifier.weight(1f))
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                                    StatValue(stringResource(R.string.reading_stats_week), formatDuration(summary.weekMs), Modifier.weight(1f))
                                    StatValue(stringResource(R.string.reading_stats_streak), stringResource(R.string.reading_stats_days, summary.streakDays), Modifier.weight(1f))
                                }
                                if (summary.totalMs == 0L) {
                                    Text(stringResource(R.string.reading_stats_empty), style = AppTypography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(stringResource(R.string.reading_stats_local_note), style = AppTypography.bodySmall,
                                modifier = Modifier.padding(horizontal = AppSpacing.xs, vertical = AppSpacing.sm),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        item {
                            StatCard {
                                StatSectionTitle(stringResource(R.string.reading_stats_activity))
                                ReadingHeatmap(summary, selectedDate) { selectedDate = it }
                                Text(
                                    stringResource(R.string.reading_stats_day_detail, selectedDate.toString(), formatDuration(summary.daily[selectedDate] ?: 0L)),
                                    style = AppTypography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        item {
                            StatCard {
                                StatSectionTitle(stringResource(R.string.reading_stats_trend))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                                    StatPeriodFilter(listOf(7, 30), trendDays) { trendDays = it ?: 7 }
                                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                        StatFilterChip(
                                            selected = !trendLineChart,
                                            onClick = { trendLineChart = false },
                                            label = stringResource(R.string.reading_stats_bar_chart)
                                        )
                                        StatFilterChip(
                                            selected = trendLineChart,
                                            onClick = { trendLineChart = true },
                                            label = stringResource(R.string.reading_stats_line_chart)
                                        )
                                    }
                                }
                                TrendChart(summary, trendDays, trendLineChart)
                            }
                        }
                        item {
                            StatCard {
                                StatSectionTitle(stringResource(R.string.reading_stats_ranking))
                                StatPeriodFilter(listOf(7, 30, null), rankingDays) { rankingDays = it }
                                val topBooks = current.rankings[rankingDays].orEmpty()
                                if (topBooks.isEmpty()) {
                                    Text(stringResource(R.string.reading_stats_no_books), style = AppTypography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    topBooks.forEachIndexed { index, book ->
                                        BookRank(index + 1, book, topBooks.first().durationMs)
                                    }
                                }
                            }
                        }
                        item {
                            StatCard {
                                StatSectionTitle(stringResource(R.string.reading_stats_tags))
                                StatPeriodFilter(listOf(7, 30, null), tagDays) { tagDays = it }
                                val tags = current.tagRankings[tagDays].orEmpty()
                                if (tags.isEmpty()) {
                                    Text(stringResource(R.string.reading_stats_no_tags), style = AppTypography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    TagDonutChart(tags)
                                    TagDistributionList(current.allTagRankings[tagDays].orEmpty())
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
private fun ReadingStatisticsIncognitoState(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "IncognitoGhost")
    val floatOffset = transition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GhostFloatOffset"
    )
    Column(
        modifier = modifier.padding(AppSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_reading_incognito_ghost),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(192.dp).graphicsLayer {
                translationY = floatOffset.value.dp.toPx()
            }
        )
        Text(
            text = stringResource(R.string.reading_stats_incognito_message),
            modifier = Modifier.padding(top = 32.dp),
            style = AppTypography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
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
    val middleAngles = tags.runningFold(-90f) { angle, tag ->
        angle + (tag.durationMs / total * 360).toFloat()
    }.let { boundaries ->
        tags.indices.map { index -> (boundaries[index] + boundaries[index + 1]) / 2f }
    }
    val chartHeight = maxOf(300, tags.size * 28).dp
    BoxWithConstraints(Modifier.fillMaxWidth().height(chartHeight)) {
        val labelWidth = 72.dp
        val labelGap = 12.dp
        val labelRadiusX = maxWidth / 2 - labelWidth - labelGap
        val labelRadiusY = chartHeight / 2 - 28.dp
        val radius = minOf(80.dp, maxWidth * .21f, labelRadiusX - labelGap)
        // Intersect each sector's radius with the label ellipse, then connect horizontally.
        val elbowPositions = middleAngles.map { degrees ->
            val angle = Math.toRadians(degrees.toDouble())
            val dx = kotlin.math.cos(angle).toFloat()
            val dy = kotlin.math.sin(angle).toFloat()
            val distance = 1f / kotlin.math.sqrt(
                dx * dx / (labelRadiusX.value * labelRadiusX.value) +
                    dy * dy / (labelRadiusY.value * labelRadiusY.value)
            )
            (maxWidth / 2 + (dx * distance).dp) to
                (chartHeight / 2 + (dy * distance).dp)
        }
        val labelPositions = elbowPositions.mapIndexed { index, (elbowX, elbowY) ->
            val side = if (kotlin.math.cos(Math.toRadians(middleAngles[index].toDouble())) >= 0) 1 else -1
            (elbowX + (labelWidth / 2 + labelGap) * side) to elbowY
        }
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val outerRadius = radius.toPx()
            val stroke = 24.dp.toPx()
            val arcRadius = outerRadius - stroke / 2
            val arcTopLeft = center - Offset(arcRadius, arcRadius)
            val arcSize = Size(arcRadius * 2, arcRadius * 2)
            var startAngle = -90f
            tags.forEachIndexed { index, tag ->
                val sweep = (tag.durationMs / total * 360).toFloat()
                drawArc(colors[index], startAngle, sweep, false, arcTopLeft, arcSize, style = Stroke(stroke))
                if (tags.size > 1) {
                    drawArc(chartBackground, startAngle, minOf(1.5f, sweep / 4), false,
                        arcTopLeft, arcSize, style = Stroke(stroke))
                }
                val angle = Math.toRadians(middleAngles[index].toDouble())
                val direction = Offset(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat())
                val start = center + direction * outerRadius
                val (elbowX, elbowY) = elbowPositions[index]
                val elbow = Offset(elbowX.toPx(), elbowY.toPx())
                val (labelX, labelY) = labelPositions[index]
                val side = if (direction.x >= 0) 1 else -1
                val end = Offset((labelX - labelWidth / 2 * side).toPx(), labelY.toPx())
                drawLine(colors[index], start, elbow, 1.dp.toPx())
                drawLine(colors[index], elbow, end, 1.dp.toPx())
                startAngle += sweep
            }
        }
        tags.forEachIndexed { index, tag ->
            val (labelX, labelY) = labelPositions[index]
            Column(
                modifier = Modifier
                    .offset(x = labelX - labelWidth / 2, y = labelY - 24.dp)
                    .width(labelWidth)
                    .height(48.dp)
                    .semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(tag.tag ?: stringResource(R.string.reading_stats_other),
                    style = AppTypography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center)
                Text(percentFormat.format(tag.durationMs / total),
                    style = AppTypography.labelMedium, color = colors[index])
            }
        }
        Column(Modifier.align(Alignment.Center).width(radius * 1.3f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(percentFormat.format(tags.first().durationMs / total), style = AppTypography.titleLarge)
            Text(tags.first().tag ?: stringResource(R.string.reading_stats_other), style = AppTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TagDistributionList(tags: List<ReadingTagTotal>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val percentFormat = remember(locale) {
        java.text.NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }
    }
    val total = tags.sumOf { it.durationMs }.toDouble()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (expanded) R.string.reading_stats_hide_tags else R.string.reading_stats_show_tags),
                modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        }
        if (expanded) {
            tags.forEach { tag ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(tag.tag.orEmpty(), modifier = Modifier.weight(1f), style = AppTypography.bodyMedium)
                    Text(percentFormat.format(tag.durationMs / total), style = AppTypography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.standard,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.md), content = content)
    }
}

@Composable
private fun StatSectionTitle(title: String) {
    Text(title, style = AppTypography.labelLarge, modifier = Modifier.semantics { heading() })
}

@Composable
private fun StatPeriodFilter(options: List<Int?>, selected: Int?, onSelect: (Int?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        options.forEach { days ->
            StatFilterChip(
                selected = selected == days,
                onClick = { onSelect(days) },
                label = stringResource(when (days) {
                    7 -> R.string.reading_stats_7_days
                    30 -> R.string.reading_stats_30_days
                    else -> R.string.reading_stats_all
                })
            )
        }
    }
}

@Composable
private fun StatFilterChip(selected: Boolean, onClick: () -> Unit, label: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        shape = AppShapes.compact,
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        label = { Text(label, style = AppTypography.labelMedium) }
    )
}

@Composable
private fun StatValue(label: String, value: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Column(
        modifier.padding(vertical = AppSpacing.xs).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        Text(label, style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = if (emphasized) AppTypography.titleLarge else AppTypography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface)
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
                        Box(
                            Modifier.size(16.dp).clip(RoundedCornerShape(3.dp))
                                .background(shade)
                                .then(if (inRange) Modifier
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
private fun TrendChart(summary: ReadingStatisticsSummary, days: Int, lineChart: Boolean) {
    val values = (days - 1 downTo 0).map { summary.daily[summary.today.minusDays(it.toLong())] ?: 0L }
    val maximum = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text(formatDuration(values.maxOrNull() ?: 0L), style = AppTypography.labelMedium, color = mutedColor)
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val inset = 4.dp.toPx()
            val chartWidth = (size.width - inset * 2).coerceAtLeast(0f)
            val chartHeight = (size.height - inset * 2).coerceAtLeast(0f)
            val baseline = inset + chartHeight
            repeat(3) { index ->
                val y = inset + chartHeight * index / 2
                drawLine(gridColor, Offset(inset, y), Offset(inset + chartWidth, y), 1.dp.toPx())
            }
            if (lineChart) {
                val points = values.mapIndexed { index, value ->
                    Offset(
                        inset + chartWidth * index / (values.size - 1).coerceAtLeast(1),
                        baseline - chartHeight * (value.toDouble() / maximum).toFloat()
                    )
                }
                val fill = Path().apply {
                    moveTo(points.first().x, baseline)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, baseline)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = .12f), Color.Transparent)))
                points.zipWithNext().forEach { (start, end) ->
                    drawLine(lineColor, start, end, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
                points.forEach { point -> drawCircle(lineColor, radius = 2.dp.toPx(), center = point) }
            } else {
                val slotWidth = chartWidth / values.size
                val barWidth = minOf(16.dp.toPx(), slotWidth * .65f)
                values.forEachIndexed { index, value ->
                    val barHeight = maxOf(2.dp.toPx(), chartHeight * (value.toDouble() / maximum).toFloat())
                    drawRoundRect(
                        color = if (value == 0L) gridColor else lineColor.copy(alpha = if (index == values.lastIndex) .9f else .5f),
                        topLeft = Offset(inset + slotWidth * index + (slotWidth - barWidth) / 2, baseline - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(summary.today.minusDays(days.toLong() - 1L).toString(),
                style = MaterialTheme.typography.labelSmall, color = mutedColor)
            Text(summary.today.toString(), style = MaterialTheme.typography.labelSmall, color = mutedColor)
        }
    }
}

@Composable
private fun BookRank(rank: Int, book: ReadingBookTotal, maximumDuration: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = AppSpacing.xs).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md), verticalAlignment = Alignment.Top) {
        Text(rank.toString().padStart(2, '0'), modifier = Modifier.width(AppSpacing.xl),
            style = AppTypography.labelLarge,
            color = if (rank == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Text(book.bookName, style = AppTypography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(3.dp).clip(AppShapes.pill)
                    .background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth((book.durationMs.toDouble() / maximumDuration.coerceAtLeast(1L)).toFloat().coerceIn(0f, 1f))
                        .height(3.dp).clip(AppShapes.pill)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (rank == 1) .8f else .4f)))
                }
                Text(formatDuration(book.durationMs), style = AppTypography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
            val tagRankings = listOf(7, 30, null).associateWith { summary.tagDistribution(it) }
            val allTagRankings = listOf(7, 30, null).associateWith { summary.tagDistribution(it, groupSmallTags = false) }
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

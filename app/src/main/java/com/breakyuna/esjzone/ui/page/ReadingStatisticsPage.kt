package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.border
import com.breakyuna.esjzone.ui.designsystem.appChartColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ShowChart
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
import androidx.compose.material3.IconToggleButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
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
import java.time.format.DateTimeFormatter
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
                            StatisticsOverview(summary)
                        }
                        item {
                            StatisticsPanel(stringResource(R.string.reading_stats_activity)) {
                                Row(
                                    Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(selectedDate.toString(), modifier = Modifier.weight(1f),
                                        style = AppTypography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formatDuration(summary.daily[selectedDate] ?: 0L),
                                        modifier = Modifier.weight(1f), textAlign = TextAlign.End,
                                        style = AppTypography.titleMedium)
                                }
                                Column(
                                    Modifier.fillMaxWidth().clip(AppShapes.standard)
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                        .padding(AppSpacing.md),
                                    verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                                ) {
                                    ReadingHeatmap(summary, selectedDate) { selectedDate = it }
                                }
                            }
                        }
                        item {
                            StatCard {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically) {
                                    StatSectionTitle(stringResource(R.string.reading_stats_trend))
                                    Row(Modifier.clip(AppShapes.pill)
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                                        listOf(false, true).forEach { line ->
                                            val active = trendLineChart == line
                                            IconToggleButton(
                                                checked = active,
                                                onCheckedChange = { trendLineChart = line },
                                                modifier = Modifier.size(48.dp).padding(4.dp).clip(AppShapes.pill)
                                                    .background(if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                            ) {
                                                Icon(
                                                    if (line) Icons.Filled.ShowChart else Icons.Filled.BarChart,
                                                    stringResource(if (line) R.string.reading_stats_line_chart else R.string.reading_stats_bar_chart),
                                                    modifier = Modifier.size(20.dp),
                                                    tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                                StatPeriodFilter(listOf(7, 30), trendDays) { trendDays = it ?: 7 }
                                TrendChart(summary, trendDays, trendLineChart)
                            }
                        }
                        item {
                            StatisticsPanel(stringResource(R.string.reading_stats_ranking)) {
                                StatisticsPeriodSelector(rankingDays) { rankingDays = it }
                                val topBooks = current.rankings[rankingDays].orEmpty()
                                if (topBooks.isEmpty()) {
                                    StatisticsEmptyState(stringResource(R.string.reading_stats_no_books))
                                } else {
                                    topBooks.forEachIndexed { index, book ->
                                        BookRank(index + 1, book, topBooks.first().durationMs)
                                    }
                                }
                            }
                        }
                        item {
                            StatisticsPanel(stringResource(R.string.reading_stats_tags)) {
                                StatisticsPeriodSelector(tagDays) { tagDays = it }
                                val tags = current.tagRankings[tagDays].orEmpty()
                                if (tags.isEmpty()) {
                                    StatisticsEmptyState(stringResource(R.string.reading_stats_no_tags))
                                } else {
                                    val otherColor = MaterialTheme.colorScheme.outline
                                    val allTags = current.allTagRankings[tagDays].orEmpty()
                                    val tagColors = (tags + allTags).associate { tag ->
                                        tag.tag to (tag.tag?.let { appChartColor(it) } ?: otherColor)
                                    }
                                    TagDonutChart(tags, tagColors)
                                    TagDistributionList(current.allTagRankings[tagDays].orEmpty(), tagColors, otherColor)
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
private fun StatisticsOverview(summary: ReadingStatisticsSummary) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        Column(
            Modifier.fillMaxWidth().clip(AppShapes.prominent)
                .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.surfaceContainerHigh)))
                .padding(AppSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Text(stringResource(R.string.reading_stats_total), style = AppTypography.labelLarge,
                    color = colors.onPrimaryContainer.copy(alpha = .7f))
                Text(formatDuration(summary.totalMs), style = AppTypography.displayLarge,
                    color = colors.onPrimaryContainer)
            }
            HorizontalDivider(color = colors.onPrimaryContainer.copy(alpha = .12f))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stacked = maxWidth / LocalConfiguration.current.fontScale < 260.dp
                FlowRow(
                    maxItemsInEachRow = if (stacked) 1 else 3,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                ) {
                    listOf(
                        stringResource(R.string.reading_stats_today) to formatDuration(summary.todayMs),
                        stringResource(R.string.reading_stats_week) to formatDuration(summary.weekMs),
                        stringResource(R.string.reading_stats_streak) to stringResource(R.string.reading_stats_days, summary.streakDays)
                    ).forEach { (label, value) ->
                        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {},
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                            Text(label, style = AppTypography.bodySmall,
                                color = colors.onPrimaryContainer.copy(alpha = .7f))
                            Text(value, style = AppTypography.labelLarge, color = colors.onPrimaryContainer)
                        }
                    }
                }
            }
            if (summary.totalMs == 0L) {
                Text(stringResource(R.string.reading_stats_empty), style = AppTypography.bodySmall,
                    color = colors.onPrimaryContainer.copy(alpha = .7f))
            }
        }
        Text(stringResource(R.string.reading_stats_local_note), style = AppTypography.bodySmall,
            modifier = Modifier.padding(horizontal = AppSpacing.xs), color = colors.onSurfaceVariant)
    }
}

@Composable
private fun StatisticsPanel(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(AppShapes.prominent)
            .background(MaterialTheme.colorScheme.surface).padding(AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
    ) {
        Text(title, style = AppTypography.labelLarge, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun StatisticsPeriodSelector(selected: Int?, onSelect: (Int?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectableGroup().clip(AppShapes.pill)
            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(AppSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        listOf(7, 30, null).forEach { days ->
            val active = selected == days
            Box(
                Modifier.weight(1f).clip(AppShapes.pill)
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(days) })
                    .heightIn(min = 48.dp).padding(horizontal = AppSpacing.sm, vertical = AppSpacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(when (days) {
                    7 -> R.string.reading_stats_7_days
                    30 -> R.string.reading_stats_30_days
                    else -> R.string.reading_stats_all
                }), style = AppTypography.labelMedium, textAlign = TextAlign.Center,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatisticsEmptyState(message: String) {
    Text(message, modifier = Modifier.fillMaxWidth().clip(AppShapes.standard)
        .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(AppSpacing.xl),
        style = AppTypography.bodyMedium, textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Box(
            Modifier.size(240.dp).background(
                Brush.radialGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, Color.Transparent)),
                CircleShape
            ), contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_reading_incognito_ghost),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(192.dp).graphicsLayer {
                    translationY = floatOffset.value.dp.toPx()
                }
            )
        }
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
private fun TagDonutChart(tags: List<ReadingTagTotal>, tagColors: Map<String?, Color>) {
    val total = tags.sumOf { it.durationMs }.toDouble()
    val locale = LocalConfiguration.current.locales[0]
    val percentFormat = remember(locale) {
        java.text.NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }
    }
    val colors = tags.map { tagColors.getValue(it.tag) }
    val chartBackground = MaterialTheme.colorScheme.surfaceContainerLow
    val middleAngles = tags.runningFold(-90f) { angle, tag ->
        angle + (tag.durationMs / total * 360).toFloat()
    }.let { boundaries ->
        tags.indices.map { index -> (boundaries[index] + boundaries[index + 1]) / 2f }
    }
    val chartHeight = maxOf(300, tags.size * 28).dp
    BoxWithConstraints(Modifier.fillMaxWidth().height(chartHeight)) {
        val labelWidth = 64.dp
        val labelGap = 8.dp
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
            val stroke = 18.dp.toPx()
            drawCircle(chartBackground, radius = outerRadius - stroke - 6.dp.toPx(), center = center)
            val arcRadius = outerRadius - stroke / 2
            val arcTopLeft = center - Offset(arcRadius, arcRadius)
            val arcSize = Size(arcRadius * 2, arcRadius * 2)
            var startAngle = -90f
            tags.forEachIndexed { index, tag ->
                val sweep = (tag.durationMs / total * 360).toFloat()
                val gap = if (tags.size > 1) minOf(3f, sweep / 4) else 0f
                drawArc(colors[index], startAngle + gap / 2, sweep - gap, false,
                    arcTopLeft, arcSize, style = Stroke(stroke))
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
                    textAlign = TextAlign.Center, color = colors[index])
                Text(percentFormat.format(tag.durationMs / total),
                    style = AppTypography.labelMedium, color = colors[index])
            }
        }
        Column(Modifier.align(Alignment.Center).width(radius * 1.3f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(percentFormat.format(tags.first().durationMs / total), style = AppTypography.titleLarge,
                color = colors.first())
            Text(tags.first().tag ?: stringResource(R.string.reading_stats_other), style = AppTypography.bodySmall,
                color = colors.first(), textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TagDistributionList(
    tags: List<ReadingTagTotal>,
    tagColors: Map<String?, Color>,
    otherColor: Color
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val percentFormat = remember(locale) {
        java.text.NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }
    }
    val total = tags.sumOf { it.durationMs }.toDouble()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = AppShapes.standard,
            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            contentPadding = PaddingValues(horizontal = AppSpacing.lg, vertical = AppSpacing.sm)
        ) {
            Text(stringResource(if (expanded) R.string.reading_stats_hide_tags else R.string.reading_stats_show_tags),
                modifier = Modifier.weight(1f), style = AppTypography.labelLarge, textAlign = TextAlign.Start)
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null,
                modifier = Modifier.size(20.dp))
        }
        if (expanded) {
            tags.forEach { tag ->
                val color = tagColors[tag.tag] ?: otherColor
                val fraction = (tag.durationMs / total).toFloat().coerceIn(0f, 1f)
                Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.xs)
                    .semantics(mergeDescendants = true) {},
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(AppSpacing.sm).background(color, CircleShape))
                        Text(tag.tag.orEmpty(), modifier = Modifier.weight(1f), style = AppTypography.bodyLarge,
                            color = color)
                        Text(percentFormat.format(tag.durationMs / total), style = AppTypography.bodyLarge,
                            color = color)
                    }
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(AppShapes.pill)
                        .background(color.copy(alpha = .1f))) {
                        Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(AppShapes.pill).background(color))
                    }
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
    val heatColor = MaterialTheme.colorScheme.primary
    val shades = listOf(
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f),
        heatColor.copy(alpha = .25f),
        heatColor.copy(alpha = .5f),
        heatColor.copy(alpha = .75f),
        heatColor
    )
    Column(Modifier.fillMaxWidth().horizontalScroll(scrollState)) {
        Box(Modifier.width((weekCount * 22 - 4).dp).height(24.dp)) {
            repeat(weekCount) { week ->
                val start = firstWeek.plusWeeks(week.toLong())
                val monthStart = (0L..6L).map { start.plusDays(it) }
                    .firstOrNull { it.dayOfMonth == 1 && !it.isBefore(firstDate) && !it.isAfter(summary.today) }
                if (monthStart != null) {
                    Text(
                        monthStart.month.getDisplayName(TextStyle.SHORT, locale),
                        modifier = Modifier.offset(x = (week * 22).dp),
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
                            duration <= 0L -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)
                            else -> when (duration.toDouble() / maximumDuration) {
                                in 0.0..0.25 -> shades[1]
                                in 0.25..0.5 -> shades[2]
                                in 0.5..0.75 -> shades[3]
                                else -> shades[4]
                            }
                        }
                        val description = stringResource(R.string.reading_stats_day_detail, date.toString(), formatDuration(duration))
                        Box(
                            Modifier.size(18.dp).clip(RoundedCornerShape(3.dp))
                                .background(shade)
                                .then(if (inRange && date == selectedDate) Modifier.border(2.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(3.dp)) else Modifier)
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
    val dates = remember(summary.today, days) {
        (days - 1 downTo 0).map { summary.today.minusDays(it.toLong()) }
    }
    val values = remember(summary, dates) { dates.map { summary.daily[it] ?: 0L } }
    val total = values.sum()
    val maximum = values.maxOrNull() ?: 0L
    // Two equal intervals, rounded up to readable whole-minute ticks.
    val peakMinutes = maximum / 60_000.0
    val tickStep = when {
        peakMinutes <= 10 -> 1L
        peakMinutes <= 60 -> 5L
        peakMinutes <= 180 -> 15L
        else -> 30L
    }
    val halfScaleMinutes = (kotlin.math.ceil(peakMinutes / (2 * tickStep)).toLong()
        .coerceAtLeast(1L)) * tickStep
    val scaleMs = halfScaleMinutes * 2 * 60_000L
    val colors = MaterialTheme.colorScheme
    val accent = colors.primary
    val barBase = colors.primary.copy(alpha = 0.45f)
    val muted = colors.onSurfaceVariant
    val grid = colors.outlineVariant.copy(alpha = .5f)
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("M/d", locale) }
    val numberFormat = remember(locale) { java.text.NumberFormat.getIntegerInstance(locale) }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = muted, fontFeatureSettings = "tnum")
    val tickLabels = listOf(halfScaleMinutes * 2, halfScaleMinutes, 0L).map {
        textMeasurer.measure(numberFormat.format(it), labelStyle)
    }
    val dateIndices = if (days == 7) listOf(0, 2, 4, 6) else listOf(0, 7, 14, 21, 29)
    val dateLabels = dateIndices.map { textMeasurer.measure(dates[it].format(formatter), labelStyle) }
    // Provide actual daily values to accessibility, including days without reading.
    val dailyDescriptions = dates.indices.map { index ->
        stringResource(R.string.reading_stats_day_detail, dates[index].toString(), formatDuration(values[index]))
    }.joinToString("; ")
    val reveal = remember(days, lineChart, values) { Animatable(0f) }
    LaunchedEffect(reveal) { reveal.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            StatValue(stringResource(R.string.reading_stats_period_total), formatDuration(total), Modifier.weight(1f))
            StatValue(stringResource(R.string.reading_stats_daily_average), formatDuration(total / days), Modifier.weight(1f))
        }
        Text(stringResource(R.string.reading_stats_axis_minutes), style = MaterialTheme.typography.labelSmall, color = muted)
        Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.fillMaxWidth().height(184.dp).semantics { contentDescription = dailyDescriptions }) {
                val top = tickLabels.maxOf { it.size.height } / 2f + 4.dp.toPx()
                val left = tickLabels.maxOf { it.size.width } + 12.dp.toPx()
                val right = size.width - 8.dp.toPx()
                val baseline = size.height - dateLabels.maxOf { it.size.height } - 16.dp.toPx()
                val plotHeight = (baseline - top).coerceAtLeast(0f)
                val plotWidth = (right - left).coerceAtLeast(0f)
                val slot = plotWidth / days
                val points = values.mapIndexed { index, value ->
                    Offset(left + slot * (index + .5f), baseline - plotHeight * (value.toDouble() / scaleMs).toFloat() * reveal.value)
                }
                tickLabels.forEachIndexed { index, label ->
                    val y = top + plotHeight * index / 2
                    drawText(label, topLeft = Offset(left - 12.dp.toPx() - label.size.width, y - label.size.height / 2f))
                    drawLine(grid, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx(),
                        pathEffect = if (index == 2) null else PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx())))
                }
                dateIndices.forEachIndexed { labelIndex, dayIndex ->
                    val label = dateLabels[labelIndex]
                    drawText(label, topLeft = Offset(
                        (points[dayIndex].x - label.size.width / 2f).coerceIn(left, maxOf(left, size.width - label.size.width)),
                        baseline + 12.dp.toPx()
                    ))
                }
                if (maximum > 0L && lineChart) {
                    val line = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.zipWithNext().forEach { (start, end) ->
                            // Horizontal tangents keep each curve between its daily values, including zero.
                            val middle = (start.x + end.x) / 2
                            cubicTo(middle, start.y, middle, end.y, end.x, end.y)
                        }
                    }
                    val area = Path().apply {
                        addPath(line)
                        lineTo(points.last().x, baseline)
                        lineTo(points.first().x, baseline)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(
                        listOf(accent.copy(alpha = .22f), accent.copy(alpha = .015f)), startY = top, endY = baseline))
                    drawPath(line, accent, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                    val markerIndices = if (days == 7) values.indices.toList() else listOf(values.indexOf(maximum), values.lastIndex).distinct()
                    markerIndices.forEach { index ->
                        drawCircle(colors.surface, 4.dp.toPx(), points[index])
                        drawCircle(accent, 2.5.dp.toPx(), points[index])
                    }
                } else if (maximum > 0L) {
                    val barWidth = minOf(if (days == 7) 24.dp.toPx() else 8.dp.toPx(), slot * .6f)
                    values.forEachIndexed { index, value ->
                        val height = baseline - points[index].y
                        if (value > 0L && height > 0f) {
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    listOf(accent.copy(alpha = if (value == maximum) 1f else .78f), barBase.copy(alpha = .65f)),
                                    startY = points[index].y, endY = baseline
                                ),
                                topLeft = Offset(points[index].x - barWidth / 2, points[index].y),
                                size = Size(barWidth, height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(minOf(barWidth / 2, height / 2, 5.dp.toPx()))
                            )
                        }
                    }
                }
            }
            if (maximum == 0L) {
                Text(stringResource(R.string.reading_stats_no_books), style = AppTypography.bodySmall,
                    color = muted, modifier = Modifier.align(Alignment.Center)
                        .background(colors.surface, AppShapes.compact).padding(AppSpacing.sm))
            }
        }
    }
}

@Composable
private fun BookRank(rank: Int, book: ReadingBookTotal, maximumDuration: Long) {
    val colors = MaterialTheme.colorScheme
    val leading = rank == 1
    val fraction = (book.durationMs.toDouble() / maximumDuration.coerceAtLeast(1L)).toFloat().coerceIn(0f, 1f)
    Row(
        Modifier.fillMaxWidth().clip(AppShapes.standard)
            .background(if (leading) colors.primaryContainer else colors.surfaceContainerLow)
            .padding(AppSpacing.md).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.size(32.dp).clip(AppShapes.compact)
                .background(if (leading) colors.primary else colors.surface),
            contentAlignment = Alignment.Center
        ) {
            Text(rank.toString().padStart(2, '0'), style = AppTypography.labelLarge,
                color = if (leading) colors.onPrimary else colors.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Text(book.bookName, style = AppTypography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (leading) colors.onPrimaryContainer else colors.onSurface)
            Text(formatDuration(book.durationMs), style = AppTypography.bodySmall,
                color = if (leading) colors.onPrimaryContainer.copy(alpha = .7f) else colors.onSurfaceVariant)
            Box(Modifier.fillMaxWidth().height(4.dp).clip(AppShapes.pill)
                .background(colors.onSurface.copy(alpha = .06f))) {
                Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(AppShapes.pill)
                    .background(colors.primary.copy(alpha = if (leading) .85f else .35f)))
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

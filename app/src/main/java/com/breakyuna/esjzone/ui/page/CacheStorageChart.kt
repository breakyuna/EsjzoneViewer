package com.breakyuna.esjzone.ui.page

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.AppMotion
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import java.text.NumberFormat

/** App installation and data usage, with matching colors in the ring and legend. */
@Composable
internal fun CacheStorageChart(storage: List<StorageUsage>?, failed: Boolean) {
    val snapshot = storage?.takeUnless { failed }
    val available = snapshot != null
    val usage = snapshot.orEmpty()
    val totalBytes = usage.sumOf { it.bytes }
    var expandedCategories by rememberSaveable { mutableIntStateOf(0) }
    val fractions = usage.map { entry ->
        key(entry.category) {
            val fraction = if (totalBytes > 0) entry.bytes.toDouble() / totalBytes else 0.0
            val animated by animateFloatAsState(
                targetValue = fraction.toFloat(),
                animationSpec = AppMotion.expressiveSpec(),
                label = "storageFraction${entry.category}"
            )
            val detailProgress by animateFloatAsState(
                targetValue = if (expandedCategories and (1 shl entry.category.ordinal) != 0) 1f else 0f,
                animationSpec = AppMotion.expressiveSpec(),
                label = "storageDetails${entry.category}"
            )
            animated to detailProgress
        }
    }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(available, totalBytes > 0) {
        reveal.animateTo(if (available && totalBytes > 0) 1f else 0f, AppMotion.expressiveSpec())
    }
    val colors = MaterialTheme.colorScheme
    val trackColor = colors.outlineVariant.copy(alpha = .3f)
    val locale = LocalConfiguration.current.locales[0]
    val percentFormat = remember(locale) {
        NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(AppSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        Text(
            stringResource(R.string.settings_cache_analysis),
            modifier = Modifier.fillMaxWidth().semantics { heading() },
            style = AppTypography.titleMedium
        )
        Box(Modifier.size(196.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 18.dp.toPx()
                val diameter = size.minDimension - stroke
                val topLeft = Offset(stroke / 2, stroke / 2)
                val arcSize = Size(diameter, diameter)
                drawCircle(trackColor, radius = diameter / 2, style = Stroke(stroke))
                var startAngle = -90f
                fractions.forEachIndexed { index, (fraction, detailProgress) ->
                    val entry = usage[index]
                    val sweep = fraction * 360f
                    // Scale the gap for small slices so their relative size stays visible.
                    val gap = if (sweep < 360f) minOf(3f, sweep / 4f) else 0f
                    if (sweep > 0f && detailProgress == 0f) {
                        drawArc(
                            color = storageColor(entry.category),
                            startAngle = startAngle + gap / 2,
                            sweepAngle = (sweep - gap) * reveal.value,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(stroke)
                        )
                    } else {
                        val parts = entry.parts.withIndex().filter { it.value.bytes > 0L }
                        var partStart = startAngle + gap / 2
                        parts.forEachIndexed { partIndex, (colorIndex, part) ->
                            val partSweep = (sweep - gap) * (part.bytes.toDouble() / entry.bytes).toFloat()
                            val detailGap = minOf(2f, partSweep / 4f) * detailProgress
                            val leadingGap = if (partIndex == 0) 0f else detailGap / 2
                            val trailingGap = if (partIndex == parts.lastIndex) 0f else detailGap / 2
                            if (partSweep > 0f) {
                                drawArc(
                                    color = lerp(storageColor(entry.category), storagePartColor(entry.category, colorIndex), detailProgress),
                                    startAngle = partStart + leadingGap,
                                    sweepAngle = (partSweep - leadingGap - trailingGap) * reveal.value,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(stroke)
                                )
                            }
                            partStart += partSweep
                        }
                    }
                    startAngle += sweep
                }
            }
            Column(
                modifier = Modifier.widthIn(max = 144.dp).semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                Text(
                    stringResource(R.string.settings_cache_total),
                    style = AppTypography.labelMedium,
                    color = colors.onSurfaceVariant
                )
                if (!available && !failed) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        if (available) formatBytes(totalBytes) else "—",
                        style = AppTypography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        if (available) {
            usage.forEach { entry ->
                key(entry.category) {
                    val expanded = expandedCategories and (1 shl entry.category.ordinal) != 0
                    Column(Modifier.fillMaxWidth()) {
                        CacheChartLegend(
                            stringResource(storageLabel(entry.category)), storageColor(entry.category),
                            formatBytes(entry.bytes),
                            percentFormat.format(if (totalBytes > 0) entry.bytes.toDouble() / totalBytes else 0.0),
                            Modifier.clickable(
                                role = Role.Button,
                                onClickLabel = stringResource(if (expanded) R.string.storage_collapse_details else R.string.storage_expand_details)
                            ) { expandedCategories = expandedCategories xor (1 shl entry.category.ordinal) }
                        )
                        AnimatedVisibility(
                            visible = expanded,
                            enter = expandVertically(AppMotion.expressiveSpec()) + fadeIn(AppMotion.standardSpec()),
                            exit = shrinkVertically(AppMotion.expressiveSpec()) + fadeOut(AppMotion.standardSpec())
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(start = AppSpacing.lg, top = AppSpacing.md),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                            ) {
                                entry.parts.forEachIndexed { index, part ->
                                    CacheChartLegend(
                                        stringResource(part.labelRes), storagePartColor(entry.category, index),
                                        formatBytes(part.bytes),
                                        percentFormat.format(if (totalBytes > 0) part.bytes.toDouble() / totalBytes else 0.0)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Text(
                stringResource(if (failed) R.string.storage_usage_failed else R.string.storage_usage_loading),
                style = AppTypography.bodySmall,
                color = if (failed) colors.error else colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CacheChartLegend(label: String, color: Color, size: String, percentage: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, modifier = Modifier.weight(1f), style = AppTypography.bodyMedium)
        Column(horizontalAlignment = Alignment.End) {
            Text(size, style = AppTypography.labelLarge)
            Text(percentage, style = AppTypography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun storagePartColor(category: StorageCategory, index: Int): Color {
    val base = storageColor(category)
    if (index == 0) return base
    return lerp(base, if (index % 2 == 1) Color.White else Color.Black, ((index + 1) / 2) * .22f)
}

private fun storageLabel(category: StorageCategory): Int = when (category) {
    StorageCategory.APPLICATION -> R.string.storage_application
    StorageCategory.DOWNLOADS -> R.string.storage_downloads
    StorageCategory.CACHE -> R.string.storage_cache
    StorageCategory.BACKUPS -> R.string.storage_backups
    StorageCategory.READING_RESOURCES -> R.string.storage_reading_resources
    StorageCategory.APP_DATA -> R.string.storage_app_data
}

private fun storageColor(category: StorageCategory): Color = when (category) {
    StorageCategory.APPLICATION -> Color(0xFF5470C6)
    StorageCategory.DOWNLOADS -> Color(0xFF2E9D68)
    StorageCategory.CACHE -> Color(0xFFEE9B32)
    StorageCategory.BACKUPS -> Color(0xFF9B6BD3)
    StorageCategory.READING_RESOURCES -> Color(0xFF4AA8C0)
    StorageCategory.APP_DATA -> Color(0xFFD65A51)
}

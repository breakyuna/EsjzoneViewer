package com.breakyuna.esjzone.ui.product

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.ui.designsystem.AppCoverImage
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTheme
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.appStateColors

@Immutable
data class NovelCoverModel(val model: Any?, val contentDescription: String, val aspectRatio: Float = 0.7f)

@Composable
fun NovelCover(model: NovelCoverModel, modifier: Modifier = Modifier) {
    AppCoverImage(
        model = model.model,
        contentDescription = model.contentDescription,
        modifier = modifier.aspectRatio(model.aspectRatio).clip(AppShapes.standard)
    )
}

@Immutable
data class NovelTagModel(val label: String, val selected: Boolean = false)

@Composable
fun NovelTag(
    tag: NovelTagModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = appStateColors()
    AssistChip(
        onClick = { onClick?.invoke() },
        label = { Text(tag.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier.semantics {
            selected = tag.selected
            if (onClick != null) role = Role.Button
        },
        enabled = onClick != null,
        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
            containerColor = if (tag.selected) colors.containerAccent else colors.containerRaised
        )
    )
}

@Immutable
data class NovelMetricModel(val label: String, val value: String)

@Composable
fun NovelMetric(metric: NovelMetricModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(metric.value, style = AppTypography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(metric.label, style = AppTypography.bodySmall, color = appStateColors().contentMuted, maxLines = 1)
    }
}

@Immutable
data class NovelMetadataModel(
    val author: String? = null,
    val description: String? = null,
    val tags: List<NovelTagModel> = emptyList(),
    val metrics: List<NovelMetricModel> = emptyList()
)

@Composable
fun NovelMetadata(
    metadata: NovelMetadataModel,
    modifier: Modifier = Modifier,
    showMetrics: Boolean = true,
    onTagClick: ((NovelTagModel) -> Unit)? = null
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        metadata.author?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = AppTypography.labelLarge, color = appStateColors().contentMuted)
        }
        metadata.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = AppTypography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (metadata.tags.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                metadata.tags.forEach { tag -> NovelTag(tag, onClick = onTagClick?.let { callback -> { callback(tag) } }) }
            }
        }
        if (showMetrics && metadata.metrics.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                metadata.metrics.forEach { NovelMetric(it) }
            }
        }
    }
}

@Immutable
data class NovelCardModel(
    val id: String,
    val title: String,
    val cover: NovelCoverModel,
    val metadata: NovelMetadataModel = NovelMetadataModel(),
    val progressLabel: String? = null
)

@Composable
fun NovelCard(
    novel: NovelCardModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = modifier.semantics { if (onClick != null) role = Role.Button },
        shape = AppShapes.standard,
        colors = CardDefaults.cardColors(containerColor = appStateColors().containerRaised)
    ) {
        Row(modifier = Modifier.padding(AppSpacing.md), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            NovelCover(novel.cover, Modifier.width(96.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text(novel.title, style = AppTypography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                NovelMetadata(novel.metadata, modifier = Modifier.fillMaxWidth())
                novel.progressLabel?.let {
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text(it, style = AppTypography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun NovelHero(
    novel: NovelCardModel,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
    actionLabel: String? = null
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = AppShapes.prominent, tonalElevation = 2.dp) {
        Row(modifier = Modifier.padding(AppSpacing.lg), horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
            NovelCover(novel.cover, Modifier.width(132.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 188.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    Text(novel.title, style = AppTypography.displayMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    NovelMetadata(novel.metadata, showMetrics = false)
                }
                if (novel.metadata.metrics.isNotEmpty() || (actionLabel != null && onAction != null)) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        if (novel.metadata.metrics.isNotEmpty()) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
                            ) {
                                novel.metadata.metrics.forEach { NovelMetric(it) }
                            }
                        }
                        if (actionLabel != null && onAction != null) {
                            androidx.compose.material3.TextButton(onClick = onAction) { Text(actionLabel) }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun NovelCardPreview() {
    AppTheme { NovelCard(sampleNovel(), Modifier.padding(AppSpacing.lg)) }
}

@Preview(showBackground = true, widthDp = 700)
@Composable
private fun NovelHeroPreview() {
    AppTheme { NovelHero(sampleNovel(), Modifier.padding(AppSpacing.lg), actionLabel = "继续阅读", onAction = {}) }
}

private fun sampleNovel() = NovelCardModel(
    id = "preview",
    title = "星海边的长夜",
    cover = NovelCoverModel(null, "作品封面"),
    metadata = NovelMetadataModel(
        author = "示例作者",
        description = "这是一段用于组件预览的简介文本，实际内容由业务层提供。",
        tags = listOf(NovelTagModel("奇幻"), NovelTagModel("恋爱")),
        metrics = listOf(NovelMetricModel("阅读", "12.4万"), NovelMetricModel("字数", "86万"))
    ),
    progressLabel = "已读至第 12 章"
)

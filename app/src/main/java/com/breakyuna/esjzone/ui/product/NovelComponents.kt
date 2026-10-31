package com.breakyuna.esjzone.ui.product

import androidx.compose.foundation.clickable
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
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.contentArrival
import androidx.compose.foundation.border
import com.breakyuna.esjzone.ui.designsystem.AppLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.ui.designsystem.AppCoverBackdrop
import com.breakyuna.esjzone.ui.designsystem.AppCoverImage
import com.breakyuna.esjzone.ui.designsystem.AppElevation
import com.breakyuna.esjzone.ui.designsystem.AppLoadedImage
import com.breakyuna.esjzone.novellibrary.novel.CoverState
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTheme
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.appAdultColors
import com.breakyuna.esjzone.ui.designsystem.appStateColors

@Immutable
data class NovelCoverModel(
    val model: Any?,
    val contentDescription: String,
    val aspectRatio: Float = 0.7f,
    val state: CoverState? = null,
    val cachedModel: Any? = null,
    val initialImage: AppLoadedImage? = null
)

@Composable
fun NovelCover(model: NovelCoverModel, modifier: Modifier = Modifier, shape: Shape = AppShapes.cover) {
    AppCoverImage(
        model = model.model,
        sourceState = model.state,
        cachedModel = model.cachedModel,
        initialImage = model.initialImage,
        contentDescription = model.contentDescription,
        modifier = modifier
            .aspectRatio(model.aspectRatio)
            .shadow(
                elevation = AppElevation.cover,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.2f),
                spotColor = Color.Black.copy(alpha = 0.25f)
            )
            .clip(shape)
            .border(AppLayout.hairline, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), shape)
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
    val isAdultTag = tag.label.contains("R18", ignoreCase = true) ||
        tag.label.contains("18+") ||
        tag.label.contains("成人") ||
        tag.label.contains("限制")
    val adultColors = appAdultColors()
    AssistChip(
        onClick = { onClick?.invoke() },
        label = { Text(tag.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier.semantics {
            selected = tag.selected
            if (onClick != null) role = Role.Button
        },
        enabled = onClick != null,
        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
            containerColor = if (isAdultTag) androidx.compose.ui.graphics.Color.Transparent
            else if (tag.selected) colors.containerAccent else colors.containerRaised,
            labelColor = if (isAdultTag) adultColors.container else MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = if (isAdultTag) androidx.compose.ui.graphics.Color.Transparent else androidx.compose.ui.graphics.Color.Unspecified,
            disabledLabelColor = if (isAdultTag) adultColors.container else androidx.compose.ui.graphics.Color.Unspecified
        ),
        border = if (isAdultTag) {
            androidx.compose.material3.AssistChipDefaults.assistChipBorder(
                enabled = onClick != null,
                borderColor = adultColors.outline,
                disabledBorderColor = adultColors.outline
            )
        } else {
            androidx.compose.material3.AssistChipDefaults.assistChipBorder(enabled = onClick != null)
        }
    )
}

@Immutable
data class NovelMetricModel(val label: String, val value: String)

@Composable
fun NovelMetric(
    metric: NovelMetricModel,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    labelColor: Color = appStateColors().contentMuted
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            metric.value,
            style = AppTypography.labelLarge,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            metric.label,
            style = AppTypography.bodySmall,
            color = labelColor,
            maxLines = 1
        )
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
    onTagClick: ((NovelTagModel) -> Unit)? = null,
    authorModifier: Modifier = Modifier,
    primaryTextColor: Color = MaterialTheme.colorScheme.onSurface,
    mutedTextColor: Color = appStateColors().contentMuted
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        metadata.author?.takeIf { it.isNotBlank() }?.let {
            Text(it, modifier = authorModifier, style = AppTypography.labelLarge, color = mutedTextColor)
        }
        metadata.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = AppTypography.bodyMedium, color = primaryTextColor, maxLines = 3, overflow = TextOverflow.Ellipsis)
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
                metadata.metrics.forEach { NovelMetric(it, valueColor = primaryTextColor, labelColor = mutedTextColor) }
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
    actionLabel: String? = null,
    onCoverClick: (() -> Unit)? = null,
    coverModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    authorModifier: Modifier = Modifier,
    animateContentArrival: Boolean = true
) {
    val coverUrl = (novel.cover.model as? String).orEmpty()
    val coverState = novel.cover.state ?: CoverState.Unknown
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.prominent,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        AppCoverBackdrop(
            coverUrl = coverUrl,
            coverState = coverState,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NovelCover(
                    novel.cover,
                    Modifier.then(coverModifier).width(104.dp).then(
                        if (onCoverClick != null) Modifier.clickable(onClick = onCoverClick) else Modifier
                    ),
                    shape = AppShapes.cover
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (animateContentArrival) Modifier.contentArrival(novel.id, delayMillis = 80) else Modifier)
                        .heightIn(min = 148.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        Text(
                            novel.title,
                            modifier = titleModifier,
                            style = AppTypography.titleLarge,
                            color = Color.White,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        NovelMetadata(
                            novel.metadata,
                            showMetrics = false,
                            authorModifier = authorModifier,
                            primaryTextColor = Color.White.copy(alpha = 0.88f),
                            mutedTextColor = Color.White.copy(alpha = 0.78f)
                        )
                    }
                    if (novel.metadata.metrics.isNotEmpty() || (actionLabel != null && onAction != null)) {
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                            if (novel.metadata.metrics.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)
                                ) {
                                    novel.metadata.metrics.forEach {
                                        NovelMetric(
                                            it,
                                            valueColor = Color.White,
                                            labelColor = Color.White.copy(alpha = 0.72f)
                                        )
                                    }
                                }
                            }
                            if (actionLabel != null && onAction != null) {
                                androidx.compose.material3.TextButton(onClick = onAction) {
                                    Text(actionLabel, color = Color.White)
                                }
                            }
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
    AppTheme { NovelHero(sampleNovel(), Modifier.padding(AppSpacing.md), actionLabel = "继续阅读", onAction = {}) }
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

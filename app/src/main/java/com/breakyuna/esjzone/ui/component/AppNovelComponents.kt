package com.breakyuna.esjzone.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.designsystem.AppContentType
import com.breakyuna.esjzone.ui.designsystem.AppImage
import com.breakyuna.esjzone.ui.designsystem.AppImageLoading
import com.breakyuna.esjzone.ui.designsystem.AppLayout
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography

/** A compact semantic tag used for genres, content warnings, and filters. */
@Composable
fun AppTag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier
                    .semantics { role = Role.Button }
                    .clickable(onClick = onClick)
            } else Modifier
        ),
        shape = AppShapes.pill,
        color = containerColor
    ) {
        Text(
            text = text,
            style = AppTypography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)
        )
    }
}

/**
 * Cover image facade. URL normalization stays in the component boundary while
 * the actual loader and state handling remain inside [AppImage].
 */
@Composable
fun AppNovelCover(
    coverUrl: String,
    title: String,
    modifier: Modifier,
    isAdult: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(AppShapes.compact)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        AppImage(
            model = EsjzoneUrls.coverOrEmpty(coverUrl).takeIf(String::isNotBlank)
                ?: R.drawable.missing_cover,
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            loading = { AppImageLoading() },
            error = {
                Image(
                    painter = painterResource(R.drawable.missing_cover),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        )
        if (isAdult) {
            AppTag(
                text = stringResource(R.string.adult_badge),
                color = MaterialTheme.colorScheme.onErrorContainer,
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(AppSpacing.sm)
            )
        }
    }
}

/** A metric with an icon and a visible value; callers may provide an accessible label. */
@Composable
fun AppMetric(
    icon: ImageVector,
    value: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    contentDescription: String? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(16.dp), tint = tint)
        Text(value, style = AppTypography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Featured card for a horizontal discovery rail. */
@Composable
fun AppFeaturedNovelCard(
    novel: CoveredNovel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .widthIn(min = AppLayout.featureCoverWidth + 160.dp, max = 320.dp)
            .then(novelClickModifier(onClick)),
        shape = AppShapes.prominent,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(AppSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                AppNovelCover(
                    coverUrl = novel.coverUrl,
                    title = novel.name,
                    modifier = Modifier.size(AppLayout.featureCoverWidth, AppLayout.featureCoverHeight),
                    isAdult = novel.isAdult
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(AppLayout.featureCoverHeight),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = novel.name,
                        style = AppTypography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    novel.author?.trim()?.takeIf(String::isNotBlank)?.let {
                        Text(
                            text = it,
                            style = AppTypography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppSpacing.md)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, AppShapes.compact)
                    .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppMetric(Icons.Filled.RemoveRedEye, formatNovelCount(novel.views), tint = MaterialTheme.colorScheme.primary)
                AppMetric(Icons.Filled.ThumbUp, formatNovelCount(novel.likes), tint = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

/** Standard list card for search, category, and history-like destinations. */
@Composable
fun AppNovelListItem(
    novel: CoveredNovel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    compact: Boolean = false,
    summary: String? = null
) {
    val coverWidth = if (compact) AppLayout.listCoverWidth - 4.dp else AppLayout.listCoverWidth + 8.dp
    val coverHeight = if (compact) AppLayout.listCoverHeight - 4.dp else AppLayout.listCoverHeight + 12.dp
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(novelClickModifier(onClick)),
        shape = AppShapes.standard,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(if (compact) AppSpacing.sm else AppSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppNovelCover(
                coverUrl = novel.coverUrl,
                title = novel.name,
                modifier = Modifier.size(coverWidth, coverHeight),
                isAdult = novel.isAdult
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = coverHeight),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = novel.name,
                    style = if (compact) AppTypography.titleMedium else AppTypography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (compact) 3 else if (summary.isNullOrBlank()) 4 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        style = AppTypography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = AppSpacing.xs)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppMetric(Icons.Filled.RemoveRedEye, formatNovelCount(novel.views), tint = MaterialTheme.colorScheme.primary)
                    AppMetric(Icons.Filled.ThumbUp, formatNovelCount(novel.likes), tint = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

/** Shared discovery card for a home rail or a search result. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppNovelPreviewCard(
    novel: CoveredNovel,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showLatestChapter: Boolean = compact,
    onClick: (() -> Unit)? = null
) {
    val coverWidth = if (compact) 76.dp else 88.dp
    val coverHeight = if (compact) 96.dp else 120.dp
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(novelClickModifier(onClick)),
        shape = AppShapes.standard,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(if (compact) AppSpacing.sm else AppSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            verticalAlignment = Alignment.Top
        ) {
            AppNovelCover(
                coverUrl = novel.coverUrl,
                title = novel.name,
                modifier = Modifier.size(coverWidth, coverHeight),
                isAdult = novel.isAdult
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = coverHeight),
                verticalArrangement = Arrangement.spacedBy(if (compact) AppSpacing.xs else AppSpacing.sm)
            ) {
                Text(
                    text = novel.name,
                    style = AppTypography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis
                )
                novel.author?.trim()?.takeIf(String::isNotBlank)?.let {
                    Text(it, style = AppTypography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
                if (showLatestChapter) {
                    novel.latestTitle?.trim()?.takeIf(String::isNotBlank)?.let {
                        Text(it, style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                }
                Spacer(Modifier.weight(1f, fill = true))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    novel.views.takeIf { it > 0 }?.let {
                        AppMetric(Icons.Filled.RemoveRedEye, formatNovelCount(it), tint = MaterialTheme.colorScheme.primary)
                    }
                    novel.likes.takeIf { it > 0 }?.let {
                        AppMetric(Icons.Filled.ThumbUp, formatNovelCount(it), tint = MaterialTheme.colorScheme.tertiary)
                    }
                    novel.words?.takeIf { it > 0 }?.let {
                        AppMetric(Icons.Filled.Description, formatNovelCount(it))
                    }
                    novel.articleCount?.takeIf { it > 0 }?.let {
                        AppMetric(Icons.Filled.MenuBook, formatNovelCount(it))
                    }
                    novel.discussionCount?.takeIf { it > 0 }?.let {
                        AppMetric(Icons.Filled.ChatBubbleOutline, formatNovelCount(it))
                    }
                }
            }
        }
    }
}

private fun novelClickModifier(onClick: (() -> Unit)?): Modifier =
    if (onClick == null) Modifier else Modifier.semantics { role = Role.Button }.clickable(onClick = onClick)

/** Stable key for `LazyColumn`/`LazyVerticalGrid` items. */
fun appNovelKey(novel: CoveredNovel): String =
    novel.url.trim().ifBlank { error("Novel URL must not be blank when used as a list key") }

/** Content type for lazy item reuse; pair with [appNovelKey]. */
fun appNovelContentType(compact: Boolean): String =
    if (compact) AppContentType.novelCompact else AppContentType.novel

private fun formatNovelCount(count: Int): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

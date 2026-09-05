package com.breakyuna.esjzone.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.ui.theme.QuietEditorial

/** A low-chrome detail toolbar that leaves the title to the editorial hero. */
@Composable
fun NovelDetailTopBar(
    onBack: () -> Unit,
    onOpenExternal: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.reader_back)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (onOpenExternal != null) {
                    IconButton(onClick = onOpenExternal, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.novel_open_source)
                        )
                    }
                }
                if (onMore != null) {
                    IconButton(onClick = onMore, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.novel_more_actions)
                        )
                    }
                }
            }
            HorizontalDivider(
                thickness = QuietEditorial.hairline,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
            )
        }
    }
}

/** The cover and metadata block shared by the responsive detail layout. */
@Composable
fun NovelDetailHero(
    novel: DetailedNovel,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = QuietEditorial.contentMaxWidth),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        QuietNovelCover(
            coverUrl = novel.coverUrl,
            title = novel.name,
            isAdult = novel.isAdult,
            modifier = Modifier.size(width = 148.dp, height = 208.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = novel.name,
                style = QuietEditorial.display.copy(
                    fontSize = 24.sp,
                    lineHeight = 31.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            novel.type.trim().takeIf(String::isNotBlank)?.let { type ->
                NovelDetailMetadata(
                    icon = Icons.Filled.Topic,
                    label = stringResource(R.string.novel_type),
                    value = type
                )
            }
            novel.author.trim().takeIf(String::isNotBlank)?.let { author ->
                NovelDetailMetadata(
                    icon = Icons.Filled.Person,
                    label = stringResource(R.string.author),
                    value = author
                )
            }
            novel.updatedAt?.trim()?.takeIf(String::isNotBlank)?.let { updatedAt ->
                NovelDetailMetadata(
                    icon = Icons.Filled.Update,
                    label = stringResource(R.string.novel_updated),
                    value = updatedAt
                )
            }
        }
    }
}

@Composable
fun NovelDetailMetadata(
    label: String,
    value: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier
) {
    val cleanValue = value.trim().takeIf(String::isNotBlank) ?: return
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = "$label ·",
            style = QuietEditorial.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = cleanValue,
            style = QuietEditorial.body,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

private data class NovelDetailStat(
    val icon: ImageVector,
    val value: String,
    val label: String
)

/** Shows only parser-backed counters; zero is treated as unavailable. */
@Composable
fun NovelDetailStats(
    novel: DetailedNovel,
    modifier: Modifier = Modifier
) {
    val stats = buildList {
        if (novel.views > 0) {
            add(
                NovelDetailStat(
                    icon = Icons.Filled.RemoveRedEye,
                    value = novel.views.toString(),
                    label = stringResource(R.string.novel_views_label)
                )
            )
        }
        if (novel.likes > 0) {
            add(
                NovelDetailStat(
                    icon = Icons.Filled.ThumbUp,
                    value = novel.likes.toString(),
                    label = stringResource(R.string.novel_likes_label)
                )
            )
        }
        if (novel.words > 0) {
            add(
                NovelDetailStat(
                    icon = Icons.Filled.Topic,
                    value = novel.words.toString(),
                    label = stringResource(R.string.novel_words_label)
                )
            )
        }
    }
    if (stats.isEmpty()) return
    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = QuietEditorial.cardShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            stats.forEach { stat ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = stat.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = stat.value,
                        style = QuietEditorial.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stat.label,
                        style = QuietEditorial.smallLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun NovelDetailSectionHeading(
    title: String,
    supportingText: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = QuietEditorial.sectionTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        supportingText?.trim()?.takeIf(String::isNotBlank)?.let { support ->
            Text(
                text = support,
                style = QuietEditorial.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = QuietEditorial.label,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Filled.ArrowForwardIos,
                    contentDescription = actionLabel,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(15.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun NovelDetailTags(
    tags: List<String>,
    modifier: Modifier = Modifier
) {
    val visibleTags = tags.map(String::trim).filter(String::isNotBlank).distinct()
    if (visibleTags.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        visibleTags.forEach { tag ->
            QuietTag(text = tag, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

@Composable
fun NovelDetailRule(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = QuietEditorial.hairline,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}


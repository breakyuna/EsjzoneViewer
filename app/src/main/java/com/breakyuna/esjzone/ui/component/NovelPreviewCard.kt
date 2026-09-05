package com.breakyuna.esjzone.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent
import com.breakyuna.esjzone.ui.page.NovelPage
import com.breakyuna.esjzone.ui.theme.QuietEditorial
import com.breakyuna.esjzone.ui.theme.quietEditorialColors

/**
 * Shared discovery card for the home rail and search/list results.
 *
 * A compact card is used by the two-row home rail. A regular card is used by
 * search results and can show the additional list-only metadata. Both modes
 * intentionally keep the same surface, cover, typography and metric grammar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuietNovelPreviewCard(
    novel: CoveredNovel,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showLatestChapter: Boolean = compact,
    onClick: (() -> Unit)? = null
) {
    val navigator = LocalBaseNavigator.current
    val editorialColors = quietEditorialColors()
    val coverWidth = if (compact) 76.dp else 88.dp
    val coverHeight = if (compact) 96.dp else 120.dp
    val cardPadding = if (compact) 8.dp else 12.dp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                onClick?.invoke() ?: navigator?.pushIfNotCurrent(NovelPage(novel))
            },
        shape = QuietEditorial.cardShape,
        color = editorialColors.cardSurface
    ) {
        Row(
            modifier = Modifier.padding(cardPadding),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 11.dp else 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            QuietNovelCover(
                coverUrl = novel.coverUrl,
                title = novel.name,
                modifier = Modifier.size(width = coverWidth, height = coverHeight),
                isAdult = novel.isAdult
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = coverHeight),
                verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)
            ) {
                Text(
                    text = novel.name,
                    style = QuietEditorial.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis
                )

                novel.author
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { author ->
                        Text(
                            text = author,
                            style = QuietEditorial.label,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                if (showLatestChapter) {
                    novel.latestTitle
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { latest ->
                            Text(
                                text = "${stringResource(R.string.novel_latest_chapter)}：$latest",
                                style = QuietEditorial.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (compact) 2 else 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                }

                if (!compact) {
                    ListCardMetadata(novel)
                }

                Spacer(modifier = Modifier.weight(1f, fill = true))

                DiscoveryMetrics(
                    novel = novel
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListCardMetadata(novel: CoveredNovel) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        novel.articleCount?.let { count ->
            QuietMetric(
                icon = Icons.Filled.MenuBook,
                value = formatPreviewCount(count),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        novel.discussionCount?.let { count ->
            QuietMetric(
                icon = Icons.Filled.ChatBubbleOutline,
                value = formatPreviewCount(count),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        novel.words?.let { count ->
            QuietMetric(
                icon = Icons.Filled.Description,
                value = formatPreviewCount(count),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoveryMetrics(
    novel: CoveredNovel
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        novel.views
            .takeIf { it > 0 }
            ?.let { count ->
                QuietMetric(
                    icon = Icons.Filled.RemoveRedEye,
                    value = formatPreviewCount(count),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        novel.likes
            .takeIf { it > 0 }
            ?.let { count ->
                QuietMetric(
                    icon = Icons.Filled.ThumbUp,
                    value = formatPreviewCount(count),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
    }
}

private fun formatPreviewCount(count: Int): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

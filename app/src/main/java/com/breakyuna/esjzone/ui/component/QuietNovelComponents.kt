package com.breakyuna.esjzone.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.breakyuna.esjzone.MainActivity
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent
import com.breakyuna.esjzone.ui.page.NovelPage
import com.breakyuna.esjzone.ui.theme.QuietEditorial
import com.breakyuna.esjzone.ui.theme.quietEditorialColors

@Composable
fun QuietTag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
) {
    Surface(modifier = modifier, shape = QuietEditorial.badgeShape, color = containerColor) {
        Text(
            text = text,
            style = QuietEditorial.smallLabel,
            color = color,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun QuietNovelCover(
    coverUrl: String,
    title: String,
    modifier: Modifier,
    isAdult: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(QuietEditorial.coverShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(
                    EsjzoneUrls.coverOrEmpty(coverUrl)
                        .takeIf { it.isNotBlank() }
                        ?: R.drawable.missing_cover
                )
                .crossfade(true)
                .build(),
            contentDescription = title,
            imageLoader = MainActivity.imageLoader,
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            },
            error = {
                Image(
                    painter = painterResource(R.drawable.missing_cover),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (isAdult) {
            QuietTag(
                text = stringResource(R.string.adult_badge),
                color = MaterialTheme.colorScheme.onErrorContainer,
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
            )
        }
    }
}

@Composable
fun QuietMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = tint)
        Text(value, style = QuietEditorial.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun QuietFeaturedNovelCard(
    novel: CoveredNovel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val navigator = LocalBaseNavigator.current
    val editorialColors = quietEditorialColors()
    Surface(
        modifier = modifier.width(296.dp).clickable {
            onClick?.invoke() ?: navigator?.pushIfNotCurrent(NovelPage(novel))
        },
        shape = QuietEditorial.largeShape,
        color = editorialColors.cardSurface
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuietNovelCover(
                    coverUrl = novel.coverUrl,
                    title = novel.name,
                    modifier = Modifier.size(width = 104.dp, height = 140.dp),
                    isAdult = novel.isAdult
                )
                Column(
                    modifier = Modifier.weight(1f).height(140.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = novel.name,
                        style = QuietEditorial.cardTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.home_recommendation_label),
                        style = QuietEditorial.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.56f), QuietEditorial.controlShape)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuietMetric(Icons.Filled.RemoveRedEye, formatCount(novel.views), MaterialTheme.colorScheme.primary)
                QuietMetric(Icons.Filled.ThumbUp, formatCount(novel.likes), MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
fun QuietNovelListItem(
    novel: CoveredNovel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    compact: Boolean = false,
    summary: String? = null
) {
    val navigator = LocalBaseNavigator.current
    val editorialColors = quietEditorialColors()
    Surface(
        modifier = modifier.fillMaxWidth().clickable {
            onClick?.invoke() ?: navigator?.pushIfNotCurrent(NovelPage(novel))
        },
        shape = QuietEditorial.cardShape,
        color = editorialColors.cardSurface
    ) {
        Row(
            modifier = Modifier.padding(if (compact) 10.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuietNovelCover(
                coverUrl = novel.coverUrl,
                title = novel.name,
                modifier = Modifier.size(
                    width = if (compact) 84.dp else 96.dp,
                    height = if (compact) 116.dp else 132.dp
                ),
                isAdult = novel.isAdult
            )
            Column(
                modifier = Modifier.weight(1f).height(if (compact) 116.dp else 132.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = novel.name,
                    style = if (compact) QuietEditorial.title else QuietEditorial.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (compact) 3 else if (summary.isNullOrBlank()) 4 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        style = QuietEditorial.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuietMetric(Icons.Filled.RemoveRedEye, formatCount(novel.views), MaterialTheme.colorScheme.primary)
                    QuietMetric(Icons.Filled.ThumbUp, formatCount(novel.likes), MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

private fun formatCount(count: Int): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

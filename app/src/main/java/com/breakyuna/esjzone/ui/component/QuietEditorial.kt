package com.breakyuna.esjzone.ui.component

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.breakyuna.esjzone.MainActivity
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent
import com.breakyuna.esjzone.ui.page.NovelPage
import com.breakyuna.esjzone.ui.theme.QuietEditorial
import com.breakyuna.esjzone.ui.theme.quietEditorialColors

/** Flat, edge-to-edge top bar used by secondary discovery destinations. */
@Composable
fun QuietBackHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
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
                    .padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.reader_back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = title,
                    style = QuietEditorial.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                actions()
            }
            HorizontalDivider(
                thickness = QuietEditorial.hairline,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * Header for Home. Shortcuts represent only existing destinations; no
 * unbacked genre or filter data is introduced here.
 */
@Composable
fun QuietHomeHeader(
    domain: String,
    onSearch: () -> Unit,
    onCategories: () -> Unit,
    onForum: () -> Unit,
    onGuestbook: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = QuietEditorial.pagePadding, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_discover),
                        style = QuietEditorial.display,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MaterialTheme.colorScheme.tertiary)
                            )
                            Text(
                                text = stringResource(R.string.home_site_status, domain),
                                style = QuietEditorial.smallLabel,
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.home_tagline),
                    style = QuietEditorial.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            IconButton(
                onClick = onSearch,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.screen_main_tab_search),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuietShortcut(
                icon = Icons.Filled.Category,
                label = stringResource(R.string.categories),
                onClick = onCategories,
                modifier = Modifier.weight(1f)
            )
            QuietShortcut(
                icon = Icons.Filled.Forum,
                label = stringResource(R.string.forum),
                onClick = onForum,
                modifier = Modifier.weight(1f)
            )
            QuietShortcut(
                icon = Icons.Filled.ChatBubbleOutline,
                label = stringResource(R.string.guestbook),
                onClick = onGuestbook,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuietShortcut(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val editorialColors = quietEditorialColors()
    Surface(
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick),
        shape = QuietEditorial.controlShape,
        color = editorialColors.softSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = QuietEditorial.label,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun QuietSearchHeader(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.reader_back),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                textStyle = QuietEditorial.body,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_placeholder),
                        style = QuietEditorial.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (value.isNotEmpty()) {
                        IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
            Button(
                onClick = onSearch,
                enabled = enabled,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(18.dp),
                contentPadding = ButtonDefaults.ContentPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(stringResource(R.string.search_action), style = QuietEditorial.label)
            }
        }
    }
}

@Composable
fun QuietSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = QuietEditorial.pagePadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(accent)
        )
        Text(
            text = title,
            style = QuietEditorial.sectionTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        )
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction
            ) {
                Text(actionLabel, style = QuietEditorial.label)
                Icon(
                    imageVector = Icons.Filled.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 3.dp)
                        .size(14.dp)
                )
            }
        }
    }
}

@Composable
fun QuietTag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
) {
    Surface(
        modifier = modifier,
        shape = QuietEditorial.badgeShape,
        color = containerColor
    ) {
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
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
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = tint
        )
        Text(
            text = value,
            style = QuietEditorial.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        modifier = modifier
            .width(296.dp)
            .clickable {
                onClick?.invoke() ?: navigator?.pushIfNotCurrent(NovelPage(novel))
            },
        shape = QuietEditorial.largeShape,
        color = editorialColors.cardSurface
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuietNovelCover(
                    coverUrl = novel.coverUrl,
                    title = novel.name,
                    modifier = Modifier.size(width = 104.dp, height = 140.dp),
                    isAdult = novel.isAdult
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(140.dp),
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
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
                        QuietEditorial.controlShape
                    )
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuietMetric(
                    icon = Icons.Filled.RemoveRedEye,
                    value = formatCount(novel.views),
                    tint = MaterialTheme.colorScheme.primary
                )
                QuietMetric(
                    icon = Icons.Filled.ThumbUp,
                    value = formatCount(novel.likes),
                    tint = MaterialTheme.colorScheme.tertiary
                )
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
        modifier = modifier
            .fillMaxWidth()
            .clickable {
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
                modifier = Modifier
                    .weight(1f)
                    .height(if (compact) 116.dp else 132.dp),
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
                    QuietMetric(
                        icon = Icons.Filled.RemoveRedEye,
                        value = formatCount(novel.views),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    QuietMetric(
                        icon = Icons.Filled.ThumbUp,
                        value = formatCount(novel.likes),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

@Composable
fun QuietLoadingState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.5.dp
        )
        Text(
            text = stringResource(R.string.loading_content),
            style = QuietEditorial.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun QuietEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.History
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = QuietEditorial.pagePadding, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Text(
            text = title,
            style = QuietEditorial.title,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            text = message,
            style = QuietEditorial.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun QuietErrorState(
    failure: LoadFailureKind,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = QuietEditorial.pagePadding, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(
                if (failure == LoadFailureKind.NETWORK) {
                    R.string.load_network_error
                } else {
                    R.string.load_client_error
                }
            ),
            style = QuietEditorial.body,
            color = MaterialTheme.colorScheme.error,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.retry), style = QuietEditorial.label)
            }
        }
    }
}

@Composable
fun QuietNotice(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = QuietEditorial.cardShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.VisibilityOff,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = text,
                style = QuietEditorial.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Shared editorial row for secondary destinations and settings. */
@Composable
fun QuietSettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String? = null,
    modifier: Modifier = Modifier,
    checked: Boolean? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val rowModifier = modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
    Row(
        modifier = rowModifier.padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = QuietEditorial.title, color = MaterialTheme.colorScheme.onSurface)
            if (!summary.isNullOrBlank()) {
                Text(
                    summary,
                    style = QuietEditorial.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        when {
            trailing != null -> trailing()
            checked != null -> Icon(
                imageVector = if (checked) {
                    Icons.Filled.Check
                } else {
                    Icons.Filled.ArrowForwardIos
                },
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A thin rule-separated surface that keeps settings and lists visually calm. */
@Composable
fun QuietGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = QuietEditorial.cardShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
    ) {
        Column(content = content)
    }
}

@Composable
fun QuietBottomNavigation(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // Frosted glass background brush with high translucency
    val glassBgBrush = if (!isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.78f),
                Color.White.copy(alpha = 0.58f),
                Color(0xFFF2F2F7).copy(alpha = 0.68f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f)
            )
        )
    }

    // High-transparency specular refraction border (curved liquid glass edge)
    val glassBorderBrush = if (!isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.95f),
                Color.White.copy(alpha = 0.28f),
                Color.White.copy(alpha = 0.70f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.38f),
                Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.28f)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.22f)
                )
                .border(
                    width = 1.2.dp,
                    brush = glassBorderBrush,
                    shape = CircleShape
                ),
            shape = CircleShape,
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = glassBgBrush)
            ) {
                // Top subtle specular reflection sheen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    if (!isDark) Color.White.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.10f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Navigation tab items
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content
                )
            }
        }
    }
}

@Composable
fun RowScope.QuietBottomNavigationItem(
    tab: Tab,
    onDoubleClick: (() -> Unit)? = null
) {
    val tabNavigator = LocalTabNavigator.current
    val isSelected = tabNavigator.current == tab
    var lastTapAt by remember { mutableLongStateOf(0L) }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = if (!isDark) {
        Color(0xFF2C2C2E).copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    }

    val animatedIconColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else inactiveColor,
        animationSpec = tween(durationMillis = 200),
        label = "tab_icon_color"
    )

    val animatedTextColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else inactiveColor,
        animationSpec = tween(durationMillis = 200),
        label = "tab_text_color"
    )

    val pillShape = CircleShape

    // Active background pill gradient & border (matching screenshot with soft glowing pill)
    val pillBackground = when {
        isSelected && !isDark -> Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.65f),
                activeColor.copy(alpha = 0.14f)
            )
        )
        isSelected && isDark -> Brush.verticalGradient(
            listOf(
                activeColor.copy(alpha = 0.30f),
                activeColor.copy(alpha = 0.16f)
            )
        )
        else -> null
    }

    val pillBorder = if (isSelected) {
        BorderStroke(
            1.dp,
            if (!isDark) Color.White.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.22f)
        )
    } else null

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(pillShape)
            .then(
                if (pillBorder != null && pillBackground != null) {
                    Modifier
                        .border(pillBorder, pillShape)
                        .background(pillBackground, pillShape)
                } else Modifier
            )
            .clickable(
                onClick = {
                    val now = SystemClock.uptimeMillis()
                    val wasAlreadySelected = tabNavigator.current == tab
                    val isDoubleClick = onDoubleClick != null &&
                        wasAlreadySelected && now - lastTapAt in 1..420
                    if (isDoubleClick) {
                        lastTapAt = 0L
                        tabNavigator.current = tab
                        onDoubleClick.invoke()
                    } else {
                        lastTapAt = now
                        tabNavigator.current = tab
                    }
                }
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            tab.options.icon?.let { icon ->
                Icon(
                    painter = icon,
                    contentDescription = tab.options.title,
                    tint = animatedIconColor,
                    modifier = Modifier.size(23.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tab.options.title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = animatedTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
        count >= 1_000 -> "%.1fK".format(count / 1_000.0)
        else -> count.toString()
    }
}

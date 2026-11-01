package com.breakyuna.esjzone.ui.tab

import com.breakyuna.esjzone.ui.designsystem.appViewportSize
import com.breakyuna.esjzone.ui.designsystem.appUsesWideLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import com.breakyuna.esjzone.ui.designsystem.appReducedMotion
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.navigation.rememberNovelCoverSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.component.AppNovelCover
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.navigation.ContainerTransformTarget
import com.breakyuna.esjzone.ui.navigation.containerTransformContent
import com.breakyuna.esjzone.ui.navigation.containerTransformSource

internal const val HOME_GRID_COLUMNS = 4

@Composable
internal fun homeGridColumnCount(): Int {
    if (!appUsesWideLayout()) return HOME_GRID_COLUMNS
    val width = minOf(appViewportSize().width, 1280f) - 32f
    val cellWidth = 120f * LocalConfiguration.current.fontScale.coerceAtLeast(1f)
    return ((width + 8f) / (cellWidth + 8f)).toInt().coerceIn(4, 8)
}
private val HOME_SECTION_TITLE_PARENTHESIS = Regex("[（(][^（）()]*[）)]")

@Composable
internal fun HomeSearchBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(40.dp)
            .clip(AppShapes.pill)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.search_action),
                onClick = onClick
            ),
        shape = AppShapes.pill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.search_placeholder),
                style = AppTypography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun HomeActions(
    onForum: () -> Unit,
    onGuestbook: () -> Unit,
    onWaterCooler: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        HomeShortcut(
            label = stringResource(R.string.forum),
            icon = Icons.Filled.Forum,
            modifier = Modifier.weight(1f).containerTransformSource(ContainerTransformTarget.FORUM),
            iconTint = MaterialTheme.colorScheme.primary,
            onClick = onForum
        )
        HomeShortcut(
            label = stringResource(R.string.guestbook),
            icon = Icons.Filled.RateReview,
            modifier = Modifier.weight(1f).containerTransformSource(ContainerTransformTarget.GUESTBOOK),
            iconTint = MaterialTheme.colorScheme.tertiary,
            onClick = onGuestbook
        )
        HomeShortcut(
            label = stringResource(R.string.home_water_cooler),
            icon = Icons.Filled.WaterDrop,
            modifier = Modifier.weight(1f).containerTransformSource(ContainerTransformTarget.WATER_COOLER),
            iconTint = MaterialTheme.colorScheme.secondary,
            onClick = onWaterCooler
        )
    }
}

@Composable
private fun HomeShortcut(
    label: String,
    icon: ImageVector?,
    modifier: Modifier,
    iconTint: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = appReducedMotion()
    val pressScale = animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) 0.97f else 1f,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 1f, stiffness = 600f),
        label = "homeShortcutPress"
    )
    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.graphicsLayer {
            scaleX = pressScale.value
            scaleY = pressScale.value
        }.height(60.dp).semantics { contentDescription = label },
        shape = AppShapes.standard,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.containerTransformContent().fillMaxSize().padding(horizontal = AppSpacing.sm),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(AppSpacing.sm))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun NovelGridRow(
    row: List<CoveredNovel>,
    showLatestTitle: Boolean,
    onNovelClick: (CoveredNovel) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = homeGridColumnCount()
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        row.forEach { novel ->
            HomeGridNovelTile(
                novel = novel,
                showLatestTitle = showLatestTitle,
                onClick = { onNovelClick(novel) },
                modifier = Modifier.weight(1f)
            )
        }
        repeat(columns - row.size) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun HomeGridNovelTile(
    novel: CoveredNovel,
    showLatestTitle: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverSource = rememberNovelCoverSource(novel.url, novel.coverUrl, novel.coverState)
    Column(
        modifier = modifier.clickable { coverSource.open(onClick) },
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        AppNovelCover(
            coverUrl = novel.coverUrl,
            onImageLoaded = coverSource.onImageLoaded,
            initialImage = coverSource.initialImage,
            coverState = novel.coverState,
            title = novel.name,
            isAdult = novel.isAdult,
            modifier = Modifier.then(coverSource.modifier).fillMaxWidth().aspectRatio(0.7f)
        )
        Text(
            text = novel.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (showLatestTitle) {
            Text(
                text = novel.latestTitle?.trim().takeUnless { it.isNullOrBlank() }
                    ?: stringResource(R.string.home_weekly_update_no_chapter),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun HomeSectionDividerItem() {
    HorizontalDivider(
        modifier = Modifier.padding(top = AppSpacing.md, bottom = AppSpacing.zero),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    )
}

/** Gives optional parenthetical section descriptors a subordinate visual weight. */
@Composable
internal fun homeSectionTitleText(title: String): androidx.compose.ui.text.AnnotatedString {
    val descriptorFontSize = MaterialTheme.typography.labelLarge.fontSize
    return buildAnnotatedString {
        var cursor = 0
        HOME_SECTION_TITLE_PARENTHESIS.findAll(title).forEach { match ->
            append(title.substring(cursor, match.range.first))
            withStyle(SpanStyle(fontSize = descriptorFontSize)) {
                append(match.value)
            }
            cursor = match.range.last + 1
        }
        append(title.substring(cursor))
    }
}

internal fun novelKey(novel: CoveredNovel): String =
    novel.url.trim().ifBlank { novel.name.trim() }

internal fun failureMessage(failure: LoadFailureKind): Int = when (failure) {
    LoadFailureKind.NETWORK -> R.string.load_network_error
    LoadFailureKind.CLIENT -> R.string.load_client_error
}

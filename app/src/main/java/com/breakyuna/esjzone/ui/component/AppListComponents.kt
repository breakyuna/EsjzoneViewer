package com.breakyuna.esjzone.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.ui.designsystem.AppContentType
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography

/** Reusable comment row with an optional avatar and reply action. */
@Composable
fun AppCommentItem(
    author: String,
    body: String,
    timestamp: String? = null,
    modifier: Modifier = Modifier,
    avatar: (@Composable () -> Unit)? = null,
    replyLabel: String? = null,
    onReply: (() -> Unit)? = null,
    footer: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        verticalAlignment = Alignment.Top
    ) {
        if (avatar != null) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) { avatar() }
        } else {
            Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(author, style = AppTypography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                timestamp?.takeIf(String::isNotBlank)?.let {
                    Text(it, style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Text(body, style = AppTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (replyLabel != null && onReply != null) {
                    androidx.compose.material3.TextButton(
                        onClick = onReply,
                        modifier = Modifier.heightIn(min = AppSpacing.xxxl),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = AppSpacing.xs)
                    ) { Text(replyLabel, style = AppTypography.labelMedium) }
                }
                footer?.invoke(this)
            }
        }
    }
}

/** Download row supporting batch selection and an explicit destructive action. */
@Composable
fun AppDownloadItem(
    title: String,
    chapterSummary: String,
    sizeLabel: String,
    modifier: Modifier = Modifier,
    cover: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    onSelectedChange: ((Boolean) -> Unit)? = null,
    selectionContentDescription: String? = null,
    deleteContentDescription: String? = null,
    onDelete: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    AppListSurface(modifier = modifier, onClick = onClick, selected = selected) {
        if (cover != null) Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { cover() }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(title, style = AppTypography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(chapterSummary, style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(sizeLabel, style = AppTypography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        }
        if (onSelectedChange != null) {
            Checkbox(
                checked = selected,
                onCheckedChange = onSelectedChange,
                modifier = selectionContentDescription?.let {
                    Modifier.semantics { contentDescription = it }
                } ?: Modifier
            )
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = deleteContentDescription ?: title,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** Reading history row. The trailing slot can expose source or sync status. */
@Composable
fun AppHistoryItem(
    title: String,
    chapterTitle: String,
    modifier: Modifier = Modifier,
    cover: (@Composable () -> Unit)? = null,
    metadata: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    AppListSurface(modifier = modifier, onClick = onClick) {
        if (cover != null) Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { cover() }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(title, style = AppTypography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(chapterTitle, style = AppTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            metadata?.takeIf(String::isNotBlank)?.let {
                Text(it, style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        trailing?.invoke(this)
    }
}

/** Bookshelf row with a cover, recent-reading summary, and optional editing affordance. */
@Composable
fun AppBookshelfItem(
    title: String,
    modifier: Modifier = Modifier,
    cover: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
    progressText: String? = null,
    selected: Boolean = false,
    onSelectedChange: ((Boolean) -> Unit)? = null,
    selectionContentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    AppListSurface(modifier = modifier, onClick = onClick, selected = selected) {
        if (cover != null) Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) { cover() }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(title, style = AppTypography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            supportingText?.takeIf(String::isNotBlank)?.let {
                Text(it, style = AppTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            progressText?.takeIf(String::isNotBlank)?.let {
                Text(it, style = AppTypography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
        }
        if (onSelectedChange != null) {
            Checkbox(
                checked = selected,
                onCheckedChange = onSelectedChange,
                modifier = selectionContentDescription?.let {
                    Modifier.semantics { contentDescription = it }
                } ?: Modifier
            )
        }
        trailing?.invoke(this)
    }
}

@Composable
private fun AppListSurface(
    modifier: Modifier,
    onClick: (() -> Unit)?,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val selectionShape = AppShapes.standard
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            selectionShape
                        )
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.62f), selectionShape)
                } else {
                    Modifier
                }
            )
            .then(
                if (onClick != null) {
                    Modifier.semantics { role = Role.Button }.clickable(onClick = onClick)
                } else Modifier
            )
            .heightIn(min = 72.dp)
            .padding(AppSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** Stable caller-provided key for list items. Blank IDs are rejected early. */
fun appItemKey(id: String): String = id.trim().ifBlank { error("List item id must not be blank") }

const val appCommentContentType: String = AppContentType.comment
const val appDownloadContentType: String = AppContentType.download
const val appHistoryContentType: String = AppContentType.history
const val appBookshelfContentType: String = AppContentType.bookshelf

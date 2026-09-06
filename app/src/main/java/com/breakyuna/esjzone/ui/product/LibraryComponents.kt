package com.breakyuna.esjzone.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.ui.designsystem.AppImage
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTheme
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.appStateColors

@Immutable
data class DownloadItemModel(
    val id: String,
    val title: String,
    val cover: NovelCoverModel,
    val chapterLabel: String,
    val sizeLabel: String,
    val statusLabel: String? = null,
    val selected: Boolean = false
)

@Composable
fun DownloadItem(
    item: DownloadItemModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Card(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth().semantics { if (onClick != null) role = Role.Button },
        shape = AppShapes.standard,
        colors = CardDefaults.cardColors(containerColor = appStateColors().containerRaised)
    ) {
        Row(Modifier.padding(AppSpacing.md), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            AppImage(item.cover.model, item.cover.contentDescription, Modifier.size(width = 64.dp, height = 88.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text(item.title, style = AppTypography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.chapterLabel, style = AppTypography.bodyMedium)
                Text(item.sizeLabel, style = AppTypography.bodySmall, color = appStateColors().contentMuted)
                item.statusLabel?.let { Text(it, style = AppTypography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.semantics { role = Role.Button }) { Text("删除") }
            }
        }
    }
}

@Immutable
data class HistoryItemModel(
    val id: String,
    val title: String,
    val cover: NovelCoverModel,
    val chapterLabel: String,
    val timeLabel: String,
    val progressLabel: String? = null
)

@Composable
fun HistoryItem(item: HistoryItemModel, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Card(
        onClick = { onClick?.invoke() }, enabled = onClick != null,
        modifier = modifier.fillMaxWidth().semantics { if (onClick != null) role = Role.Button },
        colors = CardDefaults.cardColors(containerColor = appStateColors().containerRaised)
    ) {
        Row(Modifier.padding(AppSpacing.md), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            AppImage(item.cover.model, item.cover.contentDescription, Modifier.size(width = 64.dp, height = 88.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text(item.title, style = AppTypography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.chapterLabel, style = AppTypography.bodyMedium)
                item.progressLabel?.let { Text(it, style = AppTypography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                Text(item.timeLabel, style = AppTypography.bodySmall, color = appStateColors().contentMuted)
            }
        }
    }
}

@Immutable
data class BookshelfItemModel(
    val id: String,
    val title: String,
    val cover: NovelCoverModel,
    val author: String? = null,
    val progressLabel: String? = null,
    val selected: Boolean = false
)

@Composable
fun BookshelfItem(
    item: BookshelfItemModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onSelectionChange: ((Boolean) -> Unit)? = null
) {
    Card(
        onClick = { onClick?.invoke() }, enabled = onClick != null,
        modifier = modifier.semantics {
            if (onClick != null) role = Role.Button
        },
        shape = AppShapes.standard,
        colors = CardDefaults.cardColors(containerColor = appStateColors().containerRaised)
    ) {
        Column {
            NovelCover(item.cover, Modifier.fillMaxWidth())
            Column(Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text(item.title, style = AppTypography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.author?.let { Text(it, style = AppTypography.bodySmall, color = appStateColors().contentMuted) }
                item.progressLabel?.let { Text(it, style = AppTypography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                if (onSelectionChange != null) {
                    TextButton(onClick = { onSelectionChange(!item.selected) }) { Text(if (item.selected) "已选择" else "选择") }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun LibraryItemsPreview() {
    AppTheme {
        Column(Modifier.padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            DownloadItem(DownloadItemModel("d", "下载中的作品", NovelCoverModel(null, "封面"), "已下载 12 章", "24.5 MB", "下载中"), onDelete = {})
            HistoryItem(HistoryItemModel("h", "最近阅读", NovelCoverModel(null, "封面"), "第 8 章", "刚刚", "阅读进度 36%"))
            BookshelfItem(BookshelfItemModel("b", "书架作品", NovelCoverModel(null, "封面"), "作者", "上次读到第 3 章"), onSelectionChange = {})
        }
    }
}

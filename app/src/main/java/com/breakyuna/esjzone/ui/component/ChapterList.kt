package com.breakyuna.esjzone.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.novellibrary.component.ChapterItem
import com.breakyuna.esjzone.novellibrary.component.ChapterListItem
import com.breakyuna.esjzone.novellibrary.component.VisibleChapterGroup
import com.breakyuna.esjzone.novellibrary.component.VisibleChapterItem
import com.breakyuna.esjzone.novellibrary.component.VisibleChapterRow
import com.breakyuna.esjzone.novellibrary.component.initiallyExpandedChapterKeys
import com.breakyuna.esjzone.novellibrary.component.visibleChapterRows
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.NovelChapterList
import com.breakyuna.esjzone.ui.navigation.ChapterStateHolder
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent
import com.breakyuna.esjzone.ui.page.ChapterPage
import com.breakyuna.esjzone.ui.theme.QuietEditorial

/** Compact compatibility wrapper for callers that do not own a LazyColumn. */
@Composable
fun ChapterList(
    chapterList: NovelChapterList,
    modifier: Modifier = Modifier,
    novelId: String = "",
    novelName: String = "",
    novelCoverUrl: String = "",
    history: MutableState<Chapter?> = mutableStateOf(null),
    hasHistory: MutableState<Boolean> = mutableStateOf(false)
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ChapterListHeader()
        visibleChapterRows(
            chapterList.items,
            initiallyExpandedChapterKeys(chapterList.items)
        ).forEach { row ->
            ChapterListRow(
                row = row,
                novelId = novelId,
                history = history,
                hasHistory = hasHistory,
                chapterOrder = chapterList.orderedChapters,
                novelName = novelName,
                novelCoverUrl = novelCoverUrl,
                onGroupToggle = {}
            )
        }
    }
}

@Composable
fun ChapterListHeader(
    modifier: Modifier = Modifier,
    count: Int? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = androidx.compose.material.icons.automirrored.filled.List,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.novel_chapterlist),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        count?.takeIf { it > 0 }?.let {
            Text(
                text = stringResource(R.string.novel_chapter_count, it),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/** Renders one virtualized row while retaining the source DOM order. */
@Composable
fun ChapterListRow(
    row: VisibleChapterRow,
    novelId: String,
    history: MutableState<Chapter?>,
    hasHistory: MutableState<Boolean>,
    chapterOrder: List<Chapter>,
    novelName: String,
    novelCoverUrl: String,
    onGroupToggle: (String) -> Unit
) {
    when (row) {
        is VisibleChapterItem -> {
            val chapter = (row.item as? ChapterItem)?.chapter
            if (chapter != null) {
                ChapterDetailRow(
                    chapter = chapter,
                    depth = row.depth,
                    novelId = novelId,
                    history = history,
                    hasHistory = hasHistory,
                    chapterOrder = chapterOrder,
                    novelName = novelName,
                    novelCoverUrl = novelCoverUrl
                )
            } else {
                // Explanatory source text is rendered, but never becomes a
                // synthetic chapter or a navigable row.
                row.item.Render(
                    novelId = novelId,
                    history = history,
                    hasHistory = hasHistory,
                    chapterOrder = chapterOrder,
                    novelName = novelName,
                    novelCoverUrl = novelCoverUrl
                )
            }
        }

        is VisibleChapterGroup -> {
            ChapterGroupRow(
                group = row.group,
                depth = row.depth,
                expanded = row.expanded,
                onToggle = { onGroupToggle(row.key) }
            )
        }
    }
}

@Composable
private fun ChapterDetailRow(
    chapter: Chapter,
    depth: Int,
    novelId: String,
    history: MutableState<Chapter?>,
    hasHistory: MutableState<Boolean>,
    chapterOrder: List<Chapter>,
    novelName: String,
    novelCoverUrl: String
) {
    val navigator = LocalBaseNavigator.current
    val current = chapter.isHistory || history.value == chapter
    val canOpen = chapter.url.contains("esjzone", ignoreCase = true) ||
        chapter.url.contains("/forum/", ignoreCase = true)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = (depth * 14).dp, top = 2.dp, bottom = 2.dp)
            .then(
                if (canOpen) Modifier.clickable {
                    history.value = chapter
                    hasHistory.value = true
                    navigator?.pushIfNotCurrent(
                        ChapterPage(
                            novelId = novelId,
                            chapter = chapter,
                            history = ChapterStateHolder(history),
                            chapterOrder = chapterOrder,
                            novelName = novelName,
                            novelCoverUrl = novelCoverUrl
                        )
                    )
                } else Modifier
            ),
        shape = QuietEditorial.controlShape,
        color = if (current) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f)
        },
        contentColor = if (current) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (current) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = stringResource(R.string.chapter_current),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp)
                )
            } else {
                Box(modifier = Modifier.size(17.dp))
            }
            Text(
                text = chapter.name.trim().ifBlank { stringResource(R.string.untitled_chapter) },
                style = QuietEditorial.body.copy(
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = if (canOpen) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ChapterGroupRow(
    group: ChapterListItem,
    depth: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val (text, inlineContent) = group.name.toInlineAnnotatedString(
        textMeasurer,
        androidx.compose.material3.LocalTextStyle.current,
        density
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 14).dp, top = 4.dp, bottom = 4.dp)
            .clip(QuietEditorial.cardShape)
            .clickable(onClick = onToggle),
        shape = QuietEditorial.cardShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )
            Text(
                text = text,
                inlineContent = inlineContent,
                style = QuietEditorial.title,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.chapter_group_collapse else R.string.chapter_group_expand
                ),
                modifier = Modifier.size(21.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

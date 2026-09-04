package com.breakyuna.esjzone.novellibrary.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.breakyuna.esjzone.R
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.novellibrary.novel.Chapter

interface Item {

    @Composable
    fun Render(
        currentChapter: Chapter?,
        hasHistory: Boolean,
        onChapterOpen: (Chapter) -> Unit
    )

}

class TextItem(private val component: TextComponent) : Item {

    @Composable
    override fun Render(
        currentChapter: Chapter?,
        hasHistory: Boolean,
        onChapterOpen: (Chapter) -> Unit
    ) {
        val textMeasurer = rememberTextMeasurer()
        val textStyle = LocalTextStyle.current
        val density = LocalDensity.current

        val (str, inlines) = component.toInlineAnnotatedString(
            textMeasurer,
            textStyle,
            density
        )
        Text(
            text = str,
            inlineContent = inlines,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )
    }

}

class ChapterItem(val chapter: Chapter) : Item {

    @Composable
    override fun Render(
        currentChapter: Chapter?,
        hasHistory: Boolean,
        onChapterOpen: (Chapter) -> Unit
    ) {
        val isCurrent = (hasHistory && chapter == currentChapter) || chapter.isHistory

        Surface(
            onClick = {
                if (chapter.url.contains("esjzone") || chapter.url.contains("forum")) {
                    onChapterOpen(chapter)
                }
            },
            shape = RoundedCornerShape(12.dp),
            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isCurrent) {
                        Icon(
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = chapter.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

}

class ChapterListItem(
    val name: TextComponent,
    val chapters: List<Chapter>,
    val children: List<Item> = chapters.map { ChapterItem(it) },
    val initiallyExpanded: Boolean = false
) : Item {

    @Composable
    override fun Render(
        currentChapter: Chapter?,
        hasHistory: Boolean,
        onChapterOpen: (Chapter) -> Unit
    ) {
        val textMeasurer = rememberTextMeasurer()
        val textStyle = LocalTextStyle.current
        val density = LocalDensity.current

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            var expanded by rememberSaveable(name.text, chapters.firstOrNull()?.url) {
                mutableStateOf(initiallyExpanded)
            }
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    val (str, inlines) = name.toInlineAnnotatedString(
                        textMeasurer,
                        textStyle,
                        density
                    )
                    Text(
                        text = str,
                        inlineContent = inlines,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    val vector = if (expanded)
                        Icons.Filled.ExpandLess
                    else
                        Icons.Filled.ExpandMore
                    Icon(
                        imageVector = vector,
                        contentDescription = stringResource(if (expanded) R.string.chapter_group_collapse else R.string.chapter_group_expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Avoid animating the height of potentially hundreds of chapter rows.
                if (expanded) {
                    Column(
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                    ) {
                        for (item in children) {
                            item.Render(
                                currentChapter = currentChapter,
                                hasHistory = hasHistory,
                                onChapterOpen = onChapterOpen
                            )
                        }
                    }
                }
            }
        }
    }

}

/** A row in the detail page's flattened, lazily composed table of contents. */
sealed interface VisibleChapterRow {
    val key: String
    val depth: Int
}

data class VisibleChapterItem(
    val item: Item,
    override val key: String,
    override val depth: Int
) : VisibleChapterRow

data class VisibleChapterGroup(
    val group: ChapterListItem,
    override val key: String,
    override val depth: Int,
    val expanded: Boolean
) : VisibleChapterRow

/**
 * Returns only rows currently visible in the table of contents.  Keeping this
 * as a pure transformation lets the outer LazyColumn own virtualization while
 * preserving DOM order and nested groups.
 */
fun visibleChapterRows(
    items: List<Item>,
    expandedKeys: Set<String> = emptySet(),
    depth: Int = 0,
    path: String = ""
): List<VisibleChapterRow> = buildList {
    items.forEachIndexed { index, item ->
        val itemPath = if (path.isEmpty()) index.toString() else "$path.$index"
        when (item) {
            is ChapterListItem -> {
                val key = "chapter-group:$itemPath"
                // The Compose table starts collapsed; expansion is explicit
                // and retained by the screen's saveable state.
                val expanded = expandedKeys.contains(key)
                add(VisibleChapterGroup(item, key, depth, expanded))
                if (expanded) addAll(visibleChapterRows(item.children, expandedKeys, depth + 1, itemPath))
            }
            else -> add(VisibleChapterItem(item, "chapter-row:$itemPath", depth))
        }
    }
}

/** Initial expansion follows the source document; ordinary groups stay collapsed. */
fun initiallyExpandedChapterKeys(items: List<Item>, path: String = ""): Set<String> = buildSet {
    items.forEachIndexed { index, item ->
        val itemPath = if (path.isEmpty()) index.toString() else "$path.$index"
        if (item is ChapterListItem) {
            if (item.initiallyExpanded) add("chapter-group:$itemPath")
            addAll(initiallyExpandedChapterKeys(item.children, itemPath))
        }
    }
}

package com.breakyuna.esjzone.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.novellibrary.component.ChapterItem
import com.breakyuna.esjzone.novellibrary.component.ChapterListItem
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.component.TextItem
import com.breakyuna.esjzone.novellibrary.component.VisibleChapterGroup
import com.breakyuna.esjzone.novellibrary.component.VisibleChapterItem
import com.breakyuna.esjzone.novellibrary.component.VisibleChapterRow
import com.breakyuna.esjzone.novellibrary.component.visibleChapterRows
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.NovelChapterList

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
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            ChapterListHeader()
            Spacer(modifier = Modifier.height(4.dp))
            // Kept as a compact preview/backward-compatible wrapper. The
            // production detail page uses ChapterListRow directly in its
            // outer LazyColumn.
            for (row in visibleChapterRows(chapterList.items)) {
                ChapterListRow(row, novelId, history, hasHistory,
                    chapterList.orderedChapters, novelName, novelCoverUrl, {})
            }
        }
    }
}

@Composable
fun ChapterListHeader(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(bottom = 8.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.List,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(id = R.string.novel_chapterlist),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/** Composes one visible row; callers should place each row in the outer LazyColumn. */
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
            Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
                row.item.Render(novelId, history, hasHistory, chapterOrder, novelName, novelCoverUrl)
            }
        }
        is VisibleChapterGroup -> {
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val (text, inlineContent) = row.group.name.toInlineAnnotatedString(
                textMeasurer, LocalTextStyle.current, density
            )
            Card(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onGroupToggle(row.key) }
                        .padding(start = 14.dp + (row.depth * 12).dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text, inlineContent = inlineContent, modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = if (row.expanded) Icons.Filled.ExpandLess
                        else Icons.Filled.ExpandMore,
                        contentDescription = stringResource(if (row.expanded) R.string.chapter_group_collapse else R.string.chapter_group_expand),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun ChapterListPreview() {
    ChapterList(
        chapterList = NovelChapterList(
            listOf(
                TextItem(TextComponent("Just example")),
                ChapterItem(Chapter("example chapter 1", "", false)),
                ChapterItem(Chapter("example chapter 2", "", false)),
                ChapterListItem(
                    TextComponent("Chapter list example"),
                    listOf(
                        Chapter("in chapter list 1", "", false),
                        Chapter("in chapter list 2", "", false),
                        Chapter("in chapter list 3", "", false),
                    )
                )
            )
        )
    )
}

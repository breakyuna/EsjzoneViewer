package com.breakyuna.esjzone.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.novellibrary.component.ChapterItem
import com.breakyuna.esjzone.novellibrary.component.ChapterListItem
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.component.TextItem
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
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
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            val chapterOrder = chapterList.orderedChapters
            for (item in chapterList.items) {
                item.Render(
                    novelId = novelId,
                    history = history,
                    hasHistory = hasHistory,
                    chapterOrder = chapterOrder,
                    novelName = novelName,
                    novelCoverUrl = novelCoverUrl
                )
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

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
import androidx.compose.runtime.MutableState
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
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.ChapterStateHolder
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent
import com.breakyuna.esjzone.ui.page.ChapterPage
import org.jsoup.nodes.Element

/** Walk each DOM node once so groups and loose chapters retain their original order. */
fun analyseItems(element: Element): List<Item> = parseChapterItems(element)

private fun parseChapterItems(element: Element): List<Item> = buildList {
    for (child in element.children()) {
        when {
            child.nameIs("details") -> {
                val title = child.children().firstOrNull { it.nameIs("summary") }?.text().orEmpty()
                val children = parseChapterItems(child)
                val chapters = children.flatMap {
                    when (it) {
                        is ChapterItem -> listOf(it.chapter)
                        is ChapterListItem -> it.chapters
                        else -> emptyList()
                    }
                }
                add(ChapterListItem(TextComponent(title), chapters, children, child.hasAttr("open")))
            }
            child.nameIs("a") -> {
                val href = child.attr("href")
                if (href.isNotBlank() &&
                    (child.hasAttr("data-title") || href.contains("/forum/", ignoreCase = true))
                ) {
                    add(ChapterItem(analyseChapter(child)))
                }
            }
            child.nameIs("summary") || child.nameIs("button") ||
                child.nameIs("script") || child.nameIs("style") -> Unit
            child.nameIs("p") && child.selectFirst("a[href], details") == null -> {
                if (child.text().isNotBlank()) {
                    val component = analyseParagraph(child).filterIsInstance<TextComponent>().firstOrNull()
                        ?: TextComponent(child.text())
                    add(TextItem(component))
                }
            }
            else -> addAll(parseChapterItems(child))
        }
    }
}

private fun analyseChapter(element: Element): Chapter {
    val isHistory = element.hasClass("active") || element.selectFirst(".active") != null
    val title = element.attr("data-title").trim()
        .ifBlank { element.selectFirst("p")?.text()?.trim().orEmpty() }
        .ifBlank { element.text().trim() }

    return Chapter(
        title,
        element.attr("href"),
        isHistory
    )
}

interface Item {

    @Composable
    fun Render(
        novelId: String,
        history: MutableState<Chapter?>,
        hasHistory: MutableState<Boolean>,
        chapterOrder: List<Chapter>,
        novelName: String = "",
        novelCoverUrl: String = ""
    )

}

class TextItem(private val component: TextComponent) : Item {

    @Composable
    override fun Render(
        novelId: String,
        history: MutableState<Chapter?>,
        hasHistory: MutableState<Boolean>,
        chapterOrder: List<Chapter>,
        novelName: String,
        novelCoverUrl: String
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
        novelId: String,
        history: MutableState<Chapter?>,
        hasHistory: MutableState<Boolean>,
        chapterOrder: List<Chapter>,
        novelName: String,
        novelCoverUrl: String
    ) {
        val navigator = LocalBaseNavigator.current

        var historied by rememberSaveable {
            hasHistory
        }

        var rememberedHistory by rememberSaveable {
            history
        }

        val isCurrent = (historied && chapter == rememberedHistory) || chapter.isHistory

        Surface(
            onClick = {
                if (chapter.url.contains("esjzone") || chapter.url.contains("forum")) {
                    historied = true
                    rememberedHistory = this.chapter
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
    private val name: TextComponent,
    val chapters: List<Chapter>,
    private val children: List<Item> = chapters.map { ChapterItem(it) },
    private val initiallyExpanded: Boolean = false
) : Item {

    @Composable
    override fun Render(
        novelId: String,
        history: MutableState<Chapter?>,
        hasHistory: MutableState<Boolean>,
        chapterOrder: List<Chapter>,
        novelName: String,
        novelCoverUrl: String
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
            var expanded by rememberSaveable(novelId, name.text, chapters.firstOrNull()?.url) {
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
                            item.Render(novelId, history, hasHistory, chapterOrder, novelName, novelCoverUrl)
                        }
                    }
                }
            }
        }
    }

}

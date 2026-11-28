package com.breakyuna.esjzone.novellibrary.novel

import androidx.compose.runtime.Immutable
import com.breakyuna.esjzone.novellibrary.component.ChapterItem
import com.breakyuna.esjzone.novellibrary.component.ChapterListItem
import com.breakyuna.esjzone.novellibrary.component.Item
import com.breakyuna.esjzone.novellibrary.component.analyseItems
import org.jsoup.nodes.Element
import java.io.Serializable

@Immutable
data class NovelChapterList(
    val items: List<Item>
) : Serializable {

    /** Chapters in the order shown by the detail page's canonical table of contents. */
    val orderedChapters: List<Chapter> = items.flatMap { item ->
        when (item) {
            is ChapterItem -> listOf(item.chapter)
            is ChapterListItem -> item.chapters
            else -> emptyList()
        }
    }

    val hasHistory: Boolean
    val toRead: Chapter?

    init {
        var hasHistory = false
        var toRead: Chapter? = null
        for (chapter in orderedChapters) {
            if (chapter.isExternal) continue
            if (toRead == null) toRead = chapter
            if (chapter.isHistory) {
                toRead = chapter
                hasHistory = true
                break
            }
        }
        this.hasHistory = hasHistory
        this.toRead = toRead
    }

}

fun analyseChapterList(element: Element): NovelChapterList {
    return NovelChapterList(
        analyseItems(element)
    )
}

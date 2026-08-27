package com.breakyuna.esjzone.novellibrary.novel

import com.breakyuna.esjzone.novellibrary.component.ChapterItem
import com.breakyuna.esjzone.novellibrary.component.ChapterListItem
import com.breakyuna.esjzone.novellibrary.component.Item
import com.breakyuna.esjzone.novellibrary.component.analyseItems
import org.jsoup.nodes.Element
import java.io.Serializable

data class NovelChapterList(
    val items: List<Item>
) : Serializable {

    val hasHistory: Boolean
    val toRead: Chapter?

    init {
        var hasHistory = false
        var toRead: Chapter? = null
        for (item in items) {
            if (item is ChapterItem) {
                if (toRead == null)
                    toRead = item.chapter
                if (item.chapter.isHistory) {
                    if (!toRead.isHistory)
                        toRead = item.chapter
                    hasHistory = true
                    break
                }
            } else if (item is ChapterListItem) {
                for (chapter in item.chapters) {
                    if (toRead == null)
                        toRead = chapter
                    if (chapter.isHistory) {
                        if (!toRead.isHistory)
                            toRead = chapter
                        hasHistory = true
                        break
                    }
                }
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
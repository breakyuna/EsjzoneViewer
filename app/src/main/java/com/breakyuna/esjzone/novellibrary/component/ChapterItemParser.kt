package com.breakyuna.esjzone.novellibrary.component

import com.breakyuna.esjzone.novellibrary.novel.Chapter
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
                ) add(ChapterItem(analyseChapter(child)))
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
    return Chapter(title, element.attr("href"), isHistory)
}

package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.HtmlSelector
import com.breakyuna.esjzone.network.JsoupHtmlSelector
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

private val categoryUrlRegex = Regex("^(?:https?://[^/]+)?/?forum/[0-9]+/?(?:[?#].*)?$")
private val categorySelector: HtmlSelector = JsoupHtmlSelector

/**
 * Selects forum category links without confusing boards (/forum/cat/board/)
 * or individual topics (/forum/cat/board/topic.html) for top-level categories.
 */
internal fun selectForumCategories(
    document: Document,
    selector: HtmlSelector = categorySelector
): List<Element> {
    val marked = selector.select(
        document,
        "table.forum-category a[href*='/forum/'], .forum-category a[href*='/forum/']"
    )
    val candidates = if (marked.isNotEmpty()) {
        marked
    } else {
        selector.select(document, "table a[href*='/forum/']")
    }

    return candidates
        .filter { categoryUrlRegex.matches(it.attr("href").trim()) }
        .distinctBy { it.attr("href").trim() to it.text().trim() }
}

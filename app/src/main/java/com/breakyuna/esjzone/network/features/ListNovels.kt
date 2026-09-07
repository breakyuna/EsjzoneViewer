package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.novellibrary.novel.Category
import com.breakyuna.esjzone.novellibrary.novel.CategoryNovel
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

private val novelForumBoardUrl = Regex("(?:https?://[^/]+)?/forum/[0-9]+/[0-9]+/?(?:[?#].*)?$")

fun EsjzoneClient.listNovels(
    authorization: Authorization,
    category: Category
): List<CategoryNovel> {
    val targetUrl = EsjzoneUrls.resolve(category.url)
    val responseBody = getPage(
        authorization,
        targetUrl,
        PageCacheTtl.LIST,
        pageKind = PageKind.LIST
    )

    val document = Jsoup.parse(responseBody)

    val novels = mutableListOf<CategoryNovel>()

    for (element in selectNovelForumLinks(document)) {
        val forumUrl = element.attr("href")
        val detailUrl = EsjzoneUrls.novelDetailUrlFromForumBoard(forumUrl) ?: forumUrl
        novels.add(
            CategoryNovel(
                element.text(),
                detailUrl,
                forumUrl
            )
        )
    }

    return novels.toList()
}

/** Select only board-level novel links from the category table. */
internal fun selectNovelForumLinks(document: Document): List<Element> =
    document.select("table a[href*='/forum/']")
        .filter { novelForumBoardUrl.matches(it.attr("href").trim()) }

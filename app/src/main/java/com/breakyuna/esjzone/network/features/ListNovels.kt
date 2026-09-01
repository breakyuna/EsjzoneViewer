package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.EsjzoneXPaths
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.novellibrary.novel.Category
import com.breakyuna.esjzone.novellibrary.novel.CategoryNovel
import org.jsoup.Jsoup

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

    for (element in EsjzoneXPaths.Forum.Novel.evaluate(document).elements) {
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

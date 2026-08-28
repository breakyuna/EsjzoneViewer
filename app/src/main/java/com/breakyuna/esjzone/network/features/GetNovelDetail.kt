package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.EsjzoneXPaths
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.Novel
import com.breakyuna.esjzone.novellibrary.novel.NovelChapterList
import com.breakyuna.esjzone.novellibrary.novel.NovelDescription
import com.breakyuna.esjzone.novellibrary.novel.analyseChapterList
import com.breakyuna.esjzone.novellibrary.novel.analyseDescription
import com.breakyuna.esjzone.util.AppLogger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element


fun EsjzoneClient.getNovelDetail(authorization: Authorization, novel: Novel): DetailedNovel {
    val targetUrl = EsjzoneUrls.resolve(novel.url)

    AppLogger.i("GetNovelDetail", "Fetching novel detail: ${novel.name} at $targetUrl")
    val responseBody = getPage(authorization, targetUrl, PageCacheTtl.DETAIL)

    val document = Jsoup.parse(responseBody)

    val coverUrl = document.selectFirst(".product-gallery img")
        ?.let { image -> image.attr("src").ifBlank { image.attr("data-src") } }
        ?.takeIf { it.isNotBlank() }
        ?: EsjzoneXPaths.Detail.Cover.evaluate(document).get()
        ?: EsjzoneUrls.EmptyCover

    val viewsStr = document.selectFirst("#vtimes")?.text()
        ?: EsjzoneXPaths.Detail.Views.evaluate(document).get()
        ?: "0"
    val views = viewsStr.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0

    val likesStr = document.selectFirst("#favorite")?.text()
        ?: EsjzoneXPaths.Detail.Likes.evaluate(document).get()
        ?: "0"
    val likes = likesStr.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0

    val wordsStr = document.selectFirst("#txt")?.text()
        ?: EsjzoneXPaths.Detail.Words.evaluate(document).get()
        ?: "0"
    val words = wordsStr.replace(",", "").replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0

    val detailInfo = document.selectFirst(".book-detail")
    val type = detailInfo?.select("ul li")?.firstOrNull()?.text()?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: EsjzoneXPaths.Detail.Type.evaluate(document).get()
        ?: ""
    val author = detailInfo?.select("ul li a[href^='/tags/']")?.firstOrNull()?.text()?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: EsjzoneXPaths.Detail.Author.evaluate(document).get()
        ?: ""

    val forumUrl = document.selectFirst("a.btn-forum")?.attr("href")
        ?.takeIf { it.isNotBlank() }
        ?: EsjzoneXPaths.Detail.ForumUrl.evaluate(document).get()
        ?: ""

    val tags = EsjzoneXPaths.Detail.Tags.evaluate(document).list().toList()

    val favorite = document.selectFirst("button.btn-favorite")?.text()?.trim()
        ?: EsjzoneXPaths.Detail.FavoriteText.evaluate(document).get()
        ?: ""

    val descriptionElements = EsjzoneXPaths.Detail.Description.evaluate(document).elements
    val chapterListElements = document.select("#integration").ifEmpty {
        EsjzoneXPaths.Detail.ChapterList.evaluate(document).elements
    }

    val description = descriptionElements.firstOrNull()
        ?.let(::analyseDescription)
        ?: NovelDescription(emptyList())

    val chapterList = chapterListElements.firstOrNull()
        ?.let(::analyseChapterList)
        ?: NovelChapterList(emptyList())

    return DetailedNovel(
        novel.name,
        novel.url,
        coverUrl,
        views,
        likes,
        words,
        type,
        author,
        forumUrl,
        tags,
        tags.contains("R18"),
        favorite == "已收藏",
        description,
        chapterList
    )
}

private fun analyseComments(document: Document) {
    val commentElements = mutableListOf<Element>()

    for (pages in EsjzoneXPaths.Detail.Comment.Pages.evaluate(document).elements) {
        for (element in pages.children()) {
            if (element.nameIs("div") && element.hasAttr("class") && element.attr("class") == "comment") {
                commentElements.add(element)
            }
        }
    }


}

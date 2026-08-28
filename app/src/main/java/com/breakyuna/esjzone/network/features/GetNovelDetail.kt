package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.AuthorizationCookieJar
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.EsjzoneXPaths
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.Novel
import com.breakyuna.esjzone.novellibrary.novel.NovelChapterList
import com.breakyuna.esjzone.novellibrary.novel.NovelDescription
import com.breakyuna.esjzone.novellibrary.novel.analyseChapterList
import com.breakyuna.esjzone.novellibrary.novel.analyseDescription
import com.breakyuna.esjzone.util.AppLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element


fun EsjzoneClient.getNovelDetail(authorization: Authorization, novel: Novel): DetailedNovel {
    val httpClient = OkHttpClient.Builder()
        .cookieJar(AuthorizationCookieJar(authorization))
        .build()

    val targetUrl = if (novel.url.startsWith("http://") || novel.url.startsWith("https://")) {
        novel.url.replaceFirst(Regex("^https?://[^/]+"), EsjzoneUrls.Base)
    } else if (novel.url.startsWith("/")) {
        "${EsjzoneUrls.Base}${novel.url}"
    } else {
        "${EsjzoneUrls.Base}/${novel.url}"
    }

    AppLogger.i("GetNovelDetail", "Fetching novel detail: ${novel.name} at $targetUrl")

    val response = httpClient.newCall(
        Request.Builder()
            .url(targetUrl)
            .get()
            .headers(this.headers)
            .build()
    ).execute()

    val responseBody = response.bodyStringOrEmpty()

    val document = Jsoup.parse(responseBody)

    val coverXPathResult = EsjzoneXPaths.Detail.Cover.evaluate(document).list()

    val coverUrl = if (coverXPathResult.isNotEmpty())
        EsjzoneXPaths.Detail.Cover.evaluate(document).get()
    else
        EsjzoneUrls.EmptyCover

    val viewsStr = EsjzoneXPaths.Detail.Views.evaluate(document).get() ?: "0"
    val views = viewsStr.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0

    val likesStr = EsjzoneXPaths.Detail.Likes.evaluate(document).get() ?: "0"
    val likes = likesStr.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0

    val wordsStr = EsjzoneXPaths.Detail.Words.evaluate(document).get() ?: "0"
    val words = wordsStr.replace(",", "").replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0

    val type = EsjzoneXPaths.Detail.Type.evaluate(document).get() ?: ""
    val author = EsjzoneXPaths.Detail.Author.evaluate(document).get() ?: ""

    val forumUrl = EsjzoneXPaths.Detail.ForumUrl.evaluate(document).get() ?: ""

    val tags = EsjzoneXPaths.Detail.Tags.evaluate(document).list().toList()

    val favorite = EsjzoneXPaths.Detail.FavoriteText.evaluate(document).get() ?: ""

    val descriptionElements = EsjzoneXPaths.Detail.Description.evaluate(document).elements
    val chapterListElements = EsjzoneXPaths.Detail.ChapterList.evaluate(document).elements

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

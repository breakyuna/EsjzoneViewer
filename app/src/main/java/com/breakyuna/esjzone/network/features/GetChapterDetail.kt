package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.AuthorizationCookieJar
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.EsjzoneXPaths
import com.breakyuna.esjzone.novellibrary.component.analyseComponents
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup


fun EsjzoneClient.getChapterDetail(
    authorization: Authorization,
    chapter: Chapter
): DetailedChapter {
    val httpClient = OkHttpClient.Builder()
        .cookieJar(AuthorizationCookieJar(authorization))
        .build()

    val targetUrl = if (chapter.url.startsWith("http://") || chapter.url.startsWith("https://")) {
        chapter.url.replaceFirst(Regex("^https?://[^/]+"), EsjzoneUrls.Base)
    } else if (chapter.url.startsWith("/")) {
        "${EsjzoneUrls.Base}${chapter.url}"
    } else {
        "${EsjzoneUrls.Base}/${chapter.url}"
    }

    val response = httpClient.newCall(
        Request.Builder()
            .url(targetUrl)
            .get()
            .headers(this.headers)
            .build()
    ).execute()


    val responseBody = response.body!!.string()
    response.close()

    val document = Jsoup.parse(responseBody)

    val components = analyseComponents(EsjzoneXPaths.Forum.Content.evaluate(document).elements[0])

    val previousChapter = EsjzoneXPaths.Forum.PreviousChapter.evaluate(document).elements
    val nextChapter = EsjzoneXPaths.Forum.NextChapter.evaluate(document).elements

    val previous = if (previousChapter.isNotEmpty()) {
        Chapter(
            previousChapter.attr("data-title"),
            previousChapter.attr("href"),
            false
        )
    } else null

    val next = if (nextChapter.isNotEmpty()) {
        Chapter(
            nextChapter.attr("data-title"),
            nextChapter.attr("href"),
            false
        )
    } else null

    return DetailedChapter(
        chapter.name,
        components,
        previous,
        next
    )
}
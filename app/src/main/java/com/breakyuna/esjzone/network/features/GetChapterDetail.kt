package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.EsjzoneXPaths
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.novellibrary.component.analyseComponents
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.util.AppLogger
import org.jsoup.Jsoup


fun EsjzoneClient.getChapterDetail(
    authorization: Authorization,
    chapter: Chapter
): DetailedChapter {
    val targetUrl = EsjzoneUrls.resolve(chapter.url)

    AppLogger.i("GetChapterDetail", "Fetching chapter: ${chapter.name} at $targetUrl")
    val responseBody = getPage(authorization, targetUrl, PageCacheTtl.CHAPTER)

    val document = Jsoup.parse(responseBody)

    val contentElement = document.selectFirst(".forum-content.mt-3")
        ?: document.selectFirst(".forum-content")
        ?: EsjzoneXPaths.Forum.Content.evaluate(document).elements.firstOrNull()
    val components = if (contentElement != null) {
        analyseComponents(contentElement)
    } else {
        AppLogger.w("GetChapterDetail", "No content element found in chapter page: $targetUrl")
        listOf()
    }

    val previousChapter = document.selectFirst("a.btn-prev")
        ?: EsjzoneXPaths.Forum.PreviousChapter.evaluate(document).elements.firstOrNull()
    val nextChapter = document.selectFirst("a.btn-next")
        ?: EsjzoneXPaths.Forum.NextChapter.evaluate(document).elements.firstOrNull()

    val previous = if (previousChapter != null) {
        Chapter(
            previousChapter.attr("data-title").ifBlank { previousChapter.text() },
            previousChapter.attr("href"),
            false
        )
    } else null

    val next = if (nextChapter != null) {
        Chapter(
            nextChapter.attr("data-title").ifBlank { nextChapter.text() },
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

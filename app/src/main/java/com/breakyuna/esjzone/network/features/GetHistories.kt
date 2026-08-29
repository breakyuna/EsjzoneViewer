package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.EsjzoneXPaths
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.HistoryNovel
import org.jsoup.Jsoup

fun EsjzoneClient.getHistories(authorization: Authorization): List<HistoryNovel> {
    val responseBody = getPage(authorization, EsjzoneUrls.My.View, PageCacheTtl.ACCOUNT_LIST)

    val document = Jsoup.parse(responseBody)

    val novels = mutableListOf<HistoryNovel>()

    for (element in EsjzoneXPaths.Profile.View.Novel.evaluate(document).elements) {
        val idAttr = element.attr("id")
        val vid = if (idAttr.length > 5) idAttr.substring(5) else idAttr
        val titleElements = EsjzoneXPaths.Profile.View.TitleAndUrl.evaluate(element).elements
        val chapterElements = EsjzoneXPaths.Profile.View.Chapter.evaluate(element).elements

        val novelData = titleElements.firstOrNull()
        val chapterData = chapterElements.firstOrNull()
        if (novelData != null && chapterData != null) {
            val chapterHref = chapterData.attr("href")
            val fullChapterUrl = EsjzoneUrls.resolve(chapterHref)
            novels.add(
                HistoryNovel(
                    novelData.text(),
                    novelData.attr("href"),
                    vid,
                    Chapter(
                        chapterData.text(),
                        fullChapterUrl,
                        true
                    )
                )
            )
        }
    }

    // The site renders records from oldest to newest; expose the app-facing
    // contract as newest first so every history entry point behaves alike.
    return novels.asReversed()
}

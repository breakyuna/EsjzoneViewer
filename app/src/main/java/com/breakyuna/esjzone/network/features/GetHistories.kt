package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.HtmlSelector
import com.breakyuna.esjzone.network.JsoupHtmlSelector
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.HistoryNovel
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

private val historySelector: HtmlSelector = JsoupHtmlSelector

fun EsjzoneClient.getHistories(
    authorization: Authorization,
    forceRefresh: Boolean = false
): List<HistoryNovel> {
    val responseBody = getPage(
        authorization,
        EsjzoneUrls.My.View,
        PageCacheTtl.ACCOUNT_LIST,
        forceRefresh = forceRefresh,
        pageKind = PageKind.ACCOUNT
    )

    val document = Jsoup.parse(responseBody)

    return parseHistoryNovels(document)
}

internal fun parseHistoryNovels(document: Document): List<HistoryNovel> {
    val novels = mutableListOf<HistoryNovel>()

    for (element in historySelector.select(document, "table.table tr, tr.view-log")) {
        val idAttr = element.attr("id")
        val vid = if (idAttr.length > 5) idAttr.substring(5) else idAttr
        val novelData = historySelector.first(
            element,
            ".view-log h5 a[href^='/detail/'], h5 a[href^='/detail/']"
        )
        val chapterData = historySelector.first(
            element,
            ".book-ep a[href*='/forum/'], a[href*='/forum/']"
        )
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

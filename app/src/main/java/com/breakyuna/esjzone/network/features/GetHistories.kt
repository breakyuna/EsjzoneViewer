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
): List<HistoryNovel> = parseHistoryNovels(Jsoup.parse(historyPage(authorization, forceRefresh)))

/**
 * A fresh /my/view read for deciding how to restore the site record after a
 * download. /my/view has no pagination, so this one page is the whole list.
 * Returns null when the page has no history table (not a /my/view page, e.g. a
 * login or challenge page), so the caller can tell "unknown" from "not listed".
 */
internal fun EsjzoneClient.getHistorySnapshot(authorization: Authorization): List<HistoryNovel>? =
    parseHistorySnapshot(Jsoup.parse(historyPage(authorization, forceRefresh = true)))

internal fun parseHistorySnapshot(document: Document): List<HistoryNovel>? {
    if (document.selectFirst("table.table") == null) return null
    return parseHistoryNovels(document)
}

private fun EsjzoneClient.historyPage(authorization: Authorization, forceRefresh: Boolean): String =
    getPage(
        authorization,
        EsjzoneUrls.resolve(
            "/my/view",
            EsjzoneUrls.baseForDomain(authorization.domain.ifBlank { EsjzoneUrls.BaseWithoutProtocol })
        ),
        PageCacheTtl.ACCOUNT_LIST,
        forceRefresh = forceRefresh,
        pageKind = PageKind.ACCOUNT,
        allowStaleOnError = !forceRefresh
    )

internal fun parseHistoryNovels(document: Document): List<HistoryNovel> {
    val novels = mutableListOf<HistoryNovel>()

    for (element in historySelector.select(document, "table.table tr, tr.view-log")) {
        val vid = element.selectFirst(".view-del[data-id]")?.attr("data-id")?.trim()
            ?.takeIf(String::isNotBlank)
            ?: element.id().takeIf { it.startsWith("view_") }?.removePrefix("view_").orEmpty()
        val novelData = historySelector.first(
            element,
            ".view-log h5 a[href*='/detail/'], h5 a[href*='/detail/']"
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

    return novels
}

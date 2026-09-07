package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.HtmlSelector
import com.breakyuna.esjzone.network.JsoupHtmlSelector
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.network.NovelDetailCache
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.Novel
import com.breakyuna.esjzone.novellibrary.novel.NovelChapterList
import com.breakyuna.esjzone.novellibrary.novel.NovelDescription
import com.breakyuna.esjzone.novellibrary.novel.analyseChapterList
import com.breakyuna.esjzone.novellibrary.novel.analyseDescription
import com.breakyuna.esjzone.util.AppLogger
import org.jsoup.Jsoup

private val detailSelector: HtmlSelector = JsoupHtmlSelector

fun EsjzoneClient.getNovelDetail(
    authorization: Authorization,
    novel: Novel,
    includeComments: Boolean = false,
    forceRefresh: Boolean = false,
    baseUrl: String? = null
): DetailedNovel {
    val targetUrl = baseUrl?.let { EsjzoneUrls.resolve(novel.url, it) }
        ?: EsjzoneUrls.resolve(novel.url)
    val detailCacheKey = novelDetailCacheKey(authorization, targetUrl)
    if (!includeComments && !forceRefresh) {
        NovelDetailCache.read(detailCacheKey)?.let { return it }
    }

    AppLogger.i("GetNovelDetail", "Fetching novel detail: ${novel.name} at $targetUrl")
    val responseBody = getPage(
        authorization = authorization,
        url = targetUrl,
        maxAgeMillis = PageCacheTtl.DETAIL,
        forceRefresh = forceRefresh,
        pageKind = PageKind.DETAIL
    )

    val document = Jsoup.parse(responseBody, targetUrl)

    val coverUrl = EsjzoneUrls.coverUrlFromImage(
        detailSelector.first(document, ".product-gallery img, .book-detail img")
    ).ifBlank { EsjzoneUrls.EmptyCover }

    val viewsStr = detailSelector.first(
        document,
        "#vtimes, .book-detail [data-field='views'], .book-detail .book-views"
    )?.text() ?: "0"
    val views = viewsStr.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0

    val likesStr = detailSelector.first(
        document,
        "#favorite, .book-detail [data-field='likes'], .book-detail .book-likes"
    )?.text() ?: "0"
    val likes = likesStr.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0

    val wordsStr = detailSelector.first(
        document,
        "#txt, .book-detail [data-field='words'], .book-detail .book-words"
    )?.text() ?: "0"
    val words = wordsStr.replace(",", "").replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0

    val detailInfo = document.selectFirst(".book-detail")
    val type = detailInfo?.let {
        detailSelector.first(it, "ul li[data-field='type'], ul li")?.text()
    }
        ?.let(::stripDetailLabel)
        ?.takeIf { it.isNotBlank() }
        ?: ""
    val author = detailInfo?.let {
        detailSelector.first(it, "ul li a[href^='/tags/'], a[href^='/tags/']")?.text()?.trim()
    }
        ?.takeIf { it.isNotBlank() }
        ?: ""

    val forumUrl = detailSelector.first(
        document,
        ".book-detail a.btn-forum, a.btn-forum, .book-detail a[href*='/forum/'][href$='/']"
    )?.let { link ->
        link.absUrl("href").ifBlank { link.attr("href") }
    }
        ?.takeIf { it.isNotBlank() }
        ?: ""

    val sourceLinks = detailInfo?.select("a[href]").orEmpty()
        .mapNotNull { link ->
            val href = link.absUrl("href").ifBlank { link.attr("href") }.trim()
            EsjzoneUrls.resolve(href, targetUrl).takeIf { it.isNotBlank() }
        }
    val sourceUrl = sourceLinks.firstOrNull { href ->
        !href.contains("esjzone", ignoreCase = true)
    } ?: sourceLinks.firstOrNull()

    val updatedAt = extractUpdatedAt(detailInfo ?: document)

    val tags = detailSelector.select(
        document,
        ".book-detail .tags a, .book-tags a, .tags a, [data-tags] a"
    ).map { it.text().trim() }
        .filter(String::isNotBlank)
        .distinct()

    val favorite = detailSelector.first(document, "button.btn-favorite")?.text()?.trim().orEmpty()

    val descriptionElements = detailSelector.select(
        document,
        ".book-description, #description, .description, [data-description]"
    )
    val chapterListElements = detailSelector.select(document, "#integration")

    val description = descriptionElements.firstOrNull()
        ?.let(::analyseDescription)
        ?: NovelDescription(emptyList())

    val chapterList = chapterListElements.firstOrNull()
        ?.let(::analyseChapterList)
        ?: NovelChapterList(emptyList())
    val comments = if (includeComments) {
        parseComments(document, commentParentId(targetUrl))
    } else {
        emptyList()
    }

    val detailedNovel = DetailedNovel(
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
        chapterList,
        comments,
        sourceUrl,
        updatedAt
    )
    if (!includeComments) {
        NovelDetailCache.write(detailCacheKey, detailedNovel)
    }
    return detailedNovel
}

private val UPDATED_AT_REGEX = Regex(
    "(?:更新日期|更新日|最后更新|最後更新|更新)\\s*[：:]?\\s*" +
        "(\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}(?:\\s+\\d{1,2}:\\d{2}(?::\\d{2})?)?)"
)

private val DATE_REGEX = Regex(
    "\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}(?:\\s+\\d{1,2}:\\d{2}(?::\\d{2})?)?"
)

private fun stripDetailLabel(value: String): String =
    value.trim().replaceFirst(Regex("^[^：:]+[：:]\\s*"), "").trim()

private fun extractUpdatedAt(detailInfo: org.jsoup.nodes.Element): String? {
    val text = detailInfo.text().replace(Regex("\\s+"), " ").trim()
    UPDATED_AT_REGEX.find(text)?.groupValues?.getOrNull(1)?.let { return it }

    detailInfo.selectFirst("time[datetime], [data-updated-at]")?.let { element ->
        element.attr("datetime").trim().takeIf { it.isNotBlank() }?.let { return it }
        element.attr("data-updated-at").trim().takeIf { it.isNotBlank() }?.let { return it }
        element.text().trim().takeIf { it.isNotBlank() }?.let { return it }
    }

    return DATE_REGEX.find(text)?.value
}

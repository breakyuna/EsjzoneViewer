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


fun EsjzoneClient.getNovelDetail(
    authorization: Authorization,
    novel: Novel,
    includeComments: Boolean = false
): DetailedNovel {
    val targetUrl = EsjzoneUrls.resolve(novel.url)

    AppLogger.i("GetNovelDetail", "Fetching novel detail: ${novel.name} at $targetUrl")
    val responseBody = getPage(authorization, targetUrl, PageCacheTtl.DETAIL)

    val document = Jsoup.parse(responseBody, targetUrl)

    val coverUrl = EsjzoneUrls.coverUrlFromImage(
        document.selectFirst(".product-gallery img")
    ).ifBlank {
        EsjzoneUrls.coverOrEmpty(EsjzoneXPaths.Detail.Cover.evaluate(document).get())
    }

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
    val type = detailInfo?.select("ul li")?.firstOrNull()?.text()
        ?.let(::stripDetailLabel)
        ?.takeIf { it.isNotBlank() }
        ?: EsjzoneXPaths.Detail.Type.evaluate(document).get()?.let(::stripDetailLabel)
        ?: ""
    val author = detailInfo?.select("ul li a[href^='/tags/']")?.firstOrNull()?.text()?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: EsjzoneXPaths.Detail.Author.evaluate(document).get()
        ?: ""

    val forumUrl = document.selectFirst("a.btn-forum")?.let { link ->
        link.absUrl("href").ifBlank { link.attr("href") }
    }
        ?.takeIf { it.isNotBlank() }
        ?: EsjzoneXPaths.Detail.ForumUrl.evaluate(document).get()
        ?: ""

    val sourceLinks = detailInfo?.select("a[href]").orEmpty()
        .mapNotNull { link ->
            val href = link.absUrl("href").ifBlank { link.attr("href") }.trim()
            href.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        }
    val sourceUrl = sourceLinks.firstOrNull { href ->
        !href.contains("esjzone", ignoreCase = true)
    } ?: sourceLinks.firstOrNull()

    val updatedAt = extractUpdatedAt(detailInfo ?: document)

    val tags = EsjzoneXPaths.Detail.Tags.evaluate(document)
        .list()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()

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
    val comments = if (includeComments) {
        parseComments(document, commentParentId(targetUrl))
    } else {
        emptyList()
    }

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
        chapterList,
        comments,
        sourceUrl,
        updatedAt
    )
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

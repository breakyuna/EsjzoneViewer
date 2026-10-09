package com.breakyuna.esjzone.network.wenku8

import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.cancellablePageRequest
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import kotlinx.coroutines.delay

data class Wenku8PageResponse(val html: String, val url: String)

/** Uses the existing independent Wenku transport; never supplies ESJ authorization. */
fun EsjzoneClient.getWenku8Novel(url: String, allowAutoSolve: Boolean = true, forceRefresh: Boolean = false): DetailedNovel {
    val identity = Wenku8Urls.detailIdentity(url) ?: throw Wenku8ParseException()
    val bookId = Wenku8Urls.bookId(identity) ?: throw Wenku8ParseException()
    val info = getWenkuPage(Wenku8Urls.detail(bookId), Wenku8PageKind.DETAIL, allowAutoSolve, forceRefresh)
    if (Wenku8Urls.detailIdentity(info.url) != identity) throw Wenku8ParseException()
    val detail = Wenku8Parsers.detail(info.html, info.url, com.breakyuna.esjzone.novellibrary.novel.NovelChapterList(emptyList()))
    val index = getWenkuPage(Wenku8Urls.catalog(bookId), Wenku8PageKind.CATALOG, allowAutoSolve, forceRefresh)
    if (Wenku8Urls.catalogIdentity(index.url) != identity) throw Wenku8ParseException()
    return detail.copy(chapterList = Wenku8Parsers.catalog(index.html, index.url))
}

suspend fun EsjzoneClient.searchWenku8(keyword: String, type: Wenku8SearchType, page: Int, forceRefresh: Boolean = false): Wenku8SearchResult {
    val url = wenku8SearchUrl(keyword, type, page)
    repeat(2) { attempt ->
        try {
            val response = cancellablePageRequest { getWenkuPage(url, Wenku8PageKind.SEARCH, true, forceRefresh) }
            return Wenku8Parsers.search(response.html, response.url, page)
        } catch (error: Wenku8SearchRateLimitException) {
            if (attempt == 1) throw error
            delay(5_000)
        }
    }
    throw Wenku8ParseException()
}

/** One visible page per request, sharing the existing session-scoped cache. */
suspend fun EsjzoneClient.browseWenku8(category: Wenku8Browse, page: Int, forceRefresh: Boolean = false): Wenku8SearchResult {
    val kind = if (category == Wenku8Browse.HOME) Wenku8PageKind.HOME else Wenku8PageKind.BROWSE
    val response = cancellablePageRequest { getWenkuPage(wenku8BrowseUrl(category, page), kind, true, forceRefresh) }
    return if (kind == Wenku8PageKind.HOME) Wenku8Parsers.home(response.html, response.url)
    else Wenku8Parsers.search(response.html, response.url, page)
}

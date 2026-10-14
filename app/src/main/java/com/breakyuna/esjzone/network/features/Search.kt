package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.network.ListPageCache
import com.breakyuna.esjzone.network.ParsedListPage
import com.breakyuna.esjzone.network.PageableRequester
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import org.jsoup.Jsoup

fun EsjzoneClient.search(
    authorization: Authorization,
    keyword: String,
    category: Int = 0,
    sort: Int = 1,
    forceRefresh: Boolean = false
): Pair<PageableRequester<CoveredNovel>, List<CoveredNovel>> {
    val searchUrl = EsjzoneUrls.tagsUrl(keyword = keyword, category = category, sort = sort)
    val (session, items) = ListPageCache.open(
        authorization, searchUrl, pageUrl = { page -> if (page == 1) searchUrl else EsjzoneUrls.tagsUrl(keyword, sort = sort, category = category, page = page) },
        maxAgeMillis = PageCacheTtl.SEARCH, kind = PageKind.SEARCH, forceRefresh = forceRefresh
    ) { html, _ ->
        val document = Jsoup.parse(html)
        ParsedListPage(pageCount(document), selectNovelCards(document).map {
            parseNovelCard(it, false, NovelCardLayout.LIST)
        })
    }
    return SearchNovelRequester(session) to items
}

private class SearchNovelRequester(
    private val session: ListPageCache.Session<CoveredNovel>
) : PageableRequester<CoveredNovel> {
    private var current = 2
    override fun pages(): Int = session.pageCount
    override fun isCurrent(): Boolean = session.isCurrent()
    override fun fallbackFailure() = session.fallbackFailure
    override fun more(): List<CoveredNovel> = more(current).also { current += 1 }
    override fun more(page: Int): List<CoveredNovel> = session.more(page)
    override fun end(): Boolean = current > pages()
}

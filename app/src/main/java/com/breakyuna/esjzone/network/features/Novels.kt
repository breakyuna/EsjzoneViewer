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

fun EsjzoneClient.novels(
    authorization: Authorization,
    novelType: Int,
    sortType: Int,
    forceRefresh: Boolean = false
): Pair<PageableRequester<CoveredNovel>, List<CoveredNovel>> {
    val firstPageUrl = "${EsjzoneUrls.Base}/list-$novelType$sortType"
    val (session, items) = ListPageCache.open(
        authorization, firstPageUrl, pageUrl = { page -> if (page == 1) firstPageUrl else "$firstPageUrl/$page.html" },
        maxAgeMillis = PageCacheTtl.LIST, kind = PageKind.LIST, forceRefresh = forceRefresh
    ) { html, _ ->
        val document = Jsoup.parse(html)
        ParsedListPage(pageCount(document), selectNovelCards(document).map {
            parseNovelCard(it, false, NovelCardLayout.LIST)
        })
    }
    return ListNovelRequester(session) to items
}

private class ListNovelRequester(
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

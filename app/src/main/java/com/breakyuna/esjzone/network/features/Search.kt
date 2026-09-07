package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.network.PageableRequester
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import org.jsoup.Jsoup

fun EsjzoneClient.search(
    authorization: Authorization,
    keyword: String,
    category: Int = 0,
    sort: Int = 1
): Pair<PageableRequester<CoveredNovel>, List<CoveredNovel>> {
    val searchUrl = EsjzoneUrls.tagsUrl(
        keyword = keyword,
        category = category,
        sort = sort
    )
    val responseBody = getPage(
        authorization,
        searchUrl,
        PageCacheTtl.SEARCH,
        pageKind = PageKind.SEARCH
    )

    val document = Jsoup.parse(responseBody)

    val pages = pageCount(document)

    val novels = mutableListOf<CoveredNovel>()

    for (novelData in selectNovelCards(document)) {
        novels.add(
            parseNovelCard(novelData, false, NovelCardLayout.LIST)
        )
    }

    return SearchNovelRequester(authorization, keyword, category, sort, pages) to novels
}

private class SearchNovelRequester(
    private val authorization: Authorization,
    private val keyword: String,
    private val category: Int,
    private val sort: Int,
    private val pages: Int
) : PageableRequester<CoveredNovel> {

    private var current: Int = 2
    override fun pages(): Int {
        return this.pages
    }

    override fun more(): List<CoveredNovel> {
        val more = more(this.current)
        current += 1
        return more
    }

    override fun more(page: Int): List<CoveredNovel> {
        val pageUrl = EsjzoneUrls.tagsUrl(
            keyword = keyword,
            category = category,
            sort = sort,
            page = page
        )
        val responseBody = EsjzoneClient.getPage(
            authorization,
            pageUrl,
            PageCacheTtl.SEARCH,
            pageKind = PageKind.SEARCH
        )

        val document = Jsoup.parse(responseBody)

        val novels = mutableListOf<CoveredNovel>()

        for (novelData in selectNovelCards(document)) {
            novels.add(parseNovelCard(novelData, false, NovelCardLayout.LIST))
        }

        return novels.toList()
    }

    override fun end(): Boolean {
        return current > pages
    }

}

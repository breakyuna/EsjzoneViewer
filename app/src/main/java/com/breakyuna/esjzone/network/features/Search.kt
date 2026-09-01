package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.EsjzoneXPaths
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.network.PageableRequester
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import org.jsoup.Jsoup

internal val pagesRegex = "total: ([0-9]+)".toRegex()

fun EsjzoneClient.search(
    authorization: Authorization,
    keyword: String
): Pair<PageableRequester<CoveredNovel>, List<CoveredNovel>> {
    val searchUrl = EsjzoneUrls.tagsUrl(keyword)
    val responseBody = getPage(
        authorization,
        searchUrl,
        PageCacheTtl.SEARCH,
        pageKind = PageKind.SEARCH
    )

    val document = Jsoup.parse(responseBody)

    val pagesRaw = EsjzoneXPaths.Tags.Pages.evaluate(document).get()
    val pages = if (pagesRaw != null) {
        pagesRegex.find(pagesRaw)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
    } else 1

    val novels = mutableListOf<CoveredNovel>()

    for (novelData in EsjzoneXPaths.Tags.Novel.All.evaluate(document).elements) {
        val r18Elements = EsjzoneXPaths.Tags.Novel.R18Badge.evaluate(novelData).elements
        val isR18 = r18Elements.firstOrNull()?.attr("class")?.contains("badge") == true

        novels.add(
            parseNovelCard(novelData, isR18, NovelCardLayout.LIST)
        )
    }

    return SearchNovelRequester(authorization, keyword, pages) to novels
}

private class SearchNovelRequester(
    private val authorization: Authorization,
    private val keyword: String,
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
        val pageUrl = EsjzoneUrls.tagsUrl(keyword, sort = 1, page = page)
        val responseBody = EsjzoneClient.getPage(
            authorization,
            pageUrl,
            PageCacheTtl.SEARCH,
            pageKind = PageKind.SEARCH
        )

        val document = Jsoup.parse(responseBody)

        val novels = mutableListOf<CoveredNovel>()

        for (novelData in EsjzoneXPaths.Tags.Novel.All.evaluate(document).elements) {
            val r18Elements = EsjzoneXPaths.Tags.Novel.R18Badge.evaluate(novelData).elements
            val isR18 = r18Elements.firstOrNull()?.attr("class")?.contains("badge") == true

            novels.add(parseNovelCard(novelData, isR18, NovelCardLayout.LIST))
        }

        return novels.toList()
    }

    override fun end(): Boolean {
        return current > pages
    }

}

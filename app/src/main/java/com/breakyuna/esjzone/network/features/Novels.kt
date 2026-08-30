package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.EsjzoneXPaths
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.PageableRequester
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovelImpl
import org.jsoup.Jsoup

fun EsjzoneClient.novels(
    authorization: Authorization,
    novelType: Int,
    sortType: Int
): Pair<PageableRequester<CoveredNovel>, List<CoveredNovel>> {
    val firstPageUrl = "${EsjzoneUrls.Base}/list-$novelType$sortType"
    val responseBody = getPage(authorization, firstPageUrl, PageCacheTtl.LIST)

    val document = Jsoup.parse(responseBody)

    val pagesRaw = EsjzoneXPaths.Tags.Pages.evaluate(document).get()
    val pages = if (pagesRaw != null) {
        pagesRegex.find(pagesRaw)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
    } else 1

    fun parseCount(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        return raw.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
    }

    val novels = mutableListOf<CoveredNovel>()

    for (novelData in EsjzoneXPaths.Tags.Novel.All.evaluate(document).elements) {
        val r18Elements = EsjzoneXPaths.Tags.Novel.R18Badge.evaluate(novelData).elements
        val isR18 = r18Elements.firstOrNull()?.attr("class")?.contains("badge") == true

        novels.add(
            CoveredNovelImpl(
                EsjzoneUrls.coverUrlFromNovelCard(novelData),
                EsjzoneXPaths.Tags.Novel.Name.evaluate(novelData).get() ?: "",
                EsjzoneXPaths.Tags.Novel.Url.evaluate(novelData).get() ?: "",
                parseCount(EsjzoneXPaths.Tags.Novel.Views.evaluate(novelData).get()),
                parseCount(EsjzoneXPaths.Tags.Novel.Likes.evaluate(novelData).get()),
                isR18
            )
        )
    }

    return ListNovelRequester(authorization, pages, novelType, sortType) to novels
}


private class ListNovelRequester(
    private val authorization: Authorization,
    private val pages: Int,
    private val novelType: Int,
    private val sortType: Int
) : PageableRequester<CoveredNovel> {

    private var current: Int = 2
    override fun pages(): Int {
        return this.pages
    }

    override fun more(): List<CoveredNovel> {
        val more = this.more(this.current)
        current += 1
        return more
    }

    override fun more(page: Int): List<CoveredNovel> = runNetworkSafely(
        tag = "ListNovelRequester",
        fallback = emptyList()
    ) {
        val pageUrl = "${EsjzoneUrls.Base}/list-$novelType$sortType/$page.html"
        val responseBody = EsjzoneClient.getPage(
            authorization,
            pageUrl,
            PageCacheTtl.LIST
        )

        val document = Jsoup.parse(responseBody)

        fun parseCount(raw: String?): Int {
            if (raw.isNullOrBlank()) return 0
            return raw.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
        }

        val novels = mutableListOf<CoveredNovel>()

        for (novelData in EsjzoneXPaths.Tags.Novel.All.evaluate(document).elements) {
            val r18Elements = EsjzoneXPaths.Tags.Novel.R18Badge.evaluate(novelData).elements
            val isR18 = r18Elements.firstOrNull()?.attr("class")?.contains("badge") == true

            novels.add(
                CoveredNovelImpl(
                    EsjzoneUrls.coverUrlFromNovelCard(novelData),
                    EsjzoneXPaths.Tags.Novel.Name.evaluate(novelData).get() ?: "",
                    EsjzoneXPaths.Tags.Novel.Url.evaluate(novelData).get() ?: "",
                    parseCount(EsjzoneXPaths.Tags.Novel.Views.evaluate(novelData).get()),
                    parseCount(EsjzoneXPaths.Tags.Novel.Likes.evaluate(novelData).get()),
                    isR18
                )
            )
        }

        novels.toList()
    }

    override fun end(): Boolean {
        return current > pages
    }

}

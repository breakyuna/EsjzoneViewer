package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.AuthorizationCookieJar
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.EsjzoneXPaths
import com.breakyuna.esjzone.network.PageableRequester
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovelImpl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

internal val pagesRegex = "total: ([0-9]+)".toRegex()

fun EsjzoneClient.search(
    authorization: Authorization,
    keyword: String
): Pair<PageableRequester<CoveredNovel>, List<CoveredNovel>> {
    val httpClient = OkHttpClient.Builder()
        .cookieJar(AuthorizationCookieJar(authorization))
        .build()

    val response = httpClient.newCall(
        Request.Builder()
            .url("${EsjzoneUrls.Tags}/$keyword")
            .get()
            .headers(this.headers)
            .build()
    ).execute()

    val responseBody = response.bodyStringOrEmpty()

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
                EsjzoneXPaths.Tags.Novel.Cover.evaluate(novelData).get() ?: EsjzoneUrls.EmptyCover,
                EsjzoneXPaths.Tags.Novel.Name.evaluate(novelData).get() ?: "",
                EsjzoneXPaths.Tags.Novel.Url.evaluate(novelData).get() ?: "",
                parseCount(EsjzoneXPaths.Tags.Novel.Views.evaluate(novelData).get()),
                parseCount(EsjzoneXPaths.Tags.Novel.Likes.evaluate(novelData).get()),
                isR18
            )
        )
    }

    return SearchNovelRequester(authorization, keyword, pages) to novels
}

private class SearchNovelRequester(
    authorization: Authorization,
    private val keyword: String,
    private val pages: Int
) : PageableRequester<CoveredNovel> {

    private val httpClient = OkHttpClient.Builder()
        .cookieJar(AuthorizationCookieJar(authorization))
        .build()

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
        tag = "SearchNovelRequester",
        fallback = emptyList()
    ) {
        val response = httpClient.newCall(
            Request.Builder()
                .url("${EsjzoneUrls.Tags}-01/$keyword/$page.html")
                .get()
                .headers(EsjzoneClient.headers)
                .build()
        ).execute()

        val responseBody = response.bodyStringOrEmpty()

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
                    EsjzoneXPaths.Tags.Novel.Cover.evaluate(novelData).get() ?: EsjzoneUrls.EmptyCover,
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

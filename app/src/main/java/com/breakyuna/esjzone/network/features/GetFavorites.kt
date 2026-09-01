package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.EsjzoneXPaths
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.network.PageableRequester
import com.breakyuna.esjzone.novellibrary.novel.FavoriteNovel
import org.jsoup.Jsoup

fun EsjzoneClient.getFavorites(
    authorization: Authorization,
    sort: String,
    forceRefresh: Boolean = false
): Pair<PageableRequester<FavoriteNovel>, List<FavoriteNovel>> {
    val firstPageUrl = favoritePageUrl(sort, 1)
    // The site uses the /new/ or /udate/ landing request to establish the
    // server-side order used by later numeric links.  Normal reads reuse the
    // persistent page cache; callers can force the landing request after a
    // sort change or a successful favorite mutation.
    val responseBody = getPage(
        authorization,
        firstPageUrl,
        PageCacheTtl.ACCOUNT_LIST,
        forceRefresh = forceRefresh,
        pageKind = PageKind.ACCOUNT
    )

    val document = Jsoup.parse(responseBody, firstPageUrl)

    val pagesRaw = EsjzoneXPaths.Profile.Favorite.Pages.evaluate(document).get()
    val matcher = if (pagesRaw != null) pagesRegex.find(pagesRaw) else null
    val pages = matcher?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1

    val novels = mutableListOf<FavoriteNovel>()

    for (novelData in EsjzoneXPaths.Profile.Favorite.Novel.evaluate(document).elements) {
        novels.add(
            FavoriteNovel(
                novelData.text(),
                novelData.attr("href")
            )
        )
    }

    return FavoriteNovelRequester(authorization, sort, pages) to novels.toList()
}


private class FavoriteNovelRequester(
    private val authorization: Authorization,
    private val sort: String,
    private val pages: Int
) : PageableRequester<FavoriteNovel> {

    private var current: Int = 2
    override fun pages(): Int {
        return this.pages
    }

    override fun more(): List<FavoriteNovel> {
        val more = this.more(this.current)
        current += 1
        return more
    }

    override fun more(page: Int): List<FavoriteNovel> = runNetworkSafely(
        tag = "FavoriteNovelRequester",
        fallback = emptyList()
    ) {
        val pageUrl = favoritePageUrl(sort, page)
        val responseBody = EsjzoneClient.getPage(
            authorization,
            pageUrl,
            PageCacheTtl.ACCOUNT_LIST,
            pageKind = PageKind.ACCOUNT
        )
        val document = Jsoup.parse(responseBody, pageUrl)

        val novels = mutableListOf<FavoriteNovel>()

        for (novelData in EsjzoneXPaths.Profile.Favorite.Novel.evaluate(document).elements) {
            novels.add(
                FavoriteNovel(
                    novelData.text(),
                    novelData.attr("href")
                )
            )
        }

        novels.toList()
    }

    override fun end(): Boolean {
        return current > pages
    }

}

/**
 * ESJ's sort select navigates to /new/ or /udate/ first.  The new-order pager
 * then emits bare numeric links, while the update-order pager keeps /udate/;
 * visiting the landing route establishes the server-side order before those
 * page links are requested.
 */
internal fun favoritePageUrl(sort: String, page: Int): String {
    val safePage = page.coerceAtLeast(1)
    val route = if (sort.trim().equals("udate", ignoreCase = true)) "udate" else "new"
    return when {
        route == "udate" && safePage == 1 ->
            "${EsjzoneUrls.My.Favorite}/udate/"
        route == "udate" ->
            "${EsjzoneUrls.My.Favorite}/udate/$safePage"
        safePage == 1 ->
            "${EsjzoneUrls.My.Favorite}/new/"
        else ->
            "${EsjzoneUrls.My.Favorite}/$safePage"
    }
}

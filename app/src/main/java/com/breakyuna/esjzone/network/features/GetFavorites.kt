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

    val novels = parseFavoriteNovels(document)

    return FavoriteNovelRequester(authorization, sort, pages) to novels.toList()
}

/**
 * Fetches and parses every page of the remote shelf. Any network or parser
 * failure is allowed to escape so callers can preserve their local shelf.
 */
fun EsjzoneClient.getAllFavorites(
    authorization: Authorization,
    sort: String = "new",
    forceRefresh: Boolean = true
): List<FavoriteNovel> {
    val firstPageUrl = favoritePageUrl(sort, 1)
    val firstBody = getPage(
        authorization,
        firstPageUrl,
        PageCacheTtl.ACCOUNT_LIST,
        forceRefresh = forceRefresh,
        pageKind = PageKind.ACCOUNT,
        allowStaleOnError = false
    )
    val firstDocument = Jsoup.parse(firstBody, firstPageUrl)
    val pagesRaw = EsjzoneXPaths.Profile.Favorite.Pages.evaluate(firstDocument).get()
    val pages = (pagesRegex.find(pagesRaw.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1)
        .coerceIn(1, 200)
    val all = LinkedHashMap<String, FavoriteNovel>()
    fun addPage(document: org.jsoup.nodes.Document) {
        parseFavoriteNovels(document).forEach { novel ->
            val key = EsjzoneUrls.canonicalPageKey(novel.url).ifBlank { novel.url.trim() }
            if (key.isNotBlank()) all.putIfAbsent(key, novel)
        }
    }
    fun requireFavoritePage(document: org.jsoup.nodes.Document, body: String) {
        // An empty but valid account page is allowed, while an unrelated
        // blank/template response must abort the snapshot before it reaches
        // the local merge state machine.
        val hasFavoriteMarker = body.contains("/my/favorite", ignoreCase = true) ||
            body.contains("my/favorite", ignoreCase = true)
        val hasFavoriteTable = document.select("table").isNotEmpty()
        if (parseFavoriteNovels(document).isEmpty() &&
            (!hasFavoriteMarker || !hasFavoriteTable)
        ) {
            throw IllegalStateException("favorite page marker missing")
        }
    }
    requireFavoritePage(firstDocument, firstBody)
    addPage(firstDocument)
    for (page in 2..pages) {
        val pageUrl = favoritePageUrl(sort, page)
        val body = getPage(
            authorization,
            pageUrl,
            PageCacheTtl.ACCOUNT_LIST,
            forceRefresh = forceRefresh,
            pageKind = PageKind.ACCOUNT
        )
        val document = Jsoup.parse(body, pageUrl)
        requireFavoritePage(document, body)
        addPage(document)
    }
    return all.values.toList()
}

private fun parseFavoriteNovels(document: org.jsoup.nodes.Document): List<FavoriteNovel> =
    EsjzoneXPaths.Profile.Favorite.Novel.evaluate(document).elements.mapNotNull { element ->
        val url = element.attr("href").trim()
        val title = element.text().trim()
        if (url.isBlank() || title.isBlank()) null else FavoriteNovel(title, url)
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
        val more = more(this.current)
        current += 1
        return more
    }

    override fun more(page: Int): List<FavoriteNovel> {
        val pageUrl = favoritePageUrl(sort, page)
        val responseBody = EsjzoneClient.getPage(
            authorization,
            pageUrl,
            PageCacheTtl.ACCOUNT_LIST,
            pageKind = PageKind.ACCOUNT
        )
        val document = Jsoup.parse(responseBody, pageUrl)

        return parseFavoriteNovels(document)
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

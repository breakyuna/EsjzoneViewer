package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.HtmlSelector
import com.breakyuna.esjzone.network.JsoupHtmlSelector
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.network.PageableRequester
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.novellibrary.novel.FavoriteNovel
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException

private val favoriteSelector: HtmlSelector = JsoupHtmlSelector

fun EsjzoneClient.getFavorites(
    authorization: Authorization,
    sort: String,
    forceRefresh: Boolean = false
): Pair<PageableRequester<FavoriteNovel>, List<FavoriteNovel>> {
    val firstPageUrl = favoritePageUrl(sort, 1, authorization.domain)
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

    val pages = pageCount(document)

    val novels = parseFavoriteNovels(document)

    return FavoriteNovelRequester(authorization, sort, pages) to novels.toList()
}

/**
 * Fetches and parses every page of the remote shelf. Any network or parser
 * failure is allowed to escape so callers can preserve their local shelf.
 */
fun EsjzoneClient.getAllFavorites(
    authorization: Authorization,
    sort: String = "udate",
    forceRefresh: Boolean = true
): List<FavoriteNovel> {
    fun fetchPage(url: String): String {
        var lastFailure: IOException? = null
        repeat(3) { attempt ->
            try {
                return getPage(
                    authorization, url, PageCacheTtl.ACCOUNT_LIST,
                    forceRefresh = forceRefresh, pageKind = PageKind.ACCOUNT,
                    allowStaleOnError = false
                )
            } catch (error: IOException) {
                if (error.loadFailureKind() != LoadFailureKind.NETWORK) throw error
                lastFailure = error
                if (attempt < 2) {
                    try {
                        Thread.sleep(400L * (attempt + 1))
                    } catch (interrupted: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw IOException("Favorite page retry interrupted", interrupted)
                    }
                }
            }
        }
        throw lastFailure ?: IOException("Favorite page unavailable")
    }
    val firstPageUrl = favoritePageUrl(sort, 1, authorization.domain)
    val firstBody = fetchPage(firstPageUrl)
    val firstDocument = Jsoup.parse(firstBody, firstPageUrl)
    val pages = pageCount(firstDocument)
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
        val hasFavoriteTable = favoriteSelector.select(document, "table").isNotEmpty()
        if (parseFavoriteNovels(document).isEmpty() &&
            (!hasFavoriteMarker || !hasFavoriteTable)
        ) {
            throw IllegalStateException("favorite page marker missing")
        }
    }
    requireFavoritePage(firstDocument, firstBody)
    addPage(firstDocument)
    for (page in 2..pages) {
        val pageUrl = favoritePageUrl(sort, page, authorization.domain)
        val body = fetchPage(pageUrl)
        val document = Jsoup.parse(body, pageUrl)
        requireFavoritePage(document, body)
        addPage(document)
    }
    return all.values.toList()
}

internal fun parseFavoriteNovels(document: Document): List<FavoriteNovel> =
    favoriteSelector.select(document, "table.table tr").mapNotNull { row ->
        val element = favoriteSelector.first(row, ".product-title > a, h5 a[href^='/detail/']") ?: return@mapNotNull null
        val url = element.attr("href").trim()
        val title = element.text().trim()
        if (url.isBlank() || title.isBlank()) null else FavoriteNovel(title, url)
            .let { novel ->
                val episodes = favoriteSelector.select(row, ".book-ep > div")
                val latest = episodes.getOrNull(0)
                val watched = episodes.getOrNull(1)
                novel.copy(
                    latestTitle = latest?.text()?.removePrefix("最新：")?.trim()?.ifBlank { null },
                    latestUrl = latest?.let { favoriteSelector.first(it, "a") }?.attr("href")
                        ?.trim()?.ifBlank { null },
                    remoteLastViewedTitle = watched?.text()
                        ?.removePrefix("最後觀看：")?.removePrefix("最后观看：")
                        ?.trim()?.ifBlank { null },
                    remoteUpdatedAt = favoriteSelector.first(row, ".book-update")?.text()
                        ?.removePrefix("更新日期：")?.trim()?.ifBlank { null }
                )
            }
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
        val pageUrl = favoritePageUrl(sort, page, authorization.domain)
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
internal fun favoritePageUrl(
    sort: String,
    page: Int,
    domain: String = EsjzoneUrls.BaseWithoutProtocol
): String {
    val safePage = page.coerceAtLeast(1)
    val route = if (sort.trim().equals("udate", ignoreCase = true)) "udate" else "new"
    val base = EsjzoneUrls.baseForDomain(domain.ifBlank { EsjzoneUrls.BaseWithoutProtocol })
    val favorite = "$base/my/favorite"
    return when {
        route == "udate" && safePage == 1 ->
            "$favorite/udate/"
        route == "udate" ->
            "$favorite/udate/$safePage"
        safePage == 1 ->
            "$favorite/new/"
        else ->
            "$favorite/$safePage"
    }
}

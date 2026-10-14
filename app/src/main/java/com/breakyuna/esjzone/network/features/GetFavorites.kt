package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.HtmlSelector
import com.breakyuna.esjzone.network.JsoupHtmlSelector
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.network.ListPageCache
import com.breakyuna.esjzone.network.ParsedListPage
import com.breakyuna.esjzone.network.PageableRequester
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
    val (session, novels) = ListPageCache.open(
        authorization, firstPageUrl, pageUrl = { favoritePageUrl(sort, it, authorization.domain) },
        maxAgeMillis = PageCacheTtl.ACCOUNT_LIST, kind = PageKind.ACCOUNT, forceRefresh = forceRefresh
    ) { html, _ -> parseFavoritePage(html, firstPageUrl) }
    return FavoriteNovelRequester(session) to novels
}

/** A full remote shelf is published only after every page has succeeded. */
fun EsjzoneClient.getAllFavorites(
    authorization: Authorization,
    sort: String = "udate",
    forceRefresh: Boolean = true
): List<FavoriteNovel> {
    val firstPageUrl = favoritePageUrl(sort, 1, authorization.domain)
    val (_, novels) = ListPageCache.open(
        authorization, firstPageUrl, pageUrl = { favoritePageUrl(sort, it, authorization.domain) },
        maxAgeMillis = PageCacheTtl.ACCOUNT_LIST, kind = PageKind.ACCOUNT,
        forceRefresh = forceRefresh, allowFallback = false, complete = true, networkAttempts = 3
    ) { html, _ -> parseFavoritePage(html, firstPageUrl, complete = true) }
    return novels.distinctBy { EsjzoneUrls.canonicalPageKey(it.url).ifBlank { it.url.trim() } }
}

private fun parseFavoritePage(html: String, url: String, complete: Boolean = false): ParsedListPage<FavoriteNovel> {
    val document = Jsoup.parse(html, url)
    val novels = parseFavoriteNovels(document)
    if (novels.isEmpty() && (!html.contains("my/favorite", ignoreCase = true) ||
            favoriteSelector.select(document, "table").isEmpty())) {
        throw IOException("Favorite page marker missing")
    }
    val pages = pageCount(document)
    return ParsedListPage(if (complete) pages.coerceIn(1, 200) else pages, novels)
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
    private val session: ListPageCache.Session<FavoriteNovel>
) : PageableRequester<FavoriteNovel> {
    private var current = 2
    override fun pages(): Int = session.pageCount
    override fun isCurrent(): Boolean = session.isCurrent()
    override fun fallbackFailure() = session.fallbackFailure
    override fun more(): List<FavoriteNovel> = more(current).also { current += 1 }
    override fun more(page: Int): List<FavoriteNovel> = session.more(page)
    override fun end(): Boolean = current > pages()
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

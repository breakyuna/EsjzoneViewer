package com.breakyuna.esjzone.ui.navigation

import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.data.settings.SettingsDefaults
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.FavoriteNovel
import com.breakyuna.esjzone.ui.page.ChapterPage
import com.breakyuna.esjzone.ui.page.ExperienceRecordsPage
import com.breakyuna.esjzone.ui.page.MemberRepliesPage
import com.breakyuna.esjzone.ui.page.SystemMessagesPage
import com.breakyuna.esjzone.ui.page.ForumPage
import com.breakyuna.esjzone.ui.page.GuestbookPage
import com.breakyuna.esjzone.ui.tab.HomeTab
import com.breakyuna.esjzone.ui.tab.ProfileTab
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

private val novelPath = Regex("/detail/[0-9]+\\.html")
private val forumCategoryPath = Regex("/forum/([0-9]+)")
private val forumBoardPath = Regex("/forum/[0-9]+/[0-9]+")
private val forumPostPath = Regex("/forum/([0-9]+)/[0-9]+\\.html")
private val novelListPath = Regex("/list-([0-3])([1-8])")

private fun nativeSitePath(url: HttpUrl): String? {
    val siteHost = url.host.removePrefix("www.")
    if (SettingsDefaults.DOMAINS.none { it.removePrefix("www.") == siteHost }) return null
    val defaultPort = if (url.isHttps) 443 else 80
    if (url.port != defaultPort || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
    if (url.query != null || url.fragment != null) return null

    return url.encodedPath.trimEnd('/')
}

/** Only route links whose path alone identifies a native destination. */
internal fun forumLinkRoute(url: HttpUrl): LegacyRoute? {
    val path = nativeSitePath(url) ?: return null
    when (path) {
        "" -> return LegacyRoute.Static(HomeTab.key)
        "/forum" -> return LegacyRoute.Static(ForumPage.key)
        "/guestbook" -> return LegacyRoute.Static(GuestbookPage.key)
        "/my/profile", "/my/profile.html" -> return LegacyRoute.Static(ProfileTab.key)
        "/my/sys" -> return LegacyRoute.Static(SystemMessagesPage.key)
        "/my/reply" -> return LegacyRoute.Static(MemberRepliesPage.key)
        "/my/record" -> return LegacyRoute.Static(ExperienceRecordsPage.key)
    }
    if (novelPath.matches(path)) return LegacyRoute.Novel(path)
    if (forumBoardPath.matches(path)) return LegacyRoute.ForumBoard(path)
    forumCategoryPath.matchEntire(path)?.let {
        return LegacyRoute.ForumCategory(id = it.groupValues[1], url = "$path/")
    }
    novelListPath.matchEntire(path)?.let {
        return LegacyRoute.NovelList(it.groupValues[1].toInt(), it.groupValues[2].toInt(), adultOnly = false)
    }
    val segments = url.pathSegments.dropLastWhile(String::isEmpty)
    if (segments.size == 2 && segments[0] == "tags" && segments[1].isNotBlank()) {
        return LegacyRoute.Search(segments[1])
    }
    return null
}

internal fun forumContentLinkPath(url: HttpUrl): String? =
    nativeSitePath(url)?.takeIf(forumPostPath::matches)

internal fun resolveForumContentLink(authorization: Authorization, path: String): AppDestination? {
    val client = PresentationAccess.client
    val targetUrl = EsjzoneUrls.resolve(path)
    val html = client.getPage(authorization, targetUrl, PageCacheTtl.COMMUNITY, pageKind = PageKind.COMMUNITY)
    return forumContentLinkDestination(targetUrl, html) { novelUrl ->
        client.getNovelDetail(authorization, FavoriteNovel(name = "", url = novelUrl))
    }
}

internal fun forumContentLinkDestination(
    url: String,
    html: String,
    loadNovel: (String) -> DetailedNovel
): AppDestination? {
    val targetUrl = url.toHttpUrlOrNull() ?: return null
    val path = forumContentLinkPath(targetUrl) ?: return null
    val parentId = forumPostPath.matchEntire(path)?.groupValues?.get(1) ?: return null
    val document = Jsoup.parse(html, url)
    if (document.selectFirst(".forum-content") == null) return null

    val detailPath = "/detail/$parentId.html"
    val linksToNovel = document.select("a.view-all[href]").any { link ->
        link.parents().none { it.hasClass("forum-content") || it.hasClass("comment") } &&
            targetUrl.resolve(link.attr("href"))?.let(::nativeSitePath) == detailPath
    }
    if (linksToNovel) {
        val novel = loadNovel(detailPath)
        val order = novel.chapterList.orderedChapters
        val chapter = order.firstOrNull {
            !it.isExternal && EsjzoneUrls.canonicalPageKey(it.url) == path
        }
        if (chapter != null) {
            return ChapterPage(
                novelId = parentId,
                chapter = chapter,
                history = ChapterStateHolder(chapter),
                chapterOrder = order,
                novelName = novel.name,
                novelUrl = novel.url,
                novelCoverUrl = novel.coverUrl,
                keepRequestedChapterOnRestore = true
            )
        }
    }
    return LegacyRoute.ForumPost(path).restore()
}

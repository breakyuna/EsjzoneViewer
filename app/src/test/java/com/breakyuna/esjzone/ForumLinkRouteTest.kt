package com.breakyuna.esjzone

import com.breakyuna.esjzone.novellibrary.component.ChapterItem
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.NovelChapterList
import com.breakyuna.esjzone.novellibrary.novel.NovelDescription
import com.breakyuna.esjzone.ui.navigation.LegacyRoute
import com.breakyuna.esjzone.ui.navigation.forumContentLinkDestination
import com.breakyuna.esjzone.ui.navigation.forumContentLinkPath
import com.breakyuna.esjzone.ui.navigation.forumLinkRoute
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForumLinkRouteTest {
    @Test
    fun supportedSiteLinksResolveToExistingDestinations() {
        assertEquals(
            LegacyRoute.ForumBoard("/forum/1584622376/1585970655"),
            forumLinkRoute("https://www.esjzone.one/forum/1584622376/1585970655/".toHttpUrl())
        )
        assertEquals(
            LegacyRoute.Novel("/detail/123.html"),
            forumLinkRoute("https://www.esjzone.cc/detail/123.html".toHttpUrl())
        )
        assertEquals(
            LegacyRoute.Search("TS !R18"),
            forumLinkRoute("https://www.esjzone.cc/tags/TS%20!R18/".toHttpUrl())
        )
    }

    @Test
    fun forumStyleUrlsOpenTheReaderOnlyForCatalogChapters() {
        val chapterUrl = "https://www.esjzone.cc/forum/9001/9201.html"
        val chapterPath = "/forum/9001/9201.html"
        val postUrl = "https://www.esjzone.cc/forum/9001/9202.html"
        val novelHtml = """
            <a class="view-all" href="/detail/9001.html">Book</a>
            <div class="forum-content mt-3">Body</div>
        """.trimIndent()
        val novel = DetailedNovel(
            name = "Book", url = "/detail/9001.html", coverUrl = "", views = 0, likes = 0,
            words = 0, type = "", author = "", forumUrl = "", tags = emptyList(),
            isAdult = false, isFavorite = false, description = NovelDescription(emptyList()),
            chapterList = NovelChapterList(listOf(ChapterItem(Chapter("Chapter", chapterPath, false))))
        )
        val loadNovel: (String) -> DetailedNovel = { path ->
            assertEquals(novel.url, path)
            novel
        }
        assertNull(forumLinkRoute(chapterUrl.toHttpUrl()))
        assertEquals(chapterPath, forumContentLinkPath(chapterUrl.toHttpUrl()))
        assertEquals("ChapterPage:9001:$chapterPath",
            forumContentLinkDestination(chapterUrl, novelHtml, loadNovel)?.key)
        assertEquals("ForumPostPage:/forum/9001/9202.html",
            forumContentLinkDestination(postUrl, novelHtml, loadNovel)?.key)
    }

    @Test
    fun discussionLinksDoNotNeedANovelAndUnknownPagesFallBack() {
        val url = "https://www.esjzone.cc/forum/1585405223/409387.html"
        val postHtml = """
            <div class="forum-content">
              <a class="view-all" href="/detail/1585405223.html">User supplied link</a>
            </div>
        """.trimIndent()
        assertEquals("ForumPostPage:/forum/1585405223/409387.html",
            forumContentLinkDestination(url, postHtml) { error("A post body cannot identify a chapter") }?.key)
        assertNull(forumContentLinkDestination(url, "<p>Other template</p>") { error("No post") })
    }

    @Test
    fun linksRequiringTheOriginalPageStayInWebView() {
        for (url in listOf(
            "https://www.esjzone.cc/forum/1585405223/409387.html#myQA",
            "https://www.esjzone.cc/my/profile.html?uid=7",
            "https://www.esjzone.cc/faq/",
            "https://www.esjzone.cc.evil.invalid/forum/1585405223/409387.html",
            "https://qm.qq.com/q/example"
        )) {
            assertNull(url, forumLinkRoute(url.toHttpUrl()))
            assertNull(url, forumContentLinkPath(url.toHttpUrl()))
        }
    }
}

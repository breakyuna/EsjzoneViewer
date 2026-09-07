package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.network.PageResponsePolicy
import com.breakyuna.esjzone.network.features.NovelCardLayout
import com.breakyuna.esjzone.network.features.findForumNovelDetailUrl
import com.breakyuna.esjzone.network.features.parseComments
import com.breakyuna.esjzone.network.features.parseForumThreads
import com.breakyuna.esjzone.network.features.parseForumTopicRows
import com.breakyuna.esjzone.network.features.parseNovelCard
import com.breakyuna.esjzone.network.features.hasProfileMarker
import com.breakyuna.esjzone.network.features.profileAvatarUrl
import com.breakyuna.esjzone.network.features.profileName
import com.breakyuna.esjzone.novellibrary.component.ImageComponent
import com.breakyuna.esjzone.novellibrary.component.analyseComponents
import com.breakyuna.esjzone.novellibrary.novel.analyseChapterList
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Redacted, checked-in HTML corpus for the incremental CSS parser migration.
 *
 * These tests intentionally assert the current result contract at the DOM
 * boundary. They do not change production parsing and contain no live session
 * data, cookies, or network calls.
 */
class EsjHtmlCorpusBaselineTest {

    @Test
    fun homeListAndSearchFixtures_preserveCardResults() {
        val home = fixture("home.html")
        val homeCards = home.select("#recent-translated article.card.mb-30")
        assertEquals(1, homeCards.size)
        val homeNovel = parseNovelCard(homeCards[0], false, NovelCardLayout.HOME)
        assertEquals("Home Fixture Novel", homeNovel.name)
        assertEquals("/detail/9001.html", homeNovel.url)
        assertEquals("Home Chapter 7", homeNovel.latestTitle)
        assertEquals(1_234, homeNovel.views)
        assertEquals(56, homeNovel.likes)

        val list = fixture("list.html")
        val listNovel = parseNovelCard(
            list.selectFirst("main#list-results article.card.mb-30")!!,
            false,
            NovelCardLayout.LIST
        )
        assertEquals("List Fixture Novel", listNovel.name)
        assertEquals("Fixture Author", listNovel.author)
        assertEquals(205_943, listNovel.words)
        assertEquals(480, listNovel.articleCount)
        assertEquals(12, listNovel.discussionCount)

        val search = fixture("search.html")
        val pages = Regex("total: ([0-9]+)").find(search.html())?.groupValues?.get(1)
        assertEquals("2", pages)
        assertEquals(2, search.select("#search-results article.card.mb-30").size)
        assertEquals(
            listOf("Search Fixture One", "Search Fixture Two"),
            search.select("#search-results h5.card-title a").eachText()
        )
    }

    @Test
    fun detailFixture_preservesMetadataAndNestedChapterOrder() {
        val document = fixture("detail.html")
        val detail = document.selectFirst(".book-detail")!!
        assertEquals("Detail Fixture Novel", detail.selectFirst("h2")?.text())
        assertEquals("3,210", detail.selectFirst("#vtimes")?.text())
        assertEquals("42", detail.selectFirst("#favorite")?.text())
        assertEquals("12,345", detail.selectFirst("#txt")?.text())
        assertEquals("Detail Author", detail.selectFirst("ul a[href^='/tags/']")?.text())
        assertEquals("/forum/9030/7030/", detail.selectFirst("a.btn-forum")?.attr("href"))
        assertEquals("已收藏", detail.selectFirst("button.btn-favorite")?.text())
        assertEquals(listOf("Fixture", "R18"), detail.select(".tags a").eachText())

        val chapterList = analyseChapterList(document.selectFirst("#integration")!!)
        assertEquals(
            listOf("Opening Chapter", "Second Chapter"),
            chapterList.orderedChapters.map { it.name }
        )
        assertEquals(2, chapterList.items.size)
        assertNotNull(document.selectFirst("details[open]"))
    }

    @Test
    fun chapterFixture_preservesRichBodyImagesAndNavigation() {
        val document = fixture("chapter.html")
        val content = document.selectFirst(".forum-content.mt-3")!!
        val components = analyseComponents(content)
        assertEquals(2, content.select("p").size)
        assertTrue(components.any { it is ImageComponent })
        assertTrue(content.html().contains("<strong>bold</strong>"))
        assertEquals("Previous Fixture", document.selectFirst("a.btn-prev")?.attr("data-title"))
        assertEquals("/forum/9030/7032.html", document.selectFirst("a.btn-next")?.attr("href"))
    }

    @Test
    fun commentsFixture_preservesIdentityQuoteAvatarAndReplyToken() {
        val comments = parseComments(fixture("comments.html"), "7031")
        assertEquals(1, comments.size)
        val comment = comments.single()
        assertEquals("8001", comment.id)
        assertEquals("77", comment.authorId)
        assertEquals("Fixture User", comment.authorName)
        assertEquals("#1", comment.floor)
        assertEquals("2026-09-01T01:02", comment.createdAt)
        assertEquals("Quoted fixture text", comment.quotedContentText)
        assertEquals("Comment body.", comment.contentText)
        assertEquals("8001-77", comment.replyToken)
    }

    @Test
    fun forumFixture_preservesBoardsTopicsAndNovelBoardDetection() {
        val document = fixture("forum.html")
        val threads = parseForumThreads(document)
        assertEquals(listOf("8040", "8041"), threads.map { it.id })
        assertEquals(listOf("9040", "9041"), threads.map { it.categoryId })
        assertEquals("/detail/9040.html", findForumNovelDetailUrl(
            Jsoup.parse("<div class='forum-detail'><a href='/detail/9040.html'>Fixture</a></div>")
        ))

        val topics = parseForumTopicRows(document.selectFirst("#dataTable")!!, "8040")
        assertEquals(1, topics.size)
        assertEquals("8040", topics.single().boardId)
        assertEquals("8050", topics.single().id)
        assertEquals("Fixture Topic", topics.single().title)
        assertEquals(5, topics.single().viewCount)
    }

    @Test
    fun memberFixtures_preserveFavoriteHistoryProfileAndAuthorizationMarkers() {
        val favorites = fixture("favorite.html")
        assertEquals(
            listOf("Favorite Fixture One", "Favorite Fixture Two"),
            favorites.select("table.table h5 a[href^='/detail/']").eachText()
        )
        assertEquals(2, favorites.select("table.table tr").size)

        val history = fixture("history.html")
        assertEquals(
            listOf("9060", "9061"),
            history.select("tr[id^='novel-']").map { it.id().removePrefix("novel-") }
        )
        assertEquals(
            listOf("History Chapter One", "History Chapter Two"),
            history.select(".book-ep a").eachText()
        )
        assertEquals(listOf("log-1", "log-2"), history.select(".view-del").eachAttr("data-id"))

        val profile = fixture("profile.html")
        assertEquals("Fixture Profile User", profileName(profile))
        assertEquals("/uploads/fixture-profile.jpg", profileAvatarUrl(profile))
        assertEquals("Fixture Profile User", profile.selectFirst("input[name=nickname]")?.attr("value"))

        val authorizedDocument = fixture("authorized.html")
        assertTrue(hasProfileMarker(authorizedDocument))
        val authorized = authorizedDocument.html()
        val validation = PageResponsePolicy.validate(
            statusCode = 200,
            body = authorized,
            requestedUrl = "https://example.test/my/profile",
            kind = PageKind.ACCOUNT
        )
        assertTrue(validation.trusted)

        val missingProfile = Jsoup.parse("<html><body><main>Guest</main></body></html>")
        assertEquals("User", profileName(missingProfile))
        assertEquals("", profileAvatarUrl(missingProfile))
        assertTrue(!hasProfileMarker(missingProfile))
    }

    private fun fixture(name: String): Document =
        Jsoup.parse(fixtureText(name), "https://example.test/")

    private fun fixtureText(name: String): String =
        requireNotNull(javaClass.getResource("/esj/$name")) {
            "missing checked-in parser fixture: $name"
        }.openStream().bufferedReader().use { it.readText() }
}

package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.features.ForumBoardDataException
import com.breakyuna.esjzone.network.features.findForumNovelDetailUrl
import com.breakyuna.esjzone.network.features.parseComments
import com.breakyuna.esjzone.network.features.parseForumPost
import com.breakyuna.esjzone.network.features.parseForumThreads
import com.breakyuna.esjzone.network.features.parseForumTopicRows
import com.breakyuna.esjzone.network.features.parseForumTopics
import com.breakyuna.esjzone.novellibrary.community.ForumTopic
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CommunityParserTest {

    @Test
    fun parseComments_assignsFixedPageAndPreservesReplyCapability() {
        val document = Jsoup.parse(
            """
            <div class="comments-section comments-page-2">
              <div class="comment" id="comment-42">
                <div class="comment-body">
                  <div class="comment-header">
                    <img class="avatar" src="/assets/avatar-placeholder.gif" data-src="/assets/alice.png">
                    <a href="/my/profile.html?uid=7">Alice</a>
                    <span class="comment-floor">#16</span>
                    <span class="comment-meta">2026-08-28</span>
                  </div>
                  <blockquote><p>The original message</p></blockquote>
                  <div class="comment-text"><p>Hello <strong>ESJ</strong></p></div>
                  <button class="forum_reply" data-comment="42-7">Reply</button>
                </div>
              </div>
            </div>
            """.trimIndent()
        )

        val comments = parseComments(document, "9001")

        assertEquals(1, comments.size)
        assertEquals("42", comments.single().id)
        assertEquals("9001", comments.single().parentPostId)
        assertEquals("7", comments.single().authorId)
        assertEquals("Alice", comments.single().authorName)
        assertEquals("/assets/alice.png", comments.single().authorAvatarUrl)
        assertEquals("#16", comments.single().floor)
        assertEquals("Hello ESJ", comments.single().contentText)
        assertEquals("The original message", comments.single().quotedContentText)
        assertEquals(1, comments.single().pageGroup)
        assertEquals("42-7", comments.single().replyToken)
    }

    @Test
    fun parseComments_assignsExactlyFifteenCommentsPerPage() {
        val commentsHtml = (1..16).joinToString(separator = "") { index ->
            """
            <div class="comment" id="comment-$index">
              <div class="comment-header">
                <a href="/my/profile.html?uid=$index">User $index</a>
                <time datetime="2026-08-29T00:00:00">2026-08-29</time>
              </div>
              <div class="comment-text">Comment $index</div>
            </div>
            """.trimIndent()
        }
        val comments = parseComments(
            Jsoup.parse("<section class=\"comments-section comments-page-1\">$commentsHtml</section>"),
            "9001"
        )

        assertEquals(16, comments.size)
        assertEquals(1, comments[0].pageGroup)
        assertEquals(1, comments[14].pageGroup)
        assertEquals(2, comments[15].pageGroup)
        assertEquals("2026-08-29", comments[15].createdAt)
    }

    @Test
    fun parseComments_readsEsjLazyAvatarAndSkipsFloorAsTimestamp() {
        val document = Jsoup.parse(
            """
            <section class="comments-section comments-page-1">
              <div class="comment" id="comment-114104">
                <div class="comment-title">
                  <a href="/my/profile?uid=114104">Alice</a>
                </div>
                <div class="comment-author-ava">
                  <div class="lazyload-author-ava" data-src="/uploads/avatar/115/114104.jpg"></div>
                </div>
                <span class="comment-meta comment-floor">#1</span>
                <span class="comment-meta">2026-08-29 01:02</span>
                <div class="comment-text">Hello</div>
              </div>
              <div class="comment" id="comment-114105">
                <div class="comment-author-ava">
                  <div class="lazyload-author-ava" style="background-image: url('/uploads/avatar/115/114105.jpg')"></div>
                </div>
                <span class="comment-meta comment-floor">#2</span>
                <span class="comment-meta">2026-08-29 01:03</span>
                <div class="comment-text">World</div>
              </div>
            </section>
            """.trimIndent(),
            "https://www.esjzone.cc/detail/1750168764.html"
        )

        val comments = parseComments(document, "detail-1750168764")

        assertEquals(
            "https://www.esjzone.cc/uploads/avatar/115/114104.jpg",
            comments[0].authorAvatarUrl
        )
        assertEquals(
            "https://www.esjzone.cc/uploads/avatar/115/114105.jpg",
            comments[1].authorAvatarUrl
        )
        assertEquals("2026-08-29 01:02", comments[0].createdAt)
        assertEquals("2026-08-29 01:03", comments[1].createdAt)
        assertEquals("#1", comments[0].floor)
    }

    @Test
    fun parseForumTopics_readsBootstrapTableJson() {
        val topics = parseForumTopics(
            """
            {"total":1,"rows":[
              {"subject":"<a href=\"/forum/1589699634/150985.html\">A topic</a>",
               "cdate":"Alice<div class=\"forum-desc\">2022-08-21</div>",
               "vtimes":"3<div class=\"forum-desc\">15</div>",
               "last_reply":"2026-08-26 03:52"}
            ]}
            """.trimIndent(),
            "1589699634"
        )

        assertEquals(1, topics.size)
        assertEquals(
            ForumTopic(
                boardId = "1589699634",
                id = "150985",
                title = "A topic",
                author = "Alice",
                createdAt = "2022-08-21",
                replyCount = 3,
                viewCount = 15,
                lastReplyAt = "2026-08-26 03:52",
                url = "/forum/1589699634/150985.html"
            ),
            topics.single()
        )
    }

    @Test
    fun parseForumThreads_readsEveryChildCellForBothForumLayouts() {
        val document = Jsoup.parse(
            """
            <table class="forum-board-detail">
              <tbody><tr>
                <td>
                  <a href="/forum/1584622325/1788015863/">身份保障妓院</a>
                  <div class="forum-desc">主題：3　回覆：4　最後發表：2026-08-30</div>
                </td>
                <td>unused</td>
                <td>
                  <a href="/forum/1584622376/1585405336/">推坑區</a>
                  <div class="forum-desc">主題：142　回覆：300　最後發表：2026-08-29</div>
                </td>
                <td>unused</td>
              </tr></tbody>
            </table>
            """.trimIndent()
        )

        val threads = parseForumThreads(document)

        assertEquals(2, threads.size)
        assertEquals("1584622325", threads[0].categoryId)
        assertEquals("1788015863", threads[0].id)
        assertEquals("身份保障妓院", threads[0].title)
        assertEquals("1584622376", threads[1].categoryId)
        assertEquals("1585405336", threads[1].id)
        assertEquals("推坑區", threads[1].title)
    }

    @Test
    fun parseForumTopicRows_ignoresInitialBootstrapPlaceholder() {
        val table = Jsoup.parse(
            """
            <table id="dataTable" data-url="/inc/forum_list_data.php?totalRows=142">
              <tbody><tr class="no-records-found"><td colspan="4">没有找到匹配的记录</td></tr></tbody>
            </table>
            """.trimIndent()
        ).selectFirst("#dataTable")!!

        assertEquals(emptyList<ForumTopic>(), parseForumTopicRows(table, "1585405336"))
    }

    @Test
    fun parseForumTopicRows_usesActualBoardIdFromNestedTopicLinks() {
        val table = Jsoup.parse(
            """
            <table id="dataTable"><tbody>
              <tr>
                <td><a href="/forum/1788015863/574780.html">作者的话</a></td>
                <td>Alice</td><td>1<div class="forum-desc">2</div></td><td>2026-08-30</td>
              </tr>
              <tr>
                <td><a href="/forum/1585405336/545856.html">主题</a></td>
                <td>Bob</td><td>3<div class="forum-desc">4</div></td><td>2026-08-29</td>
              </tr>
            </tbody></table>
            """.trimIndent()
        ).selectFirst("#dataTable")!!

        val topics = parseForumTopicRows(table, "1584622325")

        assertEquals(listOf("1788015863", "1585405336"), topics.map { it.boardId })
        assertEquals(
            listOf("/forum/1788015863/574780.html", "/forum/1585405336/545856.html"),
            topics.map { it.url }
        )
    }

    @Test
    fun findForumNovelDetailUrl_distinguishesNovelAndDiscussionBoards() {
        val novelBoard = Jsoup.parse(
            "<div class=\"forum-detail\"><a href=\"/detail/1788015863.html\">身份保障妓院</a></div>"
        )
        val discussionBoard = Jsoup.parse(
            "<a href=\"/forum/1585405336/545856.html\">主题</a>"
        )

        assertEquals("/detail/1788015863.html", findForumNovelDetailUrl(novelBoard))
        assertNull(findForumNovelDetailUrl(discussionBoard))
    }

    @Test
    fun findForumNovelDetailUrl_ignoresDetailLinksOutsideNovelHeader() {
        val boardWithTopicLink = Jsoup.parse(
            """
            <a href="/detail/999.html">来自主题正文的作品链接</a>
            <div class="forum-detail">
                <h2><a href="/detail/1788015863.html">作品标题</a></h2>
            </div>
            """.trimIndent()
        )

        assertEquals("/detail/1788015863.html", findForumNovelDetailUrl(boardWithTopicLink))
    }

    @Test
    fun validateForumTableResponse_rejectsNonSuccessApplicationStatus() {
        assertThrows(ForumBoardDataException::class.java) {
            validateForumTableResponse("{\"status\":301,\"total\":0,\"rows\":[]}")
        }
    }

    @Test
    fun validateForumTableResponse_acceptsSuccessfulEmptyBoard() {
        validateForumTableResponse("{\"status\":0,\"total\":0,\"rows\":[]}")
    }

    @Test(expected = ForumBoardDataException::class)
    fun parseForumTopics_rejectsMalformedJsonInsteadOfReturningEmpty() {
        parseForumTopics("not-json", "1585405336")
    }

    @Test
    fun parseForumPost_readsBodyAndAuthor() {
        val topic = ForumTopic(
            boardId = "1585405223",
            id = "335631",
            title = "Fallback title",
            author = null,
            createdAt = null,
            replyCount = null,
            viewCount = null,
            lastReplyAt = null,
            url = "/forum/1585405223/335631.html"
        )
        val post = parseForumPost(
            Jsoup.parse(
                """
                <div class="single-post-meta"><a href="/my/profile?uid=7">Alice</a> 2026-08-30 12:34</div>
                <h2>Actual title</h2>
                <div class="forum-content"><p>Hello</p><p>World</p></div>
                """.trimIndent(),
                "https://www.esjzone.cc/forum/1585405223/335631.html"
            ),
            topic
        )

        assertEquals("Actual title", post.title)
        assertEquals("Alice", post.author)
        assertEquals("2026-08-30 12:34", post.createdAt)
        assertEquals("Hello\nWorld", post.contentText)
    }
}

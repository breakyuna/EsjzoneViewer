package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.NetworkHttpException
import com.breakyuna.esjzone.network.PageCache
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.features.CommentSubmissionNotVerifiedException
import com.breakyuna.esjzone.network.features.CommentSubmissionTimeoutException
import com.breakyuna.esjzone.network.features.ForumBoardDataException
import com.breakyuna.esjzone.network.features.ForumBoardResult
import com.breakyuna.esjzone.network.features.ForumReplyBusinessException
import com.breakyuna.esjzone.network.features.ForumReplyProtocolException
import com.breakyuna.esjzone.network.features.commentPostFailure
import com.breakyuna.esjzone.network.features.findCreatedComment
import com.breakyuna.esjzone.network.features.findForumNovelDetailUrl
import com.breakyuna.esjzone.network.features.getCachedCommentPageSnapshot
import com.breakyuna.esjzone.network.features.getCachedForumPost
import com.breakyuna.esjzone.network.features.getForumBoard
import com.breakyuna.esjzone.network.features.loadForumBoardTopicsPayload
import com.breakyuna.esjzone.network.features.localCommentSubmission
import com.breakyuna.esjzone.network.features.parseComments
import com.breakyuna.esjzone.network.features.parseCommentPageSnapshot
import com.breakyuna.esjzone.network.features.parseForumCategories
import com.breakyuna.esjzone.network.features.parseForumPost
import com.breakyuna.esjzone.network.features.parseForumReplyResponse
import com.breakyuna.esjzone.network.features.parseForumThreads
import com.breakyuna.esjzone.network.features.parseForumTopicRows
import com.breakyuna.esjzone.network.features.parseForumTopics
import com.breakyuna.esjzone.network.features.requireForumAuthToken
import com.breakyuna.esjzone.network.features.validateForumTableResponse
import com.breakyuna.esjzone.novellibrary.community.FORUM_GROUP_ESJ
import com.breakyuna.esjzone.novellibrary.community.FORUM_GROUP_TIANKONG
import com.breakyuna.esjzone.novellibrary.community.ForumCategory
import com.breakyuna.esjzone.novellibrary.community.ForumThread
import com.breakyuna.esjzone.novellibrary.community.ForumTopic
import com.breakyuna.esjzone.novellibrary.community.groupForumCategories
import com.breakyuna.esjzone.ui.page.CommentSubmitError
import com.breakyuna.esjzone.ui.page.allowsImmediateRetry
import java.io.IOException
import java.nio.file.Files
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityParserTest {

    @Test
    fun commentPageSnapshot_preparesForumRouteFromTheLoadedPage() {
        val chapterUrl = "https://www.esjzone.cc/forum/123/456.html"
        val chapter = parseCommentPageSnapshot(
            Jsoup.parse(
                """
                <form class="commentEditor"><input name="forum_id" value=""></form>
                <section class="comments-section">
                  <div class="comment" id="comment-7"><div class="comment-text">Earlier</div></div>
                </section>
                """.trimIndent(),
                chapterUrl
            ),
            chapterUrl
        )
        assertEquals("forum", chapter.forumPreparation?.data)
        assertEquals("456", chapter.forumPreparation?.forumId)
        assertEquals(listOf("7"), chapter.forumPreparation?.comments?.map { it.id })

        val detailUrl = "https://www.esjzone.cc/detail/123.html"
        val detail = parseCommentPageSnapshot(
            Jsoup.parse("<form class=\"commentEditor\"><input name=\"forum_id\" value=\"\"></form>", detailUrl),
            detailUrl
        )
        assertEquals("books", detail.forumPreparation?.data)
        assertEquals("0", detail.forumPreparation?.forumId)

        val guestbookUrl = "https://www.esjzone.cc/guestbook/"
        val guestbook = parseCommentPageSnapshot(
            Jsoup.parse("<form class=\"gbEditor\"></form>", guestbookUrl),
            guestbookUrl
        )
        assertNull(guestbook.forumPreparation)
    }

    @Test
    fun findCreatedComment_ignoresAnOlderCommentWithTheSameText() {
        val comments = parseComments(
            Jsoup.parse(
                """
                <section class="comments-section">
                  <div class="comment" id="comment-1"><div class="comment-text">Same text</div></div>
                  <div class="comment" id="comment-2"><div class="comment-text">Same text</div></div>
                </section>
                """.trimIndent()
            ),
            "9001"
        )

        assertNull(findCreatedComment(comments.take(1), setOf("1"), "Same text"))
        assertEquals("2", findCreatedComment(comments, setOf("1"), "Same text")?.id)
    }

    @Test
    fun parseForumReplyResponse_readsSuccessAndBusinessFailures() {
        val success = parseForumReplyResponse(
            """{"status":200,"msg":"經驗值 +104","reload":1,"anchor":"#comment-3261580","exp":104}"""
        )
        assertEquals(200, success.status)
        assertEquals("經驗值 +104", success.msg)
        assertEquals("#comment-3261580", success.anchor)
        assertEquals(104, success.exp)

        val dailyLimit = parseForumReplyResponse(
            """{"status":214,"msg":"每日留言次數已超過限制！","reload":0}"""
        )
        assertEquals(214, dailyLimit.status)
        assertEquals("每日留言次數已超過限制！", dailyLimit.msg)
    }

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
    fun findForumNovelDetailUrl_readsOnlyNovelHeaderDetailLink() {
        val novelBoard = Jsoup.parse(
            "<div class=\"forum-detail\"><a href=\"/detail/1788015863.html\">身份保障妓院</a></div>"
        )
        val discussionBoard = Jsoup.parse(
            "<a href=\"/forum/1585405336/545856.html\">主题</a>"
        )
        val boardWithTopicLink = Jsoup.parse(
            """
            <a href="/detail/999.html">来自主题正文的作品链接</a>
            <div class="forum-detail">
                <h2><a href="/detail/1788015863.html">作品标题</a></h2>
            </div>
            """.trimIndent()
        )

        assertEquals("/detail/1788015863.html", findForumNovelDetailUrl(novelBoard))
        assertNull(findForumNovelDetailUrl(discussionBoard))
        assertEquals("/detail/1788015863.html", findForumNovelDetailUrl(boardWithTopicLink))
    }

    @Test
    fun validateForumTableResponseAndAuthToken_enforceGuards() {
        assertThrows(ForumBoardDataException::class.java) {
            validateForumTableResponse("{\"status\":301,\"total\":0,\"rows\":[]}")
        }
        validateForumTableResponse("{\"status\":0,\"total\":0,\"rows\":[]}")
        assertThrows(ForumBoardDataException::class.java) {
            requireForumAuthToken("  ")
        }
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
                <div class="forum-content">
                  <p><a href="409387.html"><strong>Hello</strong></a></p>
                  <p><a href="#myQA">World</a> <a href="javascript:alert(1)">Invalid</a></p>
                </div>
                """.trimIndent(),
                "https://www.esjzone.cc/forum/1585405223/335631.html"
            ),
            topic
        )

        assertEquals("Actual title", post.title)
        assertEquals("Alice", post.author)
        assertEquals("2026-08-30 12:34", post.createdAt)
        assertEquals("Hello\nWorld Invalid", post.contentText)
        val markup = Jsoup.parseBodyFragment(post.contentHtml)
        assertEquals(
            listOf(
                "https://www.esjzone.cc/forum/1585405223/409387.html",
                "https://www.esjzone.cc/forum/1585405223/335631.html#myQA"
            ),
            markup.select("a[href]").eachAttr("href")
        )
        assertEquals("Hello", markup.selectFirst("a strong")?.text())
    }

    @Test
    fun parseAndGroupForumCategories_splitsIntoEsjAndTiankongFiveToThree() {
        val document = Jsoup.parse(
            """
            <section id="forum-boards">
              <table class="forum-category">
                <thead><tr><th>ESJ-曉朔國度</th></tr></thead>
                <tbody>
                  <tr>
                    <td><a href="/forum/1584680829/">板塊1</a></td>
                    <td><a href="/forum/1584678947/">板塊2</a></td>
                    <td><a href="/forum/1584622251/">板塊3</a></td>
                    <td><a href="/forum/1584622325/">板塊4</a></td>
                    <td><a href="/forum/1584679807/">板塊5</a></td>
                  </tr>
                </tbody>
              </table>
              <table class="forum-category">
                <thead><tr><th>天空大公國</th></tr></thead>
                <tbody>
                  <tr>
                    <td><a href="/forum/1584622376/">R18 專區</a></td>
                    <td><a href="/forum/1584622613/">天空討論</a></td>
                    <td><a href="/forum/1584622628/">公國專區</a></td>
                  </tr>
                </tbody>
              </table>
            </section>
            """.trimIndent()
        )

        val categories = parseForumCategories(document)
        assertEquals(8, categories.size)
        val esjCategories = categories.filter { it.groupName == FORUM_GROUP_ESJ }
        val tiankongCategories = categories.filter { it.groupName == FORUM_GROUP_TIANKONG }
        assertEquals(
            listOf("1584680829", "1584678947", "1584622251", "1584622325", "1584679807"),
            esjCategories.map { it.id }
        )
        assertEquals(
            listOf("1584622376", "1584622613", "1584622628"),
            tiankongCategories.map { it.id }
        )

        val knownGrouped = groupForumCategories(categories.map { it.copy(groupName = null) })
        assertEquals(listOf(FORUM_GROUP_ESJ, FORUM_GROUP_TIANKONG), knownGrouped.keys.toList())
        assertEquals(5, knownGrouped[FORUM_GROUP_ESJ]?.size)
        assertEquals(3, knownGrouped[FORUM_GROUP_TIANKONG]?.size)

        val fallback = (1..8).map { i ->
            ForumCategory("unknown-$i", null, "Board $i", null, null, "/forum/unknown-$i/")
        }
        val fallbackGrouped = groupForumCategories(fallback)
        assertEquals(listOf(FORUM_GROUP_ESJ, FORUM_GROUP_TIANKONG), fallbackGrouped.keys.toList())
        assertEquals(
            listOf("unknown-1", "unknown-2", "unknown-3", "unknown-4", "unknown-5"),
            fallbackGrouped[FORUM_GROUP_ESJ]?.map { it.id }
        )
        assertEquals(
            listOf("unknown-6", "unknown-7", "unknown-8"),
            fallbackGrouped[FORUM_GROUP_TIANKONG]?.map { it.id }
        )
    }

    @Test
    fun commentsWithoutProfileLinks_preserveNamesAndReplyTokens() {
        val document = Jsoup.parse(
            """
            <div id="comments">
              <div class="comment" id="comment-87246">
                <div class="comment-header"><span class="comment-title"><a href="/my/profile?uid=3115">Jjnsn</a></span></div>
                <p class="comment-text">感谢翻译</p>
              </div>
              <div class="comment" id="comment-89742">
                <div class="comment-header"><span class="comment-title">豆腐</span></div>
                <p class="comment-text">想問問文庫和web版有不同嗎？</p>
                <a class="forum_reply" data-comment="89742">回覆</a>
              </div>
              <div class="comment" id="comment-95446">
                <div class="comment-header"><span class="comment-title">++片翼天使++</span></div>
                <p class="comment-text">有</p>
              </div>
              <div class="comment" id="comment-95863">
                <div class="comment-header"><span class="comment-title">閒閒</span></div>
                <p class="comment-text">因为男主现时近战绝不NB</p>
              </div>
            </div>
            """.trimIndent(),
            "https://example.test/"
        )

        val comments = parseComments(document, "fixture-post")

        assertEquals(listOf("Jjnsn", "豆腐", "++片翼天使++", "閒閒"), comments.map { it.authorName })
        assertEquals("3115", comments.first().authorId)
        assertEquals("https://example.test/my/profile?uid=3115", comments.first().authorUrl)
        assertTrue(comments.drop(1).all { it.authorId == null && it.authorUrl == null })
        assertEquals("89742", comments[1].replyToken)
    }

    @Test
    fun confirmedCommentIsAppendedWithoutWaitingForAServerCommentId() {
        val previous = localCommentSubmission(
            parentId = "456",
            content = "Earlier comment",
            replyToken = null,
            previousComments = emptyList(),
            authorName = "Earlier reader",
            authorAvatarUrl = null,
            createdCommentId = "123"
        ).createdComment
        val comments = List(15) { previous.copy(id = "${160275 + it}", floor = "#${160275 + it}") }

        val result = localCommentSubmission(
            parentId = "456",
            content = "New reply\nSecond line",
            replyToken = "123-42",
            previousComments = comments,
            authorName = "Current reader",
            authorAvatarUrl = "/avatar.png",
            requestSentAt = "2026-10-03 14:05:06"
        )

        assertEquals(comments, result.comments.dropLast(1))
        assertEquals(result.createdComment, result.comments.last())
        assertTrue(result.createdComment.id.isNotBlank())
        assertFalse(result.createdComment.id in comments.map { it.id })
        assertEquals("456", result.createdComment.parentPostId)
        assertEquals("New reply\nSecond line", result.createdComment.contentText)
        assertEquals("123-42", result.createdComment.replyToken)
        assertEquals("Current reader", result.createdComment.authorName)
        assertEquals("/avatar.png", result.createdComment.authorAvatarUrl)
        assertEquals("2026-10-03 14:05:06", result.createdComment.createdAt)
        assertEquals("#160290", result.createdComment.floor)
        assertEquals("#160289", result.comments[result.comments.lastIndex - 1].floor)
        assertEquals(2, result.createdComment.pageGroup)
    }

    @Test
    fun acceptedResponseKeepsItsServerCommentId() {
        val result = localCommentSubmission(
            parentId = "456",
            content = "New comment",
            replyToken = null,
            previousComments = emptyList(),
            authorName = null,
            authorAvatarUrl = null,
            createdCommentId = "789",
            requestSentAt = "2026-10-03 14:05:06"
        )
        assertEquals("789", result.createdComment.id)
        assertNull(result.createdComment.floor)
        assertEquals("2026-10-03 14:05:06", result.createdComment.createdAt)
        assertEquals(listOf(result.createdComment), result.comments)
    }

    @Test
    fun initialCommentsReadIsCacheOnlyAndAcceptsStaleCommentsForDisplay() {
        val directory = Files.createTempDirectory("comment-cache-").toFile()
        PageCache.initializeDirectory(directory)
        val auth = Authorization()
        val url = "https://www.esjzone.cc/forum/9001/1.html"
        val key = EsjzoneClient.pageCacheKey(auth, url)
        try {
            assertNull(EsjzoneClient.getCachedCommentPageSnapshot(auth, url))
            val html = requireNotNull(javaClass.getResource("/esj/comments-pages.html"))
                .readText()
            assertTrue(PageCache.write(key, html,
                System.currentTimeMillis() - PageCacheTtl.COMMUNITY - 1_000L))
            val snapshot = requireNotNull(EsjzoneClient.getCachedCommentPageSnapshot(auth, url))
            assertEquals(16, snapshot.comments.size)
            assertEquals("8101", snapshot.comments.first().id)
            assertEquals("Comment 16", snapshot.comments.last().contentText)

            assertTrue(PageCache.write(key, "<html><title>Just a moment</title></html>"))
            assertNull(EsjzoneClient.getCachedCommentPageSnapshot(auth, url))
            PageCache.clear()
            assertNull(EsjzoneClient.getCachedCommentPageSnapshot(auth, url))
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun getCachedForumPost_readsStaleTrustedCacheWithoutNetworkAndRejectsUntrustedHtml() {
        val directory = Files.createTempDirectory("forum-post-cache-").toFile()
        PageCache.initializeDirectory(directory)
        val auth = Authorization()
        val topic = ForumTopic(
            boardId = "1585405223",
            id = "335631",
            title = "Fallback title",
            author = null,
            createdAt = null,
            replyCount = null,
            viewCount = null,
            lastReplyAt = null,
            url = "/forum/1585405223/335631.html#reply-1"
        )
        val targetUrl = EsjzoneUrls.resolve(topic.url).substringBefore('#')
        val key = EsjzoneClient.pageCacheKey(auth, targetUrl)
        try {
            assertNull(EsjzoneClient.getCachedForumPost(auth, topic))

            val postHtml = """
                <html><head><title>Esjzone</title></head><body>
                  <a href="/forum/1585405223/">Board</a>
                  <div class="single-post-meta"><a href="/my/profile?uid=7">Alice</a> 2026-08-30 12:34</div>
                  <h2>Cached title</h2>
                  <div class="forum-content"><p>Cached body</p></div>
                  <section class="comments-section"></section>
                </body></html>
            """.trimIndent()
            assertTrue(
                PageCache.write(
                    key,
                    postHtml,
                    System.currentTimeMillis() - PageCacheTtl.COMMUNITY - 1_000L
                )
            )
            val cached = requireNotNull(EsjzoneClient.getCachedForumPost(auth, topic))
            assertEquals("Cached title", cached.title)
            assertEquals("Alice", cached.author)
            assertEquals("Cached body", cached.contentText)

            assertTrue(PageCache.write(key, "<html><title>Just a moment</title></html>"))
            assertNull(EsjzoneClient.getCachedForumPost(auth, topic))

            PageCache.clear()
            assertNull(EsjzoneClient.getCachedForumPost(auth, topic))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun forumBoardCaching_reusesFreshCacheAndFallsBackToStaleOnError() {
        val directory = Files.createTempDirectory("forum-board-cache-").toFile()
        PageCache.initializeDirectory(directory)
        val auth = Authorization()
        val thread = ForumThread(
            categoryId = "1584622613",
            id = "1585405336",
            title = "討論版",
            topicCount = 1,
            replyCount = 2,
            lastPostDate = null,
            url = "/forum/1584622613/1585405336/"
        )
        val targetUrl = EsjzoneUrls.resolve(thread.url)
        val endpoint = EsjzoneUrls.resolve("/inc/forum_list_data.php?totalRows=1") +
            "&limit=20&offset=0&sort=last_reply&order=desc"
        val jsonKey = "forum-board-json|${EsjzoneClient.pageCacheKey(auth, targetUrl)}|${EsjzoneUrls.canonicalCacheUrl(endpoint)}"
        val freshJson = """
            {"total":1,"rows":[
              {"subject":"<a href=\"/forum/1585405336/150985.html\">Fresh Topic</a>",
               "cdate":"Alice<div class=\"forum-desc\">2026-08-21</div>",
               "vtimes":"3<div class=\"forum-desc\">15</div>",
               "last_reply":"2026-08-26 03:52"}
            ]}
        """.trimIndent()
        val staleJson = """
            {"total":1,"rows":[
              {"subject":"<a href=\"/forum/1585405336/150984.html\">Stale Topic</a>",
               "cdate":"Bob<div class=\"forum-desc\">2026-08-20</div>",
               "vtimes":"1<div class=\"forum-desc\">5</div>",
               "last_reply":"2026-08-25 01:00"}
            ]}
        """.trimIndent()
        try {
            val boardHtml = """
                <html><head><title>Esjzone</title></head><body>
                  <a href="/forum/1584622613/1585405336/">討論版</a>
                  <table id="dataTable" data-url="/inc/forum_list_data.php?totalRows=1">
                    <tbody><tr class="no-records-found"><td colspan="4">none</td></tr></tbody>
                  </table>
                </body></html>
            """.trimIndent()
            assertTrue(PageCache.write(EsjzoneClient.pageCacheKey(auth, targetUrl), boardHtml))
            assertTrue(PageCache.write(jsonKey, freshJson))

            val cachedBoard = EsjzoneClient.getForumBoard(auth, thread, forceRefresh = false)
            assertTrue(cachedBoard is ForumBoardResult.Topics)
            val topics = (cachedBoard as ForumBoardResult.Topics).items
            assertEquals(listOf("Fresh Topic"), topics.map { it.title })

            // Stale JSON fallback when fetch fails and allowStaleOnError = true
            assertTrue(
                PageCache.write(
                    jsonKey,
                    staleJson,
                    System.currentTimeMillis() - PageCacheTtl.COMMUNITY - 1_000L
                )
            )
            val stalePayload = EsjzoneClient.loadForumBoardTopicsPayload(
                authorization = auth,
                targetUrl = targetUrl,
                endpoint = endpoint,
                fallbackBoardId = thread.id,
                declaredTotal = 1,
                forceRefresh = false,
                allowStaleOnError = true,
                fetchFreshBody = { throw IOException("offline") }
            )
            assertEquals(listOf("Stale Topic"), stalePayload.items.map { it.title })

            // allowStaleOnError = false propagates the error instead of serving stale cache
            assertThrows(IOException::class.java) {
                EsjzoneClient.loadForumBoardTopicsPayload(
                    authorization = auth,
                    targetUrl = targetUrl,
                    endpoint = endpoint,
                    fallbackBoardId = thread.id,
                    declaredTotal = 1,
                    forceRefresh = true,
                    allowStaleOnError = false,
                    fetchFreshBody = { throw IOException("offline") }
                )
            }

            fun refresh(fetch: () -> String) = EsjzoneClient.loadForumBoardTopicsPayload(
                auth, targetUrl, endpoint, thread.id, declaredTotal = 1, forceRefresh = true,
                allowStaleOnError = false, fetchFreshBody = fetch
            )
            refresh {
                refresh { freshJson }
                staleJson
            }
            assertEquals(freshJson, PageCache.read(jsonKey, PageCacheTtl.COMMUNITY))

            refresh {
                PageCache.clear()
                freshJson
            }
            assertNull(PageCache.readStale(jsonKey))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun commentPostFailuresOnlyAllowImmediateRetryForExplicitRejections() {
        val url = "https://www.esjzone.cc/forum/1/2.html"
        val invalidStatus = assertThrows(ForumReplyProtocolException::class.java) {
            parseForumReplyResponse("""{"status":"invalid"}""")
        }
        for (error in listOf(invalidStatus, NetworkHttpException(url, 502))) {
            val unverified = commentPostFailure(error, emptyList(), setOf("previous"))
            assertTrue(unverified is CommentSubmissionNotVerifiedException)
            assertEquals(setOf("previous"), (unverified as CommentSubmissionNotVerifiedException).previousIds)
        }
        assertTrue(commentPostFailure(IOException(), emptyList(), emptySet()) is CommentSubmissionTimeoutException)
        val rejected = NetworkHttpException(url, 403)
        assertEquals(rejected, commentPostFailure(rejected, emptyList(), emptySet()))
        val business = ForumReplyBusinessException("Rejected")
        assertEquals(business, commentPostFailure(business, emptyList(), emptySet()))
    }

    @Test
    fun parseForumTopics_singleCellParseHandlesBlankAuthorAndDescInLastReply() {
        val topics = parseForumTopics(
            """
            {"total":2,"rows":[
              {"subject":"<a href=\"/forum/1589699634/150985.html\">Topic One</a>",
               "cdate":"<div class=\"forum-desc\">2026-01-02</div>",
               "vtimes":"<div class=\"forum-desc\">42</div>",
               "last_reply":"2026-01-03<div class=\"forum-desc\">ignored</div>"},
              {"subject":"<a href=\"/forum/1589699634/150986.html\">Topic Two</a>"}
            ]}
            """.trimIndent(),
            "1589699634"
        )

        assertEquals(2, topics.size)
        assertEquals("", topics[0].author)
        assertEquals("2026-01-02", topics[0].createdAt)
        assertNull(topics[0].replyCount)
        assertEquals(42, topics[0].viewCount)
        assertEquals("2026-01-03", topics[0].lastReplyAt)

        assertNull(topics[1].author)
        assertNull(topics[1].createdAt)
        assertNull(topics[1].replyCount)
        assertNull(topics[1].viewCount)
        assertNull(topics[1].lastReplyAt)
    }

    @Test
    fun commentSubmitError_locksRetryOnlyForUncertainSubmissionStates() {
        val noError: CommentSubmitError? = null
        assertTrue(noError.allowsImmediateRetry(canRetryAfterRefresh = false))
        assertFalse(CommentSubmitError.EMPTY.requiresRefreshBeforeRetry)
        assertTrue(CommentSubmitError.EMPTY.allowsImmediateRetry(canRetryAfterRefresh = false))
        assertFalse(CommentSubmitError.FAILED.requiresRefreshBeforeRetry)
        assertTrue(CommentSubmitError.FAILED.allowsImmediateRetry(canRetryAfterRefresh = false))
        assertFalse(CommentSubmitError.Server("每日留言次數已超過限制！").requiresRefreshBeforeRetry)
        assertTrue(CommentSubmitError.Server("每日留言次數已超過限制！").allowsImmediateRetry(canRetryAfterRefresh = false))

        assertTrue(CommentSubmitError.TIMEOUT.requiresRefreshBeforeRetry)
        assertFalse(CommentSubmitError.TIMEOUT.allowsImmediateRetry(canRetryAfterRefresh = false))
        assertTrue(CommentSubmitError.TIMEOUT.allowsImmediateRetry(canRetryAfterRefresh = true))

        assertTrue(CommentSubmitError.NOT_VERIFIED.requiresRefreshBeforeRetry)
        assertFalse(CommentSubmitError.NOT_VERIFIED.allowsImmediateRetry(canRetryAfterRefresh = false))
        assertTrue(CommentSubmitError.NOT_VERIFIED.allowsImmediateRetry(canRetryAfterRefresh = true))
    }
}

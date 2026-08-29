package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.features.parseComments
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class CommunityParserTest {

    @Test
    fun parseComments_assignsFixedPageAndPreservesReplyCapability() {
        val document = Jsoup.parse(
            """
            <div class="comments-section comments-page-2">
              <div class="comment" id="comment-42">
                <div class="comment-header">
                  <img class="avatar" src="/assets/avatar-placeholder.gif" data-src="/assets/alice.png">
                  <a href="/my/profile.html?uid=7">Alice</a>
                  <span class="comment-floor">#16</span>
                  <span class="comment-meta">2026-08-28</span>
                </div>
                <div class="comment-text"><p>Hello <strong>ESJ</strong></p></div>
                <button class="forum_reply" data-comment="42-7">Reply</button>
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
}

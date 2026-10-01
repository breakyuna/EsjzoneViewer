package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.features.parseComments
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CSS comment-section and fixed fifteen-item page contract. */
class CommentCssAdapterTest {

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
    fun commentsFixture_preservesFieldsAndAssignsStablePageGroups() {
        val document = Jsoup.parse(
            requireNotNull(javaClass.getResource("/esj/comments-pages.html"))
                .openStream().bufferedReader().use { it.readText() },
            "https://example.test/"
        )

        val comments = parseComments(document, "fixture-post")

        assertEquals(16, comments.size)
        assertEquals("8101", comments.first().id)
        assertEquals("1", comments.first().authorId)
        assertEquals("User 1", comments.first().authorName)
        assertEquals("2026-09-01", comments.first().createdAt)
        assertEquals("Comment 1", comments.first().contentText)
        assertEquals(1, comments[14].pageGroup)
        assertEquals(2, comments[15].pageGroup)
        assertEquals("8102", comments[1].id)
        assertTrue(comments.all { it.parentPostId == "fixture-post" })
    }
}

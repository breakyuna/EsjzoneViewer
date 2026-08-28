package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.features.parseComments
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class CommunityParserTest {

    @Test
    fun parseComments_preservesLocalPageGroupsAndReplyCapability() {
        val document = Jsoup.parse(
            """
            <div class="comments-section comments-page-2">
              <div class="comment" id="comment-42">
                <div class="comment-header">
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
        assertEquals("#16", comments.single().floor)
        assertEquals("Hello ESJ", comments.single().contentText)
        assertEquals(2, comments.single().pageGroup)
        assertEquals("42-7", comments.single().replyToken)
    }
}

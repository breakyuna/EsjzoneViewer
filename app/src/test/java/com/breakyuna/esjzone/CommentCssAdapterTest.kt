package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.features.parseComments
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CSS comment-section and fixed fifteen-item page contract. */
class CommentCssAdapterTest {

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

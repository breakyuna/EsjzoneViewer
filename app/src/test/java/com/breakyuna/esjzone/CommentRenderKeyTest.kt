package com.breakyuna.esjzone

import com.breakyuna.esjzone.novellibrary.novel.Comment
import com.breakyuna.esjzone.ui.page.commentRenderKey
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM coverage for defensive keys used by the shared comment renderer. */
class CommentRenderKeyTest {
    @Test
    fun blankOrRepeatedIdsStillProduceDistinctNonBlankKeys() {
        val first = commentRenderKey(comment(id = "", parent = ""), index = 0)
        val second = commentRenderKey(comment(id = "", parent = ""), index = 1)
        val repeatedFirst = commentRenderKey(comment(id = "comment-7", parent = "guestbook"), index = 0)
        val repeatedSecond = commentRenderKey(comment(id = "comment-7", parent = "guestbook"), index = 1)

        assertTrue(first.isNotBlank())
        assertTrue(second.isNotBlank())
        assertNotEquals(first, second)
        assertNotEquals(repeatedFirst, repeatedSecond)
    }

    @Test
    fun sameCommentAtSamePositionHasStableKey() {
        val value = commentRenderKey(comment(id = "comment-7", parent = "guestbook"), index = 2)

        assertTrue(value.contains("guestbook"))
        assertTrue(value.contains("comment-7"))
        assertTrue(value.endsWith(":2"))
    }

    private fun comment(id: String, parent: String) = Comment(
        id = id,
        parentPostId = parent,
        authorId = null,
        authorName = null,
        authorUrl = null,
        floor = null,
        createdAt = null,
        contentHtml = "",
        contentText = "",
        pageGroup = 1,
        replyToken = null
    )
}

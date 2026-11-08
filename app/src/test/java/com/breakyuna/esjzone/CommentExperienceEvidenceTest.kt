package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.page.experienceSupportsSubmission
import com.breakyuna.esjzone.network.features.localCommentSubmission
import com.breakyuna.esjzone.network.features.parseComments
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentExperienceEvidenceTest {
    @Test
    fun serverBadgeIsPreservedIndependentlyOfDomIdAndLoadedCount() {
        val comments = parseComments(Jsoup.parse("""
            <section class="comments-section">
              <div class="comment" id="comment-900001">
                <span class="comment-floor">#160288</span><div class="comment-text">Earlier</div>
              </div>
              <div class="comment" id="comment-900002">
                <span class="comment-floor">#160289</span><div class="comment-text">Later</div>
              </div>
            </section>
        """.trimIndent()), "456")
        assertEquals(listOf("900001", "900002"), comments.map { it.id })
        assertEquals(listOf("#160288", "#160289"), comments.map { it.floor })
    }

    @Test
    fun onlyAnIncreaseFromTwoAvailableSnapshotsConfirmsAnUncertainSubmission() {
        assertTrue(experienceSupportsSubmission(7264, 7368))
        assertFalse(experienceSupportsSubmission(null, 7368))
        assertFalse(experienceSupportsSubmission(7264, null))
        assertFalse(experienceSupportsSubmission(7264, 7264))
        assertFalse(experienceSupportsSubmission(7264, 7260))
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
        assertNull(result.createdComment.floor)
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
}

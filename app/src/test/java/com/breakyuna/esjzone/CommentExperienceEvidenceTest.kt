package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.page.experienceSupportsSubmission
import com.breakyuna.esjzone.network.features.localCommentSubmission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentExperienceEvidenceTest {
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
        val comments = List(15) { previous.copy(id = "${123 + it}") }

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
        assertEquals("#16", result.createdComment.floor)
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
        assertEquals("2026-10-03 14:05:06", result.createdComment.createdAt)
        assertEquals(listOf(result.createdComment), result.comments)
    }
}

package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.comments.findObservedComment
import com.breakyuna.esjzone.network.comments.PendingCommentWrite
import com.breakyuna.esjzone.network.comments.blockingCommentWrite
import com.breakyuna.esjzone.network.comments.commentObservationDecision
import com.breakyuna.esjzone.network.comments.retainLoadedComments
import com.breakyuna.esjzone.network.features.parseComments
import com.google.gson.Gson
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class CommentBrowserEvidenceTest {
    private val comments = parseComments(Jsoup.parse("""
        <div class="comment" id="comment-1"><div class="comment-header"><a href="/my/profile?uid=1">Alice</a></div><div class="comment-text">hello world</div></div>
        <div class="comment" id="comment-2"><div class="comment-header"><a href="/my/profile?uid=2">Bob</a></div><div class="comment-text">hello world</div></div>
        <div class="comment" id="comment-3"><div class="comment-header"><a href="/my/profile?uid=1">Alice</a></div><div class="comment-text">hello world</div></div>
    """), "fixture")

    @Test fun knownServerIdNeverFallsBackToIdenticalText() {
        assertNull(findObservedComment(comments, setOf("1"), "hello world", "4", "Alice"))
        assertEquals("3", findObservedComment(comments, setOf("1"), "different", "3", null)?.id)
        assertNull(findObservedComment(comments, setOf("1"), "hello world", "1", "Alice"))
    }

    @Test fun unknownWriteRequiresNewIdExactContentAndCurrentAuthor() {
        assertEquals("3", findObservedComment(comments, setOf("1"), "hello\nworld", null, "Alice")?.id)
        assertNull(findObservedComment(comments, setOf("1", "3"), "hello world", null, "Alice"))
        assertNull(findObservedComment(comments, emptySet(), "world", null, "Alice"))
        assertNull(findObservedComment(comments, emptySet(), "hello world", null, null))
    }

    @Test fun recoveryRecordPreservesAcceptedIdentityAndDraft() {
        val operation = PendingCommentWrite(content = "line 1\nline 2", replyToken = "42", previousIds = setOf("1"),
            authorName = "Alice", accepted = true, acceptedId = "3")
        val gson = Gson()
        assertEquals(operation, gson.fromJson(gson.toJson(operation), PendingCommentWrite::class.java))
    }

    @Test fun aShortBrowserSnapshotDoesNotRemovePreviouslyLoadedComments() {
        val newComment = comments.last().copy(id = "4", contentText = "new", contentHtml = "new")
        val result = retainLoadedComments(comments, comments.take(1), newComment)
        assertEquals(listOf("1", "2", "3", "4"), result.map { it.id })
        assertEquals(comments, retainLoadedComments(comments, comments.take(1), null))
    }

    @Test fun separateDraftsKeepIndependentRecoveryRecords() {
        val accepted = PendingCommentWrite(content = "first", replyToken = null,
            previousIds = setOf("1"), accepted = true)
        val different = PendingCommentWrite(content = "second", replyToken = null,
            previousIds = setOf("1"))
        val duplicate = different.copy(operationId = "different-operation")
        assertNull(blockingCommentWrite(listOf(accepted), different, false))
        assertEquals(accepted, blockingCommentWrite(listOf(accepted), accepted.copy(), true))
        assertEquals(different, blockingCommentWrite(listOf(different), duplicate, false))
        assertNull(blockingCommentWrite(listOf(different), duplicate, true))
    }

    @Test fun unknownResultsNotifyEarlyAndAcceptanceCanKeepObserving() {
        assertTrue(commentObservationDecision(false, true, 1_000L, 0L, null, 1_200L).notifyUncertain)
        assertFalse(commentObservationDecision(false, false, 1_000L, 0L, null, 12_999L).notifyUncertain)
        assertTrue(commentObservationDecision(false, false, 1_000L, 0L, null, 13_000L).notifyUncertain)
        assertFalse(commentObservationDecision(false, false, 1_000L, 0L, 13_000L, 22_999L).stop)
        assertTrue(commentObservationDecision(false, false, 1_000L, 0L, 13_000L, 23_000L).stop)
        assertFalse(commentObservationDecision(true, true, 1_000L, 0L, 13_000L, 23_000L).stop)
    }
}

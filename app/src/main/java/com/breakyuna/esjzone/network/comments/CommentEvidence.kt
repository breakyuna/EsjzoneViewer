package com.breakyuna.esjzone.network.comments

import com.breakyuna.esjzone.novellibrary.novel.Comment

/** A server-provided ID takes precedence over every content heuristic. */
internal fun findObservedComment(
    comments: List<Comment>,
    previousIds: Set<String>,
    content: String,
    acceptedId: String?,
    authorName: String?
): Comment? {
    if (acceptedId != null) return comments.find { it.id == acceptedId && it.id !in previousIds }
    if (authorName.isNullOrBlank()) return null
    fun normalized(text: String) = text.trim().replace(Regex("\\s+"), " ")
    val expected = normalized(content)
    return comments.find {
        it.id !in previousIds && it.authorName == authorName && normalized(it.contentText) == expected
    }
}

/** A browser snapshot can contain fewer comments than the native page already loaded. */
internal fun retainLoadedComments(
    loaded: List<Comment>?,
    browser: List<Comment>,
    created: Comment?
): List<Comment> {
    val visible = loaded ?: browser
    return if (created != null && visible.none { it.id == created.id }) visible + created else visible
}

internal data class CommentObservationDecision(val notifyUncertain: Boolean, val stop: Boolean)

internal fun commentObservationDecision(
    accepted: Boolean,
    networkUnknown: Boolean,
    dispatchedAt: Long?,
    observingSince: Long,
    uncertainAt: Long?,
    now: Long
): CommentObservationDecision = when {
    accepted -> CommentObservationDecision(false, false)
    uncertainAt != null -> CommentObservationDecision(false, now - uncertainAt >= 10_000L)
    else -> CommentObservationDecision(
        networkUnknown || now - (dispatchedAt ?: observingSince) >= 12_000L,
        false
    )
}

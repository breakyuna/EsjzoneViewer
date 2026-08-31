package com.breakyuna.esjzone.novellibrary.novel

import java.io.Serializable

/** The app-facing comment page size is deliberately independent of the site's DOM groups. */
const val COMMENT_PAGE_SIZE = 15

data class Comment(
    val id: String,
    val parentPostId: String,
    val authorId: String?,
    val authorName: String?,
    val authorUrl: String?,
    val floor: String?,
    val createdAt: String?,
    val contentHtml: String,
    val contentText: String,
    /** Text quoted by a reply, rendered separately from the reply body. */
    val quotedContentText: String? = null,
    val pageGroup: Int,
    val replyToken: String?,
    val authorAvatarUrl: String? = null
) : Serializable

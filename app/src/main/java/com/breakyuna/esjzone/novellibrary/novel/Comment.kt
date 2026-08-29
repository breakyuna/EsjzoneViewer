package com.breakyuna.esjzone.novellibrary.novel

import java.io.Serializable

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
    val pageGroup: Int,
    val replyToken: String?,
    val authorAvatarUrl: String? = null
) : Serializable

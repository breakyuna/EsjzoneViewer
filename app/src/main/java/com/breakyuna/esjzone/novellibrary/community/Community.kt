package com.breakyuna.esjzone.novellibrary.community

import java.io.Serializable

data class ForumCategory(
    val id: String,
    val groupName: String?,
    val name: String,
    val description: String?,
    val postCount: Int?,
    val url: String
) : Serializable

data class ForumThread(
    val categoryId: String,
    val id: String,
    val title: String,
    val topicCount: Int?,
    val replyCount: Int?,
    val lastPostDate: String?,
    val url: String
) : Serializable

data class ForumTopic(
    val boardId: String,
    val id: String,
    val title: String,
    val author: String?,
    val createdAt: String?,
    val replyCount: Int?,
    val viewCount: Int?,
    val lastReplyAt: String?,
    val url: String
) : Serializable

data class ForumPost(
    val boardId: String,
    val id: String,
    val title: String,
    val author: String?,
    val createdAt: String?,
    val contentHtml: String,
    val contentText: String,
    val comments: List<com.breakyuna.esjzone.novellibrary.novel.Comment>,
    val url: String
) : Serializable

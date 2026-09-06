package com.breakyuna.esjzone.novellibrary.novel

import java.io.Serializable

interface Novel : Serializable {
    val name: String
    val url: String
}

interface CoveredNovel : Novel {
    val coverUrl: String
    val views: Int
    val likes: Int
    val isAdult: Boolean

    /**
     * Fields observed on server-rendered home/list cards.  They are nullable
     * because the home card intentionally does not expose the list metadata.
     * DetailedNovel supplies its existing author/words values through the
     * same contract without requiring a second detail request.
     */
    val latestTitle: String?
        get() = null
    val latestUrl: String?
        get() = null
    val author: String?
        get() = null
    val authorUrl: String?
        get() = null
    val words: Int?
        get() = null
    val articleCount: Int?
        get() = null
    val discussionCount: Int?
        get() = null
}

data class HistoryNovel(
    override val name: String,
    override val url: String,
    val vid: String,
    val chapter: Chapter
) : Novel

data class FavoriteNovel(
    override val name: String,
    override val url: String,
) : Novel

data class CategoryNovel(
    override val name: String,
    override val url: String,
    val forumUrl: String
) : Novel

data class CoveredNovelImpl(
    override val coverUrl: String,
    override val name: String,
    override val url: String,
    override val views: Int,
    override val likes: Int,
    override val isAdult: Boolean,
    override val latestTitle: String? = null,
    override val latestUrl: String? = null,
    override val author: String? = null,
    override val authorUrl: String? = null,
    override val words: Int? = null,
    override val articleCount: Int? = null,
    override val discussionCount: Int? = null
) : CoveredNovel {

    override fun equals(other: Any?): Boolean {
        if (other == null)
            return false
        if (this === other)
            return true
        if (other !is Novel)
            return false
        return other.url.contentEquals(this.url)
    }

    override fun hashCode(): Int {
        return url.hashCode()
    }

}

private val FORUM_URL_REGEX = "/forum/[0-9]+/([0-9]+)/".toRegex()

data class DetailedNovel(
    override val name: String,
    override val url: String,
    override val coverUrl: String,
    override val views: Int,
    override val likes: Int,
    override val words: Int,
    val type: String,
    override val author: String,
    val forumUrl: String,
    val tags: List<String>,
    override val isAdult: Boolean,
    val isFavorite: Boolean,
    val description: NovelDescription,
    val chapterList: NovelChapterList,
    val comments: List<Comment> = emptyList(),
    val sourceUrl: String? = null,
    val updatedAt: String? = null
) : CoveredNovel {

    fun id(): String {
        return FORUM_URL_REGEX.find(this.forumUrl)?.groupValues?.getOrNull(1).orEmpty()
    }

}

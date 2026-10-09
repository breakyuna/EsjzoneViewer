package com.breakyuna.esjzone.novellibrary.novel

import com.breakyuna.esjzone.network.wenku8.Wenku8Urls
import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Immutable
interface Novel : Serializable {
    val name: String
    val url: String
}

@Immutable
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
    val latestTitle: String? = null,
    val latestUrl: String? = null,
    val remoteLastViewedTitle: String? = null,
    val remoteUpdatedAt: String? = null,
) : Novel

data class CategoryNovel(
    override val name: String,
    override val url: String,
    val forumUrl: String
) : Novel

data class CoveredNovelImpl(
    @SerializedName("coverUrl")
    override val coverUrl: String = "",
    @SerializedName("name")
    override val name: String = "",
    @SerializedName("url")
    override val url: String = "",
    @SerializedName("views")
    override val views: Int = 0,
    @SerializedName("likes")
    override val likes: Int = 0,
    @SerializedName("isAdult")
    override val isAdult: Boolean = false,
    @SerializedName("latestTitle")
    override val latestTitle: String? = null,
    @SerializedName("latestUrl")
    override val latestUrl: String? = null,
    @SerializedName("author")
    override val author: String? = null,
    @SerializedName("authorUrl")
    override val authorUrl: String? = null,
    @SerializedName("words")
    override val words: Int? = null,
    @SerializedName("articleCount")
    override val articleCount: Int? = null,
    @SerializedName("discussionCount")
    override val discussionCount: Int? = null
) : CoveredNovel

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
        Wenku8Urls.detailIdentity(url)?.let { return it }
        return FORUM_URL_REGEX.find(this.forumUrl)?.groupValues?.getOrNull(1).orEmpty()
    }

}

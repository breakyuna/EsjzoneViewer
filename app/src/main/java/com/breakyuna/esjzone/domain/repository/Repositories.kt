package com.breakyuna.esjzone.domain.repository

import com.breakyuna.esjzone.AppLanguage
import com.breakyuna.esjzone.database.entity.Bookmark
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.database.entity.SearchHistory
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.features.AuthorizationCheckResult
import com.breakyuna.esjzone.novellibrary.novel.Category
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.Comment
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.HistoryNovel
import com.breakyuna.esjzone.novellibrary.novel.Novel
import com.breakyuna.esjzone.network.PageableRequester
import com.breakyuna.esjzone.offline.DownloadedNovelSummary
import com.breakyuna.esjzone.ui.designsystem.AppThemeVariant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The presentation-facing session boundary. Cookie persistence remains owned by the network layer. */
interface SessionRepository {
    fun restore(host: String): Authorization?
    fun login(email: String, password: String): Authorization?
    fun check(authorization: Authorization): AuthorizationCheckResult
    fun logout(authorization: Authorization)
    fun clear(host: String? = null)
}

/** Settings are exposed as flows; callers do not need to know the legacy cache key format. */
interface SettingsRepository {
    val adult: StateFlow<Boolean>
    val theme: StateFlow<AppThemeVariant>
    val domain: StateFlow<String>
    val language: StateFlow<AppLanguage>
    val readerAutoSave: StateFlow<Boolean>
    fun setAdult(value: Boolean)
    fun setTheme(value: AppThemeVariant)
    fun setDomain(value: String)
    fun setLanguage(value: AppLanguage)
    fun setReaderAutoSave(value: Boolean)
}

interface NovelRepository {
    fun detail(authorization: Authorization, novel: Novel, includeComments: Boolean = false): DetailedNovel
    fun chapter(authorization: Authorization, chapter: Chapter, preferDownloaded: Boolean = true): DetailedChapter
}

interface SearchRepository {
    fun search(
        authorization: Authorization,
        keyword: String,
        category: Int = 0,
        sort: Int = 1
    ): Pair<PageableRequester<CoveredNovel>, List<CoveredNovel>>
    fun list(
        authorization: Authorization,
        novelType: Int,
        sortType: Int
    ): Pair<PageableRequester<CoveredNovel>, List<CoveredNovel>>
    fun categories(authorization: Authorization): List<Category>
}

interface BookshelfRepository {
    fun observe(authorization: Authorization): Flow<List<BookshelfEntry>>
    suspend fun setFavorite(authorization: Authorization, novel: Novel, desired: Boolean)
    suspend fun removeBatch(authorization: Authorization, entries: List<BookshelfEntry>): Int
}

interface HistoryRepository {
    fun observeLocal(): Flow<List<LocalReadingActivity>>
    fun localLatest(): LocalReadingActivity?
    fun cloud(authorization: Authorization, forceRefresh: Boolean = false): List<HistoryNovel>
    fun removeCloud(authorization: Authorization, videoId: String)
}

interface BookmarkRepository {
    fun observe(): Flow<List<Bookmark>>
    fun find(chapterUrl: String): Bookmark?
    fun add(bookmark: Bookmark)
    fun remove(bookmark: Bookmark)
}

interface DownloadRepository {
    fun list(): List<DownloadedNovelSummary>
    fun deleteAll(novelUrls: Iterable<String>): Int
    fun isDownloaded(novelUrl: String): Boolean
}

interface CommentRepository {
    fun page(authorization: Authorization, pageUrl: String, forceRefresh: Boolean = false): List<Comment>
}

interface CommunityRepository {
    fun categories(authorization: Authorization): List<com.breakyuna.esjzone.novellibrary.community.ForumCategory>
}

/** Reader access is intentionally narrow so window/scroll/anchor state stays outside this phase. */
interface ReaderRepository {
    fun chapter(authorization: Authorization, chapter: Chapter): DetailedChapter
}

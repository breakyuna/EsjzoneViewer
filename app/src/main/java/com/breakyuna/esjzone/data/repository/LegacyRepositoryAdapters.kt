package com.breakyuna.esjzone.data.repository

import com.breakyuna.esjzone.database.BookshelfRepository as LegacyBookshelfRepository
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.database.entity.Bookmark
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.features.AuthorizationCheckResult
import com.breakyuna.esjzone.network.features.checkAuthorization
import com.breakyuna.esjzone.network.features.getCategories
import com.breakyuna.esjzone.network.features.getChapterComments
import com.breakyuna.esjzone.network.features.getChapterDetail
import com.breakyuna.esjzone.network.features.getForumCategories
import com.breakyuna.esjzone.network.features.getHistories
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.network.features.listNovels
import com.breakyuna.esjzone.network.features.login
import com.breakyuna.esjzone.network.features.novels
import com.breakyuna.esjzone.network.features.removeHistory
import com.breakyuna.esjzone.network.features.search
import com.breakyuna.esjzone.network.features.getPageComments
import com.breakyuna.esjzone.network.features.logout
import com.breakyuna.esjzone.network.PageableRequester
import com.breakyuna.esjzone.novellibrary.novel.Category
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.Comment
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.HistoryNovel
import com.breakyuna.esjzone.novellibrary.novel.Novel
import com.breakyuna.esjzone.offline.DownloadedNovelSummary
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.domain.repository.BookmarkRepository
import com.breakyuna.esjzone.domain.repository.BookshelfRepository
import com.breakyuna.esjzone.domain.repository.CommentRepository
import com.breakyuna.esjzone.domain.repository.CommunityRepository
import com.breakyuna.esjzone.domain.repository.DownloadRepository
import com.breakyuna.esjzone.domain.repository.HistoryRepository
import com.breakyuna.esjzone.domain.repository.NovelRepository
import com.breakyuna.esjzone.domain.repository.ReaderRepository
import com.breakyuna.esjzone.domain.repository.SearchRepository
import com.breakyuna.esjzone.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow

class NetworkSessionRepository : SessionRepository {
    override fun restore(host: String): Authorization? = EsjzoneClient.restoreAuthorization(host)
    override fun login(email: String, password: String): Authorization? = EsjzoneClient.login(email, password)
    override fun check(authorization: Authorization): AuthorizationCheckResult =
        EsjzoneClient.checkAuthorization(authorization)
    override fun logout(authorization: Authorization) = EsjzoneClient.logout(authorization)
    override fun clear(host: String?) = EsjzoneClient.clearSession(host)
}

class NetworkNovelRepository : NovelRepository, ReaderRepository {
    override fun detail(authorization: Authorization, novel: Novel, includeComments: Boolean): DetailedNovel =
        EsjzoneClient.getNovelDetail(authorization, novel, includeComments)

    override fun chapter(authorization: Authorization, chapter: Chapter): DetailedChapter =
        chapter(authorization, chapter, preferDownloaded = true)

    override fun chapter(
        authorization: Authorization,
        chapter: Chapter,
        preferDownloaded: Boolean
    ): DetailedChapter = EsjzoneClient.getChapterDetail(authorization, chapter, preferDownloaded)
}

class NetworkSearchRepository : SearchRepository {
    override fun search(
        authorization: Authorization,
        keyword: String,
        category: Int,
        sort: Int
    ): Pair<PageableRequester<CoveredNovel>, List<CoveredNovel>> =
        EsjzoneClient.search(authorization, keyword, category, sort)

    override fun list(
        authorization: Authorization,
        novelType: Int,
        sortType: Int
    ): Pair<PageableRequester<CoveredNovel>, List<CoveredNovel>> =
        EsjzoneClient.novels(authorization, novelType, sortType)

    override fun categories(authorization: Authorization): List<Category> =
        EsjzoneClient.getCategories(authorization)
}

class DatabaseBookshelfRepository(private val database: GeneralDatabase) : BookshelfRepository {
    init {
        LegacyBookshelfRepository.initialize(database)
    }

    override fun observe(authorization: Authorization) = LegacyBookshelfRepository.observe(authorization)
    override suspend fun setFavorite(authorization: Authorization, novel: Novel, desired: Boolean) =
        LegacyBookshelfRepository.setFavorite(authorization, novel, desired)
    override suspend fun removeBatch(authorization: Authorization, entries: List<BookshelfEntry>): Int =
        LegacyBookshelfRepository.removeBatch(authorization, entries)
}

class DatabaseHistoryRepository(private val database: GeneralDatabase) : HistoryRepository {
    override fun observeLocal(): Flow<List<LocalReadingActivity>> =
        database.localReadingActivityDao().observeAll()

    override fun localLatest(): LocalReadingActivity? = database.localReadingActivityDao().getLatest()

    override fun cloud(authorization: Authorization, forceRefresh: Boolean): List<HistoryNovel> =
        EsjzoneClient.getHistories(authorization, forceRefresh)

    override fun removeCloud(authorization: Authorization, videoId: String) =
        EsjzoneClient.removeHistory(authorization, videoId)
}

class DatabaseBookmarkRepository(private val database: GeneralDatabase) : BookmarkRepository {
    private val dao = database.bookmarkDao()
    override fun observe(): Flow<List<Bookmark>> = dao.observeAll()
    override fun find(chapterUrl: String): Bookmark? = dao.findByChapterUrl(chapterUrl)
    override fun add(bookmark: Bookmark) = dao.insert(bookmark)
    override fun remove(bookmark: Bookmark) = dao.delete(bookmark)
}

class FileDownloadRepository : DownloadRepository {
    override fun list(): List<DownloadedNovelSummary> = NovelDownloadStore.listDownloadedNovels()
    override fun deleteAll(novelUrls: Iterable<String>): Int = NovelDownloadStore.deleteAll(novelUrls)
    override fun isDownloaded(novelUrl: String): Boolean = NovelDownloadStore.isDownloaded(novelUrl)
}

class NetworkCommentRepository : CommentRepository {
    override fun page(authorization: Authorization, pageUrl: String, forceRefresh: Boolean): List<Comment> =
        EsjzoneClient.getPageComments(authorization, pageUrl, forceRefresh)
}

class NetworkCommunityRepository : CommunityRepository {
    override fun categories(authorization: Authorization) = EsjzoneClient.getForumCategories(authorization)
}

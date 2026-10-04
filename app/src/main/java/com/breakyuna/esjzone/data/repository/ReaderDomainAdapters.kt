package com.breakyuna.esjzone.data.repository

import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.database.entity.Bookmark
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.domain.reader.ReaderBookmark
import com.breakyuna.esjzone.domain.reader.ReaderChapterDocument
import com.breakyuna.esjzone.domain.reader.ReaderChapterRef
import com.breakyuna.esjzone.domain.reader.ReaderChapterRepository
import com.breakyuna.esjzone.domain.reader.ReadingProgress
import com.breakyuna.esjzone.domain.reader.ReadingProgressRepository
import com.breakyuna.esjzone.domain.reader.ReaderBookmarkRepository
import com.breakyuna.esjzone.domain.repository.ReaderRepository
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.novellibrary.component.Component
import com.breakyuna.esjzone.novellibrary.novel.Chapter

/** Adapts the verified legacy chapter loader without moving network or cache policy into domain. */
class LegacyReaderChapterRepository(
    private val delegate: ReaderRepository,
    private val authorization: Authorization
) : ReaderChapterRepository {
    override suspend fun load(chapter: ReaderChapterRef): ReaderChapterDocument {
        val source = Chapter(name = chapter.name, url = chapter.url, isHistory = false)
        val detail = delegate.chapter(authorization, source)
        return ReaderChapterDocument(
            chapter = chapter,
            blocks = detail.body?.readerBlocks { detail.imageLocations[it] ?: it } ?: detail.content.flatMap(Component::toReaderBlocks),
            previous = detail.previous?.toReaderRef(),
            next = detail.next?.toReaderRef(),
            contentHtml = detail.contentHtml,
            sourceUrl = detail.sourceUrl,
            contentFingerprint = detail.body?.fingerprint.orEmpty()
        )
    }
}

class DatabaseReadingProgressRepository(
    database: GeneralDatabase
) : ReadingProgressRepository {
    private val dao = database.localReadingActivityDao()

    override suspend fun latest(novelId: String): ReadingProgress? =
        dao.getLatestForNovel(novelId)?.toDomain()

    override suspend fun save(progress: ReadingProgress) {
        dao.upsertLatest(progress.toEntity())
    }
}

class DatabaseReaderBookmarkRepository(
    database: GeneralDatabase
) : ReaderBookmarkRepository {
    private val dao = database.bookmarkDao()

    override suspend fun find(chapterUrl: String): ReaderBookmark? =
        dao.findByChapterUrl(chapterUrl)?.toDomain()

    override suspend fun save(bookmark: ReaderBookmark) {
        dao.insert(bookmark.toEntity())
    }

    override suspend fun remove(bookmark: ReaderBookmark) {
        dao.delete(bookmark.toEntity())
    }
}

private fun Chapter.toReaderRef() = ReaderChapterRef(name = name, url = url)

private fun LocalReadingActivity.toDomain() = ReadingProgress(
    activityId, novelId, novelName, novelUrl, chapterUrl, chapterName, chapterIndex, totalChapters,
    chapterProgress, startedAt, lastReadAt, durationMs, novelCoverUrl, anchor, bookProgress
)

private fun ReadingProgress.toEntity() = LocalReadingActivity(
    activityId = activityId,
    novelId = novelId,
    novelName = novelName,
    novelUrl = novelUrl,
    chapterUrl = chapterUrl,
    chapterName = chapterName,
    chapterIndex = chapterIndex,
    totalChapters = totalChapters,
    chapterProgress = chapterProgress,
    startedAt = startedAt,
    lastReadAt = lastReadAt,
    durationMs = durationMs,
    novelCoverUrl = novelCoverUrl,
    anchor = anchor,
    bookProgress = bookProgress
)

private fun Bookmark.toDomain() = ReaderBookmark(
    chapterUrl, novelId, novelName, chapterName, createdAt
)

private fun ReaderBookmark.toEntity() = Bookmark(
    chapterUrl, novelId, novelName, chapterName, createdAt
)

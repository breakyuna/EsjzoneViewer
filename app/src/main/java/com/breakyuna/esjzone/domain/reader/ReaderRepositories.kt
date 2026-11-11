package com.breakyuna.esjzone.domain.reader

/** Reader data boundary; implementations may use network, cache, or downloaded content. */
interface ReaderChapterRepository {
    suspend fun load(chapter: ReaderChapterRef): ReaderChapterDocument
}

interface ReadingProgressRepository {
    suspend fun latest(novelId: String): ReadingProgress?
    suspend fun save(progress: ReadingProgress)
}

interface ReaderBookmarkRepository {
    suspend fun findForChapter(chapterUrl: String): List<ReaderBookmark>
    suspend fun save(bookmark: ReaderBookmark)
    suspend fun remove(bookmark: ReaderBookmark)
}

interface ReaderSettingsRepository {
    suspend fun load(): ReaderSettingsState
    suspend fun save(settings: ReaderSettingsState)
}

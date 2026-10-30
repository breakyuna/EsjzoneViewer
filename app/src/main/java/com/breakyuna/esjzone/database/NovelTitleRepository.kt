package com.breakyuna.esjzone.database

import androidx.room.withTransaction
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.cancellablePageRequest
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.novellibrary.novel.FavoriteNovel
import com.breakyuna.esjzone.novellibrary.novel.novelTitleId
import com.breakyuna.esjzone.novellibrary.novel.validNovelTitle
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Repairs title metadata only; reading positions, durations and user intents stay intact. */
object NovelTitleRepository {
    private val requests = MutableStateFlow(0L)
    val repairRequests = requests.asStateFlow()
    private val changes = MutableStateFlow(0L)
    val titleChanges = changes.asStateFlow()

    fun requestRepair() { requests.update { it + 1L } }

    suspend fun resolve(authorization: Authorization?, novelId: String, novelUrl: String, name: String = ""): String =
        withContext(Dispatchers.IO) {
            val id = novelTitleId(novelId, novelUrl)
            val url = novelUrl.ifBlank { id.takeIf(String::isNotBlank)?.let { "/detail/$it.html" }.orEmpty() }
            if (url.isBlank()) return@withContext ""
            val knownTitle = validNovelTitle(name, id, url)
            if (knownTitle.isNotBlank()) return@withContext knownTitle
            val database = PresentationAccess.database
            val history = database.localReadingActivityDao().getLatestForIdentity(id, EsjzoneUrls.resolve(url))
            val candidates = listOf(name,
                history?.let { validNovelTitle(it.novelName, id, url, it.chapterName) }.orEmpty(),
                PresentationAccess.downloads.manifest(url)?.name.orEmpty()) +
                database.bookshelfDao().forNovel(id, BookshelfRepository.keyFor(url)).map { it.title } +
                database.bookmarkDao().forNovel(id).map { validNovelTitle(it.novelName, id, url, it.chapterName) } +
                database.readingStatDao().forBook(readingStatisticsBookKey(id, url)).map { it.bookName }
            var title = candidates.firstNotNullOfOrNull { validNovelTitle(it, id, url).takeIf(String::isNotBlank) }.orEmpty()
            if (title.isBlank() && authorization != null) {
                title = validNovelTitle(cancellablePageRequest {
                    PresentationAccess.client.getNovelDetail(authorization, FavoriteNovel("", url))
                }.name, id, url)
            }
            title
        }

    suspend fun repair(novelId: String, novelUrl: String, name: String) = withContext(Dispatchers.IO) {
        val id = novelTitleId(novelId, novelUrl)
        val title = validNovelTitle(name, id, novelUrl)
        if (title.isBlank()) return@withContext
        val url = EsjzoneUrls.resolve(novelUrl)
        val database = PresentationAccess.database
        try {
            database.withTransaction {
                database.localReadingActivityDao().forNovel(id, url).forEach {
                    if (validNovelTitle(it.novelName, id, it.novelUrl, it.chapterName).isBlank()) {
                        database.localReadingActivityDao().repairNovelName(it.activityId, it.novelName, title)
                    }
                }
                if (id.isNotBlank()) database.bookmarkDao().forNovel(id).forEach {
                    if (validNovelTitle(it.novelName, id, url, it.chapterName).isBlank()) {
                        database.bookmarkDao().repairNovelName(it.chapterUrl, it.novelName, title)
                    }
                }
                val key = readingStatisticsBookKey(id, url)
                database.readingStatDao().forBook(key).map { it.bookName }.distinct().forEach {
                    if (validNovelTitle(it, id, url).isBlank()) database.readingStatDao().repairBookName(key, it, title)
                }
                database.bookshelfDao().forNovel(id, BookshelfRepository.keyFor(url)).forEach {
                    if (validNovelTitle(it.title, id, it.url).isBlank()) {
                        database.bookshelfDao().repairNovelName(it.scope, it.bookKey, it.title, title)
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.w("NovelTitle", "Unable to repair stored titles", error)
        }
        try {
            if (PresentationAccess.downloads.repairNovelName(url, title)) changes.update { it + 1L }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.w("NovelTitle", "Unable to repair downloaded title", error)
        }
    }

    /** Run after session/storage restoration and backup import, including books not opened again. */
    suspend fun repairAll(authorization: Authorization) = withContext(Dispatchers.IO) {
        val database = PresentationAccess.database
        val records = buildList {
            database.localReadingActivityDao().getAll().forEach {
                val id = novelTitleId(it.novelId, it.novelUrl).ifBlank { novelTitleId(novelUrl = it.chapterUrl) }
                add(StoredTitle(id, it.novelUrl, validNovelTitle(it.novelName, id, it.novelUrl, it.chapterName)))
            }
            database.bookmarkDao().getAll().forEach {
                val id = novelTitleId(it.novelId, it.chapterUrl)
                add(StoredTitle(id, "", validNovelTitle(it.novelName, id, chapterName = it.chapterName)))
            }
            database.readingStatDao().getAll().forEach {
                val id = novelTitleId(novelUrl = it.bookKey).ifBlank {
                    it.bookKey.takeIf { key -> key.isNotBlank() && key.all(Char::isDigit) }.orEmpty()
                }
                add(StoredTitle(id, "", validNovelTitle(it.bookName, id, it.bookKey)))
            }
            database.bookshelfDao().getAllEntries().forEach {
                add(StoredTitle(novelTitleId(it.novelId, it.url), it.url, validNovelTitle(it.title, it.novelId, it.url)))
            }
            PresentationAccess.downloads.listDownloadedNovels(calculateSizes = false).forEach {
                add(StoredTitle(novelTitleId(novelUrl = it.novelUrl), it.novelUrl,
                    validNovelTitle(it.novelName, novelUrl = it.novelUrl)))
            }
        }
        records.filter { it.id.isNotBlank() }.groupBy { it.id }.forEach { (id, rows) ->
            if (rows.none { it.name.isBlank() }) return@forEach
            try {
                val url = rows.firstOrNull { it.url.isNotBlank() }?.url ?: "/detail/$id.html"
                val title = resolve(authorization, id, url, rows.firstOrNull { it.name.isNotBlank() }?.name.orEmpty())
                repair(id, url, title)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Failed lookups remain eligible on the next repair request/session.
                AppLogger.w("NovelTitle", "Unable to resolve stored title", error)
            }
        }
    }

    private data class StoredTitle(val id: String, val url: String, val name: String)
}

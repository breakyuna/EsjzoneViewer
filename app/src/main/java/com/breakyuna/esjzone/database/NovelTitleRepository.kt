package com.breakyuna.esjzone.database

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import com.breakyuna.esjzone.EsjzoneApplication
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.UntrustedPageException
import com.breakyuna.esjzone.network.cancellablePageRequest
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.novellibrary.novel.FavoriteNovel
import com.breakyuna.esjzone.novellibrary.novel.novelTitleId
import com.breakyuna.esjzone.novellibrary.novel.validNovelTitle
import com.breakyuna.esjzone.util.AppLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Repairs title metadata only; reading positions, durations and user intents stay intact. */
object NovelTitleRepository {
    private const val PREFS_NAME = "novel_title_repair_cache"
    private const val KEY_LAST_AUTO_SCAN_AT = "last_auto_scan_at"
    private const val NEGATIVE_PREFIX = "neg:"
    private const val AUTO_SCAN_COOLDOWN_MS = 24L * 60L * 60L * 1000L
    private const val PERMANENT_NEGATIVE_COOLDOWN_MS = 7L * 24L * 60L * 60L * 1000L
    private const val TRANSIENT_BASE_BACKOFF_MS = 60L * 60L * 1000L
    private const val MAX_TRANSIENT_BACKOFF_SHIFT = 7

    private data class NegativeEntry(
        val failedAtMs: Long,
        val failureCount: Int,
        val permanent: Boolean
    )

    private val requests = MutableStateFlow(0L)
    val repairRequests = requests.asStateFlow()
    private val changes = MutableStateFlow(0L)
    val titleChanges = changes.asStateFlow()
    private val memoryNegativeCache = ConcurrentHashMap<String, NegativeEntry>()
    private val memoryLastAutoScanAt = AtomicLong(0L)

    fun requestRepair() { requests.update { it + 1L } }

    private fun negativePrefs(): SharedPreferences? = runCatching {
        EsjzoneApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }.getOrNull()

    private fun negativeKey(novelId: String, novelUrl: String): String {
        val id = novelTitleId(novelId, novelUrl)
        if (id.isNotBlank()) return "id:$id"
        val canonical = EsjzoneUrls.canonicalPageKey(novelUrl).ifBlank { novelUrl.trim() }
        return "url:$canonical"
    }

    private fun getNegativeEntry(key: String): NegativeEntry? {
        memoryNegativeCache[key]?.let { return it }
        val raw = negativePrefs()?.getString("$NEGATIVE_PREFIX$key", null) ?: return null
        val parts = raw.split('|')
        if (parts.size != 3) return null
        val failedAtMs = parts[0].toLongOrNull() ?: return null
        val failureCount = parts[1].toIntOrNull()?.coerceAtLeast(1) ?: return null
        val permanent = parts[2] == "1"
        return NegativeEntry(failedAtMs, failureCount, permanent).also {
            memoryNegativeCache.putIfAbsent(key, it)
        }
    }

    private fun isNegativelyCached(key: String, now: Long = System.currentTimeMillis()): Boolean {
        val entry = getNegativeEntry(key) ?: return false
        if (entry.failedAtMs > now) return false
        val cooldown = if (entry.permanent) {
            PERMANENT_NEGATIVE_COOLDOWN_MS
        } else {
            val shift = (entry.failureCount - 1).coerceIn(0, MAX_TRANSIENT_BACKOFF_SHIFT)
            (TRANSIENT_BASE_BACKOFF_MS shl shift).coerceAtMost(PERMANENT_NEGATIVE_COOLDOWN_MS)
        }
        return now - entry.failedAtMs < cooldown
    }

    private fun recordNegativeFailure(
        key: String,
        permanent: Boolean,
        now: Long = System.currentTimeMillis()
    ) {
        val previous = getNegativeEntry(key)
        val count = ((previous?.failureCount ?: 0) + 1).coerceAtMost(MAX_TRANSIENT_BACKOFF_SHIFT + 1)
        val isPermanent = permanent || (previous?.permanent == true)
        val next = NegativeEntry(now, count, isPermanent)
        memoryNegativeCache[key] = next
        negativePrefs()?.edit()
            ?.putString("$NEGATIVE_PREFIX$key", "$now|$count|${if (isPermanent) 1 else 0}")
            ?.apply()
    }

    private fun clearNegativeEntry(key: String) {
        memoryNegativeCache.remove(key)
        negativePrefs()?.edit()?.remove("$NEGATIVE_PREFIX$key")?.apply()
    }

    private fun getLastAutoScanAt(): Long {
        val cached = memoryLastAutoScanAt.get()
        if (cached > 0L) return cached
        val persisted = negativePrefs()?.getLong(KEY_LAST_AUTO_SCAN_AT, 0L) ?: 0L
        if (persisted > 0L) memoryLastAutoScanAt.compareAndSet(0L, persisted)
        return persisted
    }

    private fun recordLastAutoScanAt(timestamp: Long) {
        memoryLastAutoScanAt.set(timestamp)
        negativePrefs()?.edit()?.putLong(KEY_LAST_AUTO_SCAN_AT, timestamp)?.apply()
    }

    private fun isPermanentLookupFailure(error: Throwable): Boolean = when (error) {
        is UntrustedPageException ->
            error.validation.reason == "HTTP 404" || error.validation.reason == "HTTP 410"
        is IllegalArgumentException -> true
        else -> false
    }

    suspend fun resolve(authorization: Authorization?, novelId: String, novelUrl: String, name: String = ""): String =
        withContext(Dispatchers.IO) {
            val id = novelTitleId(novelId, novelUrl)
            val url = novelUrl.ifBlank { id.takeIf(String::isNotBlank)?.let { "/detail/$it.html" }.orEmpty() }
            if (url.isBlank()) return@withContext ""
            val knownTitle = validNovelTitle(name, id, url)
            if (knownTitle.isNotBlank()) return@withContext knownTitle
            val database = PresentationAccess.database
            val history = database.localReadingActivityDao().getLatestForIdentity(id, EsjzoneUrls.resolve(url))
            history?.let { validNovelTitle(it.novelName, id, url, it.chapterName) }
                ?.takeIf(String::isNotBlank)?.let { return@withContext it }
            validNovelTitle(PresentationAccess.downloads.manifest(url)?.name.orEmpty(), id, url)
                .takeIf(String::isNotBlank)?.let { return@withContext it }
            database.bookshelfDao().forNovel(id, BookshelfRepository.keyFor(url))
                .firstNotNullOfOrNull { validNovelTitle(it.title, id, url).takeIf(String::isNotBlank) }
                ?.let { return@withContext it }
            database.bookmarkDao().forNovel(id)
                .firstNotNullOfOrNull { validNovelTitle(it.novelName, id, url, it.chapterName).takeIf(String::isNotBlank) }
                ?.let { return@withContext it }
            database.readingStatDao().forBook(readingStatisticsBookKey(id, url))
                .firstNotNullOfOrNull { validNovelTitle(it.bookName, id, url).takeIf(String::isNotBlank) }
                ?.let { return@withContext it }
            if (authorization == null) return@withContext ""
            val cacheKey = negativeKey(id, url)
            if (isNegativelyCached(cacheKey)) return@withContext ""
            try {
                val detail = cancellablePageRequest {
                    PresentationAccess.client.getNovelDetail(authorization, FavoriteNovel("", url))
                }
                val resolvedTitle = validNovelTitle(detail.name, id, url)
                if (resolvedTitle.isNotBlank()) {
                    clearNegativeEntry(cacheKey)
                } else {
                    recordNegativeFailure(cacheKey, permanent = true)
                }
                resolvedTitle
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                recordNegativeFailure(cacheKey, permanent = isPermanentLookupFailure(error))
                throw error
            }
        }

    suspend fun repair(novelId: String, novelUrl: String, name: String) = withContext(Dispatchers.IO) {
        val id = novelTitleId(novelId, novelUrl)
        val title = validNovelTitle(name, id, novelUrl)
        if (title.isBlank()) return@withContext
        clearNegativeEntry(negativeKey(id, novelUrl))
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
    suspend fun repairAll(authorization: Authorization, force: Boolean = true) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force) {
            val lastScan = getLastAutoScanAt()
            if (lastScan in 1L..now && now - lastScan < AUTO_SCAN_COOLDOWN_MS) {
                return@withContext
            }
        }
        val database = PresentationAccess.database
        val context = kotlinx.coroutines.currentCoroutineContext()
        context.ensureActive()
        val records = buildList {
            database.localReadingActivityDao().titleRepairCandidates().forEach {
                val id = novelTitleId(it.novelId, it.novelUrl).ifBlank { novelTitleId(novelUrl = it.chapterUrl) }
                add(StoredTitle(id, it.novelUrl, validNovelTitle(it.novelName, id, it.novelUrl, it.chapterName)))
            }
            database.bookmarkDao().titleRepairCandidates().forEach {
                val id = novelTitleId(it.novelId, it.chapterUrl)
                add(StoredTitle(id, "", validNovelTitle(it.novelName, id, chapterName = it.chapterName)))
            }
            database.readingStatDao().titleRepairCandidates().forEach {
                val id = novelTitleId(novelUrl = it.bookKey).ifBlank {
                    it.bookKey.takeIf { key -> key.isNotBlank() && key.all(Char::isDigit) }.orEmpty()
                }
                add(StoredTitle(id, "", validNovelTitle(it.bookName, id, it.bookKey)))
            }
            database.bookshelfDao().titleRepairCandidates().forEach {
                add(StoredTitle(novelTitleId(it.novelId, it.url), it.url, validNovelTitle(it.title, it.novelId, it.url)))
            }
            PresentationAccess.downloads.listDownloadedMetadata { context.ensureActive() }.filter {
                validNovelTitle(it.novelName, novelUrl = it.novelUrl).isBlank()
            }.forEach {
                add(StoredTitle(novelTitleId(novelUrl = it.novelUrl), it.novelUrl,
                    validNovelTitle(it.novelName, novelUrl = it.novelUrl)))
            }
        }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        records.filter { it.id.isNotBlank() }.groupBy { it.id }.forEach { (id, rows) ->
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (rows.none { it.name.isBlank() }) return@forEach
            val url = rows.firstOrNull { it.url.isNotBlank() }?.url ?: "/detail/$id.html"
            val known = rows.firstNotNullOfOrNull { it.name.takeIf(String::isNotBlank) }.orEmpty()
            val title = if (known.isNotBlank()) {
                known
            } else {
                val cacheKey = negativeKey(id, url)
                if (isNegativelyCached(cacheKey)) return@forEach
                try {
                    resolve(authorization, id, url)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppLogger.w("NovelTitle", "Unable to resolve stored title", error)
                    ""
                }
            }
            if (title.isNotBlank()) {
                repair(id, url, title)
            }
        }
        recordLastAutoScanAt(System.currentTimeMillis())
    }

    private data class StoredTitle(val id: String, val url: String, val name: String)
}

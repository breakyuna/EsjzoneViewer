package com.breakyuna.esjzone.database

import com.breakyuna.esjzone.network.wenku8.Wenku8Urls
import com.breakyuna.esjzone.network.wenku8.novelDetailUrlForId
import com.breakyuna.esjzone.EsjzoneApplication
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.data.settings.SettingsDefaults
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.util.AppLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Manages local cover image files for bookmarks.
 *
 * Design constraints:
 * 1. Zero network requests: covers are NEVER fetched over the network here.
 * 2. Shared per novel: all bookmarks of the same book share the exact same cover file on disk.
 * 3. Cache-first: copies the book's cover directly from Coil's existing disk cache, as the
 *    cover is guaranteed to have loaded during browsing/reading prior to bookmarking.
 * 4. Offline fallback: falls back to copying the downloaded novel's cover if offline and image cache was cleared.
 * 5. Lifecycle-aware: cleans up the book's cover file when no bookmarks for that novel remain.
 */
object BookmarkCoverStore {
    private const val DIRECTORY_NAME = "bookmark_covers"

    private val directory: File
        get() = File(EsjzoneApplication.instance.filesDir, DIRECTORY_NAME).apply {
            if (!isDirectory) mkdirs()
        }

    fun cleanNovelId(rawNovelId: String, chapterUrl: String = ""): String {
        val id = rawNovelId.trim()
        Wenku8Urls.bookId(id)?.let { return Wenku8Urls.identity(it) }
        Wenku8Urls.detailIdentity(id)?.let { return it }
        if (id.isNotBlank() && !id.startsWith("/") && !id.startsWith("http")) {
            return id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        }
        if (chapterUrl.isNotBlank()) {
            val fromChapter = Chapter("", chapterUrl, false).novelId()
            if (fromChapter.isNotBlank()) return fromChapter
        }
        if (id.isNotBlank()) {
            val match = Regex("/detail/([^/]+?)(?:\\.html)?/?$").find(id)
            if (match != null) return match.groupValues[1]
        }
        return id.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "unknown" }
    }

    fun coverFile(novelId: String, chapterUrl: String = ""): File {
        val cleanId = cleanNovelId(novelId, chapterUrl).replace(':', '_')
        return File(directory, "cover_$cleanId.jpg")
    }

    fun hasCover(novelId: String, chapterUrl: String = ""): Boolean {
        val file = coverFile(novelId, chapterUrl)
        return file.isFile && file.length() > 0L
    }

    fun getCoverUri(novelId: String, chapterUrl: String = ""): String? {
        val file = coverFile(novelId, chapterUrl)
        return if (file.isFile && file.length() > 0L) file.toURI().toString() else null
    }

    /**
     * Looks up an already cached cover file from Coil's disk cache.
     * ZERO network requests are made.
     */
    fun findCoilCachedCover(coverUrl: String): File? {
        val trimmed = coverUrl.trim()
        if (trimmed.isBlank()) return null
        val diskCache = PresentationAccess.imageLoader.diskCache ?: return null

        val parsed = trimmed.toHttpUrlOrNull()
        val isEsj = parsed != null && EsjzoneUrls.isEsjHost(parsed.host)

        val candidateKeys = buildList {
            add(trimmed)
            val normalized = EsjzoneUrls.coverOrEmpty(trimmed)
            if (normalized.isNotBlank()) add(normalized)
            runCatching { EsjzoneUrls.resolve(trimmed) }.getOrNull()?.takeIf(String::isNotBlank)?.let(::add)
            if (isEsj && parsed != null) {
                SettingsDefaults.DOMAINS.forEach { domain ->
                    runCatching {
                        parsed.newBuilder().host(domain).build().toString()
                    }.getOrNull()?.let(::add)
                }
            }
        }.distinct()

        for (key in candidateKeys) {
            runCatching {
                diskCache.openSnapshot(key)?.use { snapshot ->
                    val file = File(snapshot.data.toString())
                    if (file.isFile && file.length() > 0L) {
                        return file
                    }
                }
            }
        }
        return null
    }

    /**
     * Copies a cached cover file to the bookmarks cover directory when a bookmark is added.
     *
     * - Reuses the existing file if this novel already has a cover saved.
     * - Copies from Coil's disk cache without any network request.
     * - Falls back to copying from the offline downloaded novel directory.
     * - Does NOT make any network requests.
     */
    suspend fun saveCoverFromCacheOrDownload(
        novelId: String,
        coverUrl: String = "",
        novelUrl: String = "",
        chapterUrl: String = ""
    ): String? = withContext(Dispatchers.IO) {
        val cleanId = cleanNovelId(novelId, chapterUrl)
        if (cleanId.isBlank() || cleanId == "unknown") return@withContext null

        val target = coverFile(cleanId)
        // 1. Reuse existing cover file if already present (same book shares one cover file)
        if (target.isFile && target.length() > 0L) {
            return@withContext target.toURI().toString()
        }

        // Determine cover URL candidates
        val effectiveCoverUrl = coverUrl.trim().ifBlank {
            PresentationAccess.database.localReadingActivityDao()
                .getLatestForNovel(cleanId)?.novelCoverUrl.orEmpty()
        }

        // 2. Try copying from Coil's cache
        if (effectiveCoverUrl.isNotBlank()) {
            val coilFile = findCoilCachedCover(effectiveCoverUrl)
            if (coilFile != null && coilFile.isFile && coilFile.length() > 0L) {
                runCatching {
                    val temp = File(directory, "${target.name}.tmp")
                    coilFile.copyTo(temp, overwrite = true)
                    if (temp.renameTo(target)) {
                        return@withContext target.toURI().toString()
                    }
                }.onFailure {
                    AppLogger.w("BookmarkCoverStore", "Failed to copy Coil cached cover for $cleanId", it)
                }
            }
        }

        // 3. Fallback: try copying from downloaded novel directory
        val downloadCandidates = listOfNotNull(
            novelUrl.takeIf(String::isNotBlank),
            novelDetailUrlForId(cleanId).takeIf(String::isNotBlank),
            cleanId
        ).distinct()

        for (candidate in downloadCandidates) {
            val downloadedCover = NovelDownloadStore.findDownloadedCover(candidate)
            if (downloadedCover != null && downloadedCover.isFile && downloadedCover.length() > 0L) {
                runCatching {
                    val temp = File(directory, "${target.name}.tmp")
                    downloadedCover.copyTo(temp, overwrite = true)
                    if (temp.renameTo(target)) {
                        return@withContext target.toURI().toString()
                    }
                }.onFailure {
                    AppLogger.w("BookmarkCoverStore", "Failed to copy downloaded cover for $cleanId", it)
                }
            }
        }

        // 4. If neither succeeded, stop without attempting any network request.
        null
    }

    /**
     * Cleans up the book's cover file when no bookmarks for this novel remain.
     */
    suspend fun cleanupIfUnused(novelId: String, chapterUrl: String = "") = withContext(Dispatchers.IO) {
        val cleanId = cleanNovelId(novelId, chapterUrl)
        if (cleanId.isBlank() || cleanId == "unknown") return@withContext

        val hasOtherBookmarks = runCatching {
            PresentationAccess.database.bookmarkDao().getAll().any { bookmark ->
                cleanNovelId(bookmark.novelId, bookmark.chapterUrl) == cleanId
            }
        }.getOrDefault(true)

        if (!hasOtherBookmarks) {
            val file = coverFile(cleanId)
            if (file.isFile) {
                file.delete()
            }
        }
    }
}

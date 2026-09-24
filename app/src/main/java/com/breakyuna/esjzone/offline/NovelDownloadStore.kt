package com.breakyuna.esjzone.offline

import android.content.Context
import com.breakyuna.esjzone.R
import android.os.StatFs
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.data.settings.SettingsDefaults
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.features.ChapterPasswordRequiredException
import com.breakyuna.esjzone.network.features.getChapterDetail
import com.breakyuna.esjzone.network.features.isPasswordProtectedChapterHtml
import com.breakyuna.esjzone.network.features.unlockPasswordProtectedChapter
import com.breakyuna.esjzone.novellibrary.component.ChapterItem
import com.breakyuna.esjzone.novellibrary.component.Component
import com.breakyuna.esjzone.novellibrary.component.ImageComponent
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.component.analyseComponents
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.NovelChapterList
import com.breakyuna.esjzone.novellibrary.novel.NovelDescription
import com.breakyuna.esjzone.util.AppLogger
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.Jsoup

data class DownloadProgress(
    val completed: Int,
    val total: Int,
    val chapterName: String
)

data class DownloadedNovelManifest(
    val version: Int = 1,
    val name: String,
    val url: String,
    val coverUrl: String,
    val views: Int,
    val likes: Int,
    val words: Int,
    val type: String,
    val author: String,
    val forumUrl: String,
    val tags: List<String>,
    val isAdult: Boolean,
    val description: String,
    val sourceUrl: String?,
    val updatedAt: String?,
    val chapters: List<DownloadedChapterRecord>,
    val downloadedAt: Long,
    val complete: Boolean,
    val commonPassword: String? = null
) {
    val pendingPasswordChapters: List<DownloadedChapterRecord>
        get() = chapters.filter { it.requiresPassword && !it.downloaded }
}

data class DownloadedChapterRecord(
    val index: Int,
    val name: String,
    val url: String,
    val fileName: String,
    val downloaded: Boolean,
    val requiresPassword: Boolean = false
)

data class DownloadedChapterContent(
    val name: String,
    val url: String,
    val components: List<DownloadedComponent>,
    val contentHtml: String? = null,
    val baseUrl: String? = null
)

data class DownloadedComponent(
    val type: String,
    val value: String,
    val localFile: String? = null,
    val mediaType: String? = null
)

/** A lightweight row model for download management screens. */
data class DownloadedNovelSummary(
    val manifest: DownloadedNovelManifest,
    val downloadedChapterCount: Int,
    val storageBytes: Long
) {
    val novelUrl: String get() = manifest.url
    val novelName: String get() = manifest.name
    val coverUrl: String get() = manifest.coverUrl
}

/**
 * Persistent, user-requested novel downloads.
 *
 * This store intentionally lives outside PageCache: downloaded chapters must not disappear
 * when the user clears the temporary page cache or when that cache reaches its size limit.
 */
object NovelDownloadStore {

    private const val MANIFEST_FILE = "manifest.json"
    private const val TEXT_COMPONENT = "text"
    private const val IMAGE_COMPONENT = "image"
    const val DEFAULT_DOWNLOAD_CONCURRENCY = 5
    private const val CHAPTER_MAX_ATTEMPTS = 2
    private const val PROGRESS_THROTTLE_MS = 150L
    private const val MANIFEST_CHECKPOINT_INTERVAL = 8
    private const val MIN_FREE_SPACE_BYTES = 100L * 1024L * 1024L

    private val gson = Gson()
    private val ioLock = Any()
    private val deletionGenerations = HashMap<String, Long>()
    /** Rebuilt on a cache miss; invalidated whenever any manifest changes. */
    private val chapterIndex = HashMap<String, ChapterMatch>()
    /** Rebuilt after a manifest write or deletion; bookshelf refreshes read this snapshot. */
    private var inventorySnapshot: List<DownloadedNovelSummary>? = null

    private data class DownloadWriteGuard(val directoryPath: String, val generation: Long)

    private fun newWriteGuard(directory: File): DownloadWriteGuard = synchronized(ioLock) {
        DownloadWriteGuard(directory.absolutePath, deletionGenerations[directory.absolutePath] ?: 0L)
    }

    private fun ensureWriteAllowed(guard: DownloadWriteGuard?) {
        if (guard == null) return
        val current = synchronized(ioLock) { deletionGenerations[guard.directoryPath] ?: 0L }
        if (current != guard.generation) throw CancellationException("Downloaded novel was removed by the user")
    }

    private fun requireFreeSpace(directory: File) {
        val available = runCatching { StatFs(directory.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (available < MIN_FREE_SPACE_BYTES) throw IOException("Insufficient storage space for download")
    }

    @Volatile
    private var rootDirectory: File? = null
    @Volatile
    private var appContext: Context? = null

    private fun missingImageLabel(): String = appContext?.getString(R.string.download_image_unavailable)
        ?: "[Image unavailable]"

    fun initialize(context: Context) {
        appContext = context.applicationContext
        val directory = File(context.applicationContext.filesDir, "downloaded_novels")
        if (directory.isDirectory || directory.mkdirs()) {
            synchronized(ioLock) { chapterIndex.clear(); inventorySnapshot = null }
            rootDirectory = directory
        } else {
            AppLogger.e("NovelDownloadStore", "Unable to create the novel download directory")
        }
    }

    fun manifest(novelUrl: String): DownloadedNovelManifest? = synchronized(ioLock) {
        readManifest(directoryFor(novelUrl, create = false))
    }

    fun findDownloadedCover(novelUrl: String): File? = synchronized(ioLock) {
        val dir = directoryFor(novelUrl, create = false) ?: return@synchronized null
        val direct = File(dir, "cover.jpg")
        if (direct.isFile && direct.length() > 0L) return@synchronized direct
        val imagesDir = File(dir, "images")
        if (imagesDir.isDirectory) {
            imagesDir.listFiles()
                ?.firstOrNull { it.isFile && it.length() > 0L && it.name.startsWith("cover") }
                ?.let { return@synchronized it }
        }
        null
    }

    fun isDownloaded(novelUrl: String): Boolean = manifest(novelUrl)?.complete == true

    /**
     * Lists every novel with at least one persisted chapter, including partial
     * auto-saves and interrupted background downloads.
     */
    fun listDownloadedNovels(): List<DownloadedNovelSummary> = synchronized(ioLock) {
        inventorySnapshot?.let { return@synchronized it }
        val snapshot = rootDirectory?.listFiles()
            .orEmpty()
            .asSequence()
            .filter(File::isDirectory)
            .mapNotNull { directory ->
                val stored = readManifest(directory) ?: return@mapNotNull null
                val count = stored.chapters.count { record ->
                    record.downloaded && resolveLocalFile(directory, record.fileName)?.isFile == true
                }
                if (count == 0) return@mapNotNull null
                DownloadedNovelSummary(
                    manifest = stored,
                    downloadedChapterCount = count,
                    storageBytes = directory.walkTopDown()
                        .filter(File::isFile)
                        .sumOf(File::length)
                )
            }
            .sortedByDescending { it.manifest.downloadedAt }
            .toList()
        inventorySnapshot = snapshot
        snapshot
    }

    /** Returns the on-disk size of one downloaded novel, including its manifest. */
    fun storageBytes(novelUrl: String): Long = synchronized(ioLock) {
        val directory = directoryFor(novelUrl, create = false) ?: return@synchronized 0L
        directory.walkTopDown().filter(File::isFile).sumOf(File::length)
    }

    /**
     * Deletes only this novel's private download directory. Other local data
     * (bookshelf, reading history, bookmarks and cloud state) is untouched.
     */
    fun delete(novelUrl: String): Boolean = synchronized(ioLock) {
        cancelActiveWork(novelUrl)
        val directory = directoryFor(novelUrl, create = false) ?: return@synchronized false
        deletionGenerations[directory.absolutePath] = (deletionGenerations[directory.absolutePath] ?: 0L) + 1L
        chapterIndex.clear()
        inventorySnapshot = null
        directory.deleteRecursively()
    }

    /** Deletes several novel download directories and returns the number removed. */
    fun deleteAll(novelUrls: Iterable<String>): Int = synchronized(ioLock) {
        chapterIndex.clear()
        inventorySnapshot = null
        novelUrls.distinct()
            .count { url ->
                cancelActiveWork(url)
                val directory = directoryFor(url, create = false) ?: return@count false
                deletionGenerations[directory.absolutePath] = (deletionGenerations[directory.absolutePath] ?: 0L) + 1L
                directory.deleteRecursively()
            }
    }

    private fun cancelActiveWork(novelUrl: String) {
        runCatching {
            val context = com.breakyuna.esjzone.EsjzoneApplication.instance
            NovelDownloadManager.cancel(context, novelUrl)
        }
    }

    data class BatchUnlockResult(
        val totalCount: Int,
        val unlockedCount: Int,
        val failedCount: Int,
        val updatedManifest: DownloadedNovelManifest?
    )

    fun updateCommonPassword(
        novel: DetailedNovel,
        commonPassword: String?
    ): DownloadedNovelManifest? {
        val directory = directoryFor(novel.url, create = true) ?: return null
        val writeGuard = newWriteGuard(directory)
        val trimmed = commonPassword?.trim()?.ifBlank { null }
        return synchronized(ioLock) {
            val manifest = readManifest(directory)
            val updated = manifest?.copy(commonPassword = trimmed)
                ?: manifestFrom(
                    novel = novel,
                    records = emptyList(),
                    downloadedAt = System.currentTimeMillis(),
                    complete = false,
                    commonPassword = trimmed
                )
            writeManifest(directory, updated, writeGuard)
            inventorySnapshot = null
            updated
        }
    }

    fun updateCommonPassword(
        novelUrl: String,
        commonPassword: String?
    ): DownloadedNovelManifest? {
        val directory = directoryFor(novelUrl, create = false) ?: return null
        val writeGuard = newWriteGuard(directory)
        val trimmed = commonPassword?.trim()?.ifBlank { null }
        return synchronized(ioLock) {
            val manifest = readManifest(directory) ?: return null
            val updated = manifest.copy(commonPassword = trimmed)
            writeManifest(directory, updated, writeGuard)
            inventorySnapshot = null
            updated
        }
    }

    suspend fun unlockChaptersWithCommonPassword(
        authorization: Authorization,
        novel: DetailedNovel,
        commonPassword: String,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): BatchUnlockResult {
        val trimmed = commonPassword.trim()
        val unlockBaseUrl = EsjzoneUrls.resolve(
            novel.url,
            EsjzoneUrls.baseForDomain(authorization.domain.ifBlank { EsjzoneUrls.BaseWithoutProtocol })
        ).ifBlank { EsjzoneUrls.baseForDomain(authorization.domain.ifBlank { EsjzoneUrls.BaseWithoutProtocol }) }
        val directory = directoryFor(novel.url, create = true)
            ?: return BatchUnlockResult(0, 0, 0, null)
        val writeGuard = newWriteGuard(directory)

        val initialManifest = synchronized(ioLock) {
            val existing = readManifest(directory)
            val withPw = (existing ?: manifestFrom(
                novel = novel,
                records = emptyList(),
                downloadedAt = System.currentTimeMillis(),
                complete = false
            )).copy(commonPassword = trimmed)
            writeManifest(directory, withPw, writeGuard)
            inventorySnapshot = null
            withPw
        }

        val pending = initialManifest.pendingPasswordChapters
        if (pending.isEmpty()) {
            return BatchUnlockResult(0, 0, 0, initialManifest)
        }

        var unlocked = 0
        var failed = 0
        val total = pending.size

        for ((idx, record) in pending.withIndex()) {
            currentCoroutineContext().ensureActive()
            onProgress(idx + 1, total)
            try {
                val detail = EsjzoneClient.unlockPasswordProtectedChapter(
                    authorization = authorization,
                    chapter = Chapter(record.name, record.url, false),
                    password = trimmed,
                    baseUrl = unlockBaseUrl
                )
                currentCoroutineContext().ensureActive()
                saveChapter(
                    novelName = novel.name,
                    novelUrl = novel.url,
                    coverUrl = novel.coverUrl,
                    chapterOrder = novel.chapterList.orderedChapters,
                    chapter = Chapter(record.name, record.url, false),
                    detail = detail,
                    authorization = authorization
                )
                currentCoroutineContext().ensureActive()
                unlocked++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w("NovelDownloadStore", "Common password unlock failed for chapter ${record.name}", e)
                failed++
            }
        }

        val finalManifest = synchronized(ioLock) { readManifest(directory) }
        return BatchUnlockResult(
            totalCount = total,
            unlockedCount = unlocked,
            failedCount = failed,
            updatedManifest = finalManifest
        )
    }

    /**
     * Persists one chapter loaded by the reader for offline access.
     * Images are persisted locally (reusing Coil's disk cache or downloaded)
     * without delaying the reader UI.
     */
    fun saveChapter(
        novelName: String,
        novelUrl: String,
        coverUrl: String,
        chapterOrder: List<Chapter>,
        chapter: Chapter,
        detail: DetailedChapter,
        authorization: Authorization? = null
    ): DownloadedNovelManifest? {
        val normalizedNovelUrl = novelUrl.trim()
        if (normalizedNovelUrl.isBlank()) return null
        val directory = directoryFor(normalizedNovelUrl, create = true) ?: return null
        val writeGuard = newWriteGuard(directory)
        requireFreeSpace(directory)

        val previous = synchronized(ioLock) {
            ensureWriteAllowed(writeGuard)
            readManifest(directory)
        }
        val targetKey = chapterKey(chapter.url)
        val existing = previous?.chapters?.firstOrNull { chapterKey(it.url) == targetKey }
        val existingFile = existing?.fileName?.let { resolveLocalFile(directory, it) }

        if (existing?.downloaded == true &&
            existingFile?.isFile == true &&
            isChapterFullyDownloaded(directory, existingFile) &&
            previous.name == novelName && previous.coverUrl == coverUrl &&
            chapterOrder.isNotEmpty() &&
            previous.chapters.size == chapterOrder.size &&
            previous.chapters.indices.all { index ->
                val record = previous.chapters[index]
                val current = chapterOrder[index]
                record.index == index && record.name == current.name &&
                    chapterKey(record.url) == chapterKey(current.url)
            }
        ) {
            return previous
        }

        val auth = authorization ?: Authorization("", "", EsjzoneUrls.BaseWithoutProtocol)
        val imageComponents = detail.content.filterIsInstance<ImageComponent>()
        val downloadedImages = imageComponents
            .distinctBy { it.url }
            .mapNotNull { component ->
                ensureWriteAllowed(writeGuard)
                val image = downloadImage(
                    authorization = auth,
                    novelDirectory = directory,
                    rawUrl = component.url,
                    baseUrl = detail.sourceUrl ?: normalizedNovelUrl,
                    writeGuard = writeGuard
                )
                if (image != null) component.url to image else null
            }.toMap()

        return synchronized(ioLock) {
            ensureWriteAllowed(writeGuard)
            saveChapterLocked(
                novelName = novelName,
                novelUrl = normalizedNovelUrl,
                coverUrl = coverUrl,
                chapterOrder = chapterOrder,
                chapter = chapter,
                detail = detail,
                downloadedImages = downloadedImages,
                writeGuard = writeGuard
            )
        }
    }

    private fun saveChapterLocked(
        novelName: String,
        novelUrl: String,
        coverUrl: String,
        chapterOrder: List<Chapter>,
        chapter: Chapter,
        detail: DetailedChapter,
        downloadedImages: Map<String, DownloadedImage> = emptyMap(),
        writeGuard: DownloadWriteGuard
    ): DownloadedNovelManifest? {
        ensureWriteAllowed(writeGuard)
        val normalizedNovelUrl = novelUrl.trim()
        if (normalizedNovelUrl.isBlank()) return null
        val directory = directoryFor(normalizedNovelUrl, create = false)
            ?: return null.also { ensureWriteAllowed(writeGuard) }
        ensureWriteAllowed(writeGuard)
        val previous = readManifest(directory)
        // Prefetch and the subsequent reader load both save the same chapter. Avoid
        // rescanning every chapter file and rewriting the full manifest on that path.
        if (previous != null && chapterOrder.isNotEmpty() &&
            previous.chapters.size == chapterOrder.size &&
            previous.chapters.indices.all { index ->
                val record = previous.chapters[index]
                val current = chapterOrder[index]
                record.index == index && record.name == current.name &&
                    chapterKey(record.url) == chapterKey(current.url)
            }
        ) {
            val existing = previous.chapters.firstOrNull {
                chapterKey(it.url) == chapterKey(chapter.url)
            }
            val existingFile = existing?.fileName?.let { resolveLocalFile(directory, it) }
            if (existing?.downloaded == true &&
                existingFile?.isFile == true &&
                isChapterFullyDownloaded(directory, existingFile) &&
                previous.name == novelName && previous.coverUrl == coverUrl
            ) return previous
        }
        val previousByKey = previous?.chapters.orEmpty().associateBy { chapterKey(it.url) }
        val ordered = buildList {
            addAll(chapterOrder)
            add(chapter)
        }.filter { chapterKey(it.url).isNotBlank() }.distinctBy { chapterKey(it.url) }
        val knownKeys = ordered.map { chapterKey(it.url) }.toHashSet()
        val records = ordered.mapIndexed { index, item ->
            val old = previousByKey[chapterKey(item.url)]
            DownloadedChapterRecord(
                index = index,
                name = item.name,
                url = item.url,
                fileName = old?.fileName ?: chapterFileName(item.url),
                downloaded = old?.downloaded == true &&
                    resolveLocalFile(directory, old.fileName)?.isFile == true,
                requiresPassword = old?.requiresPassword == true
            )
        }.toMutableList()
        // Preserve old records absent from a temporarily incomplete TOC. A
        // later full download can reconcile them against the refreshed TOC.
        previous?.chapters.orEmpty()
            .filter { chapterKey(it.url) !in knownKeys }
            .forEach { old -> records += old.copy(index = records.size) }

        val targetKey = chapterKey(chapter.url)
        val targetIndex = records.indexOfFirst { chapterKey(it.url) == targetKey }
        if (targetIndex < 0) return null
        val target = records[targetIndex]
        val targetFile = File(directory, target.fileName)
        if (!target.downloaded || !isChapterFullyDownloaded(directory, targetFile)) {
            val storedComponents = detail.content.mapNotNull { component ->
                when (component) {
                    is TextComponent -> DownloadedComponent(
                        type = TEXT_COMPONENT,
                        value = component.plainText()
                    )
                    is ImageComponent -> {
                        val image = downloadedImages[component.url]
                            ?: findExistingDownloadedImage(directory, component.url, detail.sourceUrl ?: normalizedNovelUrl)
                        DownloadedComponent(
                            type = IMAGE_COMPONENT,
                            value = component.url
                        ).withDownloadedImage(image)
                    }
                    else -> null
                }
            }
            val storedChapter = DownloadedChapterContent(
                name = detail.name.ifBlank { chapter.name },
                url = chapter.url,
                components = storedComponents,
                contentHtml = detail.contentHtml,
                baseUrl = detail.sourceUrl ?: chapter.url
            )
            writeJson(targetFile, storedChapter, writeGuard)
            records[targetIndex] = target.copy(downloaded = true, requiresPassword = false)
        }
        val complete = if (chapterOrder.isNotEmpty()) {
            records.all { it.downloaded }
        } else {
            // A history/bookmark reader may not have a TOC. Preserve a
            // previously verified full download in that case.
            previous?.complete == true
        }
        val current = manifestFromMetadata(
            previous = previous,
            name = novelName,
            url = normalizedNovelUrl,
            coverUrl = coverUrl,
            records = records,
            downloadedAt = System.currentTimeMillis(),
            complete = complete
        )
        writeManifest(directory, current, writeGuard)
        return current
    }

    /**
     * Downloads missing chapters and resumes an interrupted download when possible.
     * Existing chapter files are retained, while a refreshed table of contents can add chapters.
     */
    suspend fun download(
        authorization: Authorization,
        novel: DetailedNovel,
        baseUrl: String? = null,
        concurrency: Int = DEFAULT_DOWNLOAD_CONCURRENCY,
        selectedChapterUrls: Set<String>? = null,
        onProgress: (DownloadProgress) -> Unit = {}
    ): DownloadedNovelManifest {
        val orderedChapters = novel.chapterList.orderedChapters
            .filter { !it.isExternal }
            .distinctBy { chapterKey(it.url) }
        require(orderedChapters.isNotEmpty()) { "This novel has no downloadable chapters" }
        val selectedKeys = selectionKeys(orderedChapters.map { it.url }, selectedChapterUrls)

        val directory = directoryFor(novel.url, create = true)
            ?: error("Novel download storage is unavailable")
        requireFreeSpace(directory)
        val writeGuard = newWriteGuard(directory)
        val previousManifest = synchronized(ioLock) { readManifest(directory) }
        val previousCommonPassword = previousManifest?.commonPassword?.takeIf(String::isNotBlank)
        val previousByUrl = previousManifest?.chapters
            .orEmpty()
            .associateBy { chapterKey(it.url) }

        val records = orderedChapters.mapIndexed { index, chapter ->
            val previous = previousByUrl[chapterKey(chapter.url)]
            val fileName = previous?.fileName ?: chapterFileName(chapter.url)
            val chapterFile = File(directory, fileName)
            val isDownloaded = isChapterFullyDownloaded(directory, chapterFile)
            DownloadedChapterRecord(
                index = index,
                name = chapter.name,
                url = chapter.url,
                fileName = fileName,
                // Chapter files are atomically renamed. A process stopped between
                // checkpoints can safely recover a completed file on the next run.
                downloaded = isDownloaded,
                requiresPassword = if (isDownloaded) false else (previous?.requiresPassword == true)
            )
        }

        val currentRecords = records.toMutableList()
        var currentManifest = manifestFrom(
            novel = novel,
            records = currentRecords.toList(),
            downloadedAt = previousManifest?.downloadedAt ?: 0L,
            complete = currentRecords.all { it.downloaded },
            commonPassword = previousCommonPassword
        )
        synchronized(ioLock) { writeManifest(directory, currentManifest, writeGuard) }

        val targetRecords = currentRecords.filter { selectedKeys == null || chapterKey(it.url) in selectedKeys }
        val totalCount = targetRecords.size
        val completedCounter = AtomicInteger(targetRecords.count { it.downloaded })
        val lastProgressTime = AtomicLong(0L)

        fun reportProgress(chapterName: String, force: Boolean = false) {
            val count = completedCounter.get()
            val now = System.currentTimeMillis()
            val prev = lastProgressTime.get()
            if (force || count == totalCount || now - prev >= PROGRESS_THROTTLE_MS) {
                lastProgressTime.set(now)
                onProgress(DownloadProgress(count, totalCount, chapterName))
            }
        }

        reportProgress("", force = true)

        val pendingChapters = targetRecords.filter { !it.downloaded }
        if (pendingChapters.isNotEmpty()) {
            val wenkuBlocked = java.util.concurrent.atomic.AtomicBoolean(false)
            val semaphore = Semaphore(concurrency.coerceAtLeast(1))
            val wenkuSemaphore = Semaphore(minOf(2, concurrency.coerceAtLeast(1)))
            val failedErrors = ConcurrentLinkedQueue<Throwable>()
            val skippedPasswordChapters = ConcurrentLinkedQueue<DownloadedChapterRecord>()

            try {
                supervisorScope {
                    pendingChapters.forEach { record ->
                        launch(Dispatchers.IO) {
                            semaphore.withPermit {
                                currentCoroutineContext().ensureActive()
                                if (wenkuBlocked.get() &&
                                    com.breakyuna.esjzone.novellibrary.novel.resolveChapterSource(record.url) ==
                                    com.breakyuna.esjzone.novellibrary.novel.ChapterSource.WENKU8) return@withPermit
                                reportProgress(record.name)

                                var attempt = 0
                                var success = false
                                var isPasswordProtected = false
                                var lastError: Throwable? = null

                                while (attempt < CHAPTER_MAX_ATTEMPTS && !success) {
                                    currentCoroutineContext().ensureActive()
                                    attempt++
                                    try {
                                        val download: suspend () -> Unit = {
                                            downloadSingleChapter(
                                                authorization = authorization,
                                                record = record,
                                                directory = directory,
                                                baseUrl = baseUrl,
                                                writeGuard = writeGuard
                                            )
                                        }
                                        if (com.breakyuna.esjzone.novellibrary.novel.resolveChapterSource(record.url) ==
                                            com.breakyuna.esjzone.novellibrary.novel.ChapterSource.WENKU8) {
                                            wenkuSemaphore.withPermit { download() }
                                        } else download()
                                        success = true
                                    } catch (ce: CancellationException) {
                                        throw ce
                                    } catch (error: com.breakyuna.esjzone.network.external.CloudflareChallengeRequiredException) {
                                        wenkuBlocked.set(true)
                                        lastError = error
                                        break
                                    } catch (error: ChapterPasswordRequiredException) {
                                        if (!previousCommonPassword.isNullOrBlank()) {
                                            try {
                                                downloadSingleChapter(
                                                    authorization = authorization,
                                                    record = record,
                                                    directory = directory,
                                                    baseUrl = baseUrl,
                                                    writeGuard = writeGuard,
                                                    password = previousCommonPassword
                                                )
                                                success = true
                                                isPasswordProtected = false
                                                break
                                            } catch (ce2: CancellationException) {
                                                throw ce2
                                            } catch (fallbackError: Throwable) {
                                                isPasswordProtected = true
                                                lastError = fallbackError
                                                break
                                            }
                                        } else {
                                            isPasswordProtected = true
                                            lastError = error
                                            break
                                        }
                                    } catch (error: Throwable) {
                                        lastError = error
                                        if (attempt < CHAPTER_MAX_ATTEMPTS) {
                                            delay(500L * attempt)
                                        }
                                    }
                                }

                                if (success) {
                                    val finished = completedCounter.incrementAndGet()
                                    synchronized(ioLock) {
                                        currentRecords[record.index] = record.copy(downloaded = true, requiresPassword = false)
                                        if (finished % MANIFEST_CHECKPOINT_INTERVAL == 0 ||
                                            finished == totalCount) {
                                            currentManifest = manifestFrom(
                                                novel = novel,
                                                records = currentRecords.toList(),
                                                downloadedAt = System.currentTimeMillis(),
                                                complete = currentRecords.all { it.downloaded },
                                                commonPassword = previousCommonPassword
                                            )
                                            writeManifest(directory, currentManifest, writeGuard)
                                        }
                                    }
                                    reportProgress(record.name)
                                } else if (isPasswordProtected) {
                                    AppLogger.i(
                                        "NovelDownloadStore",
                                        "Chapter requires password, skipped for background download: ${record.name} (${record.url})"
                                    )
                                    val updated = record.copy(downloaded = false, requiresPassword = true)
                                    synchronized(ioLock) {
                                        currentRecords[record.index] = updated
                                    }
                                    skippedPasswordChapters.add(updated)
                                } else {
                                    AppLogger.w(
                                        "NovelDownloadStore",
                                        "Chapter download failed after $attempt attempts: ${record.name} (${record.url})",
                                        lastError
                                    )
                                    lastError?.let { failedErrors.add(it) }
                                }
                            }
                        }
                    }
                }
            } finally {
                // Cancellation and partial failure must still publish completed chapters,
                // but only if the directory was not deleted concurrently by the user.
                synchronized(ioLock) {
                    if (directory.isDirectory) {
                        currentManifest = manifestFrom(
                            novel = novel,
                            records = currentRecords.toList(),
                            downloadedAt = System.currentTimeMillis(),
                            complete = currentRecords.all { it.downloaded },
                            commonPassword = previousCommonPassword
                        )
                        writeManifest(directory, currentManifest, writeGuard)
                    }
                }
            }

            if (failedErrors.isNotEmpty()) {
                val firstError = failedErrors.peek()
                throw IllegalStateException(
                    "Failed to download ${failedErrors.size} chapter(s) for novel: ${novel.name}",
                    firstError
                )
            }
        }

        reportProgress("", force = true)

        return currentManifest
    }

    private suspend fun downloadSingleChapter(
        authorization: Authorization,
        record: DownloadedChapterRecord,
        directory: File,
        baseUrl: String?,
        writeGuard: DownloadWriteGuard,
        password: String? = null
    ): DownloadedChapterContent {
        val detail = if (!password.isNullOrBlank()) {
            EsjzoneClient.unlockPasswordProtectedChapter(
                authorization = authorization,
                chapter = Chapter(record.name, record.url, false),
                password = password,
                baseUrl = baseUrl
            )
        } else {
            EsjzoneClient.getChapterDetail(
                authorization = authorization,
                chapter = Chapter(record.name, record.url, false),
                preferDownloaded = false,
                forceRefresh = false,
                baseUrl = baseUrl,
                allowAutoSolve = false
            )
        }
        val storedChapter = DownloadedChapterContent(
            name = detail.name.ifBlank { record.name },
            url = record.url,
            components = detail.content.mapNotNull { component ->
                when (component) {
                    is TextComponent -> DownloadedComponent(
                        type = TEXT_COMPONENT,
                        value = component.plainText()
                    )

                    is ImageComponent -> {
                        val image = downloadImage(
                        authorization, directory, component.url, detail.sourceUrl ?: baseUrl, writeGuard
                        )
                        DownloadedComponent(
                            type = IMAGE_COMPONENT,
                            value = component.url
                        ).withDownloadedImage(image)
                    }

                    else -> null
                }
            },
            contentHtml = detail.contentHtml,
            baseUrl = detail.sourceUrl ?: EsjzoneUrls.resolve(record.url, baseUrl ?: EsjzoneUrls.Base)
        )
        writeJson(File(directory, record.fileName), storedChapter, writeGuard)
        return storedChapter
    }

    /** Returns a downloaded chapter without touching the network. */
    fun readChapter(chapterUrl: String): DetailedChapter? = synchronized(ioLock) {
        val match = findChapter(chapterUrl) ?: return@synchronized null
        val chapterFile = resolveLocalFile(match.directory, match.record.fileName)
            ?: return@synchronized null
        val stored = readJson(
            chapterFile,
            DownloadedChapterContent::class.java
        ) ?: return@synchronized null
        if (com.breakyuna.esjzone.novellibrary.novel.resolveChapterSource(chapterUrl) ==
            com.breakyuna.esjzone.novellibrary.novel.ChapterSource.WENKU8 &&
            com.breakyuna.esjzone.network.external.CloudflareChallenge.hasChallengeDocumentMarkers(
                stored.contentHtml.orEmpty())) {
            chapterFile.delete()
            return@synchronized null
        }
        val previous = match.manifest.chapters.getOrNull(match.record.index - 1)
            ?.toChapter()
        val next = match.manifest.chapters.getOrNull(match.record.index + 1)
            ?.toChapter()

        DetailedChapter(
            name = stored.name,
            content = restoreComponents(match.directory, stored),
            previous = previous,
            next = next,
            contentHtml = stored.contentHtml,
            sourceUrl = stored.baseUrl ?: stored.url
        )
    }

    /** Reconstructs enough detail data to open a fully downloaded novel while offline. */
    fun readDetailedNovel(novelUrl: String): DetailedNovel? = synchronized(ioLock) {
        val stored = readManifest(directoryFor(novelUrl, create = false))
            ?.takeIf { it.complete }
            ?: return@synchronized null
        val chapters = stored.chapters
            .filter { it.downloaded }
            .map { ChapterItem(it.toChapter()) }

        DetailedNovel(
            name = stored.name,
            url = stored.url,
            coverUrl = stored.coverUrl,
            views = stored.views,
            likes = stored.likes,
            words = stored.words,
            type = stored.type,
            author = stored.author,
            forumUrl = stored.forumUrl,
            tags = stored.tags,
            isAdult = stored.isAdult,
            isFavorite = false,
            description = NovelDescription(
                stored.description.takeIf { it.isNotBlank() }
                    ?.let { listOf(TextComponent(it)) }
                    ?: emptyList()
            ),
            chapterList = NovelChapterList(chapters),
            sourceUrl = stored.sourceUrl,
            updatedAt = stored.updatedAt
        )
    }

    fun chapterContent(
        novelUrl: String,
        record: DownloadedChapterRecord
    ): DownloadedChapterContent? = synchronized(ioLock) {
        val directory = directoryFor(novelUrl, create = false) ?: return@synchronized null
        val file = resolveLocalFile(directory, record.fileName) ?: return@synchronized null
        readJson(file, DownloadedChapterContent::class.java)
    }

    fun imageFile(novelUrl: String, component: DownloadedComponent): File? = synchronized(ioLock) {
        val relative = component.localFile ?: return@synchronized null
        val directory = directoryFor(novelUrl, create = false) ?: return@synchronized null
        resolveLocalFile(directory, relative)?.takeIf(File::isFile)
    }

    private fun manifestFrom(
        novel: DetailedNovel,
        records: List<DownloadedChapterRecord>,
        downloadedAt: Long,
        complete: Boolean,
        commonPassword: String? = null
    ) = DownloadedNovelManifest(
        name = novel.name,
        url = novel.url,
        coverUrl = novel.coverUrl,
        views = novel.views,
        likes = novel.likes,
        words = novel.words,
        type = novel.type,
        author = novel.author,
        forumUrl = novel.forumUrl,
        tags = novel.tags,
        isAdult = novel.isAdult,
        description = novel.description.components.joinToString("\n") { component ->
            when (component) {
                is TextComponent -> component.plainText()
                is ImageComponent -> component.url
                else -> ""
            }
        }.trim(),
        sourceUrl = novel.sourceUrl,
        updatedAt = novel.updatedAt,
        chapters = records,
        downloadedAt = downloadedAt,
        complete = complete,
        commonPassword = commonPassword
    )

    private fun manifestFromMetadata(
        previous: DownloadedNovelManifest?,
        name: String,
        url: String,
        coverUrl: String,
        records: List<DownloadedChapterRecord>,
        downloadedAt: Long,
        complete: Boolean,
        commonPassword: String? = previous?.commonPassword
    ) = DownloadedNovelManifest(
        version = previous?.version ?: 1,
        name = name.trim().ifBlank { previous?.name.orEmpty() },
        url = url,
        coverUrl = coverUrl.trim().ifBlank { previous?.coverUrl.orEmpty() },
        views = previous?.views ?: 0,
        likes = previous?.likes ?: 0,
        words = previous?.words ?: 0,
        type = previous?.type.orEmpty(),
        author = previous?.author.orEmpty(),
        forumUrl = previous?.forumUrl.orEmpty(),
        tags = previous?.tags.orEmpty(),
        isAdult = previous?.isAdult ?: false,
        description = previous?.description.orEmpty(),
        sourceUrl = previous?.sourceUrl,
        updatedAt = previous?.updatedAt,
        chapters = records,
        downloadedAt = downloadedAt,
        complete = complete,
        commonPassword = commonPassword
    )

    private fun findChapter(chapterUrl: String): ChapterMatch? {
        val target = chapterKey(chapterUrl)
        chapterIndex[target]?.let { return it }
        rootDirectory?.listFiles()
            ?.asSequence()
            ?.filter(File::isDirectory)
            ?.forEach { directory ->
                val manifest = readManifest(directory) ?: return@forEach
                manifest.chapters.filter { it.downloaded }.forEach { record ->
                    chapterIndex.putIfAbsent(
                        chapterKey(record.url), ChapterMatch(directory, manifest, record)
                    )
                }
            }
        return chapterIndex[target]
    }

    private fun restoreComponents(
        novelDirectory: File,
        stored: DownloadedChapterContent
    ): List<Component> {
        val html = stored.contentHtml
        if (!html.isNullOrBlank()) {
            val baseUrl = stored.baseUrl ?: EsjzoneUrls.resolve(stored.url)
            val document = Jsoup.parseBodyFragment(html, baseUrl)
            val downloadedImages = stored.components
                .filter { it.type == IMAGE_COMPONENT }
            document.select("img").forEach { image ->
                val candidates = IMAGE_URL_ATTRIBUTES.mapNotNull { attribute ->
                    image.absUrl(attribute)
                        .ifBlank { image.attr(attribute) }
                        .trim()
                        .takeIf(String::isNotBlank)
                }
                val saved = downloadedImages.firstOrNull { component ->
                    candidates.any { candidate ->
                        component.value == candidate ||
                            EsjzoneUrls.resolve(candidate, baseUrl) == component.value ||
                            EsjzoneUrls.canonicalPageKey(candidate) == EsjzoneUrls.canonicalPageKey(component.value)
                    }
                }
                val localImage = saved?.localFile
                    ?.let { relative -> resolveLocalFile(novelDirectory, relative) }
                    ?.takeIf { it.isFile && it.length() > 0L }
                if (localImage != null) {
                    IMAGE_URL_ATTRIBUTES.forEach(image::removeAttr)
                    image.removeAttr("srcset")
                    image.attr("src", localImage.toURI().toString())
                } else {
                    val fallbackUrl = saved?.value?.takeIf { it.isNotBlank() }
                        ?: candidates.firstOrNull()?.let { EsjzoneUrls.resolve(it, baseUrl) }
                    if (!fallbackUrl.isNullOrBlank()) {
                        IMAGE_URL_ATTRIBUTES.forEach(image::removeAttr)
                        image.removeAttr("srcset")
                        image.attr("src", fallbackUrl)
                    } else {
                        image.after("<span></span>")
                        image.nextElementSibling()?.text(missingImageLabel())
                        image.remove()
                    }
                }
            }
            return analyseComponents(document.body())
        }

        return stored.components.map { component ->
            if (component.type == IMAGE_COMPONENT) {
                val localImage = component.localFile
                    ?.let { relative -> resolveLocalFile(novelDirectory, relative) }
                    ?.takeIf { it.isFile && it.length() > 0L }
                if (localImage != null) ImageComponent(localImage.toURI().toString())
                else if (component.value.isNotBlank()) ImageComponent(component.value)
                else TextComponent(missingImageLabel())
            } else {
                TextComponent(component.value)
            }
        }
    }

    private fun directoryFor(novelUrl: String, create: Boolean): File? {
        val root = rootDirectory ?: return null
        val directory = File(root, digest(canonicalKey(novelUrl)))
        if (directory.isDirectory) return directory
        return if (create && directory.mkdirs()) directory else null
    }

    private fun readManifest(directory: File?): DownloadedNovelManifest? {
        if (directory == null) return null
        return readJson(File(directory, MANIFEST_FILE), DownloadedNovelManifest::class.java)
    }

    private fun writeManifest(
        directory: File,
        manifest: DownloadedNovelManifest,
        writeGuard: DownloadWriteGuard? = null
    ) {
        val previousInventory = inventorySnapshot
        writeJson(File(directory, MANIFEST_FILE), manifest, writeGuard)
        chapterIndex.clear()
        if (previousInventory != null) {
            val count = manifest.chapters.count { record ->
                record.downloaded && resolveLocalFile(directory, record.fileName)?.isFile == true
            }
            val otherNovels = previousInventory.filterNot {
                canonicalKey(it.novelUrl) == canonicalKey(manifest.url)
            }
            inventorySnapshot = if (count == 0) otherNovels else {
                (otherNovels + DownloadedNovelSummary(
                    manifest = manifest,
                    downloadedChapterCount = count,
                    storageBytes = directory.walkTopDown().filter(File::isFile).sumOf(File::length)
                )).sortedByDescending { it.manifest.downloadedAt }
            }
        }
    }

    private fun findCoilCachedImage(imageUrl: String, baseUrl: String?): File? {
        val trimmed = imageUrl.trim()
        if (trimmed.isBlank()) return null
        val diskCache = runCatching { PresentationAccess.imageLoader.diskCache }.getOrNull() ?: return null

        val parsed = trimmed.toHttpUrlOrNull()
        val isEsj = parsed != null && EsjzoneUrls.isEsjHost(parsed.host)

        val candidateKeys = buildList {
            add(trimmed)
            runCatching { EsjzoneUrls.resolve(trimmed, baseUrl ?: EsjzoneUrls.Base) }
                .getOrNull()?.takeIf(String::isNotBlank)?.let(::add)
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

    private fun findExistingDownloadedImage(
        novelDirectory: File,
        rawUrl: String,
        baseUrl: String?
    ): DownloadedImage? {
        if (rawUrl.isBlank()) return null
        val url = EsjzoneUrls.resolve(rawUrl, baseUrl ?: EsjzoneUrls.Base)
        val imagePrefix = "image-${digest(url)}."
        val imagesDirectory = File(novelDirectory, "images")
        val existing = imagesDirectory.listFiles()
            ?.firstOrNull { it.isFile && it.length() > 0L && it.name.startsWith(imagePrefix) }
            ?: return null
        return DownloadedImage(
            relativeName = "images/${existing.name}",
            mediaType = mediaTypeFromUrl(existing.name)
        )
    }

    private fun downloadImage(
        authorization: Authorization,
        novelDirectory: File,
        rawUrl: String,
        baseUrl: String?,
        writeGuard: DownloadWriteGuard? = null
    ): DownloadedImage? {
        if (rawUrl.isBlank()) return null
        return runCatching {
            val url = EsjzoneUrls.resolve(rawUrl, baseUrl ?: EsjzoneUrls.Base)
            val imagePrefix = "image-${digest(url)}."
            val imagesDirectory = File(novelDirectory, "images")
            imagesDirectory.listFiles()
                ?.firstOrNull { it.isFile && it.length() > 0L && it.name.startsWith(imagePrefix) }
                ?.let { existing ->
                    return@runCatching DownloadedImage(
                        relativeName = "images/${existing.name}",
                        mediaType = mediaTypeFromUrl(existing.name)
                    )
                }

            ensureWriteAllowed(writeGuard)
            if (!imagesDirectory.isDirectory && !imagesDirectory.mkdirs()) {
                error("Unable to create the chapter image directory")
            }

            // 1. Try to reuse cached image from Coil disk cache first (Fast, zero network)
            val cachedFile = findCoilCachedImage(rawUrl, baseUrl)
            if (cachedFile != null && cachedFile.isFile && cachedFile.length() > 0L) {
                val mediaType = mediaTypeFromUrl(url)
                val extension = extensionFor(mediaType, url)
                val relativeName = "images/$imagePrefix$extension"
                val destination = File(novelDirectory, relativeName)
                if (!destination.isFile || destination.length() == 0L) {
                    val temporary = File.createTempFile("coil_${destination.nameWithoutExtension}_", ".tmp", imagesDirectory)
                    try {
                        Files.copy(cachedFile.toPath(), temporary.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        synchronized(ioLock) {
                            ensureWriteAllowed(writeGuard)
                            moveReplacing(temporary, destination)
                        }
                    } finally {
                        if (temporary.isFile) temporary.delete()
                    }
                }
                return@runCatching DownloadedImage(relativeName, mediaType)
            }

            // 2. Fall back to network download if not found in Coil disk cache
            val client = if (com.breakyuna.esjzone.novellibrary.novel.resolveChapterSource(baseUrl.orEmpty()) ==
                com.breakyuna.esjzone.novellibrary.novel.ChapterSource.WENKU8) {
                EsjzoneClient.wenkuImageClient()
            } else EsjzoneClient.downloadClient(authorization)
            val host = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()
            val refererCandidates = listOfNotNull(
                baseUrl?.takeIf(String::isNotBlank),
                if (host.isNotBlank()) "https://$host/" else null,
                null
            ).distinct()

            var lastError: Throwable? = null
            for (referer in refererCandidates) {
                try {
                    val requestBuilder = Request.Builder()
                        .url(url)
                        .headers(EsjzoneClient.headers)
                    if (referer != null) {
                        requestBuilder.header("Referer", referer)
                    }
                    val response = client.newCall(requestBuilder.build()).execute()
                    response.use {
                        if (!it.isSuccessful) error("Image request failed with HTTP ${it.code}")
                        val body = it.body ?: error("Image response is empty")
                        val contentLength = body.contentLength()
                        if (contentLength > MAX_IMAGE_BYTES) error("Chapter image is too large")

                        val mediaType = body.contentType()?.toString()?.substringBefore(';')
                            ?.trim()
                            ?.takeIf { value -> value.startsWith("image/") }
                            ?: mediaTypeFromUrl(url)
                        val extension = extensionFor(mediaType, url)
                        ensureWriteAllowed(writeGuard)
                        val relativeName = "images/$imagePrefix$extension"
                        val destination = File(novelDirectory, relativeName)
                        if (!destination.isFile || destination.length() == 0L) {
                            val temporary = File.createTempFile("dl_${destination.nameWithoutExtension}_", ".tmp", imagesDirectory)
                            try {
                                body.byteStream().use { input ->
                                    temporary.outputStream().buffered().use { output ->
                                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                        var total = 0L
                                        while (true) {
                                            val count = input.read(buffer)
                                            if (count < 0) break
                                            total += count
                                            if (total > MAX_IMAGE_BYTES) {
                                                error("Chapter image is too large")
                                            }
                                            output.write(buffer, 0, count)
                                        }
                                    }
                                }
                                synchronized(ioLock) {
                                    ensureWriteAllowed(writeGuard)
                                    moveReplacing(temporary, destination)
                                }
                            } finally {
                                if (temporary.isFile) temporary.delete()
                            }
                        }
                        return@runCatching DownloadedImage(relativeName, mediaType)
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }
            if (lastError != null) throw lastError
            error("Unable to download image from $url")
        }.onFailure { error ->
            if (error is CancellationException) throw error
            AppLogger.w("NovelDownloadStore", "Unable to download a chapter image", error)
        }.getOrNull()
    }

    private fun isChapterFullyDownloaded(directory: File, chapterFile: File): Boolean {
        if (!chapterFile.isFile || chapterFile.length() == 0L) return false
        val content = readJson(chapterFile, DownloadedChapterContent::class.java) ?: return false
        if (isPasswordProtectedChapterHtml(content.contentHtml.orEmpty())) return false
        return content.hasAllImagesOnDisk(directory)
    }

    private fun DownloadedChapterContent.hasAllImagesOnDisk(directory: File): Boolean =
        components.filter { it.type == IMAGE_COMPONENT }.all { image ->
            val relative = image.localFile ?: return@all false
            resolveLocalFile(directory, relative)?.let { it.isFile && it.length() > 0L } == true
        }

    private fun writeJson(file: File, value: Any, writeGuard: DownloadWriteGuard? = null) {
        val parent = file.parentFile ?: return
        synchronized(ioLock) {
            ensureWriteAllowed(writeGuard)
            if (!parent.exists()) parent.mkdirs()
        }
        val prefix = (file.nameWithoutExtension.take(16).ifBlank { "temp" } + "_").takeLast(20).padStart(3, '_')
        val temporary = File.createTempFile(prefix, ".tmp", parent)
        try {
            temporary.writeText(gson.toJson(value), StandardCharsets.UTF_8)
            synchronized(ioLock) {
                ensureWriteAllowed(writeGuard)
                moveReplacing(temporary, file)
            }
        } finally {
            if (temporary.exists()) {
                temporary.delete()
            }
        }
    }

    private fun moveReplacing(temporary: File, destination: File) {
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: Exception) {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun <T> readJson(file: File, type: Class<T>): T? {
        if (!file.isFile) return null
        return runCatching {
            file.bufferedReader(StandardCharsets.UTF_8).use { gson.fromJson(it, type) }
        }.onFailure { error ->
            AppLogger.w("NovelDownloadStore", "Unable to read ${file.name}", error)
        }.getOrNull()
    }

    private fun chapterFileName(url: String): String = "chapter-${digest(chapterKey(url))}.json"

    private fun resolveLocalFile(directory: File, relative: String): File? {
        return runCatching {
            val candidate = File(directory, relative).canonicalFile
            val parent = directory.canonicalFile
            candidate.takeIf {
                it.path == parent.path || it.path.startsWith(parent.path + File.separator)
            }
        }.getOrNull()
    }

    private fun extensionFor(mediaType: String, url: String): String = when (mediaType) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/svg+xml" -> "svg"
        else -> url.substringBefore('?').substringAfterLast('.', "img")
            .lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{1,5}")) }
            ?: "img"
    }

    private fun mediaTypeFromUrl(url: String): String = when (
        url.substringBefore('?').substringAfterLast('.', "").lowercase()
    ) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        else -> "application/octet-stream"
    }

    private fun canonicalKey(url: String): String =
        EsjzoneUrls.canonicalPageKey(url).ifBlank { url.trim() }

    /** Host aliases are equivalent, but a fragment can identify a distinct TOC entry. */
    internal fun chapterKey(url: String): String {
        val resolved = EsjzoneUrls.resolve(url).trim()
        val fragment = resolved.substringAfter('#', "").trim()
        val page = canonicalKey(resolved)
        return if (fragment.isBlank()) page else "$page#$fragment"
    }

    internal fun selectionKeys(availableUrls: List<String>, selectedUrls: Set<String>?): Set<String>? {
        val selected = selectedUrls?.map(::chapterKey)?.toSet() ?: return null
        val available = availableUrls.map(::chapterKey).toSet()
        require(selected.isNotEmpty() && selected.all { it in available }) {
            "Selected chapters are no longer available in the novel table of contents"
        }
        return selected
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun DetailedChapter.toStoredContent(chapter: Chapter) = DownloadedChapterContent(
        name = name.ifBlank { chapter.name },
        url = chapter.url,
        components = content.mapNotNull { component ->
            when (component) {
                is TextComponent -> DownloadedComponent(
                    type = TEXT_COMPONENT,
                    value = component.plainText()
                )

                is ImageComponent -> DownloadedComponent(
                    type = IMAGE_COMPONENT,
                    value = component.url
                )

                else -> null
            }
        },
        contentHtml = contentHtml,
        baseUrl = sourceUrl ?: chapter.url
    )

    private fun DownloadedChapterRecord.toChapter() = Chapter(name, url, false)

    private fun TextComponent.plainText(): String = buildString {
        append(text)
        getExtras().forEach { append(it.plainText()) }
    }

    private fun DownloadedComponent.withDownloadedImage(image: DownloadedImage?) = copy(
        localFile = image?.relativeName,
        mediaType = image?.mediaType
    )

    private data class DownloadedImage(
        val relativeName: String,
        val mediaType: String
    )

    private data class ChapterMatch(
        val directory: File,
        val manifest: DownloadedNovelManifest,
        val record: DownloadedChapterRecord
    )

    private const val MAX_IMAGE_BYTES = 32L * 1024L * 1024L
    private val IMAGE_URL_ATTRIBUTES = listOf(
        "data-src",
        "data-original",
        "data-lazy-src",
        "data-actualsrc",
        "data-url",
        "data-origin",
        "data-file",
        "src"
    )
}

package com.breakyuna.esjzone.offline

import com.breakyuna.esjzone.data.reader.ChapterBody
import com.breakyuna.esjzone.data.reader.withStructuredBody
import com.breakyuna.esjzone.domain.reader.sourceText
import android.content.Context
import com.breakyuna.esjzone.R
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
import com.breakyuna.esjzone.util.storedTextReader
import com.breakyuna.esjzone.util.writeCompressedText
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
    val version: Int = 2,
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
    val commonPassword: String? = null,
    val catalogComplete: Boolean = false
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
    val requiresPassword: Boolean = false,
    val textLength: Int? = null,
    val bodyAvailable: Boolean = false,
    val localOnly: Boolean = false
)

data class DownloadedChapterContent(
    val name: String,
    val url: String,
    val components: List<DownloadedComponent>,
    val contentHtml: String? = null,
    val baseUrl: String? = null,
    val schemaVersion: Int = 1,
    val body: ChapterBody? = null
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
    private val mutableChanges = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val changes: kotlinx.coroutines.flow.StateFlow<Long> = mutableChanges


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
    /** Every successful commit publishes the latest manifest under ioLock. Keys are directory paths. */
    private val manifestCache = HashMap<String, DownloadedNovelManifest?>()
    private val deletionGenerations = HashMap<String, Long>()
    /** Built once per store initialization, then updated only for the changed novel. */
    private val chapterIndex = HashMap<String, ChapterMatch>()
    private val chapterKeysByDirectory = HashMap<String, Set<String>>()
    private var chapterIndexLoaded = false
    private val dirtyInventorySizes = HashSet<String>()
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
        val available = runCatching { directory.usableSpace }.getOrDefault(Long.MAX_VALUE)
        if (available < MIN_FREE_SPACE_BYTES) throw IOException("Insufficient storage space for download")
    }

    @Volatile
    private var rootDirectory: File? = null
    @Volatile
    private var appContext: Context? = null

    private fun missingImageLabel(): String = appContext?.getString(R.string.download_image_unavailable)
        ?: "[Image unavailable]"

    fun initialize(context: Context) = synchronized(ioLock) {
        appContext = context.applicationContext
        val directory = File(context.applicationContext.filesDir, "downloaded_novels")
        if (rootDirectory?.absolutePath != directory.absolutePath) initializeDirectory(directory)
    }

    internal fun initializeDirectory(directory: File) = synchronized(ioLock) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Unable to create the novel download directory")
        chapterIndex.clear()
        chapterKeysByDirectory.clear()
        chapterIndexLoaded = false
        inventorySnapshot = null
        dirtyInventorySizes.clear()
        manifestCache.clear()
        rootDirectory = directory
        mutableChanges.value += 1
    }

    /** Publish each independent snapshot outside ioLock so callers can ZIP and discard it immediately. */
    fun exportBackup(destination: File, onNovelSnapshot: (File) -> Unit = {}) {
        check(destination.isDirectory || destination.mkdirs())
        val sources = synchronized(ioLock) { rootDirectory?.listFiles().orEmpty().filter(File::isDirectory) }
        sources.forEach { source ->
            val target = File(destination, source.name)
            // Release the store between novels; ZIP compression runs entirely outside this lock.
            val prepared = synchronized(ioLock) {
                if (!source.isDirectory) return@synchronized false
                val manifest = readManifest(source) ?: return@synchronized false
                check(target.isDirectory || target.mkdirs())
                source.walkTopDown().filter { it.isFile && it.name != MANIFEST_FILE && !it.name.endsWith(".tmp") }
                    .forEach { file ->
                        val output = File(target, file.relativeTo(source).path)
                        output.parentFile?.mkdirs()
                        file.copyTo(output, overwrite = true)
                    }
                // The manifest is an independent, sanitized snapshot, never a link to local passwords.
                File(target, MANIFEST_FILE).bufferedWriter(StandardCharsets.UTF_8).use {
                    gson.toJson(manifest.copy(version = 2, commonPassword = null), it)
                }
                true
            }
            if (prepared) onNovelSnapshot(target)
        }
    }

    /** Keep existing novels intact, including any active download writes. */
    fun importBackup(source: File) = synchronized(ioLock) {
        val root = checkNotNull(rootDirectory)
        source.listFiles().orEmpty().filter(File::isDirectory).forEach { directory ->
            val manifest = requireNotNull(readManifest(directory))
            require(manifest.version in 1..2 && manifest.url.isNotBlank())
            manifest.chapters.forEach { record ->
                val file = requireNotNull(resolveLocalFile(directory, record.fileName))
                require(!(record.downloaded || record.bodyAvailable) || file.isFile)
                if (file.isFile) {
                    val chapter = requireNotNull(readJson(file, DownloadedChapterContent::class.java))
                    require(chapter.schemaVersion in 0..2)
                    if (chapter.schemaVersion == 2) requireNotNull(chapter.body).validate()
                }
            }
            val target = File(root, digest(canonicalKey(manifest.url)))
            if (!target.exists()) {
                val staging = File.createTempFile("restore-", ".tmp", root)
                check(staging.delete() && staging.mkdir())
                try {
                    directory.copyRecursively(staging, overwrite = true)
                    File(staging, MANIFEST_FILE).writeText(gson.toJson(manifest.copy(version = 2, commonPassword = null)))
                    check(staging.renameTo(target))
                } finally {
                    staging.deleteRecursively()
                }
            }
        }
        chapterIndex.clear()
        chapterKeysByDirectory.clear()
        chapterIndexLoaded = false
        inventorySnapshot = null
        manifestCache.clear()
        mutableChanges.value += 1
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
        inventorySnapshot?.let { cached ->
            if (dirtyInventorySizes.isEmpty()) return@synchronized cached
            val refreshed = cached.map { summary ->
                val key = canonicalKey(summary.novelUrl)
                if (key !in dirtyInventorySizes) summary else {
                    val directory = directoryFor(summary.novelUrl, create = false)
                    summary.copy(storageBytes = directory?.walkTopDown()
                        ?.filter(File::isFile)?.sumOf(File::length) ?: 0L)
                }
            }
            dirtyInventorySizes.clear()
            inventorySnapshot = refreshed
            return@synchronized refreshed
        }
        val snapshot = rootDirectory?.listFiles()
            .orEmpty()
            .asSequence()
            .filter(File::isDirectory)
            .mapNotNull { directory ->
                val stored = readManifest(directory) ?: return@mapNotNull null
                val count = stored.chapters.count { record ->
                    (record.bodyAvailable || record.downloaded) && resolveLocalFile(directory, record.fileName)?.isFile == true
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
        dirtyInventorySizes.clear()
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
        manifestCache.remove(directory.absolutePath)
        removeChapterIndex(directory)
        inventorySnapshot = null
        mutableChanges.value += 1
        directory.deleteRecursively()
    }

    /** Deletes several novel download directories and returns the number removed. */
    fun deleteAll(novelUrls: Iterable<String>): Int = synchronized(ioLock) {
        inventorySnapshot = null
        mutableChanges.value += 1
        novelUrls.distinct()
            .count { url ->
                cancelActiveWork(url)
                val directory = directoryFor(url, create = false) ?: return@count false
                deletionGenerations[directory.absolutePath] = (deletionGenerations[directory.absolutePath] ?: 0L) + 1L
                manifestCache.remove(directory.absolutePath)
                removeChapterIndex(directory)
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
            writeManifest(directory, updated, writeGuard, updatePassword = true)
            inventorySnapshot = null
            mutableChanges.value += 1
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
            writeManifest(directory, updated, writeGuard, updatePassword = true)
            inventorySnapshot = null
            mutableChanges.value += 1
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
            writeManifest(directory, withPw, writeGuard, updatePassword = true)
            inventorySnapshot = null
            mutableChanges.value += 1
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
        val savedContent = synchronized(ioLock) { existingFile?.let { readStoredChapter(directory, it) } }
        val sourceDetail = savedContent?.let { saved -> DetailedChapter(saved.name,
            requireNotNull(saved.body).components(), detail.previous, detail.next, saved.contentHtml,
            saved.baseUrl, saved.body) } ?: detail.withStructuredBody()
        val imageComponents = sourceDetail.content.filterIsInstance<ImageComponent>()
        val downloadedImages = imageComponents
            .distinctBy { it.url }
            .mapNotNull { component ->
                ensureWriteAllowed(writeGuard)
                val image = downloadImage(
                    authorization = auth,
                    novelDirectory = directory,
                    rawUrl = component.url,
                    baseUrl = sourceDetail.sourceUrl ?: normalizedNovelUrl,
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
                detail = sourceDetail,
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
                downloaded = old?.downloaded == true,
                requiresPassword = old?.requiresPassword == true,
                textLength = old?.textLength,
                bodyAvailable = old?.bodyAvailable == true,
                localOnly = old?.localOnly == true
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
        var savedContent: DownloadedChapterContent? = null
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
                baseUrl = detail.sourceUrl ?: chapter.url,
                schemaVersion = 2,
                body = detail.withStructuredBody().body
            )
            val oldContent = readStoredChapter(directory, targetFile)
            val committed = oldContent?.let { mergeChapterAssets(directory, it, storedChapter) } ?: storedChapter
            writeJson(targetFile, committed, writeGuard)
            savedContent = committed
            records[targetIndex] = target.copy(downloaded = committed.hasAllImagesOnDisk(directory),
                requiresPassword = false, bodyAvailable = true, textLength = committed.body?.textLength)
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
        return writeManifest(directory, current, writeGuard,
            changedContents = savedContent?.let { mapOf(targetKey to it) }.orEmpty())
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
        synchronized(ioLock) { writeManifest(directory, currentManifest, writeGuard, replaceCatalog = true) }

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

        return synchronized(ioLock) { readManifest(directory) } ?: currentManifest
    }

    private suspend fun downloadSingleChapter(
        authorization: Authorization,
        record: DownloadedChapterRecord,
        directory: File,
        baseUrl: String?,
        writeGuard: DownloadWriteGuard,
        password: String? = null
    ): DownloadedChapterContent {
        val chapterFile = File(directory, record.fileName)
        val existing = synchronized(ioLock) { readStoredChapter(directory, chapterFile) }
        val chapter = Chapter(record.name, record.url, false)
        val stored = existing ?: (if (!password.isNullOrBlank()) {
            EsjzoneClient.unlockPasswordProtectedChapter(authorization, chapter, password, baseUrl)
        } else {
            EsjzoneClient.getChapterDetail(authorization, chapter, preferDownloaded = false,
                forceRefresh = false, baseUrl = baseUrl, allowAutoSolve = false)
        }).toStoredContent(chapter)
        require(stored.body?.blocks?.isNotEmpty() == true) { "Chapter content is empty" }
        val completed = stored.copy(components = stored.components.map { component ->
            if (component.type != IMAGE_COMPONENT || component.localFile?.let {
                    resolveLocalFile(directory, it)?.let { file -> file.isFile && file.length() > 0L }
                } == true) component
            else component.withDownloadedImage(downloadImage(authorization, directory, component.value,
                stored.baseUrl ?: baseUrl, writeGuard))
        })
        synchronized(ioLock) {
            ensureWriteAllowed(writeGuard)
            val latest = readStoredChapter(directory, chapterFile)
            val committed = if (latest != null) mergeChapterAssets(directory, latest, completed) else completed
            if (latest != committed) writeJson(chapterFile, committed, writeGuard)
        }
        if (!completed.hasAllImagesOnDisk(directory)) throw IOException("Chapter images are incomplete")
        return completed
    }

    private fun mergeChapterAssets(directory: File, existing: DownloadedChapterContent, incoming: DownloadedChapterContent): DownloadedChapterContent {
        val images = incoming.components.filter { it.type == IMAGE_COMPONENT }.associateBy { it.value }
        return existing.copy(components = existing.components.map { component ->
            if (component.type == IMAGE_COMPONENT && component.localFile?.let { resolveLocalFile(directory, it)?.let { f -> f.isFile && f.length() > 0L } } != true) {
                images[component.value]?.takeIf { it.localFile != null } ?: component
            } else component
        })
    }

    /** Returns a downloaded chapter without touching the network. */
    fun readChapter(chapterUrl: String): DetailedChapter? = synchronized(ioLock) {
        val match = findChapter(chapterUrl) ?: return@synchronized null
        val chapterFile = resolveLocalFile(match.directory, match.record.fileName)
            ?: return@synchronized null
        val stored = readStoredChapter(match.directory, chapterFile) ?: return@synchronized null
        if (match.record.textLength != stored.body?.textLength || !match.record.bodyAvailable) {
            // Optional length metadata must not prevent reading a locally migrated chapter.
            runCatching {
                writeManifest(match.directory, match.manifest.copy(chapters = match.manifest.chapters.map {
                    if (chapterKey(it.url) == chapterKey(chapterUrl)) it.copy(textLength = stored.body?.textLength, bodyAvailable = true) else it
                }))
            }
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
            sourceUrl = stored.baseUrl ?: stored.url,
            body = stored.body,
            imageLocations = imageLocations(match.directory, stored)
        )
    }

    /** Reconstructs enough detail data to open a fully downloaded novel while offline. */
    fun readDetailedNovel(novelUrl: String): DetailedNovel? = synchronized(ioLock) {
        val stored = readManifest(directoryFor(novelUrl, create = false))
            ?.takeIf { it.complete }
            ?: return@synchronized null
        val chapters = stored.chapters
            .filter { it.downloaded && !it.localOnly }
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

    /** Recompute displayed history progress after a catalog gains chapters, without moving its anchor. */
    fun refreshProgress(activity: com.breakyuna.esjzone.database.entity.LocalReadingActivity): com.breakyuna.esjzone.database.entity.LocalReadingActivity {
        val manifest = manifest(activity.novelUrl) ?: return activity
        if (!manifest.catalogComplete) return activity
        val order = manifest.chapters.filterNot { it.localOnly }
        val key = chapterKey(activity.chapterUrl)
        val index = order.indexOfFirst { chapterKey(it.url) == key }
        if (index < 0) return activity
        return activity.copy(chapterIndex = index, totalChapters = order.size,
            bookProgress = manifest.textBookProgress(order.map { it.url }, key, activity.chapterProgress)
                ?: com.breakyuna.esjzone.domain.reader.readerBookProgress(index, order.size, activity.chapterProgress))
    }

    fun chapterContent(
        novelUrl: String,
        record: DownloadedChapterRecord
    ): DownloadedChapterContent? = synchronized(ioLock) {
        val directory = directoryFor(novelUrl, create = false) ?: return@synchronized null
        val file = resolveLocalFile(directory, record.fileName) ?: return@synchronized null
        readStoredChapter(directory, file)
    }

    suspend fun searchChapters(
        novelUrl: String,
        query: String,
        mapping: (com.breakyuna.esjzone.domain.reader.ReaderBlock) -> com.breakyuna.esjzone.domain.reader.ReaderTextOffsets = {
            com.breakyuna.esjzone.domain.reader.ReaderTextOffsets.identity(it.sourceText())
        },
        onResults: suspend (List<com.breakyuna.esjzone.domain.reader.ReaderSearchHit>) -> Unit
    ) = kotlinx.coroutines.withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext
        val records = manifest(novelUrl)?.chapters.orEmpty()
        for (record in records) {
            currentCoroutineContext().ensureActive()
            val content = chapterContent(novelUrl, record) ?: continue
            val body = content.body ?: continue
            val document = com.breakyuna.esjzone.domain.reader.ReaderChapterDocument(
                com.breakyuna.esjzone.domain.reader.ReaderChapterRef(record.name, record.url),
                body.readerBlocks(), contentFingerprint = body.fingerprint)
            val hits = com.breakyuna.esjzone.domain.reader.searchReaderDocument(document, chapterKey(record.url), query, mapping)
            if (hits.isNotEmpty()) onResults(hits)
        }
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
        commonPassword = commonPassword,
        catalogComplete = true
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
        version = 2,
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
        commonPassword = commonPassword,
        catalogComplete = previous?.catalogComplete == true || previous?.complete == true
    )

    private fun findChapter(chapterUrl: String): ChapterMatch? {
        val target = chapterKey(chapterUrl)
        chapterIndex[target]?.let { return it }
        if (chapterIndexLoaded) return null
        rootDirectory?.listFiles()
            ?.asSequence()
            ?.filter(File::isDirectory)
            ?.forEach { directory ->
                val manifest = readManifest(directory) ?: return@forEach
                indexChapterManifest(directory, manifest, recoverUnpublishedFiles = true)
            }
        chapterIndexLoaded = true
        return chapterIndex[target]
    }

    private fun removeChapterIndex(directory: File) {
        chapterKeysByDirectory.remove(directory.absolutePath).orEmpty().forEach { key ->
            if (chapterIndex[key]?.directory == directory) chapterIndex.remove(key)
        }
    }

    private fun indexChapterManifest(
        directory: File,
        manifest: DownloadedNovelManifest,
        recoverUnpublishedFiles: Boolean = false
    ) {
        val knownKeys = chapterKeysByDirectory[directory.absolutePath].orEmpty()
        removeChapterIndex(directory)
        val keys = HashSet<String>()
        manifest.chapters.forEach { record ->
            val key = chapterKey(record.url)
            // After restart, recover files committed just before an interrupted manifest checkpoint.
            if (record.bodyAvailable || record.downloaded || key in knownKeys ||
                (recoverUnpublishedFiles && resolveLocalFile(directory, record.fileName)?.isFile == true)) {
                chapterIndex.putIfAbsent(key, ChapterMatch(directory, manifest, record))
                keys += key
            }
        }
        chapterKeysByDirectory[directory.absolutePath] = keys
    }

    private fun imageLocations(directory: File, stored: DownloadedChapterContent): Map<String, String> =
        stored.components.filter { it.type == IMAGE_COMPONENT }.associate { image ->
            image.value to (image.localFile?.let { resolveLocalFile(directory, it) }
                ?.takeIf { it.isFile && it.length() > 0L }?.toURI()?.toString() ?: image.value)
        }

    private fun readStoredChapter(directory: File, file: File): DownloadedChapterContent? = synchronized(ioLock) {
        val stored = readJson(file, DownloadedChapterContent::class.java) ?: return@synchronized null
        if (stored.schemaVersion == 2) {
            return@synchronized runCatching { requireNotNull(stored.body).validate(); stored }.getOrNull()
        }
        if (stored.schemaVersion !in 0..1) throw IOException("Unsupported downloaded chapter version")
        val converted = runCatching {
            val html = stored.contentHtml
            val components = if (!html.isNullOrBlank()) {
                val document = Jsoup.parseBodyFragment(html, stored.baseUrl ?: stored.url)
                require(document.selectFirst("#oops input#pw, input#pw[name=pw]") == null)
                require(!com.breakyuna.esjzone.network.external.CloudflareChallenge.hasChallengeDocumentMarkers(html))
                document.select("img").filter { image ->
                    IMAGE_URL_ATTRIBUTES.none { image.attr(it).isNotBlank() }
                }.forEach { image ->
                    image.before(org.jsoup.nodes.Element("span").text(missingImageLabel()))
                    image.remove()
                }
                analyseComponents(document.body())
            } else stored.components.map { component ->
                if (component.type == IMAGE_COMPONENT && component.value.isNotBlank()) ImageComponent(component.value)
                else TextComponent(if (component.type == IMAGE_COMPONENT) missingImageLabel() else component.value)
            }
            require(components.isNotEmpty())
            stored.copy(schemaVersion = 2, body = ChapterBody.from(components))
        }.getOrNull() ?: return@synchronized null
        // Reading remains possible if storage is full; the original file survives a failed rename.
        runCatching { writeJson(file, converted, newWriteGuard(directory)) }
        return@synchronized converted
    }

    private fun restoreComponents(directory: File, stored: DownloadedChapterContent): List<Component> {
        val locations = imageLocations(directory, stored)
        return requireNotNull(stored.body).components { locations[it] ?: it }.map { component ->
            if (component is ImageComponent && component.url.isBlank()) TextComponent(missingImageLabel()) else component
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
        val key = directory.absolutePath
        if (manifestCache.containsKey(key)) return manifestCache[key]
        val manifest = readJson(File(directory, MANIFEST_FILE), DownloadedNovelManifest::class.java)?.also {
            if (it.version !in 1..2) throw IOException("Unsupported downloaded novel version")
        }
        manifestCache[key] = manifest
        return manifest
    }

    private fun writeManifest(
        directory: File,
        incoming: DownloadedNovelManifest,
        writeGuard: DownloadWriteGuard? = null,
        replaceCatalog: Boolean = false,
        updatePassword: Boolean = false,
        changedContents: Map<String, DownloadedChapterContent> = emptyMap()
    ): DownloadedNovelManifest {
        ensureWriteAllowed(writeGuard)
        val existing = readManifest(directory)
        val manifest = mergeDownloadManifest(existing, incoming, replaceCatalog).let { rebased ->
            val merged = if (updatePassword) rebased.copy(commonPassword = incoming.commonPassword) else rebased
            val oldRecords = existing?.chapters.orEmpty().associateBy { chapterKey(it.url) }
            val records = merged.chapters.map { record ->
                val key = chapterKey(record.url)
                val previous = oldRecords[key]
                if (!replaceCatalog && key !in changedContents && previous != null &&
                    previous.downloaded == record.downloaded && previous.bodyAvailable == record.bodyAvailable &&
                    previous.textLength == record.textLength && previous.fileName == record.fileName) {
                    return@map record.copy(requiresPassword = if (record.downloaded) false else record.requiresPassword)
                }
                val content = changedContents[key] ?: resolveLocalFile(directory, record.fileName)
                    ?.let { readStoredChapter(directory, it) }
                record.copy(bodyAvailable = content != null, downloaded = content?.hasAllImagesOnDisk(directory) == true,
                    textLength = content?.body?.textLength, requiresPassword = content == null && record.requiresPassword)
            }
            merged.copy(version = 2, chapters = records,
                complete = merged.catalogComplete && records.any { !it.localOnly } && records.filterNot { it.localOnly }.all { it.downloaded })
        }
        val previousInventory = inventorySnapshot
        writeJson(File(directory, MANIFEST_FILE), manifest, writeGuard)
        manifestCache[directory.absolutePath] = manifest
        mutableChanges.value += 1
        if (chapterIndexLoaded) indexChapterManifest(directory, manifest)
        if (previousInventory != null) {
            // Successful chapter writes precede manifest publication. Avoid filesystem
            // checks here; inventory reconstruction still validates persisted files.
            val count = manifest.chapters.count { it.bodyAvailable || it.downloaded }
            val key = canonicalKey(manifest.url)
            dirtyInventorySizes += key
            val otherNovels = previousInventory.filterNot {
                canonicalKey(it.novelUrl) == canonicalKey(manifest.url)
            }
            inventorySnapshot = if (count == 0) otherNovels else {
                (otherNovels + DownloadedNovelSummary(
                    manifest = manifest,
                    downloadedChapterCount = count,
                    // Recompute the exact size only when management UI requests inventory.
                    storageBytes = previousInventory.firstOrNull {
                        canonicalKey(it.novelUrl) == key
                    }?.storageBytes ?: 0L
                )).sortedByDescending { it.manifest.downloadedAt }
            }
        }
        return manifest
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
        val content = readStoredChapter(directory, chapterFile) ?: return false
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
            if (file.name == MANIFEST_FILE) {
                temporary.bufferedWriter(StandardCharsets.UTF_8).use { gson.toJson(value, it) }
            } else {
                temporary.writeCompressedText { gson.toJson(value, it) }
            }
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
            file.storedTextReader().use { gson.fromJson(it, type) }
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
        baseUrl = sourceUrl ?: chapter.url,
        schemaVersion = 2,
        body = withStructuredBody().body
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

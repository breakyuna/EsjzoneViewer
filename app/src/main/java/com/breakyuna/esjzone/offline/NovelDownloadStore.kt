package com.breakyuna.esjzone.offline

import com.breakyuna.esjzone.novellibrary.novel.preferredNovelTitle
import com.breakyuna.esjzone.novellibrary.novel.validNovelTitle

import com.breakyuna.esjzone.data.reader.ChapterBody
import com.breakyuna.esjzone.data.reader.withStructuredBody
import com.breakyuna.esjzone.data.reader.withFormatting
import com.breakyuna.esjzone.domain.reader.sourceText
import android.content.Context
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.data.settings.SettingsDefaults
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.PageCache
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.features.ChapterPasswordRequiredException
import com.breakyuna.esjzone.network.features.ChapterPasswordRejectedException
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
import com.breakyuna.esjzone.util.copyWithCancellation
import com.breakyuna.esjzone.util.AppLogger
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.breakyuna.esjzone.storage.StorageAccessException
import com.breakyuna.esjzone.storage.StorageFile
import com.breakyuna.esjzone.storage.StorageTransferProgress
import com.breakyuna.esjzone.storage.StorageTransferPhase
import com.breakyuna.esjzone.storage.StorageMigrationBusyException
import com.breakyuna.esjzone.storage.StorageTransferException
import com.breakyuna.esjzone.storage.StorageTransferFailure
import com.breakyuna.esjzone.storage.StorageTransferFailureReason
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.locks.ReentrantLock
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
    val catalogComplete: Boolean = false,
    /** Old or reader-only manifests do not prove that a false adult flag is trustworthy. */
    val adultStatusKnown: Boolean = false
) {
    val pendingPasswordChapters: List<DownloadedChapterRecord>
        get() = chapters.filter { it.requiresPassword && !it.isSaved }
    val savedChapterCount: Int get() = chapters.count { it.isSaved }
    val pendingImageChapterCount: Int get() = chapters.count { it.needsImages }
    /** Body completion is independent of the existing full-asset completion flag. */
    val chaptersSaved: Boolean get() = (catalogComplete || complete) &&
        chapters.any { !it.localOnly } && chapters.filterNot { it.localOnly }.all { it.isSaved }
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
) {
    val isSaved: Boolean get() = bodyAvailable || downloaded
    val needsImages: Boolean get() = bodyAvailable && !downloaded
}

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
    private data class BookRevisions(val reset: Long = 0, val books: Map<String, Long> = emptyMap())
    private val bookRevisions = kotlinx.coroutines.flow.MutableStateFlow(BookRevisions())

    fun observeNovelChanges(novelUrl: String): Flow<Long> {
        val key = canonicalKey(novelUrl)
        return bookRevisions.map { it.books[key] ?: it.reset }.distinctUntilChanged()
    }

    /** Called under ioLock; resets invalidate all books, commits invalidate only their book. */
    private fun publishChange(novelUrl: String? = null) {
        val revision = mutableChanges.value + 1
        val previous = bookRevisions.value
        bookRevisions.value = if (novelUrl == null) BookRevisions(reset = revision)
            else previous.copy(books = previous.books + (canonicalKey(novelUrl) to revision))
        mutableChanges.value = revision
    }


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
    private inline fun <T> withStoreLock(operation: String, block: () -> T): T {
        val started = System.nanoTime()
        var acquired = started
        try {
            return synchronized(ioLock) {
                acquired = System.nanoTime()
                block()
            }
        } finally {
            val finished = System.nanoTime()
            val waitMs = (acquired - started) / 1_000_000
            val holdMs = (finished - acquired) / 1_000_000
            if (waitMs >= 50 || holdMs >= 50) {
                AppLogger.i("ReaderTiming", "storage operation=$operation waitMs=$waitMs holdMs=$holdMs")
            }
        }
    }

    private val inventoryScanLock = ReentrantLock()
    private val novelWriteLocks = ConcurrentHashMap<String, ReentrantLock>()
    private fun lockNovelWrites(directory: StorageFile): ReentrantLock {
        check(!Thread.holdsLock(ioLock)) { "Novel writes must not wait while holding the storage lock" }
        return novelWriteLocks.computeIfAbsent(directory.absolutePath) { ReentrantLock() }.also { it.lock() }
    }
    private val deletingDirectories = HashSet<String>()
    private var metadataSnapshot: List<DownloadedNovelSummary>? = null
    private val readMaintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val imageIndex = DownloadedImageIndex()
    /** Every successful commit publishes the latest manifest under ioLock. Keys are directory paths. */
    private val manifestCache = HashMap<String, DownloadedNovelManifest?>()
    /** Includes body commits between manifest checkpoints; guarded by ioLock. */
    private val contentWriteRevisions = HashMap<String, Long>()
    private fun contentRevision(directory: StorageFile): Long = contentWriteRevisions[directory.absolutePath] ?: 0L
    /** Folder names are presentation; the manifest URL remains the book identity. */
    private val novelDirectories = LinkedHashMap<String, StorageFile>()
    private var directoryIndexCache: File? = null
    private var novelDirectoryIndexLoaded = false
    private var directoryHintsRead = false
    private var savedDirectoryHints: DirectoryHints? = null
    private var renameLegacyDirectories = false
    private val historyProgressCache = HashMap<String, DownloadedHistoryProgress>()
    private val deletionGenerations = HashMap<String, Long>()
    /** Built once per store initialization, then updated only for the changed novel. */
    private val chapterIndex = HashMap<String, ChapterMatch>()
    private val chapterKeysByDirectory = HashMap<String, MutableSet<String>>()
    private var chapterIndexLoaded = false
    private val dirtyInventorySizes = HashSet<String>()
    /** Rebuilt after a manifest write or deletion; bookshelf refreshes read this snapshot. */
    private var inventorySnapshot: List<DownloadedNovelSummary>? = null

    private val mutableMigrating = kotlinx.coroutines.flow.MutableStateFlow(false)
    val migrating: kotlinx.coroutines.flow.StateFlow<Boolean> = mutableMigrating
    private var migrationCopyRunning = false
    private var backupRestoreInProgress = false
    private var storageGeneration = 0L
    private var privatePasswords: android.content.SharedPreferences? = null
    private data class DownloadWriteGuard(val directoryPath: String, val generation: Long, val storage: Long)

    private fun newWriteGuard(directory: StorageFile): DownloadWriteGuard = synchronized(ioLock) {
        if (directory.parentFile != rootDirectory) throw StorageMigrationBusyException()
        DownloadWriteGuard(directory.absolutePath, deletionGenerations[directory.absolutePath] ?: 0L, storageGeneration)
    }

    private fun ensureWriteAllowed(guard: DownloadWriteGuard?) = synchronized(ioLock) {
        if (mutableMigrating.value || backupRestoreInProgress) throw StorageMigrationBusyException()
        if (guard != null && guard.directoryPath in deletingDirectories) {
            throw CancellationException("The novel is being removed")
        }
        if (guard != null && guard.storage != storageGeneration) throw StorageMigrationBusyException()
        if (guard != null && (deletionGenerations[guard.directoryPath] ?: 0L) != guard.generation) {
            throw CancellationException("Download storage changed or the novel was removed")
        }
    }

    private fun requireFreeSpace(directory: StorageFile) {
        val available = runCatching { directory.usableSpace }.getOrDefault(Long.MAX_VALUE)
        if (available < MIN_FREE_SPACE_BYTES) throw IOException("Insufficient storage space for download")
    }

    @Volatile
    private var rootDirectory: StorageFile? = null
    @Volatile
    private var appContext: Context? = null

    private fun missingImageLabel(): String = appContext?.getString(R.string.download_image_unavailable)
        ?: "[Image unavailable]"

    fun initialize(context: Context, selectedDirectory: StorageFile? = null, onlyIfUninitialized: Boolean = false) = synchronized(ioLock) {
        appContext = context.applicationContext
        directoryIndexCache = File(context.applicationContext.cacheDir, "download-directory-index.json")
        privatePasswords = context.applicationContext.getSharedPreferences("download_secrets", Context.MODE_PRIVATE)
        val directory = selectedDirectory ?: StorageFile(File(context.applicationContext.filesDir, "downloaded_novels"))
        if ((!onlyIfUninitialized || rootDirectory == null) && rootDirectory?.absolutePath != directory.absolutePath) initializeStorageDirectory(directory)
    }

    internal fun initializeDirectory(directory: File, indexCache: File? = null) = synchronized(ioLock) {
        directoryIndexCache = indexCache
        initializeStorageDirectory(StorageFile(directory))
    }

    private fun initializeStorageDirectory(directory: StorageFile) = synchronized(ioLock) {
        if (backupRestoreInProgress) throw StorageMigrationBusyException()
        check(deletingDirectories.isEmpty()) { "Download deletion is still running" }
        // A disconnected user-selected volume must not prevent opening Settings to repair access.
        if (!directory.isDocument && !directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Unable to create the novel download directory")
        }
        // Refreshing handles/indexes for the same location must preserve active downloads.
        if (rootDirectory != directory) {
            storageGeneration += 1
            renameLegacyDirectories = true
            novelDirectories.clear()
        } else {
            // Preserve folders reserved by a save that has not published its first manifest yet.
            novelDirectories.replaceAll { _, existing -> StorageFile(directory, existing.name) }
        }
        novelDirectoryIndexLoaded = false
        directoryHintsRead = false
        savedDirectoryHints = null
        imageIndex.clear()
        chapterIndex.clear()
        chapterKeysByDirectory.clear()
        chapterIndexLoaded = false
        inventorySnapshot = null
        metadataSnapshot = null
        dirtyInventorySizes.clear()
        manifestCache.clear()
        historyProgressCache.clear()
        rootDirectory = directory
        publishChange()
    }

    /** Rescan metadata after files are restored or changed outside the app. */
    fun reindex(directory: StorageFile? = null) = synchronized(ioLock) {
        directoryIndexCache?.delete()
        (directory ?: rootDirectory)?.let { initializeStorageDirectory(it) }
    }

    /** Copy complete book directories before switching; original files survive any copy failure. */
    internal data class StorageMigration(
        val source: StorageFile?, val books: List<StorageFile>, val targets: Map<String, String> = emptyMap()
    )

    internal fun migrateStorage(
        destination: StorageFile,
        onProgress: (StorageTransferProgress) -> Unit = {},
        stagingJournal: (String) -> Unit = {},
        holdWritesUntilCommit: Boolean = false,
        checkActive: () -> Unit
    ): StorageMigration {
        val source = synchronized(ioLock) {
            check(!migrationCopyRunning && !backupRestoreInProgress && deletingDirectories.isEmpty())
            // Resolve legacy names before readers can build an index during the copy.
            ensureNovelDirectoryIndex(StorageTransferFailureReason.SOURCE_MANIFEST_INVALID)
            mutableMigrating.value = true
            migrationCopyRunning = true
            rootDirectory
        }
        try {
            if (source == destination) {
                synchronized(ioLock) { initializeStorageDirectory(destination) }
                return StorageMigration(source, emptyList())
            }
            require(source == null || !destination.isDescendantOf(source)) {
                "The destination is inside the source download directory"
            }
            val migrated = mutableListOf<StorageFile>()
            val targets = linkedMapOf<String, String>()
            check(destination.mkdirs())
            val destinationDirectories = synchronized(ioLock) {
                scanNovelDirectories(destination, StorageTransferFailureReason.TARGET_MANIFEST_CONFLICT)
            }
            val retiredSources = source?.let(::readRetiredDirectories).orEmpty()
            val sources = source?.takeIf { it.exists() }?.entries().orEmpty()
                .filter { it.directory && !isStagingDirectory(it.file.name) && it.file.name !in retiredSources }.map { it.file }
            val copyBuffer = ByteArray(64 * 1024)
            val comparisonBuffer by lazy { ByteArray(copyBuffer.size) }
            sources.forEach { directory ->
                checkActive()
                val manifest = synchronized(ioLock) {
                    readManifest(directory, StorageTransferFailureReason.SOURCE_MANIFEST_INVALID)?.also(::rememberPassword)
                } ?: return@forEach
                var files = directory.snapshotFiles(checkActive)
                val replacements = files.filter { it.file.name.endsWith(".previous.tmp") }
                if (replacements.isNotEmpty()) {
                    synchronized(ioLock) {
                        replacements.forEach { previous ->
                            checkActive()
                            StorageFile(checkNotNull(previous.file.parentFile),
                                previous.file.name.removeSuffix(".previous.tmp")).recoverReplacement()
                        }
                    }
                    files = directory.snapshotFiles(checkActive)
                }
                val copyFiles = files.filter { it.file.name != MANIFEST_FILE && !it.file.name.endsWith(".tmp") }
                val expected = copyFiles.associateByTo(HashMap()) { it.file.relativePathFrom(directory) }
                manifest.chapters.filter { it.bodyAvailable || it.downloaded }.forEach { record ->
                    if ((expected[record.fileName]?.size ?: 0L) == 0L) {
                        throw StorageTransferException(StorageTransferFailure(StorageTransferFailureReason.SOURCE_FILE_MISSING,
                            "downloads/${directory.name}/${record.fileName}"))
                    }
                }
                val key = canonicalKey(manifest.url)
                val target = destinationDirectories[key] ?: availableNovelDirectory(destination, manifest.name, key)
                val sanitizedManifest = gson.toJson(if (destination.isDocument) manifest.copy(commonPassword = null) else manifest)
                if (target.exists()) {
                    // A completed book from an interrupted run is validated before allocating a second copy.
                    val existingManifest = synchronized(ioLock) {
                        readManifest(target, StorageTransferFailureReason.TARGET_MANIFEST_CONFLICT)
                    }
                    if (existingManifest == null ||
                        gson.toJson(if (destination.isDocument) existingManifest.copy(commonPassword = null) else existingManifest) != sanitizedManifest) {
                        throw StorageTransferException(StorageTransferFailure(StorageTransferFailureReason.TARGET_MANIFEST_CONFLICT,
                            "downloads/${target.name}/$MANIFEST_FILE"))
                    }
                    var verified = 0
                    for (other in target.fileEntries(checkActive)) {
                        checkActive()
                        // Existing extra files are retained, but cannot stand in for a missing source file.
                        val entry = expected.remove(other.file.relativePathFrom(target)) ?: continue
                        onProgress(StorageTransferProgress(StorageTransferPhase.VERIFYING, manifest.name, verified, copyFiles.size))
                        if (other.size != entry.size ||
                            !other.file.sameContent(entry.file, checkActive, sizesChecked = true,
                                buffer = copyBuffer, otherBuffer = comparisonBuffer)) {
                            throw StorageTransferException(StorageTransferFailure(StorageTransferFailureReason.TARGET_FILE_CONFLICT,
                                "downloads/${target.name}/${other.file.relativePathFrom(target)}"))
                        }
                        verified += 1
                    }
                    if (expected.isNotEmpty()) {
                        throw StorageTransferException(StorageTransferFailure(StorageTransferFailureReason.TARGET_FILE_CONFLICT,
                            "downloads/${target.name}/${expected.keys.first()}"))
                    }
                } else {
                    val staging = StorageFile(destination, "migration-${java.util.UUID.randomUUID()}.tmp")
                    stagingJournal(staging.name)
                    check(staging.mkdirs())
                    try {
                        val parents = hashSetOf(staging.absolutePath)
                        copyFiles.forEachIndexed { index, entry ->
                            checkActive()
                            onProgress(StorageTransferProgress(StorageTransferPhase.COPYING, manifest.name, index, copyFiles.size))
                            val output = StorageFile(staging, entry.file.relativePathFrom(directory))
                            val parent = checkNotNull(output.parentFile)
                            if (parents.add(parent.absolutePath)) check(parent.mkdirs())
                            entry.file.inputStream().use { input -> output.createNewOutputStream().use { stream ->
                                input.copyWithCancellation(stream, copyBuffer, checkActive)
                            } }
                        }
                        onProgress(StorageTransferProgress(StorageTransferPhase.VERIFYING, manifest.name, copyFiles.size, copyFiles.size))
                        // Consume the source map while scanning, without a second whole-book list/map.
                        staging.fileEntries(checkActive).forEach { copied ->
                            val entry = expected.remove(copied.file.relativePathFrom(staging))
                            if (entry == null || copied.size != entry.size) {
                                throw StorageTransferException(StorageTransferFailure(StorageTransferFailureReason.VERIFICATION_FAILED,
                                    "downloads/${target.name}/${copied.file.relativePathFrom(staging)}"))
                            }
                        }
                        if (expected.isNotEmpty()) {
                            throw StorageTransferException(StorageTransferFailure(StorageTransferFailureReason.VERIFICATION_FAILED,
                                "downloads/${target.name}/${expected.keys.first()}"))
                        }
                        // Publish metadata last; no unfinished book is visible to normal scans.
                        checkActive()
                        StorageFile(staging, MANIFEST_FILE).createNewOutputStream().bufferedWriter().use {
                            it.write(sanitizedManifest)
                        }
                        checkActive()
                        check(staging.renameTo(target))
                    } finally {
                        runCatching { check(staging.deleteRecursively()) }
                            .onFailure { AppLogger.w("NovelDownloadStore", "Unable to remove migration staging directory", it) }
                    }
                }
                migrated += directory
                targets[directory.name] = target.name
                destinationDirectories[key] = target
                onProgress(StorageTransferProgress(StorageTransferPhase.VERIFYING, manifest.name, copyFiles.size, copyFiles.size))
            }
            checkActive()
            synchronized(ioLock) { initializeStorageDirectory(destination) }
            return StorageMigration(source, migrated, targets)
        } finally {
            synchronized(ioLock) {
                migrationCopyRunning = false
                if (!holdWritesUntilCommit) mutableMigrating.value = false
            }
        }
    }

    /** Revalidate a journaled destination before resuming deletion of possibly partial originals. */
    internal fun verifyMigratedBook(source: StorageFile, target: StorageFile, checkActive: () -> Unit) {
        checkActive()
        if (!target.isDirectory) throw StorageTransferException(StorageTransferFailure(
            StorageTransferFailureReason.TARGET_FILE_CONFLICT, "downloads/${target.name}"))
        // Read fresh documents: a restarted cleanup must not trust a pre-interruption metadata cache.
        val manifest = synchronized(ioLock) {
            readManifest(target, StorageTransferFailureReason.TARGET_MANIFEST_CONFLICT)
        }
            ?: throw StorageTransferException(StorageTransferFailure(StorageTransferFailureReason.TARGET_MANIFEST_CONFLICT,
                "downloads/${target.name}/$MANIFEST_FILE"))
        val targetFiles = target.fileEntries(checkActive).associateBy { it.file.relativePathFrom(target) }
        manifest.chapters.filter { it.downloaded || it.bodyAvailable }.forEach { record ->
            if ((targetFiles[record.fileName]?.size ?: 0L) <= 0L) {
                throw StorageTransferException(StorageTransferFailure(StorageTransferFailureReason.TARGET_FILE_CONFLICT,
                    "downloads/${target.name}/${record.fileName}"))
            }
        }
        if (!source.exists()) return
        val comparisonBuffer = ByteArray(64 * 1024)
        val otherComparisonBuffer = ByteArray(comparisonBuffer.size)
        source.fileEntries(checkActive).filter { !it.file.name.endsWith(".tmp") }.forEach { entry ->
            checkActive()
            if (entry.file.name == MANIFEST_FILE) {
                val original = synchronized(ioLock) {
                    readManifest(entry.file.parentFile, StorageTransferFailureReason.SOURCE_MANIFEST_INVALID)
                }
                    ?: throw StorageTransferException(StorageTransferFailure(StorageTransferFailureReason.SOURCE_MANIFEST_INVALID,
                        "downloads/${source.name}/$MANIFEST_FILE"))
                if (canonicalKey(original.url) != canonicalKey(manifest.url)) {
                    throw StorageTransferException(StorageTransferFailure(StorageTransferFailureReason.TARGET_MANIFEST_CONFLICT,
                        "downloads/${target.name}/$MANIFEST_FILE"))
                }
                val currentRecords = manifest.chapters.associateBy { chapterKey(it.url) }
                original.chapters.filter { it.downloaded || it.bodyAvailable }.forEach { record ->
                    val current = currentRecords[chapterKey(record.url)]
                    if (current == null || current.fileName != record.fileName || !(current.downloaded || current.bodyAvailable)) {
                        throw StorageTransferException(StorageTransferFailure(StorageTransferFailureReason.TARGET_MANIFEST_CONFLICT,
                            "downloads/${target.name}/$MANIFEST_FILE"))
                    }
                }
            } else {
                val copied = targetFiles[entry.file.relativePathFrom(source)]
                if (copied == null || copied.size != entry.size ||
                    !copied.file.sameContent(entry.file, checkActive, sizesChecked = true,
                        buffer = comparisonBuffer, otherBuffer = otherComparisonBuffer)) {
                    throw StorageTransferException(StorageTransferFailure(StorageTransferFailureReason.TARGET_FILE_CONFLICT,
                        "downloads/${target.name}/${entry.file.relativePathFrom(source)}"))
                }
            }
        }
    }

    internal fun setStorageMigrationPending(pending: Boolean) = synchronized(ioLock) {
        check(!migrationCopyRunning)
        if (pending && (deletingDirectories.isNotEmpty() || backupRestoreInProgress)) throw StorageMigrationBusyException()
        mutableMigrating.value = pending
    }

    internal fun finishStorageMigration() = setStorageMigrationPending(false)

    internal fun restoreStorage(directory: StorageFile) = initializeStorageDirectory(directory)

    private fun rememberPassword(manifest: DownloadedNovelManifest) {
        val preferences = privatePasswords ?: return
        val key = digest(canonicalKey(manifest.url))
        if (preferences.getString(key, null) == manifest.commonPassword) return
        check(preferences.edit().putString(key, manifest.commonPassword).commit())
    }

    /** Copy committed files one at a time; readers can use the store between files and during ZIP. */
    fun exportBackup(destination: File, checkActive: () -> Unit = {}, onNovelSnapshot: (File) -> Unit = {}) {
        checkActive()
        check(destination.isDirectory || destination.mkdirs())
        val sources = synchronized(ioLock) {
            ensureNovelDirectoryIndex()
            novelDirectories.values.filter { it.absolutePath !in deletingDirectories }
        }
        for (source in sources) {
            checkActive()
            val target = File(destination, source.name)
            val (manifest, guard) = synchronized(ioLock) {
                if (source.absolutePath in deletingDirectories || !source.isDirectory) return@synchronized null
                val manifest = readManifest(source) ?: return@synchronized null
                manifest to newWriteGuard(source)
            } ?: continue
            // Listings and copies may be slow provider calls. Keep only stable-handle opening
            // and generation checks under the store lock.
            val files = source.snapshotFiles(checkActive).filter {
                it.file.name != MANIFEST_FILE && !it.file.name.endsWith(".tmp")
            }
            fun checkSnapshot() {
                ensureSnapshotCurrent(guard)
            }
            check(target.isDirectory || target.mkdirs())
            for (entry in files) {
                val file = entry.file
                val output = File(target, file.relativePathFrom(source))
                val parent = checkNotNull(output.parentFile)
                check(parent.isDirectory || parent.mkdirs())
                checkActive()
                val input = synchronized(ioLock) {
                    checkSnapshot()
                    file.inputStream()
                }
                input.use {
                    output.outputStream().use { out -> it.copyWithCancellation(out, checkActive) }
                }
                synchronized(ioLock) { checkSnapshot() }
            }
            synchronized(ioLock) { checkSnapshot() }
            checkActive()
            // Use the starting manifest: newly downloaded chapters belong to the next backup.
            File(target, MANIFEST_FILE).bufferedWriter(StandardCharsets.UTF_8).use {
                gson.toJson(manifest.copy(version = 2, commonPassword = null), it)
            }
            onNovelSnapshot(target)
        }
    }

    /** Keep old downloads until the complete replacement and its database transaction succeed. */
    fun importBackup(source: File, replaceExisting: Boolean = false, checkActive: () -> Unit = {}) {
        val imported = beginBackupImport(StorageFile(source), replaceExisting, checkActive)
        finishBackupImport(imported, committed = true)
    }

    internal suspend fun withRestoredBackup(source: File, checkActive: () -> Unit,
                                          restoreDatabase: suspend () -> Unit) {
        val imported = beginBackupImport(StorageFile(source), replaceExisting = true, checkActive = checkActive)
        var committed = false
        try {
            restoreDatabase()
            committed = true
        } finally {
            // No suspension/cancellation point between the database result and cleanup/rollback.
            finishBackupImport(imported, committed)
        }
    }

    private class BackupImport(val previous: Map<String, StorageFile>, val replacing: Boolean) {
        val staged = mutableListOf<StorageFile>()
        val published = mutableListOf<StorageFile>()
    }

    private fun beginBackupImport(source: StorageFile, replaceExisting: Boolean,
                                  checkActive: () -> Unit): BackupImport = synchronized(ioLock) {
        ensureWriteAllowed(null)
        if (deletingDirectories.isNotEmpty()) throw StorageMigrationBusyException()
        checkActive()
        val root = checkNotNull(rootDirectory)
        prepareDownloadRoot(root)
        ensureNovelDirectoryIndex()
        val imported = BackupImport(LinkedHashMap(novelDirectories), replaceExisting)
        backupRestoreInProgress = true
        // Retire in-flight write guards even if this restore later rolls back.
        storageGeneration += 1L
        if (replaceExisting) imported.previous.keys.forEach(::cancelActiveWork)
        try {
            val directories = if (source.exists()) source.entries().filter { it.directory }.map { it.file } else emptyList()
            val incoming = directories.map { directory ->
                checkActive()
                directory to requireNotNull(readManifest(directory)).also { manifest ->
                    manifest.chapters.forEach { record ->
                        checkActive()
                        val file = requireNotNull(resolveLocalFile(directory, record.fileName))
                        require(!(record.downloaded || record.bodyAvailable) || file.isFile)
                        if (file.isFile) {
                            val chapter = requireNotNull(readJson(file, DownloadedChapterContent::class.java))
                            require(chapter.schemaVersion in 0..2)
                            if (chapter.schemaVersion == 2) requireNotNull(chapter.body).validate()
                        }
                    }
                }
            }
            require(incoming.map { canonicalKey(it.second.url) }.distinct().size == incoming.size)
            val prepared = incoming.filter { replaceExisting || canonicalKey(it.second.url) !in imported.previous }
                .map { (directory, manifest) ->
                    checkActive()
                    val staging = StorageFile.createTempFile("restore-", ".tmp", root)
                    imported.staged += staging
                    check(staging.delete() && staging.mkdirs())
                    directory.fileEntries(checkActive).forEach { entry ->
                        val file = entry.file
                        val output = StorageFile(staging, file.relativePathFrom(directory))
                        check(output.parentFile?.mkdirs() == true)
                        file.inputStream().use { input ->
                            output.outputStream().use { input.copyWithCancellation(it, checkActive) }
                        }
                        check(output.length() == entry.size) { "Incomplete restored download" }
                    }
                    StorageFile(staging, MANIFEST_FILE).writeText(gson.toJson(manifest.copy(version = 2, commonPassword = null)))
                    staging to manifest
                }
            // Publish only after every book has been copied. Original directories remain intact.
            val next = if (replaceExisting) linkedMapOf<String, StorageFile>() else LinkedHashMap(imported.previous)
            prepared.forEach { (staging, manifest) ->
                checkActive()
                val key = canonicalKey(manifest.url)
                val target = availableNovelDirectory(root, manifest.name, key)
                writeRetiredDirectories(root, readRetiredDirectories(root) + target.name)
                check(staging.renameTo(target))
                imported.published += target
                next[key] = target
            }
            checkActive()
            novelDirectories.clear()
            novelDirectories.putAll(next)
            invalidateBackupIndexes()
            imported
        } catch (error: Throwable) {
            finishBackupImport(imported, committed = false)
            throw error
        }
    }

    private fun finishBackupImport(imported: BackupImport, committed: Boolean) = synchronized(ioLock) {
        try {
            val root = checkNotNull(rootDirectory)
            if (committed) {
                val obsolete = if (imported.replacing) imported.previous.values.map { it.name } else emptyList()
                // This durable switch precedes deletion. A refused cleanup cannot change the selected copy.
                writeRetiredDirectories(root,
                    (readRetiredDirectories(root) + obsolete) - imported.published.map { it.name }.toSet())
            } else {
                novelDirectories.clear()
                novelDirectories.putAll(imported.previous)
                // Published directories were excluded before their first rename, so rollback needs no write.
            }
            val obsolete = if (committed && imported.replacing) imported.previous.values else
                if (!committed) imported.published else emptyList()
            (obsolete + imported.staged).forEach { directory ->
                runCatching { check(!directory.exists() || directory.deleteRecursively()) }
                    .onFailure { AppLogger.w("NovelDownloadStore", "Unable to remove restore working directory", it) }
            }
            // A failed pruning write is harmless: allocation also avoids still-reserved names.
            runCatching {
                val retained = readRetiredDirectories(root).filterTo(linkedSetOf()) { StorageFile(root, it).exists() }
                writeRetiredDirectories(root, retained)
            }.onFailure { AppLogger.w("NovelDownloadStore", "Unable to prune retired restore directories", it) }
        } finally {
            backupRestoreInProgress = false
            invalidateBackupIndexes()
            novelDirectoryIndexLoaded = false
            publishChange()
        }
    }

    private const val RETIRED_DIRECTORIES_FILE = ".restore-retired.json"

    /** Durable exclusion, independent of the disposable directory-index cache and of directory order. */
    private fun readRetiredDirectories(root: StorageFile): Set<String> {
        val file = StorageFile(root, RETIRED_DIRECTORIES_FILE)
        file.recoverReplacement()
        if (!file.exists()) return emptySet()
        val retired = file.inputStream().bufferedReader().use {
            com.google.gson.JsonParser.parseReader(it).asJsonObject
        }
        require(retired.get("version")?.asInt == 1)
        return requireNotNull(retired.getAsJsonArray("names")).mapTo(linkedSetOf()) { entry ->
            val name = entry.asString
            require(name.isNotBlank() && name != "." && name != ".." && '/' !in name && '\\' !in name)
            name
        }
    }

    private fun writeRetiredDirectories(root: StorageFile, names: Set<String>) {
        val file = StorageFile(root, RETIRED_DIRECTORIES_FILE)
        if (names.isEmpty()) {
            file.recoverReplacement()
            check(!file.exists() || file.delete())
            return
        }
        val temporary = StorageFile.createTempFile("restore-", ".tmp", root)
        try {
            val retired = com.google.gson.JsonObject().apply {
                addProperty("version", 1)
                add("names", gson.toJsonTree(names))
            }
            temporary.writeText(retired.toString())
            file.replaceWith(temporary)
        } finally {
            runCatching { temporary.delete() }
        }
    }

    private fun invalidateBackupIndexes() {
        directoryIndexCache?.delete()
        directoryHintsRead = false
        savedDirectoryHints = null
        imageIndex.clear()
        chapterIndex.clear()
        chapterKeysByDirectory.clear()
        chapterIndexLoaded = false
        inventorySnapshot = null
        metadataSnapshot = null
        manifestCache.clear()
        historyProgressCache.clear()
    }

    fun manifest(novelUrl: String): DownloadedNovelManifest? = withStoreLock("manifest") {
        readManifest(directoryFor(novelUrl, create = false))
    }

    fun findDownloadedCover(novelUrl: String): StorageFile? = synchronized(ioLock) {
        val dir = directoryFor(novelUrl, create = false) ?: return@synchronized null
        val direct = StorageFile(dir, "cover.jpg")
        if (direct.isFile && direct.length() > 0L) return@synchronized direct
        val imagesDir = StorageFile(dir, "images")
        if (imagesDir.isDirectory) {
            imagesDir.listFiles()
                ?.firstOrNull { it.isFile && it.length() > 0L && it.name.startsWith("cover") }
                ?.let { return@synchronized it }
        }
        null
    }

    fun isDownloaded(novelUrl: String): Boolean = manifest(novelUrl)?.complete == true

    private data class DirectoryScanSnapshot(val root: StorageFile, val generation: Long, val revision: Long,
        val retired: Set<String>)

    /** Metadata maintenance must not hold ioLock while enumerating/decoding every book. */
    private fun prepareMetadataDirectories(checkActive: () -> Unit) {
        val snapshot = withStoreLock("metadata-index-start") {
            if (novelDirectoryIndexLoaded || rootDirectory == null || backupRestoreInProgress) return
            val root = checkNotNull(rootDirectory)
            DirectoryScanSnapshot(root, storageGeneration, mutableChanges.value, readRetiredDirectories(root))
        }
        checkActive()
        val entries = if (snapshot.root.exists()) snapshot.root.entries() else emptyList()
        val directories = LinkedHashMap<String, StorageFile>()
        entries.asSequence().filter { it.directory && !isStagingDirectory(it.file.name) &&
            it.file.name !in snapshot.retired }.sortedBy { it.file.name }.forEach { entry ->
            checkActive()
            val key = readManifestSnapshot(entry.file, checkActive)?.url
                ?.takeIf(String::isNotBlank)?.let(::canonicalKey) ?: return@forEach
            directories.putIfAbsent(key, entry.file)
        }
        withStoreLock("metadata-index-publish") {
            if (snapshot.generation != storageGeneration || snapshot.revision != mutableChanges.value ||
                novelDirectoryIndexLoaded || backupRestoreInProgress) return
            // Keep the existing rename/recovery transaction for legacy hash folders.
            if (renameLegacyDirectories && directories.any { (key, directory) -> directory.name == digest(key) }) {
                ensureNovelDirectoryIndex()
                return
            }
            novelDirectories.forEach { (key, directory) ->
                if (directory.name !in snapshot.retired) directories.putIfAbsent(key, directory)
            }
            novelDirectories.clear()
            novelDirectories.putAll(directories)
            novelDirectoryIndexLoaded = true
            renameLegacyDirectories = false
            saveDirectoryHints()
        }
    }

    /** Manifest metadata only; explicit reindex/validation handles external file changes. */
    fun listDownloadedMetadata(checkActive: () -> Unit = {}): List<DownloadedNovelSummary> {
        while (true) {
            checkActive()
            prepareMetadataDirectories(checkActive)
            val snapshot = withStoreLock("metadata-index") {
                metadataSnapshot?.let { return it }
                if (rootDirectory == null) return emptyList()
                if (backupRestoreInProgress) throw StorageMigrationBusyException()
                if (!novelDirectoryIndexLoaded) null else Triple(storageGeneration, mutableChanges.value,
                    novelDirectories.values.filter { it.absolutePath !in deletingDirectories })
            } ?: continue
            val rows = snapshot.third.mapNotNull { directory ->
                checkActive()
                val stored = readManifestSnapshot(directory, checkActive) ?: return@mapNotNull null
                val count = stored.savedChapterCount
                if (count == 0) null else DownloadedNovelSummary(stored, count, -1L)
            }.sortedByDescending { it.manifest.downloadedAt }
            val current = synchronized(ioLock) {
                (storageGeneration == snapshot.first && mutableChanges.value == snapshot.second &&
                    !backupRestoreInProgress).also { if (it) metadataSnapshot = rows }
            }
            if (current) return rows
        }
    }

    fun downloadedNovelUrls(): Set<String> = listDownloadedMetadata().mapTo(LinkedHashSet()) { it.novelUrl }

    /** Lists partial and complete downloads; recursive size scans never hold the reader's lock. */
    fun listDownloadedNovels(
        calculateSizes: Boolean = true,
        onMetadata: (List<DownloadedNovelSummary>) -> Unit = {},
        checkActive: () -> Unit = {}
    ): List<DownloadedNovelSummary> {
        if (!calculateSizes) return listDownloadedMetadata(checkActive)
        inventoryScanLock.lockInterruptibly()
        try {
            checkActive()
            val root: StorageFile
            val revision: Long
            val cached: List<DownloadedNovelSummary>?
            val dirty: Set<String>
            val directories: Map<String, StorageFile>
            synchronized(ioLock) {
                root = rootDirectory ?: return emptyList()
                ensureNovelDirectoryIndex()
                directories = novelDirectories.filterValues { it.absolutePath !in deletingDirectories }
                revision = mutableChanges.value
                cached = inventorySnapshot
                dirty = dirtyInventorySizes.toSet()
            }
            if (cached != null && dirty.isEmpty()) return cached
            val snapshot = if (cached != null) {
                onMetadata(cached.map { summary ->
                    if (canonicalKey(summary.novelUrl) in dirty) summary.copy(storageBytes = -1L) else summary
                })
                cached.map { summary ->
                    val key = canonicalKey(summary.novelUrl)
                    if (key !in dirty) summary else {
                        val directory = directories[key]
                        summary.copy(storageBytes = directory?.fileEntries(checkActive)?.sumOf { it.size } ?: 0L)
                    }
                }
            } else {
                val rootEntries = mutableMapOf<StorageFile, List<StorageFile.Entry>>()
                val metadata = directories.values.mapNotNull { directory ->
                    checkActive()
                    val stored = synchronized(ioLock) { readManifest(directory) } ?: return@mapNotNull null
                    val entries = directory.entries()
                    rootEntries[directory] = entries
                    val files = entries.filter { !it.directory }.mapTo(HashSet()) { it.file.name }
                    val count = stored.chapters.count { record ->
                        (record.bodyAvailable || record.downloaded) && record.fileName in files
                    }
                    if (count == 0) null else DownloadedNovelSummary(stored, count, -1L)
                }.sortedByDescending { it.manifest.downloadedAt }
                onMetadata(metadata)
                metadata.map { summary ->
                    val directory = directories.getValue(canonicalKey(summary.novelUrl))
                    val bytes = rootEntries.getValue(directory).sumOf { entry ->
                        if (entry.directory) entry.file.fileEntries(checkActive).sumOf { it.size } else entry.size
                    }
                    summary.copy(storageBytes = bytes)
                }
            }
            val current = synchronized(ioLock) {
                // A download or deletion during the scan must not publish an obsolete inventory cache.
                if (rootDirectory == root && mutableChanges.value == revision) {
                    inventorySnapshot = snapshot
                    metadataSnapshot = snapshot.map { it.copy(storageBytes = -1L) }
                    dirtyInventorySizes.clear()
                    true
                } else false
            }
            return if (current) snapshot else listDownloadedMetadata(checkActive)
        } finally { inventoryScanLock.unlock() }
    }

    /** Existence checks stop at the first file instead of counting every byte. */
    fun hasLocalContent(novelUrl: String): Boolean {
        val directory = synchronized(ioLock) { directoryFor(novelUrl, create = false) } ?: return false
        return directory.fileEntries().any()
    }

    fun storageBytes(novelUrl: String): Long {
        val directory: StorageFile
        val guard: DownloadWriteGuard
        synchronized(ioLock) {
            directory = directoryFor(novelUrl, create = false) ?: return 0L
            guard = newWriteGuard(directory)
        }
        val bytes = directory.fileEntries().sumOf { it.size }
        synchronized(ioLock) { ensureSnapshotCurrent(guard) }
        return bytes
    }

    private fun ensureSnapshotCurrent(guard: DownloadWriteGuard) {
        check(guard.storage == storageGeneration &&
            guard.generation == (deletionGenerations[guard.directoryPath] ?: 0L) &&
            guard.directoryPath !in deletingDirectories) { "Download storage changed during the operation" }
    }

    private enum class DeleteResult { DELETED, ALREADY_ABSENT, BUSY_OR_FAILED }

    /** Retire this book before slow deletion; unrelated books never wait for the recursive walk. */
    fun delete(novelUrl: String): Boolean = deleteResult(novelUrl) != DeleteResult.BUSY_OR_FAILED

    private fun deleteResult(novelUrl: String): DeleteResult {
        cancelActiveWork(novelUrl)
        val directory: StorageFile
        synchronized(ioLock) {
            if (mutableMigrating.value || backupRestoreInProgress) return DeleteResult.BUSY_OR_FAILED
            if (novelDirectories[canonicalKey(novelUrl)]?.absolutePath in deletingDirectories) return DeleteResult.BUSY_OR_FAILED
            directory = directoryFor(novelUrl, create = false) ?: return DeleteResult.ALREADY_ABSENT
            if (!deletingDirectories.add(directory.absolutePath)) return DeleteResult.BUSY_OR_FAILED
            deletionGenerations[directory.absolutePath] = (deletionGenerations[directory.absolutePath] ?: 0L) + 1
            manifestCache.remove(directory.absolutePath)
            historyProgressCache.remove(directory.absolutePath)
            imageIndex.remove(directory)
            removeChapterIndex(directory)
            inventorySnapshot = null
            metadataSnapshot = null
            publishChange(novelUrl)
        }
        try {
            val deleted = directory.deleteRecursively()
            synchronized(ioLock) {
                if (deleted) novelDirectories.remove(canonicalKey(novelUrl))
            }
            return if (deleted) DeleteResult.DELETED else DeleteResult.BUSY_OR_FAILED
        } finally {
            synchronized(ioLock) {
                deletingDirectories.remove(directory.absolutePath)
                manifestCache.remove(directory.absolutePath)
                inventorySnapshot = null
                metadataSnapshot = null
                publishChange(novelUrl)
            }
        }
    }

    fun deleteAll(novelUrls: Iterable<String>): Int =
        novelUrls.distinct().count { deleteResult(it) == DeleteResult.DELETED }

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
        val directory = directoryFor(novel.url, create = true, novelName = novel.name) ?: return null
        val writeGuard = newWriteGuard(directory)
        val trimmed = commonPassword?.trim()?.ifBlank { null }
        val manifest = synchronized(ioLock) { readManifest(directory) }
        val updated = manifest?.copy(commonPassword = trimmed) ?: manifestFrom(
            novel, emptyList(), System.currentTimeMillis(), complete = false, commonPassword = trimmed)
        return writeManifest(directory, updated, writeGuard, updatePassword = true)
    }

    fun updateCommonPassword(novelUrl: String, commonPassword: String?): DownloadedNovelManifest? {
        val directory = directoryFor(novelUrl, create = false) ?: return null
        val writeGuard = newWriteGuard(directory)
        val manifest = synchronized(ioLock) { readManifest(directory) } ?: return null
        return writeManifest(directory, manifest.copy(commonPassword = commonPassword?.trim()?.ifBlank { null }),
            writeGuard, updatePassword = true)
    }

    suspend fun unlockChaptersWithCommonPassword(
        authorization: Authorization,
        novelUrl: String,
        selectedChapterUrls: Set<String>,
        baseUrl: String,
        onProgress: (DownloadProgress) -> Unit = {}
    ): BatchUnlockResult {
        val directory = directoryFor(novelUrl, create = false)
            ?: throw IOException("Novel download storage is unavailable")
        val initialManifest = synchronized(ioLock) { readManifest(directory) }
            ?: throw IOException("Novel download manifest is unavailable")
        val password = initialManifest.commonPassword?.takeIf(String::isNotBlank)
            ?: throw IOException("Common chapter password is unavailable")
        require(selectedChapterUrls.isNotEmpty())
        val selectedKeys = requireNotNull(selectionKeys(initialManifest.chapters.map { it.url }, selectedChapterUrls))
        // Keep the queued scope, but an already saved body no longer needs unlocking.
        val targets = initialManifest.chapters.filter { chapterKey(it.url) in selectedKeys }
            .distinctBy { chapterKey(it.url) }
        val chapterOrder = initialManifest.chapters.filterNot { it.localOnly }
            .map { Chapter(it.name, it.url, false) }

        var unlocked = targets.count { it.bodyAvailable || it.downloaded }
        var failed = 0
        val total = targets.size
        var retryableError: Exception? = null
        var processed = unlocked

        onProgress(DownloadProgress(processed, total, ""))
        for (record in targets.filterNot { it.bodyAvailable || it.downloaded }) {
            currentCoroutineContext().ensureActive()
            onProgress(DownloadProgress(processed, total, record.name))
            try {
                val detail = EsjzoneClient.unlockPasswordProtectedChapter(
                    authorization = authorization,
                    chapter = Chapter(record.name, record.url, false),
                    password = password,
                    baseUrl = baseUrl
                )
                currentCoroutineContext().ensureActive()
                val saved = saveChapter(
                    novelName = initialManifest.name,
                    novelUrl = novelUrl,
                    coverUrl = initialManifest.coverUrl,
                    chapterOrder = chapterOrder,
                    chapter = Chapter(record.name, record.url, false),
                    detail = detail,
                    authorization = authorization
                )
                if (saved?.chapters?.any { chapterKey(it.url) == chapterKey(record.url) && it.bodyAvailable } != true) {
                    throw IOException("Unlocked chapter download is incomplete")
                }
                currentCoroutineContext().ensureActive()
                unlocked++
            } catch (e: CancellationException) {
                throw e
            } catch (e: ChapterPasswordRejectedException) {
                failed++
            } catch (e: Exception) {
                AppLogger.w("NovelDownloadStore", "Common password unlock failed for chapter ${record.name}", e)
                failed++
                if (retryableError == null) retryableError = e
            }
            processed++
            onProgress(DownloadProgress(processed, total, record.name))
        }

        retryableError?.let { throw IOException("Batch chapter unlock needs retry", it) }

        val finalManifest = synchronized(ioLock) { readManifest(directory) }
        return BatchUnlockResult(
            totalCount = total,
            unlockedCount = unlocked,
            failedCount = failed,
            updatedManifest = finalManifest
        )
    }

    /** Fill a missing catalog even when its first chapter is still being saved. */
    fun saveChapterCatalog(
        novelName: String,
        novelUrl: String,
        coverUrl: String,
        chapterOrder: List<Chapter>
    ): DownloadedNovelManifest? {
        val normalizedNovelUrl = novelUrl.trim()
        val ordered = chapterOrder.filterNot { it.isExternal }
            .filter { chapterKey(it.url).isNotBlank() }.distinctBy { chapterKey(it.url) }
        if (normalizedNovelUrl.isBlank() || ordered.isEmpty()) return null
        val directory = directoryFor(normalizedNovelUrl, create = true, novelName = novelName)
            ?: return null
        val writeGuard = newWriteGuard(directory)
        ensureWriteAllowed(writeGuard)
        val previous = synchronized(ioLock) { readManifest(directory) }
        // A queued reader snapshot has no revision proving it is newer than the
        // catalog already published by a download or another reader.
        if (previous?.catalogComplete == true || previous?.complete == true) return previous
        val previousByKey = previous?.chapters.orEmpty().associateBy { chapterKey(it.url) }
        val records = ordered.mapIndexed { index, chapter ->
            previousByKey[chapterKey(chapter.url)]?.copy(
                index = index, name = chapter.name, url = chapter.url, localOnly = false
            ) ?: DownloadedChapterRecord(index, chapter.name, chapter.url,
                chapterFileName(chapter.url), downloaded = false)
        }
        val current = manifestFromMetadata(previous, novelName, normalizedNovelUrl, coverUrl,
            records, previous?.downloadedAt ?: 0L, complete = false, catalogComplete = true)
        return writeManifest(directory, current, writeGuard, replaceCatalog = true,
            revalidateChapterFiles = false, onlySeedCatalog = true)
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
        authorization: Authorization? = null,
        chapterOrderComplete: Boolean = false
    ): DownloadedNovelManifest? {
        val normalizedNovelUrl = novelUrl.trim()
        if (normalizedNovelUrl.isBlank()) return null
        val directory = directoryFor(normalizedNovelUrl, create = true, novelName = novelName) ?: return null
        val writeGuard = newWriteGuard(directory)
        requireFreeSpace(directory)

        val previous = synchronized(ioLock) {
            ensureWriteAllowed(writeGuard)
            readManifest(directory)
        }
        val targetKey = chapterKey(chapter.url)
        val existing = previous?.chapters?.firstOrNull { chapterKey(it.url) == targetKey }
        val existingFile = existing?.fileName?.let { resolveLocalFile(directory, it) }

        val validatedDownload = synchronized(ioLock) {
            detail.downloadedSourceRevision != null && detail.downloadedSourceRevision == mutableChanges.value &&
                chapterKey(detail.sourceUrl.orEmpty()) == targetKey && previous === readManifest(directory)
        }
        if (existing?.downloaded == true &&
            (validatedDownload || (existingFile?.let { readStoredSnapshot(it, writeGuard)?.hasAllImagesOnDisk(directory) } == true)) &&
            previous.name == novelName && previous.coverUrl == coverUrl &&
            (!chapterOrderComplete || previous.catalogComplete || previous.complete) &&
            chapterOrder.isNotEmpty() &&
            previous.chapters.size == chapterOrder.size &&
            previous.chapters.indices.all { index ->
                val record = previous.chapters[index]
                val current = chapterOrder[index]
                record.index == index && record.name == current.name &&
                    chapterKey(record.url) == chapterKey(current.url)
            }
        ) {
            reclaimSavedChapter(previous, chapter, detail)
            return previous
        }

        val auth = authorization ?: Authorization("", "", EsjzoneUrls.BaseWithoutProtocol)
        val savedContent = existingFile?.let { readStoredSnapshot(it, writeGuard) }
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

        val committed = saveChapterPrepared(
            novelName, normalizedNovelUrl, coverUrl, chapterOrder, chapterOrderComplete,
            chapter, sourceDetail, downloadedImages, directory, writeGuard
        )
        if (committed != null) reclaimSavedChapter(committed, chapter, detail)
        return committed
    }

    private fun reclaimSavedChapter(manifest: DownloadedNovelManifest, chapter: Chapter, detail: DetailedChapter) {
        val reference = detail.sourceCacheReference ?: return
        val snapshot = synchronized(ioLock) {
            val record = manifest.chapters.firstOrNull { chapterKey(it.url) == chapterKey(chapter.url) }
                ?.takeIf { it.downloaded } ?: return
            val directory = directoryFor(manifest.url, create = false) ?: return
            val file = resolveLocalFile(directory, record.fileName) ?: return
            Triple(directory, newWriteGuard(directory), file) to contentRevision(directory)
        }
        val (directory, guard, file) = snapshot.first
        val stored = readStoredSnapshot(file, guard) ?: return
        val matches = stored.hasAllImagesOnDisk(directory) && stored.body == detail.withStructuredBody().body &&
            stored.contentHtml == detail.contentHtml
        val current = synchronized(ioLock) {
            guard.storage == storageGeneration && directory.absolutePath !in deletingDirectories &&
                guard.generation == (deletionGenerations[directory.absolutePath] ?: 0L) &&
                contentRevision(directory) == snapshot.second
        }
        if (matches && current) PageCache.removeReference(reference)
    }

    private fun saveChapterPrepared(
        novelName: String,
        novelUrl: String,
        coverUrl: String,
        chapterOrder: List<Chapter>,
        chapterOrderComplete: Boolean,
        chapter: Chapter,
        detail: DetailedChapter,
        downloadedImages: Map<String, DownloadedImage> = emptyMap(),
        directory: StorageFile,
        writeGuard: DownloadWriteGuard
    ): DownloadedNovelManifest? {
        val writeLock = lockNovelWrites(directory)
        try {
            val normalizedNovelUrl = novelUrl.trim()
            while (true) {
                val snapshot = withStoreLock("reader-save-snapshot") {
                    ensureWriteAllowed(writeGuard)
                    readManifest(directory) to contentRevision(directory)
                }
                val previous = snapshot.first
                val previousByKey = previous?.chapters.orEmpty().associateBy { chapterKey(it.url) }
                // Completeness does not establish freshness. Only seed a missing catalog;
                // subsequent reader saves rebase completions on the published order.
                val replaceCatalog = chapterOrderComplete && previous?.catalogComplete != true && previous?.complete != true
                val catalogKeys = chapterOrder.map { chapterKey(it.url) }.toHashSet()
                val ordered = buildList {
                    if (!chapterOrderComplete || replaceCatalog) addAll(chapterOrder)
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
                        localOnly = when {
                            chapterOrderComplete && !replaceCatalog -> old?.localOnly ?: true
                            chapterOrderComplete -> chapterKey(item.url) !in catalogKeys
                            else -> old?.localOnly == true
                        }
                    )
                }.toMutableList()
                // Only the first complete catalog reclassifies absent snapshots as local-only.
                if (!replaceCatalog) {
                    previous?.chapters.orEmpty()
                        .filter { chapterKey(it.url) !in knownKeys }
                        .forEach { old -> records += old.copy(index = records.size) }
                }

                val targetKey = chapterKey(chapter.url)
                val targetIndex = records.indexOfFirst { chapterKey(it.url) == targetKey }
                if (targetIndex < 0) return null
                val target = records[targetIndex]
                val targetFile = StorageFile(directory, target.fileName)
                val oldContent = readStoredSnapshot(targetFile, writeGuard)
                var savedContent: DownloadedChapterContent? = null
                if (!target.downloaded || oldContent?.hasAllImagesOnDisk(directory) != true) {
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
                    val committed = oldContent?.let { mergeChapterAssets(directory, it, storedChapter) } ?: storedChapter
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
                    complete = complete,
                    catalogComplete = chapterOrderComplete || previous?.catalogComplete == true || previous?.complete == true
                )
                val manifest = buildManifest(directory, previous, current, writeGuard,
                    replaceCatalog = replaceCatalog, revalidateChapterFiles = false,
                    changedContents = savedContent?.let { mapOf(targetKey to it) }.orEmpty())
                val bodyTemporary = savedContent?.let { prepareJson(targetFile, it, writeGuard) }
                try {
                    val manifestFile = StorageFile(directory, MANIFEST_FILE)
                    val manifestTemporary = prepareJson(manifestFile,
                        if (directory.isDocument) manifest.copy(commonPassword = null) else manifest, writeGuard)
                    try {
                        val committed = withStoreLock("reader-save-commit") {
                            ensureWriteAllowed(writeGuard)
                            if (contentRevision(directory) != snapshot.second || readManifest(directory) !== previous) false
                            else {
                                if (bodyTemporary != null) commitJson(bodyTemporary, targetFile, writeGuard)
                                if (directory.isDocument) rememberPassword(manifest)
                                commitJson(manifestTemporary, manifestFile, writeGuard)
                                publishManifest(directory, manifest)
                                true
                            }
                        }
                        if (committed) return manifest
                    } finally { manifestTemporary.delete() }
                } finally { bodyTemporary?.delete() }
            }
        } finally { writeLock.unlock() }
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

        val directory = directoryFor(novel.url, create = true, novelName = novel.name)
            ?: error("Novel download storage is unavailable")
        requireFreeSpace(directory)
        val writeGuard = newWriteGuard(directory)
        val previousManifest = synchronized(ioLock) { readManifest(directory) }
        val previousCommonPassword = previousManifest?.commonPassword?.takeIf(String::isNotBlank)
        val previousByUrl = previousManifest?.chapters
            .orEmpty()
            .associateBy { chapterKey(it.url) }

        val records = orderedChapters.mapIndexed { index, chapter ->
            currentCoroutineContext().ensureActive()
            val key = chapterKey(chapter.url)
            val previous = previousByUrl[key]
            if (selectedKeys != null && key !in selectedKeys) {
                return@mapIndexed previous?.copy(index = index, name = chapter.name, url = chapter.url, localOnly = false)
                    ?: DownloadedChapterRecord(index, chapter.name, chapter.url, chapterFileName(chapter.url), false)
            }
            val fileName = previous?.fileName ?: chapterFileName(chapter.url)
            val chapterFile = StorageFile(directory, fileName)
            val stored = readStoredSnapshot(chapterFile, writeGuard)
            val isDownloaded = stored?.hasAllImagesOnDisk(directory) == true
            DownloadedChapterRecord(
                index = index,
                name = chapter.name,
                url = chapter.url,
                fileName = fileName,
                // Chapter files are atomically renamed. A process stopped between
                // checkpoints can safely recover a completed file on the next run.
                downloaded = isDownloaded,
                requiresPassword = stored == null && previous?.requiresPassword == true,
                bodyAvailable = stored != null,
                textLength = stored?.body?.textLength
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
        val verified = records.filter { selectedKeys == null || chapterKey(it.url) in selectedKeys }
            .associateBy { chapterKey(it.url) }
        currentManifest = writeManifest(directory, currentManifest.copy(chapters = currentRecords.toList()),
            writeGuard, replaceCatalog = true, revalidateChapterFiles = false, verifiedChapters = verified,
            verificationBaseline = previousByUrl)
        val committedRecords = currentManifest.chapters.associateBy { chapterKey(it.url) }
        currentRecords.forEachIndexed { index, original ->
            committedRecords[chapterKey(original.url)]?.let { latest ->
                currentRecords[index] = original.copy(downloaded = latest.downloaded, bodyAvailable = latest.bodyAvailable,
                    textLength = latest.textLength, requiresPassword = latest.requiresPassword)
            }
        }

        val targetRecords = currentRecords.filter { selectedKeys == null || chapterKey(it.url) in selectedKeys }
        val totalCount = targetRecords.size
        val completedCounter = AtomicInteger(targetRecords.count { it.isSaved })
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

        val pendingCacheReclaims = ConcurrentHashMap<String, String>()
        fun reclaimCommitted(manifest: DownloadedNovelManifest) {
            manifest.chapters.filter { it.downloaded }.forEach { record ->
                val reference = pendingCacheReclaims.remove(chapterKey(record.url)) ?: return@forEach
                PageCache.removeReference(reference)
            }
        }

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
                                var savedContent: DownloadedChapterContent? = null

                                while (attempt < CHAPTER_MAX_ATTEMPTS && !success) {
                                    currentCoroutineContext().ensureActive()
                                    attempt++
                                    try {
                                        val download: suspend () -> Unit = {
                                            savedContent = downloadSingleChapter(
                                                authorization = authorization,
                                                record = record,
                                                directory = directory,
                                                baseUrl = baseUrl,
                                                writeGuard = writeGuard,
                                                onCacheReady = { pendingCacheReclaims[chapterKey(record.url)] = it }
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
                                                savedContent = downloadSingleChapter(
                                                    authorization = authorization,
                                                    record = record,
                                                    directory = directory,
                                                    baseUrl = baseUrl,
                                                    writeGuard = writeGuard,
                                                    password = previousCommonPassword,
                                                    onCacheReady = { pendingCacheReclaims[chapterKey(record.url)] = it }
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
                                    val saved = requireNotNull(savedContent)
                                    val fullyDownloaded = saved.hasAllImagesOnDisk(directory)
                                    val checkpoint = synchronized(ioLock) {
                                        val finished = if (!record.isSaved) completedCounter.incrementAndGet()
                                            else completedCounter.get()
                                        currentRecords[record.index] = record.copy(downloaded = fullyDownloaded,
                                            requiresPassword = false, bodyAvailable = true, textLength = saved.body?.textLength)
                                        if (finished % MANIFEST_CHECKPOINT_INTERVAL == 0 || finished == totalCount) {
                                            manifestFrom(novel, currentRecords.toList(), System.currentTimeMillis(),
                                                complete = currentRecords.all { it.downloaded }, commonPassword = previousCommonPassword)
                                        } else null
                                    }
                                    if (checkpoint != null) {
                                        val published = writeManifest(directory, checkpoint, writeGuard)
                                        synchronized(ioLock) { currentManifest = published }
                                        reclaimCommitted(published)
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
                val checkpoint = synchronized(ioLock) {
                    if (directory.isDirectory) manifestFrom(novel, currentRecords.toList(), System.currentTimeMillis(),
                        complete = currentRecords.all { it.downloaded }, commonPassword = previousCommonPassword)
                    else null
                }
                if (checkpoint != null) {
                    val published = writeManifest(directory, checkpoint, writeGuard)
                    synchronized(ioLock) { currentManifest = published }
                    reclaimCommitted(published)
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
        directory: StorageFile,
        baseUrl: String?,
        writeGuard: DownloadWriteGuard,
        password: String? = null,
        onCacheReady: (String) -> Unit = {}
    ): DownloadedChapterContent {
        val chapterFile = StorageFile(directory, record.fileName)
        val existing = readStoredSnapshot(chapterFile, writeGuard)
        val chapter = Chapter(record.name, record.url, false)
        var sourceReference: String? = null
        val stored = existing ?: run {
            val detail = if (!password.isNullOrBlank()) {
                EsjzoneClient.unlockPasswordProtectedChapter(authorization, chapter, password, baseUrl)
            } else {
                EsjzoneClient.getChapterDetail(authorization, chapter, preferDownloaded = false,
                    forceRefresh = false, baseUrl = baseUrl, allowAutoSolve = false)
            }
            sourceReference = detail.sourceCacheReference
            detail.toStoredContent(chapter)
        }
        require(stored.body?.blocks?.isNotEmpty() == true) { "Chapter content is empty" }
        val completed = stored.copy(components = stored.components.map { component ->
            if (component.type != IMAGE_COMPONENT || component.localFile?.let {
                    resolveLocalFile(directory, it)?.let { file -> file.isFile && file.length() > 0L }
                } == true) component
            else component.withDownloadedImage(downloadImage(authorization, directory, component.value,
                stored.baseUrl ?: baseUrl, writeGuard))
        })
        val committed = mergeAndWriteChapter(chapterFile, completed, directory, writeGuard)
        if (committed.body == stored.body && committed.contentHtml == stored.contentHtml) {
            // Keep the source version across image retries; reclamation still waits for the manifest.
            sourceReference?.let(onCacheReady)
        }
        // Missing images remain retryable assets; their failure must not discard a saved body
        // or fail the task. The manifest still distinguishes readable bodies from complete assets.
        return committed
    }

    private fun mergeChapterAssets(directory: StorageFile, existing: DownloadedChapterContent, incoming: DownloadedChapterContent): DownloadedChapterContent {
        val images = incoming.components.filter { it.type == IMAGE_COMPONENT }.associateBy { it.value }
        return existing.copy(components = existing.components.map { component ->
            if (component.type == IMAGE_COMPONENT && component.localFile?.let { resolveLocalFile(directory, it)?.let { f -> f.isFile && f.length() > 0L } } != true) {
                images[component.value]?.takeIf { it.localFile != null } ?: component
            } else component
        })
    }

    private fun mergeAndWriteChapter(file: StorageFile, incoming: DownloadedChapterContent,
        directory: StorageFile, guard: DownloadWriteGuard, forceRewrite: Boolean = false): DownloadedChapterContent {
        val writeLock = lockNovelWrites(directory)
        try {
            while (true) {
                val revision = synchronized(ioLock) { ensureWriteAllowed(guard); contentRevision(directory) }
                val latest = readStoredSnapshot(file, guard)
                val merged = latest?.let { mergeChapterAssets(directory, it, incoming) } ?: incoming
                val temporary = if (!forceRewrite && latest == merged) null else prepareJson(file, merged, guard)
                try {
                    val committed = withStoreLock("chapter-commit") {
                        ensureWriteAllowed(guard)
                        if (contentRevision(directory) != revision) false
                        else {
                            if (temporary != null) commitJson(temporary, file, guard)
                            true
                        }
                    }
                    if (committed) return merged
                } finally { temporary?.delete() }
            }
        } finally { writeLock.unlock() }
    }

    /** Returns a downloaded chapter without touching the network. */
    fun readChapter(chapterUrl: String, novelUrl: String? = null): DetailedChapter? {
        val started = System.nanoTime()
        var lockAcquired = started
        val snapshot: ChapterReadSnapshot
        val original: DownloadedChapterContent
        val opened: Long
        try {
            snapshot = withStoreLock("chapter-open") {
                lockAcquired = System.nanoTime()
                rootDirectory?.withMetadataSnapshot {
                    val lookupStarted = System.nanoTime()
                    val match = findChapter(chapterUrl, novelUrl) ?: return@withMetadataSnapshot null
                    val found = System.nanoTime()
                    val file = resolveLocalFile(match.directory, match.record.fileName) ?: return@withMetadataSnapshot null
                    file.recoverReplacement()
                    if (!file.isFile) return@withMetadataSnapshot null
                    val checked = System.nanoTime()
                    // Keep a stable handle across replacement; decode outside the store lock.
                    val reader = file.storedTextReader()
                    AppLogger.i("ReaderTiming", "chapter lookupMs=${(found - lookupStarted) / 1_000_000} " +
                        "fileCheckMs=${(checked - found) / 1_000_000} handleMs=${(System.nanoTime() - checked) / 1_000_000}")
                    ChapterReadSnapshot(match, newWriteGuard(match.directory), mutableChanges.value, reader)
                }
            } ?: return null
            opened = System.nanoTime()
            original = snapshot.reader.use { gson.fromJson(it, DownloadedChapterContent::class.java) }
                ?: return null
        } catch (error: Exception) {
            if (error is com.breakyuna.esjzone.storage.StorageAccessException || error is CancellationException) throw error
            AppLogger.w("NovelDownloadStore", "Unable to read downloaded chapter", error)
            return null
        }
        val decoded = System.nanoTime()
        val stored = decodeStoredChapter(original) ?: return null
        val validated = System.nanoTime()
        val match = snapshot.match
        val locations = imageLocations(match.directory, stored)
        val content = restoreComponents(stored, locations)
        val assetsPrepared = System.nanoTime()
        var guardAcquired = assetsPrepared
        withStoreLock("chapter-validate") {
            guardAcquired = System.nanoTime()
            if (snapshot.guard.storage != storageGeneration ||
                snapshot.guard.generation != (deletionGenerations[snapshot.guard.directoryPath] ?: 0L)) {
                throw CancellationException("Download storage changed or the novel was removed")
            }
        }
        val formattingRecovered = original.body?.formatting != stored.body?.formatting
        if (original.schemaVersion != 2 || formattingRecovered ||
            match.record.textLength != stored.body?.textLength || !match.record.bodyAvailable) {
            // Migration and optional metadata repair must not delay first publication.
            readMaintenanceScope.launch {
                runCatching {
                    val current = synchronized(ioLock) { readManifest(match.directory) }
                    val currentRecord = current?.chapters?.firstOrNull { chapterKey(it.url) == chapterKey(chapterUrl) }
                    val file = currentRecord?.let { resolveLocalFile(match.directory, it.fileName) }
                    val latest = file?.let { readStoredSnapshot(it, snapshot.guard) }
                    if ((original.schemaVersion != 2 || formattingRecovered) && file != null && latest != null) {
                        mergeAndWriteChapter(file, latest, match.directory, snapshot.guard, forceRewrite = true)
                    }
                    if (current != null && currentRecord != null && latest != null &&
                        (currentRecord.textLength != latest.body?.textLength || !currentRecord.bodyAvailable)) {
                        writeManifest(match.directory, current.copy(chapters = current.chapters.map {
                            if (chapterKey(it.url) == chapterKey(chapterUrl))
                                it.copy(textLength = latest.body?.textLength, bodyAvailable = true) else it
                        }), snapshot.guard)
                    }

                }.onFailure { error ->
                    if (error !is CancellationException && error !is StorageMigrationBusyException)
                        AppLogger.w("NovelDownloadStore", "Unable to maintain downloaded chapter metadata", error)
                }
            }
        }
        val previous = match.manifest.chapters.getOrNull(match.record.index - 1)
            ?.toChapter()
        val next = match.manifest.chapters.getOrNull(match.record.index + 1)
            ?.toChapter()

        val fullyLocal = match.record.downloaded && stored.components.filter { it.type == IMAGE_COMPONENT }.all {
            it.localFile != null && locations[it.value] != it.value
        }
        AppLogger.i("ReaderTiming", "local waitMs=${(lockAcquired - started) / 1_000_000} " +
            "openMs=${(opened - lockAcquired) / 1_000_000} decodeMs=${(decoded - opened) / 1_000_000} " +
            "validateMs=${(validated - decoded) / 1_000_000} " +
            "assetsMs=${(assetsPrepared - validated) / 1_000_000} " +
            "guardWaitMs=${(guardAcquired - assetsPrepared) / 1_000_000} totalMs=${(System.nanoTime() - started) / 1_000_000}")
        return DetailedChapter(
            name = stored.name,
            content = content,
            previous = previous,
            next = next,
            contentHtml = stored.contentHtml,
            sourceUrl = stored.baseUrl ?: stored.url,
            body = stored.body,
            imageLocations = locations,
            downloadedSourceRevision = snapshot.revision.takeIf { fullyLocal && original.schemaVersion == 2 },
            servedFromDownload = true
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
            adultStatusKnown = stored.adultStatusKnown,
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

    fun repairNovelName(novelUrl: String, name: String): Boolean {
        val title = validNovelTitle(name, novelUrl = novelUrl)
        if (title.isBlank()) return false
        val directory = directoryFor(novelUrl, create = false) ?: return false
        val guard = newWriteGuard(directory)
        val previous = synchronized(ioLock) { readManifest(directory) } ?: return false
        if (validNovelTitle(previous.name, novelUrl = previous.url).isNotBlank()) return false
        val repaired = writeManifest(directory, previous.copy(name = title), guard, onlyRepairName = true)
        return repaired.name == title
    }

    /** Recompute displayed history progress after a catalog gains chapters, without moving its anchor. */
    fun refreshProgress(activity: com.breakyuna.esjzone.database.entity.LocalReadingActivity): com.breakyuna.esjzone.database.entity.LocalReadingActivity = synchronized(ioLock) {
        // Reuse unchanged catalog projections without repeated document-provider queries.
        novelDirectories[canonicalKey(activity.novelUrl)]?.let { known ->
            if (known.absolutePath !in deletingDirectories) historyProgressCache[known.absolutePath]?.let {
                return@synchronized it.refresh(activity)
            }
        }
        val directory = directoryFor(activity.novelUrl, create = false) ?: return@synchronized activity
        val manifest = readManifest(directory) ?: return@synchronized activity
        if (!manifest.catalogComplete) return@synchronized activity
        historyProgressCache.getOrPut(directory.absolutePath) { DownloadedHistoryProgress(manifest) }
            .refresh(activity)
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

    fun imageFile(novelUrl: String, component: DownloadedComponent): StorageFile? = synchronized(ioLock) {
        val relative = component.localFile ?: return@synchronized null
        val directory = directoryFor(novelUrl, create = false) ?: return@synchronized null
        resolveLocalFile(directory, relative)?.takeIf(StorageFile::isFile)
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
        catalogComplete = true,
        adultStatusKnown = novel.adultStatusKnown
    )

    private fun manifestFromMetadata(
        previous: DownloadedNovelManifest?,
        name: String,
        url: String,
        coverUrl: String,
        records: List<DownloadedChapterRecord>,
        downloadedAt: Long,
        complete: Boolean,
        commonPassword: String? = previous?.commonPassword,
        catalogComplete: Boolean = previous?.catalogComplete == true || previous?.complete == true
    ) = DownloadedNovelManifest(
        version = 2,
        name = preferredNovelTitle(name, previous?.name.orEmpty(), novelUrl = url),
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
        catalogComplete = catalogComplete,
        adultStatusKnown = previous?.adultStatusKnown == true
    )

    private fun findChapter(chapterUrl: String, novelUrl: String?): ChapterMatch? {
        val target = chapterKey(chapterUrl)
        chapterIndex[target]?.let { return it }
        if (chapterIndexLoaded) return null
        // The reader supplies its parent for external chapters. ESJ chapter routes carry it directly.
        val parent = novelUrl?.takeIf(String::isNotBlank)
            ?: Regex("^/forum/([^/]+)/[^/]+\\.html(?:#.*)?$").matchEntire(target)
                ?.groupValues?.get(1)?.let { "/detail/$it.html" }
        if (parent != null) {
            val directory = directoryFor(parent, create = false) ?: return null
            if (directory.absolutePath !in chapterKeysByDirectory) {
                val manifest = readManifest(directory) ?: return null
                indexChapterManifest(directory, manifest)
            }
            // A readable checkpoint needs no recovery scan of unfinished catalog entries.
            // Recover on demand even when this directory was indexed by an earlier read.
            if (target !in chapterIndex) {
                val manifest = readManifest(directory) ?: return null
                indexChapterManifest(directory, manifest, recoverUnpublishedFiles = true)
            }
            return chapterIndex[target]
        }
        ensureNovelDirectoryIndex()
        novelDirectories.values.forEach { directory ->
            if (directory.absolutePath in deletingDirectories) return@forEach
            val manifest = readManifest(directory) ?: return@forEach
            indexChapterManifest(directory, manifest, recoverUnpublishedFiles = true)
        }
        chapterIndexLoaded = true
        return chapterIndex[target]
    }

    private fun removeChapterIndex(directory: StorageFile) {
        chapterKeysByDirectory.remove(directory.absolutePath).orEmpty().forEach { key ->
            if (chapterIndex[key]?.directory == directory) chapterIndex.remove(key)
        }
    }

    private fun indexChapterManifest(
        directory: StorageFile,
        manifest: DownloadedNovelManifest,
        recoverUnpublishedFiles: Boolean = false
    ) {
        val knownKeys = chapterKeysByDirectory[directory.absolutePath].orEmpty()
        removeChapterIndex(directory)
        val keys = HashSet<String>()
        // One directory listing replaces a provider query for every unpublished chapter.
        val unpublishedFiles = if (recoverUnpublishedFiles && manifest.chapters.any {
            !it.bodyAvailable && !it.downloaded && chapterKey(it.url) !in knownKeys
        }) directory.entries().filterNot { it.directory }.mapTo(HashSet()) { it.file.name }
            else emptySet()
        manifest.chapters.forEach { record ->
            val key = chapterKey(record.url)
            // After restart, recover files committed just before an interrupted manifest checkpoint.
            if (record.bodyAvailable || record.downloaded || key in knownKeys ||
                (recoverUnpublishedFiles && (record.fileName in unpublishedFiles ||
                    ('/' in record.fileName && resolveLocalFile(directory, record.fileName)?.isFile == true)))) {
                chapterIndex.putIfAbsent(key, ChapterMatch(directory, manifest, record))
                keys += key
            }
        }
        chapterKeysByDirectory[directory.absolutePath] = keys
    }

    private fun imageLocations(directory: StorageFile, stored: DownloadedChapterContent): Map<String, String> {
        val images = stored.components.filter { it.type == IMAGE_COMPONENT }
        val files = images.mapNotNull { it.localFile?.let { path -> resolveLocalFile(directory, path) } }
        // SAF metadata properties each issue a provider query. List each asset folder
        // once, retaining type/size from that same query for all images in this chapter.
        val available = if (directory.isDocument) {
            files.mapNotNull { it.parentFile }.distinct().flatMap { parent ->
                val entries = try {
                    parent.entries()
                } catch (error: StorageAccessException) {
                    // Cached SAF URIs can outlive an externally deleted images folder.
                    // exists() also verifies tree access, so provider failures still surface.
                    if (parent.exists()) throw error
                    emptyList()
                }
                entries.filter { !it.directory && it.size > 0L }.map { it.file }
            }.toHashSet()
        } else files.filter { it.isFile && it.length() > 0L }.toHashSet()
        return images.associate { image ->
            image.value to (image.localFile?.let { resolveLocalFile(directory, it) }
                ?.takeIf { it in available }?.uriString ?: image.value)
        }
    }

    /** A stable read handle lets download preflight decode without excluding unrelated readers. */
    private fun readStoredSnapshot(file: StorageFile, guard: DownloadWriteGuard): DownloadedChapterContent? {
        return try {
            val reader = synchronized(ioLock) {
                ensureWriteAllowed(guard)
                file.recoverReplacement()
                if (!file.isFile) return null
                file.storedTextReader()
            }
            val stored = reader.use { gson.fromJson(it, DownloadedChapterContent::class.java) } ?: return null
            val decoded = decodeStoredChapter(stored)
            synchronized(ioLock) { ensureSnapshotCurrent(guard) }
            decoded
        } catch (error: Exception) {
            if (error is StorageAccessException || error is CancellationException || error is StorageMigrationBusyException) throw error
            AppLogger.w("NovelDownloadStore", "Unable to read downloaded chapter during validation", error)
            null
        }
    }

    private fun readStoredChapter(directory: StorageFile, file: StorageFile): DownloadedChapterContent? = synchronized(ioLock) {
        val stored = readJson(file, DownloadedChapterContent::class.java) ?: return@synchronized null
        val decoded = decodeStoredChapter(stored) ?: return@synchronized null
        if (stored.schemaVersion != 2 || stored.body?.formatting != decoded.body?.formatting) {
            // Reading remains possible if storage is full; the original survives a failed rename.
            runCatching { writeJson(file, decoded, newWriteGuard(directory)) }
        }
        decoded
    }

    private fun decodeStoredChapter(stored: DownloadedChapterContent): DownloadedChapterContent? {
        if (stored.schemaVersion == 2) {
            return runCatching {
                val body = requireNotNull(stored.body).validate()
                    .withFormatting(stored.contentHtml, stored.baseUrl ?: stored.url)
                if (body === stored.body) stored else stored.copy(body = body)
            }.getOrNull()
        }
        if (stored.schemaVersion !in 0..1) throw IOException("Unsupported downloaded chapter version")
        return runCatching {
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
            stored.copy(schemaVersion = 2, body = ChapterBody.from(components)
                .withFormatting(html, stored.baseUrl ?: stored.url))
        }.getOrNull()
    }

    private fun restoreComponents(stored: DownloadedChapterContent, locations: Map<String, String>): List<Component> {
        return requireNotNull(stored.body).components { locations[it] ?: it }.map { component ->
            if (component is ImageComponent && component.url.isBlank()) TextComponent(missingImageLabel()) else component
        }
    }

    private fun directoryFor(novelUrl: String, create: Boolean, novelName: String = ""): StorageFile? = synchronized(ioLock) {
        val root = rootDirectory ?: return@synchronized null
        if (create && (mutableMigrating.value || backupRestoreInProgress)) return@synchronized null
        val key = canonicalKey(novelUrl)
        if (!novelDirectoryIndexLoaded && directoryIndexCache == null) ensureNovelDirectoryIndex()
        if (!novelDirectoryIndexLoaded && key !in novelDirectories) {
            val hints = loadDirectoryHints(root)
            val folder = hints?.directories?.entries?.firstOrNull { it.value == key }?.key
            if (folder != null && folder != digest(key) && !isStagingDirectory(folder)) {
                val hinted = runCatching { StorageFile(root, folder) }.getOrNull()
                root.restoreDocumentHint(hints?.rootDocumentUri)
                hinted?.restoreDocumentHint(hints?.documentUris?.get(folder))
                if (hinted != null && folder !in readRetiredDirectories(root) && hinted.isDirectory &&
                    readManifest(hinted)?.let { canonicalKey(it.url) == key } == true) {
                    novelDirectories[key] = hinted
                } else {
                    // A renamed/reused folder must be rediscovered from its actual manifest identity.
                    savedDirectoryHints = null
                    directoryHintsRead = true
                    directoryIndexCache?.delete()
                }
            }
        }
        if (key !in novelDirectories) ensureNovelDirectoryIndex()
        novelDirectories[key]?.takeIf { it.absolutePath in deletingDirectories }?.let { return@synchronized null }
        novelDirectories[key]?.let { directory ->
            // The persisted directory index is only a hint. Always verify the requested
            // book against its manifest before using it (folders can be replaced externally).
            if (directory.isDirectory && readManifest(directory)?.let { canonicalKey(it.url) == key } != false) {
                return@synchronized directory
            }
            novelDirectories.remove(key)
            novelDirectoryIndexLoaded = false
            directoryIndexCache?.delete()
            directoryHintsRead = false
            savedDirectoryHints = null
            ensureNovelDirectoryIndex()
            novelDirectories[key]?.let { return@synchronized it }
        }
        if (!create) return@synchronized null
        prepareDownloadRoot(root)
        val directory = availableNovelDirectory(root, novelName, key)
        if (!directory.mkdirs()) return@synchronized null
        novelDirectories[key] = directory
        directory
    }

    private fun prepareDownloadRoot(root: StorageFile) {
        check(root.mkdirs())
        if (root.isDocument) {
            val marker = StorageFile(root, ".nomedia")
            if (!marker.exists()) marker.outputStream().close()
        }
    }

    private fun scanNovelDirectories(
        root: StorageFile, migrationFailureReason: StorageTransferFailureReason? = null,
        directoryHints: Map<String, String> = emptyMap()
    ): LinkedHashMap<String, StorageFile> {
        val directories = LinkedHashMap<String, StorageFile>()
        if (!root.exists()) return directories
        val retired = readRetiredDirectories(root)
        root.entries().filter { it.directory && !isStagingDirectory(it.file.name) && it.file.name !in retired }.map { it.file }
            .sortedBy(StorageFile::name).forEach { directory ->
                val key = directoryHints[directory.name] ?: readManifest(directory, migrationFailureReason)
                    ?.url?.takeIf(String::isNotBlank)?.let(::canonicalKey) ?: return@forEach
                directories.putIfAbsent(key, directory)
            }
        return directories
    }

    private fun isStagingDirectory(name: String): Boolean =
        Regex("(?:migration|restore)-[a-zA-Z0-9-]+\\.tmp").matches(name)

    /** Lazy so a disconnected public volume never blocks startup and access to Settings. */
    private fun ensureNovelDirectoryIndex(migrationFailureReason: StorageTransferFailureReason? = null) {
        if (novelDirectoryIndexLoaded) return
        val root = rootDirectory ?: return
        val hints = if (migrationFailureReason == null) readDirectoryHints(root) else emptyMap()
        val directories = scanNovelDirectories(root, migrationFailureReason, hints)
        val retired = readRetiredDirectories(root)
        novelDirectories.forEach { (key, directory) ->
            if (key !in directories && directory.name !in retired && directory.isDirectory) directories[key] = directory
        }
        if (renameLegacyDirectories && !mutableMigrating.value) directories.entries.forEach { entry ->
            val directory = entry.value
            if (directory.name != digest(entry.key)) return@forEach
            val manifest = readManifest(directory) ?: return@forEach
            val named = availableNovelDirectory(root, manifest.name, entry.key)
            // A provider may deny renaming. Keep the original readable and retry next process.
            val renamed = runCatching { directory.renameTo(named) }.getOrDefault(false)
            if (renamed) {
                manifestCache.remove(directory.absolutePath)
                entry.setValue(named)
            }
        }
        novelDirectories.clear()
        novelDirectories.putAll(directories)
        novelDirectoryIndexLoaded = true
        renameLegacyDirectories = false
        saveDirectoryHints()
    }

    /** Alternates read hints written before the keep rule; a mismatched root is ignored anyway. */
    private data class DirectoryHints(
        @SerializedName(value = "root", alternate = ["a"]) val root: String,
        @SerializedName(value = "directories", alternate = ["b"]) val directories: Map<String, String>,
        @SerializedName("rootDocumentUri") val rootDocumentUri: String? = null,
        @SerializedName("documentUris") val documentUris: Map<String, String>? = null
    )

    private fun loadDirectoryHints(root: StorageFile): DirectoryHints? {
        if (!directoryHintsRead) {
            savedDirectoryHints = runCatching {
                val file = directoryIndexCache ?: return@runCatching null
                if (!file.isFile) return@runCatching null
                file.reader().use { gson.fromJson(it, DirectoryHints::class.java) }
                    ?.takeIf { it.root == root.absolutePath && it.directories != null }
            }.getOrNull()
            directoryHintsRead = true
        }
        return savedDirectoryHints?.takeIf { it.root == root.absolutePath }
    }

    private fun readDirectoryHints(root: StorageFile): Map<String, String> =
        loadDirectoryHints(root)?.directories.orEmpty()

    /** Private, disposable name-to-identity hints; no chapter data or credentials. */
    private fun saveDirectoryHints() {
        val file = directoryIndexCache ?: return
        val root = rootDirectory ?: return
        runCatching {
            val temporary = File(file.parentFile, "${file.name}.tmp")
            try {
                temporary.writer().use { writer ->
                    val previous = if (!novelDirectoryIndexLoaded) loadDirectoryHints(root) else null
                    val known = novelDirectories.entries.associate { it.value.name to it.key }
                    val folders = previous?.directories.orEmpty().filterValues { it !in known.values } + known
                    val documents = (previous?.documentUris.orEmpty() + novelDirectories.values.mapNotNull { directory ->
                        directory.documentUriHint()?.let { directory.name to it }
                    }.toMap()).filterKeys { it in folders }
                    val hints = DirectoryHints(root.absolutePath, folders,
                        root.documentUriHint() ?: previous?.rootDocumentUri, documents)
                    gson.toJson(hints, writer)
                    savedDirectoryHints = hints
                    directoryHintsRead = true
                }
                check(temporary.renameTo(file))
            } finally { temporary.delete() }
        }.onFailure { AppLogger.w("NovelDownloadStore", "Unable to cache download directory index", it) }
    }

    private fun availableNovelDirectory(root: StorageFile, name: String, key: String): StorageFile {
        val base = novelDirectoryName(name)
        val retired = readRetiredDirectories(root)
        var candidate = StorageFile(root, base)
        val suffix = digest(key).take(8)
        var number = 1
        while (candidate.name in retired || candidate.exists()) {
            candidate = StorageFile(root, "$base ($suffix${if (number == 1) "" else "-$number"})")
            number += 1
        }
        return candidate
    }

    internal fun novelDirectoryName(name: String): String {
        val cleaned = name.map { character ->
            if (character < ' ' || character == '\u007f' || character in "<>:\"/\\|?*") '_' else character
        }.joinToString("").trim().trim('.').ifBlank { "Untitled" }
            .let { if (isStagingDirectory(it) || it == RETIRED_DIRECTORIES_FILE) "${it}_" else it }
        // Leave room for a collision suffix within the usual filesystem component limit.
        var bytes = 0
        val shortened = buildString {
            for (codePoint in cleaned.codePoints().toArray()) {
                val character = String(Character.toChars(codePoint))
                val size = character.toByteArray(StandardCharsets.UTF_8).size
                if (bytes + size > 180) break
                append(character)
                bytes += size
            }
        }
        return shortened.trimEnd(' ', '.').ifBlank { "Untitled" }
    }

    private fun readManifest(
        directory: StorageFile?, migrationFailureReason: StorageTransferFailureReason? = null
    ): DownloadedNovelManifest? {
        if (directory == null) return null
        val key = directory.absolutePath
        if (migrationFailureReason == null && manifestCache.containsKey(key)) return manifestCache[key]
        return decodeManifest(directory, migrationFailureReason).also { manifestCache[key] = it }
    }

    private fun decodeManifest(directory: StorageFile,
        migrationFailureReason: StorageTransferFailureReason? = null): DownloadedNovelManifest? {
        val file = StorageFile(directory, MANIFEST_FILE)
        return try {
            validateManifest(directory, readJson(file, DownloadedNovelManifest::class.java,
                propagateErrors = migrationFailureReason != null)).also {
                if (migrationFailureReason != null && it == null && file.exists()) {
                    throw IOException("Invalid downloaded novel manifest")
                }
            }
        } catch (error: Exception) {
            if (migrationFailureReason == null || error is CancellationException) throw error
            val reason = if (error is com.breakyuna.esjzone.storage.StorageAccessException)
                StorageTransferFailureReason.ACCESS else migrationFailureReason
            throw StorageTransferException(StorageTransferFailure(reason,
                "downloads/${directory.name}/$MANIFEST_FILE"), error)
        }
    }

    private fun validateManifest(directory: StorageFile, manifest: DownloadedNovelManifest?): DownloadedNovelManifest? =
        manifest?.also {
            if (it.version !in 1..2) throw IOException("Unsupported downloaded novel version")
            require(!it.url.isNullOrBlank()) { "Missing downloaded novel URL" }
            requireNotNull(it.name) { "Missing downloaded novel title" }
            requireNotNull(it.coverUrl)
            requireNotNull(it.type)
            requireNotNull(it.author)
            requireNotNull(it.forumUrl)
            requireNotNull(it.description)
            requireNotNull(it.tags)
            requireNotNull(it.chapters) { "Missing downloaded chapter list" }.forEach { record ->
                requireNotNull(record)
                require(!record.url.isNullOrBlank() && !record.fileName.isNullOrBlank())
                requireNotNull(record.name)
            }
        }?.let { stored ->
            if (directory.isDocument) stored.copy(commonPassword = privatePasswords?.getString(digest(canonicalKey(stored.url)), null))
            else stored
        }

    private data class ManifestReadSnapshot(val guard: DownloadWriteGuard, val revision: Long,
        val reader: java.io.Reader?)

    /** Open under the replacement lock, then decode JSON without excluding foreground reads. */
    private fun readManifestSnapshot(directory: StorageFile,
        checkActive: () -> Unit = {}): DownloadedNovelManifest? {
        while (true) {
            checkActive()
            val snapshot = withStoreLock("manifest-open") {
                if (directory.parentFile != rootDirectory || directory.absolutePath in deletingDirectories ||
                    backupRestoreInProgress) return null
                if (manifestCache.containsKey(directory.absolutePath)) return manifestCache[directory.absolutePath]
                val file = StorageFile(directory, MANIFEST_FILE)
                file.recoverReplacement()
                ManifestReadSnapshot(newWriteGuard(directory), mutableChanges.value,
                    if (file.isFile) file.storedTextReader() else null)
            }
            val manifest = validateManifest(directory, runCatching {
                snapshot.reader?.use { gson.fromJson(it, DownloadedNovelManifest::class.java) }
            }.onFailure { error ->
                if (error is com.breakyuna.esjzone.storage.StorageAccessException || error is CancellationException) throw error
                AppLogger.w("NovelDownloadStore", "Unable to decode download manifest", error)
            }.getOrNull())
            checkActive()
            synchronized(ioLock) {
                if (snapshot.guard.storage != storageGeneration || directory.absolutePath in deletingDirectories ||
                    snapshot.guard.generation != (deletionGenerations[directory.absolutePath] ?: 0L)) return null
                if (manifestCache.containsKey(directory.absolutePath)) return manifestCache[directory.absolutePath]
                if (snapshot.revision == mutableChanges.value && !backupRestoreInProgress) {
                    manifestCache[directory.absolutePath] = manifest
                    return manifest
                }
            }
        }
    }

    private fun writeManifest(
        directory: StorageFile,
        incoming: DownloadedNovelManifest,
        writeGuard: DownloadWriteGuard? = null,
        replaceCatalog: Boolean = false,
        updatePassword: Boolean = false,
        changedContents: Map<String, DownloadedChapterContent> = emptyMap(),
        revalidateChapterFiles: Boolean = true,
        verifiedChapters: Map<String, DownloadedChapterRecord> = emptyMap(),
        verificationBaseline: Map<String, DownloadedChapterRecord>? = null,
        onlySeedCatalog: Boolean = false,
        onlyRepairName: Boolean = false
    ): DownloadedNovelManifest {
        val writeLock = lockNovelWrites(directory)
        try {
            check(!Thread.holdsLock(ioLock)) { "Manifest preparation must run outside the storage lock" }
            val guard = writeGuard ?: newWriteGuard(directory)
            while (true) {
                val snapshot = withStoreLock("manifest-snapshot") {
                    ensureWriteAllowed(guard)
                    val existing = readManifest(directory)
                    if (onlySeedCatalog && existing != null && (existing.catalogComplete || existing.complete)) return existing
                    if (onlyRepairName && existing != null && validNovelTitle(existing.name, novelUrl = existing.url).isNotBlank()) return existing
                    existing to contentRevision(directory)
                }
                val manifest = buildManifest(directory, snapshot.first, incoming, guard, replaceCatalog,
                    updatePassword, changedContents, revalidateChapterFiles, verifiedChapters, verificationBaseline)
                val file = StorageFile(directory, MANIFEST_FILE)
                val temporary = prepareJson(file, if (directory.isDocument) manifest.copy(commonPassword = null) else manifest, guard)
                try {
                    val committed = withStoreLock("manifest-commit") {
                        ensureWriteAllowed(guard)
                        if (contentRevision(directory) != snapshot.second || readManifest(directory) !== snapshot.first) false
                        else {
                            if (directory.isDocument) rememberPassword(manifest)
                            commitJson(temporary, file, guard)
                            publishManifest(directory, manifest)
                            true
                        }
                    }
                    if (committed) return manifest
                } finally { temporary.delete() }
            }
        } finally { writeLock.unlock() }
    }

    private fun buildManifest(
        directory: StorageFile, existing: DownloadedNovelManifest?, incoming: DownloadedNovelManifest,
        writeGuard: DownloadWriteGuard, replaceCatalog: Boolean = false, updatePassword: Boolean = false,
        changedContents: Map<String, DownloadedChapterContent> = emptyMap(),
        revalidateChapterFiles: Boolean = true, verifiedChapters: Map<String, DownloadedChapterRecord> = emptyMap(),
        verificationBaseline: Map<String, DownloadedChapterRecord>? = null
    ): DownloadedNovelManifest {
        val update = if (updatePassword && existing != null) existing.copy(commonPassword = incoming.commonPassword) else incoming
        return mergeDownloadManifest(existing, update, replaceCatalog).let { rebased ->
            val merged = if (updatePassword) rebased.copy(commonPassword = incoming.commonPassword) else rebased
            val oldRecords = existing?.chapters.orEmpty().associateBy { chapterKey(it.url) }
            val records = merged.chapters.map { mergedRecord ->
                val key = chapterKey(mergedRecord.url)
                val verified = verifiedChapters[key]?.takeUnless {
                    verificationBaseline != null && oldRecords[key] != verificationBaseline[key]
                }
                val record = if (verified == null) mergedRecord else mergedRecord.copy(
                    downloaded = verified.downloaded, bodyAvailable = verified.bodyAvailable,
                    textLength = verified.textLength, requiresPassword = verified.requiresPassword)
                val previous = oldRecords[key]
                // Reader writes supply changed content explicitly; catalog-only
                // records reuse metadata without querying every chapter file.
                if (key !in changedContents && (!revalidateChapterFiles || (!replaceCatalog && previous != null &&
                    previous.downloaded == record.downloaded && previous.bodyAvailable == record.bodyAvailable &&
                    previous.textLength == record.textLength && previous.fileName == record.fileName))) {
                    return@map record.copy(requiresPassword = if (record.downloaded) false else record.requiresPassword)
                }
                val content = changedContents[key] ?: resolveLocalFile(directory, record.fileName)
                    ?.let { readStoredSnapshot(it, writeGuard) }
                record.copy(bodyAvailable = content != null, downloaded = content?.hasAllImagesOnDisk(directory) == true,
                    textLength = content?.body?.textLength, requiresPassword = content == null && record.requiresPassword)
            }
            merged.copy(version = 2, chapters = records,
                complete = merged.catalogComplete && records.any { !it.localOnly } && records.filterNot { it.localOnly }.all { it.downloaded })
        }
    }

    /** Commit-side cache publication; callers hold ioLock and have checked the prepared revision. */
    private fun publishManifest(directory: StorageFile, manifest: DownloadedNovelManifest): DownloadedNovelManifest {
        val previousInventory = inventorySnapshot
        manifestCache[directory.absolutePath] = manifest
        saveDirectoryHints()
        historyProgressCache.remove(directory.absolutePath)
        metadataSnapshot = metadataSnapshot?.filterNot { canonicalKey(it.novelUrl) == canonicalKey(manifest.url) }?.let {
            val count = manifest.savedChapterCount
            (if (count > 0) it + DownloadedNovelSummary(manifest, count, -1L) else it)
                .sortedByDescending { row -> row.manifest.downloadedAt }
        }
        publishChange(manifest.url)
        if (chapterIndexLoaded || directory.absolutePath in chapterKeysByDirectory) {
            indexChapterManifest(directory, manifest)
        }
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
        novelDirectory: StorageFile,
        rawUrl: String,
        baseUrl: String?
    ): DownloadedImage? {
        if (rawUrl.isBlank()) return null
        val url = EsjzoneUrls.resolve(rawUrl, baseUrl ?: EsjzoneUrls.Base)
        val existing = synchronized(ioLock) { imageIndex.find(novelDirectory, digest(url)) } ?: return null
        return DownloadedImage(
            relativeName = "images/${existing.name}",
            mediaType = mediaTypeFromUrl(existing.name)
        )
    }

    private fun downloadImage(
        authorization: Authorization,
        novelDirectory: StorageFile,
        rawUrl: String,
        baseUrl: String?,
        writeGuard: DownloadWriteGuard? = null
    ): DownloadedImage? {
        if (rawUrl.isBlank()) return null
        return runCatching {
            val url = EsjzoneUrls.resolve(rawUrl, baseUrl ?: EsjzoneUrls.Base)
            val imagePrefix = "image-${digest(url)}."
            val imagesDirectory = StorageFile(novelDirectory, "images")
            synchronized(ioLock) { imageIndex.find(novelDirectory, digest(url)) }
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
                val destination = StorageFile(novelDirectory, relativeName)
                if (!destination.isFile || destination.length() == 0L) {
                    val temporary = StorageFile.createTempFile("coil_${destination.nameWithoutExtension}_", ".tmp", imagesDirectory)
                    try {
                        cachedFile.inputStream().use { input -> temporary.outputStream().use { input.copyTo(it) } }
                        synchronized(ioLock) {
                            ensureWriteAllowed(writeGuard)
                            moveReplacing(temporary, destination)
                            imageIndex.committed(novelDirectory, destination)
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
                        val destination = StorageFile(novelDirectory, relativeName)
                        if (!destination.isFile || destination.length() == 0L) {
                            val temporary = StorageFile.createTempFile("dl_${destination.nameWithoutExtension}_", ".tmp", imagesDirectory)
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
                                    imageIndex.committed(novelDirectory, destination)
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

    private fun DownloadedChapterContent.hasAllImagesOnDisk(directory: StorageFile): Boolean =
        components.filter { it.type == IMAGE_COMPONENT }.all { image ->
            val relative = image.localFile ?: return@all false
            resolveLocalFile(directory, relative)?.let { it.isFile && it.length() > 0L } == true
        }

    private fun prepareJson(file: StorageFile, value: Any, writeGuard: DownloadWriteGuard): StorageFile {
        val parent = requireNotNull(file.parentFile)
        val (temporary, stream) = withStoreLock("write-open") {
            ensureWriteAllowed(writeGuard)
            if (!parent.exists()) parent.mkdirs()
            val prefix = (file.nameWithoutExtension.take(16).ifBlank { "temp" } + "_").takeLast(20).padStart(3, '_')
            val temporary = StorageFile.createTempFile(prefix, ".tmp", parent)
            try { temporary to temporary.outputStream() }
            catch (error: Throwable) { temporary.delete(); throw error }
        }
        try {
            stream.use { output ->
                if (file.name == MANIFEST_FILE) {
                    output.bufferedWriter(StandardCharsets.UTF_8).use { gson.toJson(value, it) }
                } else {
                    java.util.zip.GZIPOutputStream(output.buffered()).bufferedWriter(StandardCharsets.UTF_8)
                        .use { gson.toJson(value, it) }
                }
            }
            return temporary
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    private fun commitJson(temporary: StorageFile, file: StorageFile, writeGuard: DownloadWriteGuard) {
        ensureWriteAllowed(writeGuard)
        moveReplacing(temporary, file)
        contentWriteRevisions[writeGuard.directoryPath] =
            (contentWriteRevisions[writeGuard.directoryPath] ?: 0L) + 1L
    }

    private fun writeJson(file: StorageFile, value: Any, writeGuard: DownloadWriteGuard) {
        val temporary = prepareJson(file, value, writeGuard)
        try { synchronized(ioLock) { commitJson(temporary, file, writeGuard) } }
        finally { temporary.delete() }
    }

    private fun moveReplacing(temporary: StorageFile, destination: StorageFile) {
        destination.replaceWith(temporary)
    }

    private fun <T> readJson(file: StorageFile, type: Class<T>, propagateErrors: Boolean = false): T? {
        return runCatching {
            file.recoverReplacement()
            if (!file.isFile) return@runCatching null
            file.storedTextReader().use { gson.fromJson(it, type) }
        }.onFailure { error ->
            // Lost access must reach the UI; it is not evidence that a chapter is absent/corrupt.
            if (propagateErrors || error is com.breakyuna.esjzone.storage.StorageAccessException || error is CancellationException) throw error
            AppLogger.w("NovelDownloadStore", "Unable to read ${file.name}", error)
        }.getOrNull()
    }

    private fun chapterFileName(url: String): String = "chapter-${digest(chapterKey(url))}.json"

    private fun resolveLocalFile(directory: StorageFile, relative: String): StorageFile? =
        runCatching { StorageFile(directory, relative) }.getOrNull()

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

    private data class ChapterReadSnapshot(
        val match: ChapterMatch,
        val guard: DownloadWriteGuard,
        val revision: Long,
        val reader: java.io.Reader
    )

    private data class ChapterMatch(
        val directory: StorageFile,
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

package com.breakyuna.esjzone.database

import com.breakyuna.esjzone.EsjzoneApplication
import com.breakyuna.esjzone.app.CoverLoadingPolicy
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.data.settings.SettingsDefaults
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.util.AppLogger
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.decode.BlackholeDecoder
import androidx.room.withTransaction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Durable local copies of bookshelf covers.
 *
 * Coil remains the common image cache for the rest of the app, while every
 * visible bookshelf item gets a stable app-files copy.  The file name includes
 * the remote image hash, so an updated cover replaces the previous file.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object BookshelfCoverStore {
    private const val DIRECTORY_NAME = "bookshelf_covers"
    private const val BATCH_SIZE = 4
    private const val BATCH_COOLDOWN_MILLIS = 300L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val maintenanceMutex = Mutex()
    private var maintenanceJob: Job? = null
    private var scheduledKeys = emptySet<Pair<String, String>>()
    private val changes = MutableStateFlow(0L)
    private val downloadSemaphore = Semaphore(2)
    private val homePreloadSemaphore = Semaphore(2)

    /** Home covers enter the shared image queue before new shelf persistence downloads. */
    suspend fun preloadHomeCovers(urls: List<String>): Set<String> {
        if (urls.isEmpty()) return emptySet()
        CoverLoadingPolicy.homePreloadStarted()
        try {
            return coroutineScope {
                urls.map { source ->
                    async {
                        homePreloadSemaphore.withPermit {
                            val request = ImageRequest.Builder(EsjzoneApplication.instance)
                                .data(EsjzoneUrls.resolve(source))
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .decoderFactory(BlackholeDecoder.Factory())
                                .apply {
                                    if (source.toHttpUrlOrNull()?.host?.let(EsjzoneUrls::isEsjHost) == true) {
                                        val key = EsjzoneUrls.canonicalCacheUrl(source)
                                        diskCacheKey(key)
                                    }
                                }
                                .build()
                            source.takeIf { PresentationAccess.imageLoader.execute(request) is SuccessResult }
                        }
                    }
                }.awaitAll().filterNotNull().toSet()
            }
        } finally {
            CoverLoadingPolicy.homePreloadFinished()
        }
    }

    private val directory: File
        get() = File(EsjzoneApplication.instance.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    internal fun canonicalSource(rawUrl: String): String {
        val trimmed = EsjzoneUrls.coverOrEmpty(rawUrl).trim()
        if (trimmed.isBlank()) return ""
        val parsed = trimmed.toHttpUrlOrNull() ?: return trimmed
        return if (EsjzoneUrls.isEsjHost(parsed.host)) {
            "esj:${parsed.encodedPath}${parsed.encodedQuery?.let { "?$it" }.orEmpty()}"
        } else {
            parsed.toString().substringBefore('#')
        }
    }

    private fun digest(identity: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    internal fun fileName(entry: BookshelfEntry): String {
        val source = canonicalSource(entry.coverUrl)
        val identity = "${entry.bookKey}|$source"
        return "thumb_v1_${digest(identity)}.img"
    }

    internal fun originalFileNames(entry: BookshelfEntry): List<String> =
        listOf(fileName(entry).replaceFirst("thumb_v1_", "cover_")) + legacyFileNames(entry)

    internal fun legacyFileNames(entry: BookshelfEntry): List<String> {
        val trimmed = EsjzoneUrls.coverOrEmpty(entry.coverUrl).trim()
        if (trimmed.isBlank()) return emptyList()
        val parsed = trimmed.toHttpUrlOrNull()
        val isEsj = parsed != null && EsjzoneUrls.isEsjHost(parsed.host)

        val candidateSources = buildList {
            add(trimmed)
            if (isEsj && parsed != null) {
                SettingsDefaults.DOMAINS.forEach { domain ->
                    runCatching {
                        parsed.newBuilder().host(domain).build().toString()
                    }.getOrNull()?.let(::add)
                }
            }
        }.distinct()

        return candidateSources.map { source ->
            val identity = "${entry.bookKey}|$source"
            "cover_${digest(identity)}.img"
        }.distinct()
    }

    private fun fileFor(entry: BookshelfEntry): File {
        val target = File(directory, fileName(entry))
        if (target.isFile && target.length() > 0L) return target
        for (legacyName in originalFileNames(entry)) {
            val legacy = File(directory, legacyName)
            if (legacy.isFile && legacy.length() > 0L) {
                return legacy
            }
        }
        return target
    }

    /** Returns a durable local URI when present, otherwise the original URL. */
    fun localOrRemote(entry: BookshelfEntry): String {
        if (entry.coverUrl.isBlank()) return ""
        val local = fileFor(entry)
        return if (local.isFile && local.length() > 0L) local.toURI().toString() else entry.coverUrl
    }

    /** Resolve each visible cover off the UI thread; keep it stable across recompositions. */
    @Composable
    fun rememberLocalOrRemote(entry: BookshelfEntry): String {
        return key(entry.bookKey, entry.coverUrl) {
            // Wait for the local lookup before requesting a remote cover.
            produceState(initialValue = "") {
                changes.collect {
                    value = withContext(Dispatchers.IO) { localOrRemote(entry) }
                }
            }.value
        }
    }

    /** Schedules a throttled, best-effort persistence pass without blocking shelf UI. */
    @Synchronized
    fun schedulePersist(entries: List<BookshelfEntry>) {
        val keys = entries.filter { it.visible }.map { it.bookKey to it.coverUrl }.toSet()
        if (maintenanceJob?.isActive == true && keys == scheduledKeys) return
        scheduledKeys = keys
        maintenanceJob?.cancel()
        maintenanceJob = scope.launch {
            try {
                maintenanceMutex.withLock {
                    val allEntries = pruneUnused()
                    // Shrink existing files across accounts before waiting on any network work.
                    val pending = (entries + allEntries.filter { fileFor(it).isFile })
                        .filter { it.visible && EsjzoneUrls.coverOrEmpty(it.coverUrl).isNotBlank() }
                        .distinctBy(::fileName)
                        .filter { File(directory, fileName(it)).length() <= 0L }
                    val (local, remote) = pending.partition { fileFor(it).isFile }
                    persistBatches(local)
                    pruneUnused()
                    persistBatches(remote)
                    pruneUnused()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.w("BookshelfCoverStore", "Unable to maintain shelf covers", error)
            }
        }
    }

    private suspend fun persistBatches(entries: List<BookshelfEntry>) {
        entries.chunked(BATCH_SIZE).forEachIndexed { index, batch ->
            coroutineScope {
                batch.map { entry -> async { downloadSemaphore.withPermit { persist(entry) } } }.awaitAll()
            }
            if (index < entries.lastIndex / BATCH_SIZE) delay(BATCH_COOLDOWN_MILLIS)
        }
    }

    private suspend fun pruneUnused(): List<BookshelfEntry> {
        val database = PresentationAccess.database
        return database.withTransaction {
            val entries = database.bookshelfDao().getAllVisibleWithCovers()
            pruneUnused(directory, entries)
            entries
        }
    }

    /** Called with all scopes, never a filtered/current-account shelf. */
    internal fun pruneUnused(directory: File, entries: List<BookshelfEntry>) {
        val retained = entries.filter { it.visible }.flatMap { entry ->
            val thumbnail = fileName(entry)
            if (File(directory, thumbnail).length() > 0L) listOf(thumbnail)
            else listOf(thumbnail) + originalFileNames(entry)
        }.toHashSet()
        directory.listFiles()?.filter { file ->
            file.isFile && file.name.endsWith(".img") &&
                (file.name.startsWith("cover_") || file.name.startsWith("thumb_v1_")) &&
                file.name !in retained
        }?.forEach { it.delete() }
    }

    private suspend fun persist(entry: BookshelfEntry) = withContext(Dispatchers.IO) {
        val target = File(directory, fileName(entry))
        if (target.isFile && target.length() > 0L) return@withContext
        val source = EsjzoneUrls.coverOrEmpty(entry.coverUrl)
        if (source.isBlank()) return@withContext

        // First use an already-downloaded Coil source.  This avoids duplicate
        // transfers for the covers visible while the shelf is opened.
        val existing = fileFor(entry).takeIf { it.isFile && it.length() > 0L }
        val cached = existing ?: BookmarkCoverStore.findCoilCachedCover(source)
        if (cached != null && writeThumbnail(cached, target)) return@withContext

        // A cold shelf still needs to become fully offline-capable.  Loading via
        // the app-scoped Coil loader keeps this request in the same cache and
        // networking pipeline as normal image rendering.
        try {
            CoverLoadingPolicy.state
                .map { it.allowBackgroundNetwork("BOOKSHELF") }
                .distinctUntilChanged()
                .transformLatest { allowed ->
                    if (!allowed) return@transformLatest
                    val request = ImageRequest.Builder(EsjzoneApplication.instance)
                        .data(EsjzoneUrls.resolve(source))
                        .decoderFactory(BlackholeDecoder.Factory())
                        .apply {
                            if (source.toHttpUrlOrNull()?.host?.let(EsjzoneUrls::isEsjHost) == true) {
                                diskCacheKey(EsjzoneUrls.canonicalCacheUrl(source))
                            }
                        }
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .build()
                    PresentationAccess.imageLoader.execute(request)
                    BookmarkCoverStore.findCoilCachedCover(source)?.let { writeThumbnail(it, target) }
                    emit(Unit)
                }.first()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.w("BookshelfCoverStore", "Unable to persist cover for ${entry.bookKey}", error)
        }
    }

    private suspend fun writeThumbnail(source: File, target: File): Boolean {
        if (!source.isFile || source.length() <= 0L) return false
        return try {
            val context = currentCoroutineContext()
            writeBookshelfThumbnail(source, target) { context.ensureActive() }
            changes.update { it + 1 }
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.w("BookshelfCoverStore", "Unable to prepare shelf cover", error)
            false
        }
    }
}

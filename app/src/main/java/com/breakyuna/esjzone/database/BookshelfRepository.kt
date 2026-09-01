package com.breakyuna.esjzone.database

import com.breakyuna.esjzone.database.dao.BookshelfDao
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.database.entity.BookshelfSyncState
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.features.getAllFavorites
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.network.features.toggleFavorite
import com.breakyuna.esjzone.network.hasCredentials
import com.breakyuna.esjzone.novellibrary.novel.Novel
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.novellibrary.novel.FavoriteNovel
import com.breakyuna.esjzone.util.AppLogger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock

/** Result of a best-effort remote synchronization. Local rows are never removed by import. */
data class BookshelfSyncResult(
    val success: Boolean,
    val added: Int = 0
)

/**
 * Single owner of the local-first shelf state machine. UI reads only its Room
 * flow; all remote work is serialized here and writes back into Room.
 *
 * Authorization currently exposes no stable user id, so rows are scoped by
 * domain only. This intentionally preserves offline data across cookie/session
 * rotation; account switching on one domain remains a known trade-off until a
 * stable account identifier is available.
 */
object BookshelfRepository {
    private lateinit var dao: BookshelfDao
    private val syncMutex = Mutex()
    private val intentMutex = Mutex()
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scheduledScopes = ConcurrentHashMap.newKeySet<String>()
    private val rescheduleScopes = ConcurrentHashMap.newKeySet<String>()
    private val metadataAttempts = ConcurrentHashMap<String, Long>()
    private val metadataSemaphore = Semaphore(2)

    /** Avoid repeatedly refetching rows that are known to have no cover. */
    private const val METADATA_RETRY_INTERVAL_MILLIS = 30 * 60 * 1000L

    fun initialize(database: GeneralDatabase) {
        dao = database.bookshelfDao()
    }

    private fun requireDao(): BookshelfDao {
        check(::dao.isInitialized) { "BookshelfRepository has not been initialized" }
        return dao
    }

    fun scopeFor(authorization: Authorization): String {
        val domain = authorization.domain.ifBlank { EsjzoneUrls.BaseWithoutProtocol }
        return "domain:$domain"
    }

    fun keyFor(url: String): String =
        EsjzoneUrls.canonicalPageKey(url).ifBlank { EsjzoneUrls.resolve(url).substringBefore('#') }

    /** Extracts ESJ's stable detail id from a canonical /detail/{id}.html URL. */
    fun novelIdFor(url: String): String =
        Regex("^/detail/([^/]+?)(?:\\.html)?/?$")
            .find(EsjzoneUrls.canonicalPageKey(url))
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()

    fun observe(authorization: Authorization): Flow<List<BookshelfEntry>> =
        requireDao().observeVisible(scopeFor(authorization))

    fun observeEntry(authorization: Authorization, url: String): Flow<BookshelfEntry?> =
        requireDao().observe(scopeFor(authorization), keyFor(url))

    /** Applies a local intent immediately, then schedules a serialized remote retry. */
    suspend fun setFavorite(authorization: Authorization, novel: Novel, desired: Boolean) {
        intentMutex.withLock {
            val dao = requireDao()
            val scope = scopeFor(authorization)
            val key = keyFor(novel.url)
            val current = dao.find(scope, key)
            val nextVersion = (current?.operationVersion ?: 0L) + 1L
            val suppliedCover = (novel as? CoveredNovel)?.coverUrl
                ?.let { EsjzoneUrls.coverOrEmpty(it) }
                .orEmpty()
            val retainedCover = current?.coverUrl?.takeIf { it.isNotBlank() } ?: suppliedCover
            val next = if (desired) {
                BookshelfEntry(
                    scope = scope,
                    bookKey = key,
                    novelId = current?.novelId?.takeIf { it.isNotBlank() } ?: novelIdFor(novel.url),
                    url = EsjzoneUrls.resolve(novel.url).substringBefore('#'),
                    title = novel.name,
                    author = current?.author.orEmpty(),
                    coverUrl = retainedCover,
                    isAdult = current?.isAdult ?: false,
                    addedAt = if (current?.syncState == BookshelfSyncState.PENDING_REMOVE) {
                        System.currentTimeMillis()
                    } else {
                        current?.addedAt ?: System.currentTimeMillis()
                    },
                    syncState = if (current?.syncState == BookshelfSyncState.SYNCED) {
                        BookshelfSyncState.SYNCED
                    } else {
                        BookshelfSyncState.PENDING_ADD
                    },
                    visible = true,
                    retryCount = 0,
                    lastError = null,
                    operationVersion = nextVersion
                )
            } else {
                BookshelfEntry(
                    scope = scope,
                    bookKey = key,
                    novelId = current?.novelId?.takeIf { it.isNotBlank() } ?: novelIdFor(novel.url),
                    url = EsjzoneUrls.resolve(novel.url).substringBefore('#'),
                    title = novel.name,
                    author = current?.author.orEmpty(),
                    coverUrl = retainedCover,
                    isAdult = current?.isAdult ?: false,
                    addedAt = current?.addedAt ?: System.currentTimeMillis(),
                    syncState = BookshelfSyncState.PENDING_REMOVE,
                    visible = false,
                    retryCount = 0,
                    lastError = null,
                    operationVersion = nextVersion
                )
            }
            dao.upsert(next)
        }
        scheduleSync(authorization)
    }

    /** Seeds a remote favorite or supplements missing metadata without changing intent state. */
    suspend fun seedRemoteFavorite(
        authorization: Authorization,
        novel: Novel,
        author: String = "",
        coverUrl: String = "",
        isAdult: Boolean = false
    ) {
        val dao = requireDao()
        val scope = scopeFor(authorization)
        val key = keyFor(novel.url)
        val existing = dao.find(scope, key)
        if (existing == null) {
            dao.insertIfAbsent(
                BookshelfEntry(
                    scope = scope,
                    bookKey = key,
                    novelId = novelIdFor(novel.url),
                    url = EsjzoneUrls.resolve(novel.url).substringBefore('#'),
                    title = novel.name,
                    author = author,
                    coverUrl = coverUrl,
                    isAdult = isAdult,
                    syncState = BookshelfSyncState.SYNCED
                )
            )
        } else {
            dao.supplementMetadata(scope, key, novel.name, author, coverUrl, isAdult)
        }
    }

    fun scheduleSync(authorization: Authorization) {
        if (authorization.hasCredentials()) {
            val scope = scopeFor(authorization)
            if (!scheduledScopes.add(scope)) {
                // A new local intent arrived while the current sync was in
                // flight. Run one more pass after it finishes.
                rescheduleScopes.add(scope)
                return
            }
            workerScope.launch {
                try {
                    sync(authorization)
                } finally {
                    scheduledScopes.remove(scope)
                    if (rescheduleScopes.remove(scope)) scheduleSync(authorization)
                }
            }
        }
    }

    /**
     * Completes metadata missing from cloud favorite rows in the background.
     * This is intentionally best effort: a failed/empty detail response does
     * not alter the stored URL and is retried only after a short in-process
     * cooldown. At most two detail pages are fetched concurrently.
     */
    fun scheduleMetadataSupplement(authorization: Authorization) {
        if (!authorization.hasCredentials()) return
        workerScope.launch {
            val dao = requireDao()
            val scope = scopeFor(authorization)
            val now = System.currentTimeMillis()
            val candidates = dao.getAll(scope)
                .asSequence()
                .filter { it.visible && it.coverUrl.isBlank() && it.url.isNotBlank() }
                .filter { row ->
                    val attemptKey = "$scope:${row.bookKey}"
                    val previous = metadataAttempts.putIfAbsent(attemptKey, now)
                    previous == null ||
                        (now - previous >= METADATA_RETRY_INTERVAL_MILLIS &&
                            metadataAttempts.replace(attemptKey, previous, now))
                }
                .toList()

            coroutineScope {
                candidates.map { row ->
                    launch {
                        metadataSemaphore.withPermit {
                            try {
                                val detail = EsjzoneClient.getNovelDetail(
                                    authorization,
                                    FavoriteNovel(
                                        row.title,
                                        row.url
                                    )
                                )
                                // supplementMetadata only fills blank fields,
                                // so a late response cannot replace a newer
                                // local/detail-page value.
                                dao.supplementMetadata(
                                    scope = scope,
                                    bookKey = row.bookKey,
                                    title = detail.name,
                                    author = detail.author,
                                    coverUrl = EsjzoneUrls.coverOrEmpty(detail.coverUrl),
                                    isAdult = detail.isAdult
                                )
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                AppLogger.w(
                                    "BookshelfRepository",
                                    "Metadata supplement unavailable for ${row.bookKey}",
                                    error
                                )
                            }
                        }
                    }
                }.joinAll()
            }
        }
    }

    suspend fun sync(authorization: Authorization): BookshelfSyncResult = syncMutex.withLock {
        val dao = requireDao()
        val scope = scopeFor(authorization)
        val remote = try {
            // A complete, successfully parsed snapshot is required before any
            // import. An exception leaves every local row untouched.
            EsjzoneClient.getAllFavorites(authorization, forceRefresh = true)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.w("BookshelfRepository", "Remote shelf snapshot unavailable; keeping local rows", error)
            return@withLock BookshelfSyncResult(success = false)
        }
        val remoteByKey = remote.associateBy { keyFor(it.url) }.filterKeys { it.isNotBlank() }
            .let { byKey ->
                BookshelfSyncRules.deduplicateRemoteKeys(byKey.keys).associateWith { byKey.getValue(it) }
            }
        var added = 0
        var operationFailed = false

        // Resolve pending intents against the snapshot before importing it.
        dao.getAll(scope).filter {
            it.syncState == BookshelfSyncState.PENDING_ADD ||
                it.syncState == BookshelfSyncState.PENDING_REMOVE
        }.forEach { local ->
            val remoteHas = remoteByKey.containsKey(local.bookKey)
            if (local.syncState == BookshelfSyncState.PENDING_ADD) {
                if (remoteHas) {
                    val current = dao.find(scope, local.bookKey)
                    if (current != null && BookshelfSyncRules.shouldApplyResponse(
                            current.operationVersion, local.operationVersion
                        )
                    ) {
                        dao.updateStateIfVersion(scope, local.bookKey, local.operationVersion,
                            BookshelfSyncState.SYNCED, visible = true)
                    }
                } else if (EsjzoneClient.toggleFavorite(authorization, local)) {
                    val current = dao.find(scope, local.bookKey)
                    if (current != null && BookshelfSyncRules.shouldApplyResponse(
                            current.operationVersion, local.operationVersion
                        )
                    ) {
                        dao.updateStateIfVersion(scope, local.bookKey, local.operationVersion,
                            BookshelfSyncState.SYNCED, visible = true)
                    }
                } else {
                    operationFailed = true
                    dao.markRetry(scope, local.bookKey, local.operationVersion, "favorite request failed")
                }
            } else if (!remoteHas) {
                val current = dao.find(scope, local.bookKey)
                if (current != null && BookshelfSyncRules.shouldApplyResponse(
                        current.operationVersion, local.operationVersion
                    )
                ) {
                    dao.deleteIfVersion(scope, local.bookKey, local.operationVersion)
                }
            } else if (EsjzoneClient.toggleFavorite(authorization, local)) {
                val current = dao.find(scope, local.bookKey)
                if (current != null && BookshelfSyncRules.shouldApplyResponse(
                        current.operationVersion, local.operationVersion
                    )
                ) {
                    dao.deleteIfVersion(scope, local.bookKey, local.operationVersion)
                }
            } else {
                operationFailed = true
                dao.markRetry(scope, local.bookKey, local.operationVersion, "unfavorite request failed")
            }
        }

        // Import only cloud-only entries. Never update local metadata or remove
        // rows based on a missing/empty cloud entry.
        val currentRows = dao.getAll(scope)
        val localKeys = currentRows.mapTo(mutableSetOf<String>()) { it.bookKey }
        val tombstoneKeys = currentRows
            .filter { it.syncState == BookshelfSyncState.PENDING_REMOVE }
            .mapTo(mutableSetOf<String>()) { it.bookKey }
        remoteByKey.values.forEach { remoteNovel ->
            val key = keyFor(remoteNovel.url)
            if (BookshelfSyncRules.shouldImport(key, localKeys, tombstoneKeys)) {
                val inserted = dao.insertIfAbsent(
                    BookshelfEntry(
                        scope = scope,
                        bookKey = key,
                        novelId = novelIdFor(remoteNovel.url),
                        url = EsjzoneUrls.resolve(remoteNovel.url).substringBefore('#'),
                        title = remoteNovel.name,
                        isAdult = false,
                        syncState = BookshelfSyncState.SYNCED
                    )
                )
                if (inserted != -1L) {
                    localKeys += key
                    added += 1
                }
            }
        }
        scheduleMetadataSupplement(authorization)
        BookshelfSyncResult(success = !operationFailed, added = added)
    }
}

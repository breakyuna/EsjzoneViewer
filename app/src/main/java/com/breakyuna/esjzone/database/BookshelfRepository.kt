package com.breakyuna.esjzone.database

import com.breakyuna.esjzone.database.dao.BookshelfDao
import com.breakyuna.esjzone.database.dao.LocalReadingActivityDao
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.database.entity.BookshelfSyncState
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.network.features.getAllFavorites
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.network.features.toggleFavorite
import com.breakyuna.esjzone.network.hasCredentials
import com.breakyuna.esjzone.novellibrary.novel.Novel
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.novellibrary.novel.FavoriteNovel
import com.breakyuna.esjzone.util.AppLogger
import androidx.room.withTransaction
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock

/** Result of a best-effort remote synchronization. Local rows are never removed by import. */
data class BookshelfSyncResult(
    val success: Boolean,
    val added: Int = 0,
    val loadFailure: LoadFailureKind? = null
)

private data class MetadataSupplementItem(
    val bookKey: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val isAdult: Boolean
)

/**
 * Single owner of the local-first shelf state machine. UI reads only its Room
 * flow; all remote work is serialized here and writes back into Room.
 *
 * Rows are scoped by account identity and domain, providing complete
 * isolation across account switches while preserving offline data across
 * transparent cookie rotations. Legacy domain-only scopes are smoothly migrated
 * on first access for single-account upgrades.
 */
object BookshelfRepository {
    private lateinit var database: GeneralDatabase
    private lateinit var dao: BookshelfDao
    private lateinit var localReadingDao: LocalReadingActivityDao
    private val syncMutex = Mutex()
    private val intentMutex = Mutex()
    private const val MAX_SYNC_RETRIES = 5
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scheduledScopes = ConcurrentHashMap.newKeySet<String>()
    private val rescheduleScopes = ConcurrentHashMap.newKeySet<String>()
    private val metadataAttempts = ConcurrentHashMap<String, Long>()
    private val metadataSemaphore = Semaphore(2)

    /** Avoid repeatedly refetching rows that are known to have no cover. */
    private const val METADATA_RETRY_INTERVAL_MILLIS = 30 * 60 * 1000L
    private const val METADATA_BATCH_SIZE = 36
    private const val METADATA_BATCH_COOLDOWN_MILLIS = 3_000L

    fun initialize(database: GeneralDatabase) {
        this.database = database
        dao = database.bookshelfDao()
        localReadingDao = database.localReadingActivityDao()
    }

    private fun requireDatabase(): GeneralDatabase {
        check(::database.isInitialized) { "BookshelfRepository has not been initialized" }
        return database
    }

    private fun requireDao(): BookshelfDao {
        check(::dao.isInitialized) { "BookshelfRepository has not been initialized" }
        return dao
    }

    fun scopeFor(authorization: Authorization): String {
        return EsjzoneClient.accountScope(authorization)
    }

    suspend fun migrateLegacyScopeIfNeeded(authorization: Authorization) {
        val domain = authorization.domain.ifBlank { EsjzoneUrls.BaseWithoutProtocol }
        val legacyScope = "domain:$domain"
        val targetScope = scopeFor(authorization)
        if (legacyScope == targetScope) return

        val dao = requireDao()
        val targetCount = dao.count(targetScope)
        if (targetCount == 0) {
            val legacyCount = dao.count(legacyScope)
            if (legacyCount > 0) {
                dao.migrateScope(oldScope = legacyScope, newScope = targetScope)
                AppLogger.i("BookshelfRepository", "Migrated $legacyCount legacy bookshelf items to $targetScope")
            }
        }
    }

    /** An old cache-scoped shelf can only be assigned after the user explicitly claims it. */
    suspend fun pendingLegacyBookshelfCount(authorization: Authorization): Int {
        val oldScope = EsjzoneClient.pendingLegacyBookshelfScope(authorization) ?: return 0
        return requireDao().count(oldScope)
    }

    suspend fun claimLegacyBookshelf(authorization: Authorization): Int = intentMutex.withLock {
        val oldScope = EsjzoneClient.pendingLegacyBookshelfScope(authorization) ?: return@withLock 0
        val targetScope = scopeFor(authorization)
        val moved = requireDao().migrateScope(oldScope, targetScope)
        EsjzoneClient.clearPendingLegacyBookshelfScope(authorization)
        if (moved > 0) scheduleSync(authorization)
        moved
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

    /** Clears the dot only when the reader actually opens the known latest chapter. */
    suspend fun markLatestRead(chapterUrl: String) {
        val fingerprint = EsjzoneUrls.canonicalPageKey(chapterUrl).ifBlank { chapterUrl.substringBefore('#') }
        if (fingerprint.isNotBlank()) requireDao().clearUpdateForFingerprint(fingerprint)
    }

    fun observe(authorization: Authorization): Flow<List<BookshelfEntry>> = combine(
        requireDao().observeVisible(scopeFor(authorization)),
        localReadingDao.observeAll()
    ) { entries, activities ->
        withContext(Dispatchers.Default) {
            BookshelfSort.sort(
                entries = entries,
                activities = activities,
                keyForUrl = ::keyFor
            )
        }
    }.distinctUntilChanged()

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
            val crossScope = if (current == null || current.coverUrl.isBlank() || current.author.isBlank()) {
                dao.findAnyWithCover(key)
            } else null
            val fallbackCover = crossScope?.coverUrl?.takeIf { it.isNotBlank() }?.let { rawCover ->
                runCatching { EsjzoneUrls.resolve(rawCover) }.getOrDefault(rawCover)
            }.orEmpty()
            val retainedCover = current?.coverUrl?.takeIf { it.isNotBlank() }
                ?: suppliedCover.takeIf { it.isNotBlank() }
                ?: fallbackCover
            val author = current?.author?.takeIf { it.isNotBlank() } ?: crossScope?.author.orEmpty()
            val isAdult = current?.isAdult ?: crossScope?.isAdult ?: false
            val base = current ?: BookshelfEntry(
                scope = scope,
                bookKey = key,
                url = EsjzoneUrls.resolve(novel.url).substringBefore('#'),
                title = novel.name
            )
            val next = if (desired) {
                base.copy(
                    scope = scope,
                    bookKey = key,
                    novelId = current?.novelId?.takeIf { it.isNotBlank() } ?: novelIdFor(novel.url),
                    url = EsjzoneUrls.resolve(novel.url).substringBefore('#'),
                    title = novel.name,
                    author = author,
                    coverUrl = retainedCover,
                    isAdult = isAdult,
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
                base.copy(
                    scope = scope,
                    bookKey = key,
                    novelId = current?.novelId?.takeIf { it.isNotBlank() } ?: novelIdFor(novel.url),
                    url = EsjzoneUrls.resolve(novel.url).substringBefore('#'),
                    title = novel.name,
                    author = author,
                    coverUrl = retainedCover,
                    isAdult = isAdult,
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

    /** Applies multiple local removal intents in one transaction and schedules one retry pass. */
    suspend fun removeBatch(
        authorization: Authorization,
        entries: List<BookshelfEntry>
    ): Int {
        val removed = intentMutex.withLock {
            val dao = requireDao()
            val scope = scopeFor(authorization)
            val intents = entries.mapNotNull { snapshot ->
                val current = dao.find(scope, snapshot.bookKey) ?: return@mapNotNull null
                if (!current.visible || current.syncState == BookshelfSyncState.PENDING_REMOVE) {
                    return@mapNotNull null
                }
                current.copy(
                    syncState = BookshelfSyncState.PENDING_REMOVE,
                    visible = false,
                    retryCount = 0,
                    lastError = null,
                    operationVersion = current.operationVersion + 1L
                )
            }
            if (intents.isNotEmpty()) {
                dao.upsertRemovalIntents(intents)
            }
            intents.size
        }
        if (removed > 0) scheduleSync(authorization)
        return removed
    }

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
        val crossScope = if (coverUrl.isBlank() || author.isBlank()) {
            dao.findAnyWithCover(key)
        } else null
        val fallbackCover = crossScope?.coverUrl?.takeIf { it.isNotBlank() }?.let { rawCover ->
            runCatching { EsjzoneUrls.resolve(rawCover) }.getOrDefault(rawCover)
        }.orEmpty()
        val effectiveCover = coverUrl.takeIf { it.isNotBlank() } ?: fallbackCover
        val effectiveAuthor = author.takeIf { it.isNotBlank() } ?: crossScope?.author.orEmpty()
        val effectiveIsAdult = isAdult || (crossScope?.isAdult ?: false)
        if (existing == null) {
            dao.insertIfAbsent(
                BookshelfEntry(
                    scope = scope,
                    bookKey = key,
                    novelId = novelIdFor(novel.url),
                    url = EsjzoneUrls.resolve(novel.url).substringBefore('#'),
                    title = novel.name,
                    author = effectiveAuthor,
                    coverUrl = effectiveCover,
                    isAdult = effectiveIsAdult,
                    syncState = BookshelfSyncState.SYNCED
                )
            )
        } else {
            dao.supplementMetadata(scope, key, novel.name, effectiveAuthor, effectiveCover, effectiveIsAdult)
            dao.updateCoverIfChanged(scope, key, EsjzoneUrls.coverOrEmpty(effectiveCover))
        }
    }

    fun scheduleSync(authorization: Authorization, delayMillis: Long = 0L) {
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
                    if (delayMillis > 0L) delay(delayMillis)
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

            val initialCandidates = dao.getAll(scope)
                .filter { it.visible && it.coverUrl.isBlank() && it.url.isNotBlank() }

            if (initialCandidates.isNotEmpty()) {
                requireDatabase().withTransaction {
                    for (row in initialCandidates) {
                        val existing = dao.findAnyWithCover(row.bookKey)
                        if (existing != null && existing.coverUrl.isNotBlank()) {
                            val resolvedCover = runCatching { EsjzoneUrls.resolve(existing.coverUrl) }
                                .getOrDefault(existing.coverUrl)
                            dao.supplementMetadata(
                                scope = scope,
                                bookKey = row.bookKey,
                                title = existing.title.takeIf { it.isNotBlank() } ?: row.title,
                                author = existing.author,
                                coverUrl = resolvedCover,
                                isAdult = existing.isAdult
                            )
                        }
                    }
                }
            }

            delay(2_500L)
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

            candidates.chunked(METADATA_BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                coroutineScope {
                    val batchResults = batch.map { row ->
                        async {
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
                                    MetadataSupplementItem(
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
                                    null
                                }
                            }
                        }
                    }.awaitAll().filterNotNull()

                    if (batchResults.isNotEmpty()) {
                        requireDatabase().withTransaction {
                            for (item in batchResults) {
                                dao.supplementMetadata(
                                    scope = scope,
                                    bookKey = item.bookKey,
                                    title = item.title,
                                    author = item.author,
                                    coverUrl = item.coverUrl,
                                    isAdult = item.isAdult
                                )
                            }
                        }
                    }
                }
                if (batchIndex < candidates.lastIndex / METADATA_BATCH_SIZE) {
                    delay(METADATA_BATCH_COOLDOWN_MILLIS)
                }
            }
        }
    }

    suspend fun sync(authorization: Authorization, manualRetry: Boolean = false): BookshelfSyncResult = syncMutex.withLock {
        migrateLegacyScopeIfNeeded(authorization)
        val dao = requireDao()
        val scope = scopeFor(authorization)
        if (manualRetry) {
            dao.resetFailedIntents(scope)
        }
        val remote = try {
            // A complete, successfully parsed snapshot is required before any
            // import. An exception leaves every local row untouched.
            com.breakyuna.esjzone.network.cancellablePageRequest {
                EsjzoneClient.getAllFavorites(authorization, forceRefresh = true)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.w("BookshelfRepository", "Remote shelf snapshot unavailable; keeping local rows", error)
            return@withLock BookshelfSyncResult(success = false, loadFailure = error.loadFailureKind())
        }
        val remoteByKey = remote.associateBy { keyFor(it.url) }.filterKeys { it.isNotBlank() }
            .let { byKey ->
                BookshelfSyncRules.deduplicateRemoteKeys(byKey.keys).associateWith { byKey.getValue(it) }
            }
        var added = 0
        var operationFailed = false

        val pendingRows = dao.getAll(scope).filter {
            it.syncState == BookshelfSyncState.PENDING_ADD ||
                it.syncState == BookshelfSyncState.PENDING_REMOVE
        }.filter { it.retryCount < MAX_SYNC_RETRIES }
        val initialRemovalKeys = pendingRows
            .filter { it.syncState == BookshelfSyncState.PENDING_REMOVE }
            .mapTo(mutableSetOf()) { it.bookKey }
        val processedRemovalKeys = mutableSetOf<String>()

        // Resolve pending intents against the snapshot before importing it.
        var remoteCallCount = 0
        pendingRows.forEach { local ->
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
                } else {
                    if (remoteCallCount > 0) delay(150)
                    val current = dao.find(scope, local.bookKey)
                    if (current == null || current.operationVersion != local.operationVersion ||
                        current.syncState != BookshelfSyncState.PENDING_ADD) {
                        return@forEach
                    }
                    remoteCallCount++
                    if (EsjzoneClient.toggleFavorite(authorization, local)) {
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
                        if (local.retryCount + 1 >= MAX_SYNC_RETRIES) {
                            dao.markFailed(scope, local.bookKey, local.operationVersion, "favorite request failed")
                        } else dao.markRetry(scope, local.bookKey, local.operationVersion, "favorite request failed")
                    }
                }
            } else {
                processedRemovalKeys += local.bookKey
                if (!remoteHas) {
                    val current = dao.find(scope, local.bookKey)
                    if (current != null && BookshelfSyncRules.shouldApplyResponse(
                            current.operationVersion, local.operationVersion
                        )
                    ) {
                        dao.deleteIfVersion(scope, local.bookKey, local.operationVersion)
                    }
                } else {
                    if (remoteCallCount > 0) delay(150)
                    val current = dao.find(scope, local.bookKey)
                    if (current == null || current.operationVersion != local.operationVersion ||
                        current.syncState != BookshelfSyncState.PENDING_REMOVE) {
                        return@forEach
                    }
                    remoteCallCount++
                    if (EsjzoneClient.toggleFavorite(authorization, local)) {
                        val current = dao.find(scope, local.bookKey)
                        if (current != null && BookshelfSyncRules.shouldApplyResponse(
                                current.operationVersion, local.operationVersion
                            )
                        ) {
                            dao.deleteIfVersion(scope, local.bookKey, local.operationVersion)
                        }
                    } else {
                        operationFailed = true
                        if (local.retryCount + 1 >= MAX_SYNC_RETRIES) {
                            dao.markFailed(scope, local.bookKey, local.operationVersion, "unfavorite request failed")
                        } else dao.markRetry(scope, local.bookKey, local.operationVersion, "unfavorite request failed")
                    }
                }
            }
        }

        // Import only cloud-only entries. Never update local metadata or remove
        // rows based on a missing/empty cloud entry.
        val currentRows = dao.getAll(scope)
        val localKeys = currentRows.mapTo(mutableSetOf<String>()) { it.bookKey }
        val tombstoneKeys = BookshelfSyncRules.excludedImportKeys(
            initialTombstoneKeys = initialRemovalKeys,
            processedRemovalKeys = processedRemovalKeys
        ).toMutableSet().apply {
            addAll(
                currentRows
                    .filter { it.syncState == BookshelfSyncState.PENDING_REMOVE }
                    .map { it.bookKey }
            )
        }
        remoteByKey.values.forEach { remoteNovel ->
            val key = keyFor(remoteNovel.url)
            if (BookshelfSyncRules.shouldImport(key, localKeys, tombstoneKeys)) {
                val existing = dao.findAnyWithCover(key)
                val resolvedCover = existing?.coverUrl?.takeIf { it.isNotBlank() }?.let { rawCover ->
                    runCatching { EsjzoneUrls.resolve(rawCover) }.getOrDefault(rawCover)
                }.orEmpty()
                val inserted = dao.insertIfAbsent(
                    BookshelfEntry(
                        scope = scope,
                        bookKey = key,
                        novelId = novelIdFor(remoteNovel.url),
                        url = EsjzoneUrls.resolve(remoteNovel.url).substringBefore('#'),
                        title = remoteNovel.name,
                        author = existing?.author.orEmpty(),
                        coverUrl = resolvedCover,
                        isAdult = existing?.isAdult ?: false,
                        syncState = BookshelfSyncState.SYNCED
                    )
                )
                if (inserted != -1L) {
                    localKeys += key
                    added += 1
                }
            }
        }
        // The favorite page itself exposes the latest chapter and update time;
        // persist that snapshot without touching local favorite/remove intent.
        requireDatabase().withTransaction {
            remoteByKey.values.forEach { remoteNovel ->
                val key = keyFor(remoteNovel.url)
                val current = dao.find(scope, key) ?: return@forEach
                val latestUrl = remoteNovel.latestUrl.orEmpty()
                val fingerprint = EsjzoneUrls.canonicalPageKey(latestUrl).ifBlank {
                    listOf(remoteNovel.latestTitle.orEmpty(), remoteNovel.remoteUpdatedAt.orEmpty())
                        .joinToString("|").takeIf { it != "|" }.orEmpty()
                }
                val effectiveFingerprint = fingerprint.ifBlank { current.latestFingerprint }
                val changed = current.latestFingerprint.isNotBlank() && fingerprint.isNotBlank() &&
                    current.latestFingerprint != fingerprint
                dao.updateRemoteStatus(
                    scope, key, remoteNovel.latestTitle.orEmpty(), latestUrl,
                    remoteNovel.remoteLastViewedTitle.orEmpty(), remoteNovel.remoteUpdatedAt.orEmpty(),
                    effectiveFingerprint, current.hasUpdate || changed
                )
            }
        }
        scheduleMetadataSupplement(authorization)
        val hasRemainingFailed = dao.countFailed(scope) > 0
        val isSuccess = !operationFailed && !hasRemainingFailed
        BookshelfSyncResult(
            success = isSuccess,
            added = added,
            loadFailure = if (!isSuccess) LoadFailureKind.NETWORK else null
        )
    }
}

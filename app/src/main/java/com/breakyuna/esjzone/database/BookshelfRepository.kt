package com.breakyuna.esjzone.database

import com.breakyuna.esjzone.network.wenku8.Wenku8Urls
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

/**
 * Single owner of the local-first shelf state machine. UI reads only its Room
 * flow; all remote work is serialized here and writes back into Room.
 *
 * Rows are scoped by account identity, providing complete
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
    private val metadataScheduleLock = Any()
    private val supplementingScopes = mutableSetOf<String>()
    private val pendingSupplementAuthorizations = mutableMapOf<String, Authorization>()
    private val metadataAttempts = ConcurrentHashMap<String, Long>()
    private val metadataSemaphore = Semaphore(6)

    /** Avoid repeatedly refetching rows with missing metadata. */
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

    const val WENKU8_SCOPE = "wenku8:local"

    fun scopeFor(authorization: Authorization, wenku8: Boolean = false): String =
        if (wenku8) WENKU8_SCOPE else EsjzoneClient.accountScope(authorization)

    fun scopeForNovel(authorization: Authorization, url: String): String =
        scopeFor(authorization, Wenku8Urls.detailIdentity(url) != null)

    suspend fun migrateLegacyScopeIfNeeded(authorization: Authorization) {
        val domain = authorization.domain.ifBlank { EsjzoneUrls.BaseWithoutProtocol }
        val legacyScope = "domain:$domain"
        val targetScope = scopeFor(authorization)
        val dao = requireDao()
        val oldScopes = listOfNotNull(
            EsjzoneClient.legacyBookshelfAccountScope(authorization),
            EsjzoneClient.matchingEmailLegacyBookshelfScope(authorization),
            legacyScope.takeIf { EsjzoneClient.mayMigrateLegacyDomainScope(authorization) }
        ).distinct().filter { it != targetScope }
        oldScopes.forEach { oldScope ->
            if (dao.count(oldScope) > 0) {
                dao.migrateScope(oldScope = oldScope, newScope = targetScope)
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

    /** Source-aware local identity; ESJ keys retain their existing format. */
    fun novelIdFor(url: String): String =
        Wenku8Urls.detailIdentity(url) ?: Regex("^/detail/([^/]+?)(?:\\.html)?/?$")
            .find(EsjzoneUrls.canonicalPageKey(url))
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()

    /** Clears the dot only when the reader actually opens the known latest chapter. */
    suspend fun markLatestRead(chapterUrl: String) {
        val fingerprint = EsjzoneUrls.canonicalPageKey(chapterUrl).ifBlank { chapterUrl.substringBefore('#') }
        if (fingerprint.isNotBlank()) requireDao().clearUpdateForFingerprint(fingerprint)
    }

    fun observe(authorization: Authorization, wenku8: Boolean = false): Flow<List<BookshelfEntry>> = combine(
        requireDao().observeVisible(scopeFor(authorization, wenku8)),
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
        requireDao().observe(scopeForNovel(authorization, url), keyFor(url))

    /** Applies a local intent immediately, then schedules a serialized remote retry. */
    suspend fun setFavorite(authorization: Authorization, novel: Novel, desired: Boolean) {
        if (Wenku8Urls.detailIdentity(novel.url) != null) {
            intentMutex.withLock {
                requireDatabase().withTransaction {
                    val dao = requireDao()
                    val key = keyFor(novel.url)
                    val existing = dao.find(WENKU8_SCOPE, key)
                    if (!desired) {
                        if (existing != null && dao.deleteIfVersion(WENKU8_SCOPE, key, existing.operationVersion) > 0) {
                            requireDatabase().bookshelfGroupDao().ungroup(WENKU8_SCOPE, listOf(key))
                        }
                    } else {
                        val covered = novel as? CoveredNovel
                        dao.upsert((existing ?: BookshelfEntry(scope = WENKU8_SCOPE, bookKey = key,
                            url = novel.url, title = novel.name)).copy(
                            novelId = novelIdFor(novel.url), title = novel.name,
                            author = covered?.author ?: existing?.author.orEmpty(),
                            coverUrl = covered?.coverUrl?.takeIf(String::isNotBlank) ?: existing?.coverUrl.orEmpty(),
                            isAdult = covered?.isAdult ?: existing?.isAdult ?: false,
                            visible = true, syncState = BookshelfSyncState.SYNCED,
                            operationVersion = (existing?.operationVersion ?: 0L) + 1
                        ))
                    }
                }
            }
            return
        }
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
        if (entries.isNotEmpty() && entries.all { it.scope == WENKU8_SCOPE }) {
            return intentMutex.withLock {
                val dao = requireDao()
                requireDatabase().withTransaction {
                    val removed = entries.filter { entry ->
                        dao.deleteIfVersion(WENKU8_SCOPE, entry.bookKey, entry.operationVersion) > 0
                    }
                    if (removed.isNotEmpty()) requireDatabase().bookshelfGroupDao()
                        .ungroup(WENKU8_SCOPE, removed.map { it.bookKey })
                    removed.size
                }
            }
        }
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
        val scope = scopeForNovel(authorization, novel.url)
        val key = keyFor(novel.url)
        val existing = dao.find(scope, key)
        if (scope == WENKU8_SCOPE) {
            if (existing != null) {
                dao.supplementMetadata(scope, key, novel.name, author, coverUrl, isAdult)
                dao.updateCoverIfChanged(scope, key, EsjzoneUrls.coverOrEmpty(coverUrl))
            }
            return
        }
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
     * cooldown. At most six detail pages are fetched concurrently.
     */
    fun scheduleMetadataSupplement(authorization: Authorization, delayMillis: Long = 0L) {
        if (!authorization.hasCredentials()) return
        val scope = scopeFor(authorization)
        val shouldStart = synchronized(metadataScheduleLock) {
            if (supplementingScopes.add(scope)) true else {
                pendingSupplementAuthorizations[scope] = authorization
                false
            }
        }
        if (!shouldStart) return
        workerScope.launch {
            try {
                if (delayMillis > 0L) delay(delayMillis)
                val dao = requireDao()

                val initialCandidates = dao.getAll(scope)
                    .filter {
                        it.visible && it.url.isNotBlank() &&
                            (it.coverUrl.isBlank() || EsjzoneUrls.coverOrEmpty(it.coverUrl).isBlank())
                    }
                val readingActivities = if (initialCandidates.isNotEmpty()) localReadingDao.getAll() else null

                if (initialCandidates.isNotEmpty()) {
                    val readingCoverByKey = mutableMapOf<String, String>()
                    val readingCoverById = mutableMapOf<String, String>()
                    // getAll is newest first; retain the first usable cover per novel.
                    for (activity in readingActivities.orEmpty()) {
                        val cover = EsjzoneUrls.coverOrEmpty(activity.novelCoverUrl)
                        if (cover.isBlank()) continue
                        if (activity.novelUrl.isNotBlank()) {
                            val key = keyFor(activity.novelUrl)
                            if (key.isNotBlank()) readingCoverByKey.putIfAbsent(key, cover)
                        }
                        if (activity.novelId.isNotBlank()) {
                            readingCoverById.putIfAbsent(activity.novelId, cover)
                        }
                    }

                    requireDatabase().withTransaction {
                        for (row in initialCandidates) {
                            if (row.coverUrl.isNotBlank()) {
                                dao.clearCoverIfCurrent(scope, row.bookKey, row.coverUrl)
                            }
                            val existing = dao.findAnyWithCover(row.bookKey)
                            val readingCover = readingCoverByKey[row.bookKey]
                                ?: readingCoverById[row.novelId.ifBlank { novelIdFor(row.url) }]
                            val availableCover = existing?.coverUrl
                                ?.let(EsjzoneUrls::coverOrEmpty)
                                ?.takeIf { it.isNotBlank() }
                                ?: readingCover
                            if (availableCover != null) {
                                dao.supplementMetadata(
                                    scope = scope,
                                    bookKey = row.bookKey,
                                    title = existing?.title?.takeIf { it.isNotBlank() } ?: row.title,
                                    author = existing?.author.orEmpty(),
                                    coverUrl = availableCover,
                                    isAdult = existing?.isAdult ?: false
                                )
                            }
                        }
                    }
                }

                val now = System.currentTimeMillis()
                val remainingCandidates = dao.getAll(scope)
                    .asSequence()
                    // A reading-history cover does not provide author or R18 metadata.
                    .filter { it.visible && it.url.isNotBlank() && (it.coverUrl.isBlank() || it.author.isBlank()) }
                    .filter { row ->
                        val attemptKey = "$scope:${row.bookKey}"
                        val previous = metadataAttempts.putIfAbsent(attemptKey, now)
                        previous == null ||
                            (now - previous >= METADATA_RETRY_INTERVAL_MILLIS &&
                                metadataAttempts.replace(attemptKey, previous, now))
                    }
                    .toList()

                if (remainingCandidates.isEmpty()) return@launch

                val activities = readingActivities ?: localReadingDao.getAll()
                val sortedCandidates = BookshelfSort.sort(
                    entries = remainingCandidates,
                    activities = activities,
                    keyForUrl = ::keyFor,
                    order = BookshelfSort.Order.RECENT_READ
                )

                sortedCandidates.chunked(METADATA_BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                    coroutineScope {
                        batch.forEach { row ->
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
                        }
                    }
                    if (batchIndex < sortedCandidates.lastIndex / METADATA_BATCH_SIZE) {
                        delay(METADATA_BATCH_COOLDOWN_MILLIS)
                    }
                }
            } finally {
                val nextAuthorization = synchronized(metadataScheduleLock) {
                    supplementingScopes.remove(scope)
                    pendingSupplementAuthorizations.remove(scope)
                }
                nextAuthorization?.let(::scheduleMetadataSupplement)
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

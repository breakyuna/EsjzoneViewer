package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.EsjzoneApplication
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.hasCredentials
import com.breakyuna.esjzone.novellibrary.novel.ChapterSource
import com.breakyuna.esjzone.novellibrary.novel.resolveChapterSource
import com.breakyuna.esjzone.util.AppLogger
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

/**
 * One website view record per novel. The site keeps the last chapter page that
 * was actually requested, so a saved chapter is reported once, and a newer
 * chapter replaces an older pending report.
 */
internal data class CloudViewRecord(
    val site: String,
    val novelKey: String,
    val novelId: String,
    val novelUrl: String,
    val chapterKey: String,
    val chapterUrl: String,
    val reported: Boolean,
    val updatedAt: Long,
    val account: String = "",
    val generation: Int = 0
)

internal fun sameCloudView(left: CloudViewRecord, right: CloudViewRecord): Boolean =
    left.account == right.account && left.site == right.site && left.novelKey == right.novelKey

/** A fetch still represents the site record only when nothing newer was confirmed afterwards. */
internal fun cloudViewFetchIsCurrent(fetchGeneration: Int?, confirmation: Int): Boolean =
    fetchGeneration != null && fetchGeneration == confirmation

internal fun cloudViewNovelKey(novelId: String, novelUrl: String, chapterUrl: String): String {
    val id = novelId.trim().ifBlank {
        com.breakyuna.esjzone.novellibrary.novel.Chapter(chapterUrl, chapterUrl, false).novelId()
    }
    if (id.isNotBlank()) return "id:$id"
    val novel = EsjzoneUrls.canonicalPageKey(novelUrl)
    if (novel.isNotBlank() && novel != "/") return "url:$novel"
    return ""
}

/** Resolves a chapter onto the logged-in site. Other mirrors and external links are skipped. */
internal fun chapterViewUrl(site: String, chapterUrl: String): String? {
    if (site.isBlank() || chapterUrl.isBlank()) return null
    val base = EsjzoneUrls.baseForDomain(site)
    val resolved = EsjzoneUrls.resolve(chapterUrl, base)
    if (resolveChapterSource(resolved, base) != ChapterSource.ESJ_ZONE) return null
    val host = resolved.toHttpUrlOrNull()?.host ?: return null
    if (!host.equals(site, ignoreCase = true)) return null
    return resolved
}

internal fun mergeCloudViewRecord(
    records: List<CloudViewRecord>,
    next: CloudViewRecord,
    replaceReported: Boolean = false
): List<CloudViewRecord> {
    val existing = records.firstOrNull { sameCloudView(it, next) }
    if (!replaceReported && existing?.reported == true && existing.chapterKey == next.chapterKey && !next.reported) {
        return records
    }
    return records.filterNot { sameCloudView(it, next) } + next
}

/**
 * A pending chapter is still the one to request while local history has not
 * moved on to a later chapter. History written before the pending note is an
 * older position and must not cancel the report.
 */
internal fun shouldRequestCloudView(record: CloudViewRecord, latest: LocalReadingActivity?): Boolean {
    if (record.reported) return false
    if (latest == null) return true
    if (EsjzoneUrls.canonicalPageKey(latest.chapterUrl) == record.chapterKey) return true
    return latest.lastReadAt <= record.updatedAt
}

internal class CloudViewReportStore(private val file: File?) {
    private val gson = Gson()
    private val lock = Any()
    private val records = mutableListOf<CloudViewRecord>()

    init {
        load()
    }

    fun snapshot(): List<CloudViewRecord> = synchronized(lock) { records.toList() }

    /** Returns false when the chapter was already reported and nothing changed. */
    fun merge(next: CloudViewRecord, replaceReported: Boolean = false): Boolean = synchronized(lock) {
        val updated = mergeCloudViewRecord(records, next, replaceReported)
        if (updated == records) return false
        records.clear()
        records += updated
        persist()
        true
    }

    fun find(account: String, site: String, novelKey: String): CloudViewRecord? = synchronized(lock) {
        records.firstOrNull { it.account == account && it.site == site && it.novelKey == novelKey }
    }

    fun pending(account: String, site: String): List<CloudViewRecord> = synchronized(lock) {
        records.filter { it.account == account && it.site == site && !it.reported }
    }

    fun remove(record: CloudViewRecord) = synchronized(lock) {
        if (records.removeAll { sameCloudView(it, record) }) persist()
    }

    /** Marks the report done only if that chapter is still the novel's current record. */
    fun markReported(record: CloudViewRecord): Boolean = synchronized(lock) {
        val index = records.indexOfFirst { sameCloudView(it, record) }
        if (index < 0) return false
        val current = records[index]
        if (current.chapterKey != record.chapterKey) return false
        records[index] = current.copy(reported = true)
        persist()
        true
    }

    /**
     * A stale chapter request has already reached the site. Put the newer chapter
     * back in the queue when this attempt is no longer the novel's current record.
     */
    fun reopenSuperseding(attempt: CloudViewRecord): Boolean = synchronized(lock) {
        val index = records.indexOfFirst { sameCloudView(it, attempt) }
        if (index < 0) return false
        val current = records[index]
        if (current.chapterKey == attempt.chapterKey) return false
        if (current.reported) {
            records[index] = current.copy(reported = false)
            persist()
        }
        true
    }

    private fun load() {
        val stored = file?.takeIf { it.isFile } ?: return
        val decoded = runCatching {
            gson.fromJson(stored.readText(), Array<StoredCloudView>::class.java)?.toList().orEmpty()
        }.getOrElse {
            AppLogger.w("CloudView", "Unable to read saved chapter view reports", it)
            emptyList()
        }
        records.clear()
        records += decoded.mapNotNull { it.toRecord() }
    }

    private data class StoredCloudView(
        val site: String? = null,
        val novelKey: String? = null,
        val novelId: String? = null,
        val novelUrl: String? = null,
        val chapterKey: String? = null,
        val chapterUrl: String? = null,
        val reported: Boolean? = null,
        val updatedAt: Long? = null,
        val account: String? = null
    ) {
        fun toRecord(): CloudViewRecord? {
            val accountScope = account?.trim().orEmpty()
            val siteName = site?.trim().orEmpty()
            val novel = novelKey?.trim().orEmpty()
            val chapter = chapterKey?.trim().orEmpty()
            if (accountScope.isBlank() || siteName.isBlank() || novel.isBlank() || chapter.isBlank()) return null
            return CloudViewRecord(
                site = siteName,
                novelKey = novel,
                novelId = novelId.orEmpty(),
                novelUrl = novelUrl.orEmpty(),
                chapterKey = chapter,
                chapterUrl = chapterUrl.orEmpty(),
                reported = reported == true,
                updatedAt = updatedAt ?: 0L,
                account = accountScope,
                // Generations are process-local; persisted values cannot be compared
                // with counters created after the app restarts.
                generation = 0
            )
        }
    }

    private fun persist() {
        val target = file ?: return
        runCatching {
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, "${target.name}.tmp")
            temporary.writeText(gson.toJson(records))
            if (!temporary.renameTo(target)) {
                target.writeText(gson.toJson(records))
                temporary.delete()
            }
        }.onFailure { AppLogger.w("CloudView", "Unable to save chapter view reports", it) }
    }
}

internal class CloudViewReporter(
    private val store: CloudViewReportStore,
    private val touch: (Authorization, String, () -> Boolean) -> Boolean,
    private val latestActivity: (novelId: String, novelUrl: String) -> LocalReadingActivity? = { _, _ -> null },
    private val enabled: () -> Boolean = { true },
    private val now: () -> Long = System::currentTimeMillis,
    private val accountScope: (Authorization) -> String? = { HistoryDataCache.scopeFor(it) },
    private val scope: CoroutineScope
) {
    private data class ViewScope(val account: String, val site: String, val novelKey: String)

    private val sendMutex = Mutex()
    private val scheduled = AtomicBoolean(false)
    private val confirmations = ConcurrentHashMap<ViewScope, AtomicInteger>()

    private fun viewScope(
        authorization: Authorization,
        novelId: String,
        novelUrl: String,
        chapterUrl: String
    ): ViewScope? {
        val account = accountScope(authorization)?.takeIf { it.isNotBlank() } ?: return null
        val site = authorization.domain.trim()
        val resolved = chapterViewUrl(site, chapterUrl) ?: return null
        val novelKey = cloudViewNovelKey(novelId, novelUrl, resolved)
        if (novelKey.isBlank()) return null
        return ViewScope(account, site, novelKey)
    }

    fun confirmation(authorization: Authorization, novelId: String, novelUrl: String, chapterUrl: String): Int {
        val key = viewScope(authorization, novelId, novelUrl, chapterUrl) ?: return 0
        return confirmations[key]?.get() ?: 0
    }

    fun fetchIsCurrent(
        authorization: Authorization,
        novelId: String,
        novelUrl: String,
        chapterUrl: String,
        fetchGeneration: Int?
    ): Boolean {
        val key = viewScope(authorization, novelId, novelUrl, chapterUrl) ?: return false
        return cloudViewFetchIsCurrent(fetchGeneration, confirmations[key]?.get() ?: 0)
    }

    /** Counts a real chapter-page response, including a prefetch the reader has not opened. */
    fun observeNetworkFetch(
        authorization: Authorization,
        novelId: String,
        novelUrl: String,
        chapterUrl: String
    ): Int? {
        val key = viewScope(authorization, novelId, novelUrl, chapterUrl) ?: return null
        return confirmations.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet()
    }

    fun noteLocal(authorization: Authorization, novelId: String, novelUrl: String, chapterUrl: String) {
        if (coversCurrentChapter(authorization, novelId, novelUrl, chapterUrl)) return
        if (!remember(authorization, novelId, novelUrl, chapterUrl, reported = false, replaceReported = true)) return
        schedule(authorization)
    }

    fun noteFetched(authorization: Authorization, novelId: String, novelUrl: String, chapterUrl: String) {
        // A real chapter request already updated the site, including while reporting is off.
        remember(authorization, novelId, novelUrl, chapterUrl, reported = true, requireEnabled = false)
    }

    fun flush(authorization: Authorization) {
        if (!enabled() || !authorization.hasCredentials()) return
        schedule(authorization)
    }

    private fun remember(
        authorization: Authorization,
        novelId: String,
        novelUrl: String,
        chapterUrl: String,
        reported: Boolean,
        requireEnabled: Boolean = true,
        replaceReported: Boolean = false
    ): Boolean {
        if ((requireEnabled && !enabled()) || !authorization.hasCredentials()) return false
        val account = accountScope(authorization)?.takeIf { it.isNotBlank() } ?: return false
        val site = authorization.domain.trim()
        val resolved = chapterViewUrl(site, chapterUrl) ?: return false
        val novelKey = cloudViewNovelKey(novelId, novelUrl, resolved)
        if (novelKey.isBlank()) return false
        val chapterKey = EsjzoneUrls.canonicalPageKey(resolved)
        if (chapterKey.isBlank()) return false
        val existing = store.find(account, site, novelKey)
        val key = ViewScope(account, site, novelKey)
        val current = confirmations[key]?.get() ?: 0
        if (reported && existing?.reported == true && existing.chapterKey == chapterKey &&
            existing.generation == current && existing.generation != 0) {
            return false
        }
        val generation = confirmations.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet()
        return store.merge(CloudViewRecord(
            site = site,
            novelKey = novelKey,
            novelId = novelId.trim(),
            novelUrl = novelUrl.trim(),
            chapterKey = chapterKey,
            chapterUrl = resolved,
            reported = reported,
            updatedAt = now(),
            account = account,
            generation = generation
        ), replaceReported)
    }

    private fun coversCurrentChapter(
        authorization: Authorization,
        novelId: String,
        novelUrl: String,
        chapterUrl: String
    ): Boolean {
        val account = accountScope(authorization)?.takeIf { it.isNotBlank() } ?: return false
        val site = authorization.domain.trim()
        val resolved = chapterViewUrl(site, chapterUrl) ?: return false
        val novelKey = cloudViewNovelKey(novelId, novelUrl, resolved)
        if (novelKey.isBlank()) return false
        val chapterKey = EsjzoneUrls.canonicalPageKey(resolved)
        val record = store.find(account, site, novelKey) ?: return false
        if (!record.reported || record.chapterKey != chapterKey) return false
        val current = confirmations[ViewScope(account, site, novelKey)]?.get() ?: 0
        return current == 0 || record.generation == current
    }

    private fun schedule(authorization: Authorization) {
        if (!scheduled.compareAndSet(false, true)) return
        scope.launch {
            try {
                sendMutex.withLock {
                    scheduled.set(false)
                    sendPending(authorization)
                }
            } catch (error: CancellationException) {
                scheduled.set(false)
                throw error
            } catch (error: Exception) {
                scheduled.set(false)
                AppLogger.w("CloudView", "Unable to report the current chapter", error)
            }
        }
    }

    private fun sendPending(authorization: Authorization) {
        val account = accountScope(authorization)?.takeIf { it.isNotBlank() } ?: return
        val site = authorization.domain.trim()
        if (!enabled() || !authorization.hasCredentials() || site.isBlank()) return
        val pending = store.pending(account, site)
        if (pending.isEmpty()) return
        val current = pending.map { record ->
            record to shouldRequestCloudView(record, latestActivity(record.novelId, record.novelUrl))
        }
        current.filter { !it.second }.forEach { store.remove(it.first) }
        var superseded = false
        for (next in current.filter { it.second }.map { it.first }) {
            if (!enabled()) break
            val latest = store.pending(account, site).firstOrNull {
                it.novelKey == next.novelKey && it.chapterKey == next.chapterKey &&
                    it.generation == next.generation
            } ?: continue
            val url = chapterViewUrl(site, latest.chapterUrl) ?: run {
                store.remove(latest)
                continue
            }
            val reported = try {
                touch(authorization, url) {
                    val pending = store.find(account, site, latest.novelKey)
                    pending != null && !pending.reported &&
                        pending.chapterKey == latest.chapterKey &&
                        pending.generation == latest.generation
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.w("CloudView", "Chapter view request failed", error)
                false
            }
            if (reported && !store.markReported(latest) && store.reopenSuperseding(latest)) {
                superseded = true
            }
        }
        if (superseded) schedule(authorization)
    }
}

object CloudViewReports {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var reporter: CloudViewReporter? = null

    fun initialize(directory: File) {
        if (reporter != null) return
        synchronized(this) {
            if (reporter != null) return
            reporter = create(directory)
        }
    }

    private fun create(directory: File) = CloudViewReporter(
            store = CloudViewReportStore(File(directory, "cloud_view_reports.json")),
            touch = { authorization, url, stillCurrent ->
                val reported = EsjzoneClient.touchChapterView(authorization, url, stillCurrent)
                if (reported) HistoryDataCache.clearSnapshot(authorization)
                reported
            },
            latestActivity = { novelId, novelUrl ->
                runCatching {
                    EsjzoneApplication.instance.container.database.localReadingActivityDao()
                        .getLatestForIdentity(novelId, novelUrl)
                }.getOrNull()
            },
            enabled = {
                runCatching {
                    EsjzoneApplication.instance.container.settings.updateWebsiteHistory.value
                }.getOrDefault(true)
            },
            scope = scope
        )

    fun noteLocal(authorization: Authorization, novelId: String, novelUrl: String, chapterUrl: String) {
        reporter?.noteLocal(authorization, novelId, novelUrl, chapterUrl)
    }

    fun noteFetched(authorization: Authorization, novelId: String, novelUrl: String, chapterUrl: String) {
        reporter?.noteFetched(authorization, novelId, novelUrl, chapterUrl)
    }

    fun fetchIsCurrent(
        authorization: Authorization,
        novelId: String,
        novelUrl: String,
        chapterUrl: String,
        fetchGeneration: Int?
    ): Boolean = reporter?.fetchIsCurrent(authorization, novelId, novelUrl, chapterUrl, fetchGeneration) ?: false

    fun observeNetworkFetch(
        authorization: Authorization,
        novelId: String,
        novelUrl: String,
        chapterUrl: String
    ): Int? = reporter?.observeNetworkFetch(authorization, novelId, novelUrl, chapterUrl)

    fun flush(authorization: Authorization) {
        reporter?.flush(authorization)
    }
}

package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.EsjzoneApplication
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.hasCredentials
import com.breakyuna.esjzone.novellibrary.novel.ChapterSource
import com.breakyuna.esjzone.novellibrary.novel.HistoryNovel
import com.breakyuna.esjzone.novellibrary.novel.resolveChapterSource
import com.breakyuna.esjzone.util.AppLogger
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

/**
 * A background download requests every chapter page with the account cookies,
 * and the site records each response as the novel's latest view. This snapshot
 * remembers where the site record stood before that happened.
 */
internal data class BackgroundChapterFetch(
    val authorization: Authorization,
    val account: String,
    val site: String,
    val novelKey: String,
    val novelId: String,
    val novelUrl: String,
    /** What /my/view said about this novel before the download. */
    val siteRecord: SiteViewRecord,
    val startedAt: Long
)

/** The novel's /my/view row read before a background download. */
internal sealed interface SiteViewRecord {
    /** The site listed the novel with this last-viewed chapter. */
    data class Found(val chapterUrl: String) : SiteViewRecord
    /**
     * /my/view was read and the novel is not on it. The page has no pagination
     * and seems to keep only the latest books, so a novel that dropped off the
     * list counts as having no record, matching what the site remembers.
     */
    data object Absent : SiteViewRecord
    /** /my/view could not be fetched or parsed; nothing is known. */
    data object Unknown : SiteViewRecord
}

/** Looks the novel up in one /my/view snapshot; a null snapshot means the read failed. */
internal fun siteViewRecord(histories: List<HistoryNovel>?, site: String, novelKey: String): SiteViewRecord {
    histories ?: return SiteViewRecord.Unknown
    return siteHistoryChapter(histories, site, novelKey)?.let(SiteViewRecord::Found) ?: SiteViewRecord.Absent
}

/**
 * The chapter the site should show after a background download:
 * 1. a reader report made during the download is the newest position;
 * 2. otherwise, when the site had a record before the download, that record;
 * 3. when the site had no record, the novel's first chapter, so the new record
 *    stops at chapter 1 instead of the last downloaded chapter;
 * 4. when the site record is unknown (the read failed), nothing is reset to
 *    chapter 1; an older reader report is the only fallback.
 * Returns null when nothing should be requested.
 */
internal fun cloudViewRestoreChapter(
    known: CloudViewRecord?,
    siteRecord: SiteViewRecord,
    startedAt: Long,
    firstChapterUrl: () -> String? = { null }
): String? {
    if (known != null && known.updatedAt >= startedAt && known.chapterUrl.isNotBlank()) return known.chapterUrl
    val older = known?.chapterUrl?.takeIf { it.isNotBlank() }
    return when (siteRecord) {
        is SiteViewRecord.Found -> siteRecord.chapterUrl.trim().takeIf { it.isNotBlank() } ?: older
        SiteViewRecord.Absent -> firstChapterUrl()?.trim()?.takeIf { it.isNotBlank() } ?: older
        SiteViewRecord.Unknown -> older
    }
}

/** The first chapter, in table-of-contents order, that is a chapter page on the logged-in site. */
internal fun firstSiteChapter(site: String, chapterUrls: List<String>): String? =
    chapterUrls.firstNotNullOfOrNull { chapterViewUrl(site, it) }

/** Finds this novel's row in the site's view history. */
internal fun siteHistoryChapter(histories: List<HistoryNovel>, site: String, novelKey: String): String? {
    if (novelKey.isBlank()) return null
    return histories.firstOrNull { history ->
        val chapter = chapterViewUrl(site, history.chapter.url) ?: return@firstOrNull false
        cloudViewNovelKey("", history.url, chapter) == novelKey
    }?.chapter?.url?.let { chapterViewUrl(site, it) }
}

internal class CloudViewReportStore(private val file: File?) {
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
        val decoded = runCatching { CloudViewReportCodec.decode(stored.readText()) }.getOrElse {
            // Log once and start fresh. The original is kept beside it, and
            // rewriting the file keeps the warning from repeating on every launch.
            AppLogger.w("CloudView", "Unable to read saved chapter view reports; starting fresh", it)
            preserveOriginal(stored)
            records.clear()
            persist()
            return
        }
        records.clear()
        records += decoded.records
        if (decoded.discarded > 0) {
            AppLogger.i("CloudView", "Dropped ${decoded.discarded} saved chapter view reports with missing fields")
            preserveOriginal(stored)
            persist()
        }
    }

    /** Keeps the last file that could not be read in full as `<name>.bak`. */
    private fun preserveOriginal(stored: File) {
        runCatching { stored.copyTo(File(stored.parentFile, "${stored.name}.bak"), overwrite = true) }
            .onFailure { AppLogger.w("CloudView", "Unable to keep a copy of saved chapter view reports", it) }
    }

    private fun persist() {
        val target = file ?: return
        runCatching {
            target.parentFile?.mkdirs()
            val json = CloudViewReportCodec.encode(records)
            val temporary = File(target.parentFile, "${target.name}.tmp")
            temporary.writeText(json)
            if (!temporary.renameTo(target)) {
                target.writeText(json)
                temporary.delete()
            }
        }.onFailure { AppLogger.w("CloudView", "Unable to save chapter view reports", it) }
    }
}

/**
 * Wire form of one pending report in `cloud_view_reports.json`. Reading and
 * writing both go through this class, and every field has a fixed
 * [SerializedName], so the JSON keys stay the same in R8-minified release builds
 * (see the matching keep rule in proguard-rules.pro). All fields are nullable
 * with defaults: a record missing a required field is dropped instead of failing
 * the whole file.
 *
 * Earlier release builds (versionName 0.2.5) wrote [CloudViewRecord] directly and
 * R8 renamed its fields in declaration order (`a` = site ... `i` = account,
 * `j` = generation, which is not persisted). The alternates read those files.
 */
internal data class StoredCloudView(
    @SerializedName(value = "site", alternate = ["a"]) val site: String? = null,
    @SerializedName(value = "novelKey", alternate = ["b"]) val novelKey: String? = null,
    @SerializedName(value = "novelId", alternate = ["c"]) val novelId: String? = null,
    @SerializedName(value = "novelUrl", alternate = ["d"]) val novelUrl: String? = null,
    @SerializedName(value = "chapterKey", alternate = ["e"]) val chapterKey: String? = null,
    @SerializedName(value = "chapterUrl", alternate = ["f"]) val chapterUrl: String? = null,
    @SerializedName(value = "reported", alternate = ["g"]) val reported: Boolean? = null,
    @SerializedName(value = "updatedAt", alternate = ["h"]) val updatedAt: Long? = null,
    @SerializedName(value = "account", alternate = ["i"]) val account: String? = null
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

    companion object {
        fun from(record: CloudViewRecord) = StoredCloudView(
            site = record.site,
            novelKey = record.novelKey,
            novelId = record.novelId,
            novelUrl = record.novelUrl,
            chapterKey = record.chapterKey,
            chapterUrl = record.chapterUrl,
            reported = record.reported,
            updatedAt = record.updatedAt,
            account = record.account
        )
    }
}

internal data class DecodedCloudViews(val records: List<CloudViewRecord>, val discarded: Int)

/**
 * Entries missing a required field are discarded one by one; text that is not a
 * JSON array throws.
 */
internal object CloudViewReportCodec {
    private val gson = Gson()

    fun encode(records: List<CloudViewRecord>): String =
        gson.toJson(records.map(StoredCloudView::from).toTypedArray())

    fun decode(text: String): DecodedCloudViews {
        val array = JsonParser.parseString(text).asJsonArray
        var discarded = 0
        val records = array.mapNotNull { element ->
            val record = if (element.isJsonObject) {
                runCatching { gson.fromJson(element, StoredCloudView::class.java)?.toRecord() }.getOrNull()
            } else {
                null
            }
            if (record == null) discarded++
            record
        }
        return DecodedCloudViews(records, discarded)
    }
}

internal class CloudViewReporter(
    private val store: CloudViewReportStore,
    private val touch: (Authorization, String, () -> Boolean) -> Boolean,
    private val latestActivity: (novelId: String, novelUrl: String) -> LocalReadingActivity? = { _, _ -> null },
    private val now: () -> Long = System::currentTimeMillis,
    private val accountScope: (Authorization) -> String? = { HistoryDataCache.scopeFor(it) },
    private val siteHistory: (Authorization) -> List<HistoryNovel>? = { null },
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
        // A real chapter request already updated the site.
        remember(authorization, novelId, novelUrl, chapterUrl, reported = true)
    }

    fun flush(authorization: Authorization) {
        if (!authorization.hasCredentials()) return
        schedule(authorization)
    }

    /**
     * Call before a background job requests chapter pages for one novel. Reads the
     * site's current record so [finishBackgroundFetch] can put it back.
     */
    fun beginBackgroundFetch(authorization: Authorization, novelId: String, novelUrl: String): BackgroundChapterFetch? {
        if (!authorization.hasCredentials()) return null
        val account = accountScope(authorization)?.takeIf { it.isNotBlank() } ?: return null
        val site = authorization.domain.trim()
        if (site.isBlank()) return null
        val novelKey = cloudViewNovelKey(novelId, novelUrl, "")
        if (novelKey.isBlank()) return null
        // Reading can advance while the history response is in flight too.
        val startedAt = now()
        val histories = runCatching { siteHistory(authorization) }
            .onFailure { AppLogger.w("CloudView", "Unable to read the site history before a download", it) }
            .getOrNull()
        val siteRecord = siteViewRecord(histories, site, novelKey)
        if (siteRecord == SiteViewRecord.Unknown) {
            AppLogger.w("CloudView", "Site history unknown before a download; chapter 1 will not be revisited")
        }
        return BackgroundChapterFetch(authorization, account, site, novelKey, novelId.trim(), novelUrl.trim(),
            siteRecord, startedAt)
    }

    /**
     * The download's chapter requests moved the site record to whichever chapter
     * finished last. Request the chapter chosen by [cloudViewRestoreChapter] so the
     * site shows the reader's real position again, or chapter 1 for a novel the
     * site had no record of. [firstChapterUrl] is only called in that case.
     */
    suspend fun finishBackgroundFetch(
        fetch: BackgroundChapterFetch?,
        firstChapterUrl: () -> String? = { null }
    ): Boolean {
        fetch ?: return false
        return sendMutex.withLock {
            val known = store.find(fetch.account, fetch.site, fetch.novelKey)
            val target = cloudViewRestoreChapter(known, fetch.siteRecord, fetch.startedAt) {
                runCatching(firstChapterUrl)
                    .onFailure { AppLogger.w("CloudView", "Unable to find the first chapter after a download", it) }
                    .getOrNull()
            }
            if (target == null) {
                if (fetch.siteRecord == SiteViewRecord.Unknown) {
                    AppLogger.i("CloudView", "Skipped the site record restore: history before the download is unknown")
                }
                return@withLock false
            }
            val url = chapterViewUrl(fetch.site, target) ?: return@withLock false
            // Queue the restore like an ordinary report so a chapter the reader
            // opens while the request is in flight is kept, not overwritten.
            remember(fetch.authorization, fetch.novelId, fetch.novelUrl, url,
                reported = false, replaceReported = true)
            val attempt = store.find(fetch.account, fetch.site, fetch.novelKey)
                ?.takeIf { !it.reported && it.chapterKey == EsjzoneUrls.canonicalPageKey(url) }
                ?: return@withLock false
            val restored = try {
                touch(fetch.authorization, url) {
                    val current = store.find(fetch.account, fetch.site, fetch.novelKey)
                    current != null && !current.reported &&
                        current.chapterKey == attempt.chapterKey &&
                        current.generation == attempt.generation
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.w("CloudView", "Unable to restore the site record after a download", error)
                false
            }
            // A newer chapter recorded during the request is put back in the queue,
            // because the restore may have reached the site after it.
            if (restored && !store.markReported(attempt) && store.reopenSuperseding(attempt)) {
                schedule(fetch.authorization)
            }
            restored
        }
    }

    private fun remember(
        authorization: Authorization,
        novelId: String,
        novelUrl: String,
        chapterUrl: String,
        reported: Boolean,
        replaceReported: Boolean = false
    ): Boolean {
        if (!authorization.hasCredentials()) return false
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
        if (!authorization.hasCredentials() || site.isBlank()) return
        val pending = store.pending(account, site)
        if (pending.isEmpty()) return
        val current = pending.map { record ->
            record to shouldRequestCloudView(record, latestActivity(record.novelId, record.novelUrl))
        }
        current.filter { !it.second }.forEach { store.remove(it.first) }
        var superseded = false
        for (next in current.filter { it.second }.map { it.first }) {
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

/**
 * Creates the process-wide reporter once. A download worker can start in a
 * process that never opened MainActivity, so the background path initializes it
 * itself instead of relying on app startup.
 */
internal class CloudViewReporterHolder(private val factory: (File) -> CloudViewReporter) {
    @Volatile private var reporter: CloudViewReporter? = null

    fun current(): CloudViewReporter? = reporter

    fun initialize(directory: File): CloudViewReporter {
        reporter?.let { return it }
        synchronized(this) {
            reporter?.let { return it }
            return factory(directory).also { reporter = it }
        }
    }

    /**
     * Runs a background job that requests chapter pages for one novel, then puts
     * the site's reading position back, also when the job fails or is cancelled.
     */
    suspend fun <T> preservingSiteView(
        directory: File,
        authorization: Authorization,
        novelId: String,
        novelUrl: String,
        firstChapterUrl: () -> String? = { null },
        block: suspend () -> T
    ): T {
        val fetch = withContext(Dispatchers.IO) {
            runCatching { initialize(directory) }
                .onFailure { AppLogger.w("CloudView", "Unable to prepare chapter view reports", it) }
                .getOrNull()
                ?.beginBackgroundFetch(authorization, novelId, novelUrl)
        }
        try {
            return block()
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                current()?.finishBackgroundFetch(fetch, firstChapterUrl)
            }
        }
    }
}

object CloudViewReports {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val holder = CloudViewReporterHolder(::create)
    private val reporter: CloudViewReporter? get() = holder.current()

    fun initialize(directory: File) {
        holder.initialize(directory)
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
            siteHistory = { authorization -> EsjzoneClient.getHistorySnapshot(authorization) },
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

    internal suspend fun <T> preservingSiteView(
        directory: File,
        authorization: Authorization,
        novelId: String,
        novelUrl: String,
        firstChapterUrl: () -> String? = { null },
        block: suspend () -> T
    ): T = holder.preservingSiteView(directory, authorization, novelId, novelUrl, firstChapterUrl, block)
}

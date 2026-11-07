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
import java.util.concurrent.atomic.AtomicBoolean
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
    val updatedAt: Long
)

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

internal fun mergeCloudViewRecord(records: List<CloudViewRecord>, next: CloudViewRecord): List<CloudViewRecord> {
    val existing = records.firstOrNull { it.site == next.site && it.novelKey == next.novelKey }
    if (existing?.reported == true && existing.chapterKey == next.chapterKey && !next.reported) return records
    return records.filterNot { it.site == next.site && it.novelKey == next.novelKey } + next
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
    fun merge(next: CloudViewRecord): Boolean = synchronized(lock) {
        val updated = mergeCloudViewRecord(records, next)
        if (updated == records) return false
        records.clear()
        records += updated
        persist()
        true
    }

    fun pending(site: String): List<CloudViewRecord> = synchronized(lock) {
        records.filter { it.site == site && !it.reported }
    }

    fun remove(record: CloudViewRecord) = synchronized(lock) {
        if (records.removeAll { it.site == record.site && it.novelKey == record.novelKey }) persist()
    }

    /** Marks the report done only if that chapter is still the novel's current record. */
    fun markReported(record: CloudViewRecord) = synchronized(lock) {
        val index = records.indexOfFirst { it.site == record.site && it.novelKey == record.novelKey }
        if (index < 0) return
        val current = records[index]
        if (current.chapterKey != record.chapterKey) return
        records[index] = current.copy(reported = true)
        persist()
    }

    private fun load() {
        val stored = file?.takeIf { it.isFile } ?: return
        val decoded = runCatching {
            gson.fromJson(stored.readText(), Array<CloudViewRecord>::class.java)?.toList().orEmpty()
        }.getOrElse {
            AppLogger.w("CloudView", "Unable to read saved chapter view reports", it)
            emptyList()
        }
        records.clear()
        records += decoded.filter { it.site.isNotBlank() && it.novelKey.isNotBlank() && it.chapterKey.isNotBlank() }
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
    private val touch: (Authorization, String) -> Boolean,
    private val latestActivity: (novelId: String, novelUrl: String) -> LocalReadingActivity? = { _, _ -> null },
    private val enabled: () -> Boolean = { true },
    private val now: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope
) {
    private val sendMutex = Mutex()
    private val scheduled = AtomicBoolean(false)

    fun noteLocal(authorization: Authorization, novelId: String, novelUrl: String, chapterUrl: String) {
        if (!remember(authorization, novelId, novelUrl, chapterUrl, reported = false)) return
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
        requireEnabled: Boolean = true
    ): Boolean {
        if ((requireEnabled && !enabled()) || !authorization.hasCredentials()) return false
        val site = authorization.domain.trim()
        val resolved = chapterViewUrl(site, chapterUrl) ?: return false
        val novelKey = cloudViewNovelKey(novelId, novelUrl, resolved)
        if (novelKey.isBlank()) return false
        val chapterKey = EsjzoneUrls.canonicalPageKey(resolved)
        if (chapterKey.isBlank()) return false
        return store.merge(CloudViewRecord(
            site = site,
            novelKey = novelKey,
            novelId = novelId.trim(),
            novelUrl = novelUrl.trim(),
            chapterKey = chapterKey,
            chapterUrl = resolved,
            reported = reported,
            updatedAt = now()
        ))
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
        val site = authorization.domain.trim()
        if (!enabled() || !authorization.hasCredentials() || site.isBlank()) return
        val pending = store.pending(site)
        if (pending.isEmpty()) return
        val current = pending.map { record ->
            record to shouldRequestCloudView(record, latestActivity(record.novelId, record.novelUrl))
        }
        current.filter { !it.second }.forEach { store.remove(it.first) }
        for (next in current.filter { it.second }.map { it.first }) {
            if (!enabled()) return
            val latest = store.pending(site).firstOrNull {
                it.novelKey == next.novelKey && it.chapterKey == next.chapterKey
            } ?: continue
            val url = chapterViewUrl(site, latest.chapterUrl) ?: run {
                store.remove(latest)
                continue
            }
            val reported = try {
                touch(authorization, url)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.w("CloudView", "Chapter view request failed", error)
                false
            }
            if (reported) store.markReported(latest)
        }
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
            touch = { authorization, url ->
                val reported = EsjzoneClient.touchChapterView(authorization, url)
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

    fun flush(authorization: Authorization) {
        reporter?.flush(authorization)
    }
}

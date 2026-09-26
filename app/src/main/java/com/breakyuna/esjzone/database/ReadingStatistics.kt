package com.breakyuna.esjzone.database

import android.os.SystemClock
import com.breakyuna.esjzone.EsjzoneApplication
import com.breakyuna.esjzone.database.entity.ReadingStat
import com.breakyuna.esjzone.database.dao.put
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import com.breakyuna.esjzone.util.AppLogger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val DETAIL_ID_REGEX = Regex("/detail/([^/?#]+?)(?:\\.html)?/?(?:[?#]|$)")

/** Shared identity for time and tags, including detail URLs without a forum id. */
internal fun readingStatisticsBookKey(novelId: String, novelUrl: String, chapterUrl: String = ""): String =
    novelId.trim().ifBlank {
        DETAIL_ID_REGEX.find(novelUrl)?.groupValues?.getOrNull(1).orEmpty()
    }.ifBlank {
        EsjzoneUrls.canonicalPageKey(novelUrl).ifBlank {
            EsjzoneUrls.canonicalPageKey(chapterUrl).ifBlank { chapterUrl.trim() }
        }
    }

/** Splits monotonic elapsed time across local calendar dates. */
internal fun readingTimeSlices(
    endWallMs: Long,
    elapsedMs: Long,
    zone: ZoneId
): Map<LocalDate, Long> {
    if (elapsedMs <= 0L) return emptyMap()
    val result = linkedMapOf<LocalDate, Long>()
    var cursor = endWallMs - elapsedMs
    while (cursor < endWallMs) {
        val date = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
        val nextDay = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(endWallMs, nextDay)
        result[date] = (result[date] ?: 0L) + end - cursor
        cursor = end
    }
    return result
}

/** One foreground reader interval. Repeated stop/checkpoint calls cannot count time twice. */
class ReadingStatisticsSession(
    private val bookKey: String,
    private val bookName: String,
    private val record: (ReadingStat) -> Unit = ReadingStatisticsRecorder::record,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val elapsedClock: () -> Long = SystemClock::elapsedRealtime,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) {
    private var lastElapsedMs: Long? = null

    @Synchronized
    fun start() {
        if (lastElapsedMs == null) lastElapsedMs = elapsedClock()
    }

    @Synchronized
    fun checkpoint() {
        val previous = lastElapsedMs ?: return
        val nowElapsed = elapsedClock()
        val duration = (nowElapsed - previous).coerceAtLeast(0L)
        lastElapsedMs = nowElapsed
        if (duration == 0L) return
        readingTimeSlices(wallClock(), duration, zone()).forEach { (date, milliseconds) ->
            record(ReadingStat(date.toString(), bookKey, bookName, milliseconds))
        }
    }

    @Synchronized
    fun stop() {
        checkpoint()
        lastElapsedMs = null
    }
}

object ReadingStatisticsRecorder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val generation = AtomicLong()

    const val TAGS_KEY_PREFIX = "reading_stats_tags:"

    fun recordTags(novel: DetailedNovel) {
        val bookKey = readingStatisticsBookKey(novel.id(), novel.url)
        val tags = novel.tags.map(String::trim).filter(String::isNotEmpty).distinct()
        if (bookKey.isBlank() || tags.isEmpty()) return
        scope.launch {
            mutex.withLock {
                try {
                    EsjzoneApplication.instance.container.database.cacheDao()
                        .put(TAGS_KEY_PREFIX + bookKey, Gson().toJson(tags))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppLogger.e("ReadingStatistics", "Failed to persist book tags", error)
                }
            }
        }
    }

    fun record(stat: ReadingStat) {
        val recordedGeneration = generation.get()
        scope.launch {
            mutex.withLock {
                if (recordedGeneration != generation.get()) return@withLock
                try {
                    EsjzoneApplication.instance.container.database.readingStatDao().add(stat)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppLogger.e("ReadingStatistics", "Failed to persist reading time", error)
                }
            }
        }
    }

    suspend fun clear() {
        generation.incrementAndGet()
        mutex.withLock {
            EsjzoneApplication.instance.container.database.readingStatDao().deleteAll()
        }
    }
}

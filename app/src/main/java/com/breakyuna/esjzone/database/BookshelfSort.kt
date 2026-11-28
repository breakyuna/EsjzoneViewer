package com.breakyuna.esjzone.database

import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Pure ordering rules for the local-first bookshelf. */
object BookshelfSort {

    enum class Order {
        RECENT_READ,
        RECENT_ADDED,
        RECENT_UPDATED
    }

    private const val MAX_DATE_CACHE_SIZE = 512
    private val dateCache = ConcurrentHashMap<String, Long>()

    /**
     * Fast-path sorting using precomputed read timestamps.
     * Avoids rebuilding novelId/bookKey maps and date parsing when not required.
     */
    fun sortWithReadProvider(
        entries: List<BookshelfEntry>,
        order: Order = Order.RECENT_READ,
        readAtProvider: (BookshelfEntry) -> Long?
    ): List<BookshelfEntry> {
        if (entries.isEmpty()) return entries
        return when (order) {
            Order.RECENT_READ -> {
                entries.map { entry ->
                    RankedEntry(
                        entry = entry,
                        readAt = readAtProvider(entry),
                        updatedAt = null
                    )
                }.sortedWith(
                    Comparator { left, right ->
                        val comparison = compareNullableDescending(left.readAt, right.readAt)
                            .takeIf { it != 0 }
                            ?: right.entry.addedAt.compareTo(left.entry.addedAt)
                        comparison.takeIf { it != 0 } ?: left.entry.bookKey.compareTo(right.entry.bookKey)
                    }
                ).map { it.entry }
            }
            Order.RECENT_ADDED -> {
                entries.sortedWith(
                    Comparator { left, right ->
                        val comparison = right.addedAt.compareTo(left.addedAt)
                        comparison.takeIf { it != 0 } ?: left.bookKey.compareTo(right.bookKey)
                    }
                )
            }
            Order.RECENT_UPDATED -> {
                entries.map { entry ->
                    RankedEntry(
                        entry = entry,
                        readAt = null,
                        updatedAt = updatedAt(entry)
                    )
                }.sortedWith(
                    Comparator { left, right ->
                        val comparison = compareNullableDescending(left.updatedAt, right.updatedAt)
                            .takeIf { it != 0 }
                            ?: right.entry.addedAt.compareTo(left.entry.addedAt)
                        comparison.takeIf { it != 0 } ?: left.entry.bookKey.compareTo(right.entry.bookKey)
                    }
                ).map { it.entry }
            }
        }
    }

    /**
     * Recently read books come first. Books without a local reading record
     * follow by local insertion time. The maps avoid an O(n*m) pairwise scan.
     */
    fun sort(
        entries: List<BookshelfEntry>,
        activities: List<LocalReadingActivity>,
        keyForUrl: (String) -> String,
        order: Order = Order.RECENT_READ
    ): List<BookshelfEntry> {
        if (entries.isEmpty()) return entries
        if (order != Order.RECENT_READ) {
            return sortWithReadProvider(entries, order) { null }
        }
        val latestByNovelId = HashMap<String, Long>()
        val latestByBookKey = HashMap<String, Long>()

        activities.forEach { activity ->
            if (activity.novelId.isNotBlank()) {
                latestByNovelId[activity.novelId] = maxOf(
                    latestByNovelId[activity.novelId] ?: Long.MIN_VALUE,
                    activity.lastReadAt
                )
            }
            val bookKey = activity.novelUrl.takeIf { it.isNotBlank() }?.let(keyForUrl).orEmpty()
            if (bookKey.isNotBlank()) {
                latestByBookKey[bookKey] = maxOf(
                    latestByBookKey[bookKey] ?: Long.MIN_VALUE,
                    activity.lastReadAt
                )
            }
        }

        return sortWithReadProvider(entries, Order.RECENT_READ) { entry ->
            readingAt(entry, latestByNovelId, latestByBookKey, keyForUrl)
        }
    }

    private data class RankedEntry(
        val entry: BookshelfEntry,
        val readAt: Long?,
        val updatedAt: Long?
    )

    private fun compareNullableDescending(left: Long?, right: Long?): Int = when {
        left != null && right == null -> -1
        left == null && right != null -> 1
        left != null && right != null -> right.compareTo(left)
        else -> 0
    }

    /**
     * The site exposes update dates as text. Parse the observed date forms when
     * possible, while retaining addedAt as a stable fallback for old rows or
     * pages whose date text is unavailable.
     */
    private fun updatedAt(entry: BookshelfEntry): Long? = entry.remoteUpdatedAt
        .trim()
        .takeIf(String::isNotBlank)
        ?.let(::parseDate)

    private fun parseDate(value: String): Long? {
        val normalized = value
            .replace('年', '-')
            .replace('月', '-')
            .replace("日", "")
            .replace('/', '-')
            .trim()
        if (normalized.isBlank()) return null
        val cached = dateCache[normalized]
        if (cached != null) return cached
        val zone = ZoneId.systemDefault()
        val parsed = try {
            if (normalized.contains(':')) {
                val formatter = if (normalized.count { it == ':' } >= 2) DT_WITH_SECONDS else DT_WITHOUT_SECONDS
                LocalDateTime.parse(normalized, formatter).atZone(zone).toInstant().toEpochMilli()
            } else {
                LocalDate.parse(normalized, DATE_ONLY).atStartOfDay(zone).toInstant().toEpochMilli()
            }
        } catch (_: Exception) {
            null
        }
        if (parsed != null) {
            if (dateCache.size >= MAX_DATE_CACHE_SIZE) dateCache.clear()
            dateCache[normalized] = parsed
        }
        return parsed
    }

    private val DT_WITH_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    private val DT_WITHOUT_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
    private val DATE_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)

    private fun readingAt(
        entry: BookshelfEntry,
        latestByNovelId: Map<String, Long>,
        latestByBookKey: Map<String, Long>,
        keyForUrl: (String) -> String
    ): Long? = latestByNovelId[entry.novelId]
        ?: latestByBookKey[entry.bookKey]
        ?: entry.url.takeIf { it.isNotBlank() }?.let(keyForUrl)?.let(latestByBookKey::get)
}

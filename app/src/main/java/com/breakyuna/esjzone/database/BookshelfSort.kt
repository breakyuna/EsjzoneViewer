package com.breakyuna.esjzone.database

import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.database.entity.LocalReadingActivity

/** Pure ordering rules for the local-first bookshelf. */
object BookshelfSort {

    /**
     * Recently read books come first. Books without a local reading record
     * follow by local insertion time. The maps avoid an O(n*m) pairwise scan.
     */
    fun sort(
        entries: List<BookshelfEntry>,
        activities: List<LocalReadingActivity>,
        keyForUrl: (String) -> String
    ): List<BookshelfEntry> {
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

        // Resolve each row once before sorting. URL normalization therefore
        // remains O(n), rather than being repeated inside O(n log n) compares.
        return entries.map { entry ->
            RankedEntry(
                entry = entry,
                readAt = readingAt(entry, latestByNovelId, latestByBookKey, keyForUrl)
            )
        }.sortedWith(
            Comparator { left, right ->
                val leftReadAt = left.readAt
                val rightReadAt = right.readAt
                when {
                    leftReadAt != null && rightReadAt == null -> -1
                    leftReadAt == null && rightReadAt != null -> 1
                    leftReadAt != null && rightReadAt != null -> {
                        rightReadAt.compareTo(leftReadAt).takeIf { it != 0 }
                            ?: left.entry.bookKey.compareTo(right.entry.bookKey)
                    }
                    else -> {
                        right.entry.addedAt.compareTo(left.entry.addedAt).takeIf { it != 0 }
                            ?: left.entry.bookKey.compareTo(right.entry.bookKey)
                    }
                }
            }
        ).map { it.entry }
    }

    private data class RankedEntry(
        val entry: BookshelfEntry,
        val readAt: Long?
    )

    private fun readingAt(
        entry: BookshelfEntry,
        latestByNovelId: Map<String, Long>,
        latestByBookKey: Map<String, Long>,
        keyForUrl: (String) -> String
    ): Long? = latestByNovelId[entry.novelId]
        ?: latestByBookKey[entry.bookKey]
        ?: entry.url.takeIf { it.isNotBlank() }?.let(keyForUrl)?.let(latestByBookKey::get)
}

package com.breakyuna.esjzone.database

import com.breakyuna.esjzone.database.entity.ReadingStat
import java.time.LocalDate

data class ReadingBookTotal(val bookKey: String, val bookName: String, val durationMs: Long)

data class ReadingTagTotal(val tag: String?, val durationMs: Long)

class ReadingStatisticsSummary(
    rows: List<ReadingStat>,
    val today: LocalDate,
    private val bookTags: Map<String, List<String>> = emptyMap()
) {
    private val datedRows = rows.mapNotNull { row ->
        runCatching { LocalDate.parse(row.date) }.getOrNull()?.let { date -> date to row }
    }
    val daily: Map<LocalDate, Long> = datedRows.groupBy { it.first }
        .mapValues { (_, values) -> values.sumOf { it.second.durationMs } }
    val totalMs: Long = daily.values.sum()
    val todayMs: Long = daily[today] ?: 0L
    val weekMs: Long = durationInLastDays(7)
    val streakDays: Int = run {
        var date = if ((daily[today] ?: 0L) >= 60_000L) today else today.minusDays(1)
        var count = 0
        while ((daily[date] ?: 0L) >= 60_000L) {
            count++
            date = date.minusDays(1)
        }
        count
    }
    fun durationInLastDays(days: Int): Long {
        val first = today.minusDays(days.toLong() - 1L)
        return daily.filterKeys { !it.isBefore(first) && !it.isAfter(today) }.values.sum()
    }

    fun tagDistribution(days: Int?): List<ReadingTagTotal> {
        val first = days?.let { today.minusDays(it.toLong() - 1L) }
        val totals = mutableMapOf<String, Long>()
        datedRows.forEach { (date, row) ->
            if (!date.isAfter(today) && (first == null || !date.isBefore(first))) {
                bookTags[row.bookKey].orEmpty().map(String::trim)
                    .filter(String::isNotEmpty).distinct().forEach { tag ->
                        totals[tag] = (totals[tag] ?: 0L) + row.durationMs
                    }
            }
        }
        val total = totals.values.sum()
        if (total <= 0L) return emptyList()
        val (visible, small) = totals.map { (tag, duration) -> ReadingTagTotal(tag, duration) }
            .filter { it.durationMs > 0L }
            .sortedWith(compareByDescending<ReadingTagTotal> { it.durationMs }.thenBy { it.tag })
            .partition { it.durationMs.toDouble() / total >= 0.05 }
        val otherDuration = small.sumOf { it.durationMs }
        return if (otherDuration > 0L) visible + ReadingTagTotal(null, otherDuration) else visible
    }

    fun topBooks(days: Int?): List<ReadingBookTotal> {
        val first = days?.let { today.minusDays(it.toLong() - 1L) }
        return datedRows.asSequence()
            .filter { (date, _) ->
                !date.isAfter(today) && (first == null || !date.isBefore(first))
            }
            .groupBy { it.second.bookKey }
            .map { (key, values) ->
                ReadingBookTotal(key, values.maxBy { it.first }.second.bookName, values.sumOf { it.second.durationMs })
            }
            .sortedWith(compareByDescending<ReadingBookTotal> { it.durationMs }.thenBy { it.bookName })
            .take(5)
    }
}

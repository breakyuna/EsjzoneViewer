package com.breakyuna.esjzone

import com.breakyuna.esjzone.database.ReadingStatisticsSession
import com.breakyuna.esjzone.database.ReadingStatisticsSummary
import com.breakyuna.esjzone.database.readingStatisticsBookKey
import com.breakyuna.esjzone.database.entity.ReadingStat
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingStatisticsTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun readingTimeAndTagsUseSameDetailFallbackKey() {
        val detailUrl = "https://www.esjzone.cc/detail/12345.html"
        assertEquals("12345", readingStatisticsBookKey("", detailUrl, "/forum/12345/99.html"))
        assertEquals("12345", readingStatisticsBookKey("", detailUrl))
        assertEquals("known-id", readingStatisticsBookKey("known-id", detailUrl))
    }

    @Test
    fun invalidDatesDoNotHideValidStatistics() {
        val today = LocalDate.parse("2026-09-26")
        val summary = ReadingStatisticsSummary(listOf(
            ReadingStat("invalid", "broken", "Broken", 900_000L),
            ReadingStat(today.toString(), "valid", "Valid", 60_000L)
        ), today, mapOf("valid" to listOf("Fantasy"), "broken" to listOf("Other")))

        assertEquals(60_000L, summary.totalMs)
        assertEquals("valid", summary.topBooks(null).single().bookKey)
        assertEquals("Fantasy", summary.tagDistribution(null).single().tag)
    }

    @Test
    fun intervalAcrossMidnightIsAssignedToBothDates() {
        val end = LocalDate.parse("2026-09-27").atStartOfDay(zone).plusSeconds(20).toInstant().toEpochMilli()
        val records = mutableListOf<ReadingStat>()
        var elapsed = 1_000L
        val session = ReadingStatisticsSession(
            "book", "Book", { records.add(it) },
            wallClock = { end }, elapsedClock = { elapsed }, zone = { zone }
        )

        session.start()
        elapsed += 50_000L
        session.stop()
        session.stop()

        assertEquals(2, records.size)
        assertEquals(30_000L, records.single { it.date == "2026-09-26" }.durationMs)
        assertEquals(20_000L, records.single { it.date == "2026-09-27" }.durationMs)
    }

    @Test
    fun checkpointsAndPauseDoNotDoubleCount() {
        val records = mutableListOf<ReadingStat>()
        var elapsed = 0L
        val session = ReadingStatisticsSession(
            "book", "Book", { records.add(it) },
            wallClock = { 1_700_000_000_000L + elapsed },
            elapsedClock = { elapsed }, zone = { zone }
        )
        session.start()
        elapsed = 30_000L
        session.checkpoint()
        elapsed = 45_000L
        session.stop()
        elapsed = 80_000L
        session.checkpoint()
        session.start()
        elapsed = 90_000L
        session.stop()

        assertEquals(55_000L, records.sumOf { it.durationMs })
    }

    @Test
    fun summaryUsesOneMinuteForStreakAndRanksBySelectedPeriod() {
        val today = LocalDate.parse("2026-09-26")
        val summary = ReadingStatisticsSummary(listOf(
            ReadingStat("2026-09-26", "a", "A", 59_000L),
            ReadingStat("2026-09-25", "a", "A", 70_000L),
            ReadingStat("2026-09-24", "b", "B", 90_000L),
            ReadingStat("2026-08-01", "c", "C", 400_000L)
        ), today)

        assertEquals(2, summary.streakDays)
        assertEquals(219_000L, summary.weekMs)
        assertEquals("a", summary.topBooks(7).first().bookKey)
        assertEquals("c", summary.topBooks(null).first().bookKey)
    }

    @Test
    fun tagTotalsIncludeAllBooksDeduplicateTagsAndRespectPeriod() {
        val today = LocalDate.parse("2026-09-26")
        val rows = (1..6).map { ReadingStat("2026-09-26", "$it", "Book $it", 60_000L) } + listOf(
            ReadingStat("2026-08-01", "old", "Old", 900_000L),
            ReadingStat("2026-09-27", "future", "Future", 900_000L),
            ReadingStat("2026-09-26", "unknown", "Unknown", 900_000L)
        )
        val tags = (1..6).associate { "$it" to listOf("Fantasy", " Fantasy ", " ") } + mapOf(
            "1" to listOf("Fantasy", "Fantasy", "Adventure"),
            "old" to listOf("Older"),
            "future" to listOf("Future")
        )
        val summary = ReadingStatisticsSummary(rows, today, tags)

        assertEquals(listOf("Fantasy", "Adventure"), summary.tagDistribution(7).map { it.tag })
        assertEquals(360_000L, summary.tagDistribution(7).first().durationMs)
        assertEquals(60_000L, summary.tagDistribution(30).last().durationMs)
        assertEquals("Older", summary.tagDistribution(null).first().tag)
        assertEquals(emptyList<Any>(), ReadingStatisticsSummary(rows, today).tagDistribution(null))
    }

    @Test
    fun tagDistributionGroupsBelowFivePercentWithoutDroppingDuration() {
        val today = LocalDate.parse("2026-09-26")
        val amounts = listOf(85L, 5L, 4L, 3L, 2L, 1L)
        val rows = amounts.mapIndexed { index, amount ->
            ReadingStat(today.toString(), "$index", "Book", amount)
        }
        val tags = amounts.indices.associate { "$it" to listOf("Tag $it") }
        val slices = ReadingStatisticsSummary(rows, today, tags).tagDistribution(null)
        assertEquals(listOf("Tag 0", "Tag 1", null), slices.map { it.tag })
        assertEquals(listOf(85L, 5L, 10L), slices.map { it.durationMs })
        assertEquals(100L, slices.sumOf { it.durationMs })
    }

    @Test
    fun tagDistributionCanContainOnlyOther() {
        val today = LocalDate.parse("2026-09-26")
        val rows = (1..21).map { ReadingStat(today.toString(), "$it", "Book", 1L) }
        val tags = (1..21).associate { "$it" to listOf("Tag $it") }
        val slices = ReadingStatisticsSummary(rows, today, tags).tagDistribution(7)
        assertEquals(1, slices.size)
        assertEquals(null, slices.single().tag)
        assertEquals(21L, slices.single().durationMs)
    }

}

package com.breakyuna.esjzone

import com.breakyuna.esjzone.database.ReadingStatisticsSession
import com.breakyuna.esjzone.database.ReadingStatisticsSummary
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.database.entity.ReadingStat
import com.breakyuna.esjzone.database.readingStatisticsBookKey
import com.breakyuna.esjzone.ui.page.statisticsActivitiesByBook
import com.breakyuna.esjzone.ui.page.statisticsCoverLookupUrl
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingStatisticsTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun activity(id: String, novelId: String, novelUrl: String, chapterUrl: String = "") =
        LocalReadingActivity(id, novelId, "书名", novelUrl, chapterUrl, "章节", 0, 1,
            0f, 0L, 0L, 0L)

    @Test
    fun readingTimeTagsAndHistoryUseSameBookIdentity() {
        val detailUrl = "https://www.esjzone.cc/detail/12345.html"
        assertEquals("12345", readingStatisticsBookKey("", detailUrl, "/forum/12345/99.html"))
        assertEquals("12345", readingStatisticsBookKey("", detailUrl))
        assertEquals("known-id", readingStatisticsBookKey("known-id", detailUrl))

        val latest = activity("new", "123", "https://www.esjzone.cc/detail/123.html")
        val older = activity("old", "123", "https://www.esjzone.one/detail/123.html")
        val missingId = activity("other", "", "https://www.esjzone.cc/detail/456.html")
        val byBook = statisticsActivitiesByBook(listOf(latest, older, missingId))
        assertEquals(latest, byBook["123"])
        assertEquals(missingId, byBook["456"])
    }

    @Test
    fun fallbackCoverLookupDoesNotTreatAForumPostAsABook() {
        assertEquals("/detail/123.html", statisticsCoverLookupUrl("123"))
        assertEquals("/detail/123.html", statisticsCoverLookupUrl("/detail/123.html"))
        assertEquals("https://example.com/book/42", statisticsCoverLookupUrl("https://example.com/book/42"))
        assertNull(statisticsCoverLookupUrl("/forum/123/456.html"))
        assertNull(statisticsCoverLookupUrl("https://user:password@example.com/book"))
        assertNull(statisticsCoverLookupUrl(""))
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
            wallClock = { end }, elapsedClock = { elapsed }, zone = { zone }, enabled = { true }
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
            elapsedClock = { elapsed }, zone = { zone }, enabled = { true }
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
    fun incognitoReadingIsExcludedAndNormalReadingResumes() {
        val records = mutableListOf<ReadingStat>()
        var elapsed = 0L
        var incognito = true
        val session = ReadingStatisticsSession(
            "book", "Book", { records.add(it) },
            wallClock = { 1_700_000_000_000L + elapsed },
            elapsedClock = { elapsed }, zone = { zone }, enabled = { !incognito }
        )
        session.start()
        elapsed = 30_000L
        session.stop()
        assertEquals(0, records.size)
        incognito = false
        session.start()
        elapsed = 40_000L
        session.stop()
        incognito = true
        session.start()
        elapsed = 70_000L
        session.stop()
        assertEquals(10_000L, records.sumOf { it.durationMs })
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
    fun tagDistributionGroupsBelowFivePercentAndSupportsFullListAndOnlyOther() {
        val today = LocalDate.parse("2026-09-26")
        val amounts = listOf(85L, 5L, 4L, 3L, 2L, 1L)
        val rows = amounts.mapIndexed { index, amount ->
            ReadingStat(today.toString(), "$index", "Book", amount)
        }
        val tags = amounts.indices.associate { "$it" to listOf("Tag $it") }
        val summary = ReadingStatisticsSummary(rows, today, tags)
        val grouped = summary.tagDistribution(null)
        assertEquals(listOf("Tag 0", "Tag 1", null), grouped.map { it.tag })
        assertEquals(listOf(85L, 5L, 10L), grouped.map { it.durationMs })
        assertEquals(100L, grouped.sumOf { it.durationMs })

        val full = summary.tagDistribution(null, groupSmallTags = false)
        assertEquals(amounts.indices.map { "Tag $it" }, full.map { it.tag })
        assertEquals(grouped.sumOf { it.durationMs }, full.sumOf { it.durationMs })

        val tinyRows = (1..21).map { ReadingStat(today.toString(), "$it", "Book", 1L) }
        val tinyTags = (1..21).associate { "$it" to listOf("Tag $it") }
        val onlyOther = ReadingStatisticsSummary(tinyRows, today, tinyTags).tagDistribution(7)
        assertEquals(1, onlyOther.size)
        assertEquals(null, onlyOther.single().tag)
        assertEquals(21L, onlyOther.single().durationMs)
    }

    @Test
    fun rankingResolvesKnownTitleAndHidesUnresolvedIdentityWithoutDroppingTime() {
        val today = LocalDate.parse("2026-10-30")
        val resolved = ReadingStatisticsSummary(listOf(
            ReadingStat("2026-09-01", "123", "1984", 500_000L),
            ReadingStat("2026-10-29", "123", "123", 60_000L),
            ReadingStat("2026-10-30", "123", "", 90_000L)
        ), today)
        assertEquals("1984", resolved.topBooks(7).single().bookName)
        assertEquals(150_000L, resolved.topBooks(7).single().durationMs)
        assertEquals(650_000L, resolved.topBooks(null).single().durationMs)

        val unresolved = ReadingStatisticsSummary(listOf(ReadingStat(today.toString(), "123", "123", 60_000L)), today)
        assertEquals("", unresolved.topBooks(7).single().bookName)
        assertEquals(60_000L, unresolved.totalMs)
    }
}

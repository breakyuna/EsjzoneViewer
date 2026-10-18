package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.features.selectWeeklyPopularSeeds
import com.breakyuna.esjzone.network.features.initialWeeklyPopular
import com.breakyuna.esjzone.network.features.mergeWeeklyPopularEnrichment
import com.breakyuna.esjzone.network.features.seedToInitialWeeklyPopular
import com.breakyuna.esjzone.network.features.WeeklyPopularSeed
import com.breakyuna.esjzone.novellibrary.data.WeeklyPopularNovel
import com.breakyuna.esjzone.novellibrary.novel.CoverFailure
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyPopularParserTest {
    @Test
    fun failedEnrichmentKeepsPreviouslyParsedCoverAndMetadata() {
        val previous = WeeklyPopularNovel(
            rank = 1, weeklyViews = 2000, name = "Current name", url = "/detail/1.html",
            coverUrl = "https://images.example.com/cover.jpg", coverResolved = true,
            descriptionPreview = "Cached description", author = "Cached author", isAdult = true
        )
        val failed = seedToInitialWeeklyPopular(
            WeeklyPopularSeed(1, 2000, "Current name", "/detail/1.html")
        ).copy(coverFailure = CoverFailure.NETWORK)

        assertEquals(previous, mergeWeeklyPopularEnrichment(previous, failed))
        val parsedWithoutCover = previous.copy(coverUrl = "")
        assertEquals(parsedWithoutCover, mergeWeeklyPopularEnrichment(parsedWithoutCover, failed))
        val legacyCover = previous.copy(coverResolved = null)
        assertEquals(legacyCover, mergeWeeklyPopularEnrichment(legacyCover, failed))
    }

    @Test
    fun failedEnrichmentReportsFailureWithoutLosingKnownMetadata() {
        val previous = WeeklyPopularNovel(
            url = "/detail/1.html", name = "Current name", isAdult = true,
            descriptionPreview = "Cached description", coverResolved = false
        )
        val failed = WeeklyPopularNovel(url = previous.url, coverFailure = CoverFailure.TIMEOUT)

        val result = mergeWeeklyPopularEnrichment(previous, failed)

        assertEquals(CoverFailure.TIMEOUT, result.coverFailure)
        assertEquals(previous.descriptionPreview, result.descriptionPreview)
        assertEquals(previous.name, result.name)
        assertTrue(result.isAdult)
    }

    @Test
    fun successfulEnrichmentReplacesPreviousFailure() {
        val previous = WeeklyPopularNovel(url = "/detail/1.html", coverFailure = CoverFailure.NETWORK)
        val refreshed = previous.copy(
            coverUrl = "https://images.example.com/new.jpg", coverResolved = true,
            descriptionPreview = "New description", coverFailure = null
        )

        assertEquals(refreshed, mergeWeeklyPopularEnrichment(previous, refreshed))
    }

    @Test
    fun initialRankingKeepsCachedCoverAndAdultFlagWhileUpdatingSeeds() {
        val cached = WeeklyPopularNovel(
            rank = 7, weeklyViews = 10, name = "Old name",
            url = "https://www.esjzone.cc/detail/1.html",
            coverUrl = "https://images.example.com/cover.jpg", coverResolved = true,
            isAdult = true, descriptionPreview = "Cached description"
        )
        val result = initialWeeklyPopular(
            listOf(WeeklyPopularSeed(1, 2000, "Current name", "/detail/1.html"),
                WeeklyPopularSeed(2, 1000, "New book", "/detail/2.html")),
            listOf(cached, WeeklyPopularNovel(url = "/detail/removed.html"))
        )

        assertEquals(listOf("/detail/1.html", "/detail/2.html"), result.map { it.url })
        assertEquals(1, result[0].rank)
        assertEquals(2000, result[0].weeklyViews)
        assertEquals("Current name", result[0].name)
        assertEquals(cached.coverUrl, result[0].coverUrl)
        assertEquals(cached.descriptionPreview, result[0].descriptionPreview)
        assertTrue(result[0].isAdult)
        assertEquals(true, result[0].coverResolved)
        assertEquals(false, result[1].coverResolved)
        assertTrue(result[1].coverUrl.isEmpty())
        assertFalse(result[1].isAdult)
    }

    @Test
    fun `keeps only the first ten ranked novels`() {
        val items = (1..12).joinToString("") { index ->
            "<li><a href='/detail/$index.html'>Novel $index</a><span>${index * 1000}</span></li>"
        }
        val document = Jsoup.parse("<section class='widget-categories-hot'><ul>$items</ul></section>")

        val result = selectWeeklyPopularSeeds(document)

        assertEquals(10, result.size)
        assertEquals(1, result.first().rank)
        assertEquals("/detail/1.html", result.first().url)
        assertEquals(10_000, result.last().weeklyViews)
    }
}

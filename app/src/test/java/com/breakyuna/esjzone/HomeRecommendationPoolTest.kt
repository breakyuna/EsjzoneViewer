package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.PageableRequester
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovelImpl
import com.breakyuna.esjzone.ui.tab.HomeRecommendationPool
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HomeRecommendationPoolTest {
    @Test
    fun batchesKeepSixTwelveSixAndReuseUnshownCardsWithoutMoreRequests() = runBlocking {
        val catalog = Catalog()
        val pool = HomeRecommendationPool(catalog::open)
        val shown = mutableSetOf<String>()

        repeat(3) {
            val batch = pool.nextBatch(adult = true, restartIfExhausted = false)
            assertEquals(24, batch.size)
            assertEquals(6, batch.count { it.name.startsWith("4-") })
            assertEquals(12, batch.count { it.name.startsWith("1-") })
            assertEquals(6, batch.count { it.name.startsWith("2-") })
            assertTrue(batch.all { shown.add(it.url) })
        }

        assertEquals(listOf(4 to 1, 1 to 1, 2 to 1), catalog.requests)
        assertTrue(pool.hasMore)
    }

    @Test
    fun overlappingSourcesAndBatchesNeverRepeatTheSameNovel() = runBlocking {
        val catalog = Catalog(pages = 1) { _, _ -> books("shared", 40) }
        val pool = HomeRecommendationPool(catalog::open)
        val shown = mutableSetOf<String>()

        repeat(3) {
            val batch = pool.nextBatch(adult = true, restartIfExhausted = false)
            assertTrue(batch.all { shown.add(it.url) })
        }

        assertEquals(40, shown.size)
        assertFalse(pool.hasMore)
    }

    @Test
    fun adultFilteringDoesNotFillMissingQuotaFromAnotherSource() = runBlocking {
        val catalog = Catalog(pages = 1) { sort, _ ->
            books("$sort-public", 10) + books("$sort-adult", 30, adult = true)
        }
        val pool = HomeRecommendationPool(catalog::open)

        val batch = pool.nextBatch(adult = false, restartIfExhausted = false)

        assertTrue(batch.none { it.isAdult })
        assertEquals(6, batch.count { it.name.startsWith("4-") })
        assertEquals(10, batch.count { it.name.startsWith("1-") })
        assertEquals(6, batch.count { it.name.startsWith("2-") })
    }

    @Test
    fun paginationStaysWithinSourceLimitsAndKeepsEveryUnusedCard() = runBlocking {
        val catalog = Catalog(pages = 100)
        val pool = HomeRecommendationPool(catalog::open)
        val shown = mutableSetOf<String>()
        var batches = 0

        while (pool.hasMore && batches < 150) {
            val batch = pool.nextBatch(adult = true, restartIfExhausted = false)
            assertTrue(batch.all { shown.add(it.url) })
            batches += 1
        }

        assertFalse(pool.hasMore)
        assertEquals(1200, shown.size)
        assertEquals((1..5).toSet(), catalog.requests.filter { it.first == 4 }.map { it.second }.toSet())
        assertEquals((1..20).toSet(), catalog.requests.filter { it.first == 1 }.map { it.second }.toSet())
        assertEquals((1..5).toSet(), catalog.requests.filter { it.first == 2 }.map { it.second }.toSet())
        assertEquals(30, catalog.requests.size)
    }

    @Test
    fun filteringBoundsEachSourceToThreePageReadsPerBatch() = runBlocking {
        val catalog = Catalog(pages = 100) { sort, page -> books("$sort-$page", 40, adult = true) }
        val pool = HomeRecommendationPool(catalog::open)

        assertTrue(pool.nextBatch(adult = false, restartIfExhausted = false).isEmpty())

        assertEquals(mapOf(4 to 3, 1 to 3, 2 to 3), catalog.requests.groupingBy { it.first }.eachCount())
        assertTrue(pool.hasMore)
    }

    @Test
    fun failedBatchDoesNotConsumeCandidatesBeforeRetry() = runBlocking {
        val catalog = Catalog(pages = 1) { sort, _ -> books("$sort", if (sort == 1) 12 else 6) }
        var failUpdateSource = true
        val pool = HomeRecommendationPool { sort ->
            if (sort == 1 && failUpdateSource) throw IOException("Test request failure")
            catalog.open(sort)
        }
        try {
            pool.nextBatch(adult = true, restartIfExhausted = false)
            fail("Expected request failure")
        } catch (_: IOException) {
            // The next attempt must still include the six most-viewed books.
        }
        failUpdateSource = false

        val batch = pool.nextBatch(adult = true, restartIfExhausted = false)

        assertEquals(24, batch.size)
        assertEquals(6, batch.count { it.name.startsWith("4-") })
        assertFalse(pool.hasMore)
    }

    @Test
    fun exhaustedPoolOnlyStartsAnotherRoundWhenReplacing() = runBlocking {
        val catalog = Catalog(pages = 1) { sort, _ -> books("$sort", if (sort == 1) 12 else 6) }
        val pool = HomeRecommendationPool(catalog::open)
        val first = pool.nextBatch(adult = true, restartIfExhausted = false)

        assertFalse(pool.hasMore)
        assertTrue(pool.nextBatch(adult = true, restartIfExhausted = false).isEmpty())
        val replacement = pool.nextBatch(adult = true, restartIfExhausted = true)

        assertEquals(first.map { it.url }.toSet(), replacement.map { it.url }.toSet())
        assertEquals(24, replacement.size)
    }

    private class Catalog(
        private val pages: Int = 3,
        private val cards: (Int, Int) -> List<CoveredNovel> = { sort, page -> books("$sort-$page", 40) }
    ) {
        val requests = mutableListOf<Pair<Int, Int>>()

        fun open(sort: Int): Pair<PageableRequester<CoveredNovel>, List<CoveredNovel>> {
            val requester = object : PageableRequester<CoveredNovel> {
                override fun pages() = pages
                override fun more(): List<CoveredNovel> = error("Explicit page required")
                override fun end() = false
                override fun more(page: Int): List<CoveredNovel> {
                    requests += sort to page
                    return cards(sort, page)
                }
            }
            return requester to requester.more(1)
        }
    }

    companion object {
        private fun books(prefix: String, count: Int, adult: Boolean = false): List<CoveredNovel> =
            (1..count).map { index ->
                CoveredNovelImpl(
                    name = "$prefix-$index",
                    url = "/detail/$prefix-$index.html",
                    isAdult = adult
                )
            }
    }
}

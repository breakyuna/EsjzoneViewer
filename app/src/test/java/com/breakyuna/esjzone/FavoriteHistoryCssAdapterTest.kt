package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.features.pageCount
import com.breakyuna.esjzone.network.features.parseFavoriteNovels
import com.breakyuna.esjzone.network.features.parseHistoryNovels
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CSS contracts for account favorites and cloud history pages. */
class FavoriteHistoryCssAdapterTest {

    @Test
    fun favoriteFixture_preservesPagerTitlesAndDetailLinks() {
        val document = fixture("favorite.html")
        val favorites = parseFavoriteNovels(document)

        assertEquals(2, pageCount(document))
        assertEquals(
            listOf("Favorite Fixture One", "Favorite Fixture Two"),
            favorites.map { it.name }
        )
        assertEquals(
            listOf("/detail/9050.html", "/detail/9051.html"),
            favorites.map { it.url }
        )
    }

    @Test
    fun historyFixture_preservesDomOrder() {
        val document = fixture("history.html")
        val histories = parseHistoryNovels(document)

        assertEquals(
            listOf("9060", "9061"),
            document.select("tr[id^='novel-']").map { it.id().removePrefix("novel-") }
        )
        assertEquals(
            listOf("log-1", "log-2"),
            histories.map { it.vid }
        )
        assertEquals(
            listOf("History Fixture One", "History Fixture Two"),
            histories.map { it.name }
        )
        assertEquals(
            listOf("History Chapter One", "History Chapter Two"),
            histories.map { it.chapter.name }
        )
        assertTrue(histories.all { it.chapter.isHistory })
        assertTrue(histories.all { it.chapter.url.contains("/forum/") })
        assertEquals(
            listOf("log-1", "log-2"),
            document.select(".view-del").eachAttr("data-id")
        )
    }

    @Test
    fun historyDeletionId_prefersButtonThenViewRowAndNeverNovelId() {
        val document = Jsoup.parse("""
            <table class="table">
              <tr id="view_9101"><td><h5><a href="/detail/9001.html">One</a></h5>
                <a href="/forum/9001/9201.html">Chapter</a><a class="view-del" data-id="9102"></a></td></tr>
              <tr id="view_9103"><td><h5><a href="/detail/9002.html">Two</a></h5>
                <a href="/forum/9002/9202.html">Chapter</a></td></tr>
              <tr id="novel-9003"><td><h5><a href="/detail/9003.html">Three</a></h5>
                <a href="/forum/9003/9203.html">Chapter</a></td></tr>
            </table>
        """)
        assertEquals(listOf("9102", "9103", ""), parseHistoryNovels(document).map { it.vid })
    }

    private fun fixture(name: String) =
        Jsoup.parse(
            requireNotNull(javaClass.getResource("/esj/$name"))
                .openStream().bufferedReader().use { it.readText() },
            "https://example.test/"
        )
}

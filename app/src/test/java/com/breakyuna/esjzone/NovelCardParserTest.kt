package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.features.HomeSection
import com.breakyuna.esjzone.network.features.NovelCardLayout
import com.breakyuna.esjzone.network.features.pageCount
import com.breakyuna.esjzone.network.features.parseNovelCard
import com.breakyuna.esjzone.network.features.selectHomeSectionCards
import com.breakyuna.esjzone.network.features.selectNovelCards
import com.breakyuna.esjzone.network.features.selectNovelForumLinks
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelCardParserTest {
    @Test
    fun parsesSharedFieldsAndCountTextWithoutChangingR18Override() {
        val card = Jsoup.parse(
            """<div class="card"><img data-src="/cover.jpg"><span class="ignored"></span></div>""",
            "https://www.esjzone.cc/"
        ).selectFirst("div")!!
        val novel = parseNovelCard(card, "小說", "/detail/42.html", "觀看：1,234", "收藏：56", true)
        assertEquals("小說", novel.name)
        assertEquals("/detail/42.html", novel.url)
        assertEquals(1234, novel.views)
        assertEquals(56, novel.likes)
        assertTrue(novel.isAdult)
    }

    @Test
    fun parsesHomeLatestChapterAndKeepsListOnlyMetadataAbsent() {
        val card = Jsoup.parse(
            """
            <div class="card mb-30">
                <div class="card-img-tiles"><a href="/detail/42.html"><img data-src="/cover.jpg"></a></div>
                <div><h5 class="card-title"><a href="/detail/42.html">小說</a></h5></div>
                <div class="card-ep"><a href="/forum/42/7.html">第 7 章</a></div>
                <div class="card-other"><span data-feather="eye"></span> 1,234</div>
                <div class="card-other"><span data-feather="heart"></span> 56</div>
            </div>
            """.trimIndent(),
            "https://www.esjzone.cc/"
        ).selectFirst(".card")!!

        val novel = parseNovelCard(card, false, NovelCardLayout.HOME)

        assertEquals("第 7 章", novel.latestTitle)
        assertEquals("/forum/42/7.html", novel.latestUrl)
        assertEquals(1234, novel.views)
        assertEquals(56, novel.likes)
        assertNull(novel.author)
        assertNull(novel.words)
        assertNull(novel.articleCount)
        assertNull(novel.discussionCount)
    }

    @Test
    fun parsesListMetadataAcrossMultipleCardOtherBlocks() {
        val card = Jsoup.parse(
            """
            <div class="card mb-30">
                <div class="card-img-tiles"><a href="/detail/42.html"><img data-src="/cover.jpg"></a></div>
                <div><h5 class="card-title"><a href="/detail/42.html">小說</a></h5></div>
                <div class="card-ep"><a href="/forum/42/7.html">第 7 章</a></div>
                <div class="card-author"><a href="/tags/author/">作者</a></div>
                <div class="card-other"><span data-feather="file-text"></span> 205,943</div>
                <div class="card-other"><span data-feather="eye"></span> 1,234 <span data-feather="heart"></span> 56</div>
                <div class="card-other"><span data-feather="feather"></span> 480 <span data-feather="message-square"></span> 1023</div>
            </div>
            """.trimIndent(),
            "https://www.esjzone.cc/"
        ).selectFirst(".card")!!

        val novel = parseNovelCard(card, false, NovelCardLayout.LIST)

        assertEquals("作者", novel.author)
        assertEquals("/tags/author/", novel.authorUrl)
        assertEquals(205943, novel.words)
        assertEquals(480, novel.articleCount)
        assertEquals(1023, novel.discussionCount)
        assertEquals(1234, novel.views)
        assertEquals(56, novel.likes)
    }

    @Test
    fun homeSections_keepIndependentOrderingStatisticsAndAdultSemantics() {
        val document = fixture("home.html")
        val expected = listOf(
            HomeSection.TRANSLATED to Triple("Home Fixture Novel", 1_234, false),
            HomeSection.ORIGINAL to Triple("Original Fixture Novel", 321, false),
            HomeSection.TRANSLATED_R18 to Triple("Translated R18 Fixture", 654, true),
            HomeSection.ORIGINAL_R18 to Triple("Original R18 Fixture", 987, true),
            HomeSection.RECOMMENDATION to Triple("Recommended Fixture Novel", 78, true)
        )

        expected.forEach { (section, contract) ->
            val cards = selectHomeSectionCards(document, section)
            assertEquals("card count for ${section.key}", 1, cards.size)
            val novel = parseNovelCard(cards.single(), section.name.endsWith("R18"), NovelCardLayout.HOME)
            assertEquals(contract.first, novel.name)
            assertEquals("/detail/${when (section) {
                HomeSection.TRANSLATED -> "9001"
                HomeSection.ORIGINAL -> "9003"
                HomeSection.TRANSLATED_R18 -> "9004"
                HomeSection.ORIGINAL_R18 -> "9005"
                HomeSection.RECOMMENDATION -> "9002"
            }}.html", novel.url)
            assertEquals(contract.second, novel.views)
            assertEquals(contract.third, novel.isAdult)
        }
    }

    @Test
    fun listFixture_selectsOnlyNovelForumBoards() {
        val links = selectNovelForumLinks(fixture("list.html"))
        assertEquals(1, links.size)
        assertEquals("Category Fixture Novel", links.single().text())
        assertEquals("/forum/9100/9110/", links.single().attr("href"))
    }

    @Test
    fun listAndSearchFixtures_selectCardsAndPagerWithoutPositionalXpath() {
        assertEquals(1, selectNovelCards(fixture("list.html")).size)
        assertEquals(2, selectNovelCards(fixture("search.html")).size)
        assertEquals(2, pageCount(fixture("search.html")))
        assertEquals(1, pageCount(fixture("list.html")))
    }

    private fun fixture(name: String): Document =
        Jsoup.parse(
            requireNotNull(javaClass.getResource("/esj/$name")) {
                "missing checked-in parser fixture: $name"
            }.openStream().bufferedReader().use { it.readText() },
            "https://example.test/"
        )
}

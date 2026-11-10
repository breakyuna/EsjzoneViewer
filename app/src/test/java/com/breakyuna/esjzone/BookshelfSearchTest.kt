package com.breakyuna.esjzone

import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.ui.page.BookshelfSearch
import com.breakyuna.esjzone.ui.page.bookshelfSelectAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfSearchTest {
    // JVM tests have no ICU data; this stands in for Android's Traditional-Simplified transform.
    private val traditional = mapOf('劍' to '剑', '戀' to '恋', '愛' to '爱', '書' to '书', '說' to '说', '輕' to '轻')
    private val fold: (String) -> String = { text -> text.map { traditional[it] ?: it }.joinToString("") }

    private fun entry(key: String, title: String, author: String = "") = BookshelfEntry(
        scope = "scope", bookKey = key, novelId = key, url = "https://www.esjzone.cc/detail/$key.html",
        title = title, author = author
    )

    private val books = listOf(
        entry("1", "刀剑神域 Sword Art Online", "川原砾"),
        entry("2", "我的青春戀愛物語果然有問題", "渡航"),
        entry("3", "轻小说的书", ""),
        entry("4", "ＲＥ：ゼロから始める異世界生活", "長月達平")
    )

    private fun search(query: String): List<String> {
        val terms = BookshelfSearch.terms(query, fold)
        return BookshelfSearch.filter(books, terms, BookshelfSearch.index(books, fold), fold).map { it.bookKey }
    }

    @Test
    fun blankQueryKeepsEveryBookInOrder() {
        assertEquals(listOf("1", "2", "3", "4"), search(""))
        assertEquals(listOf("1", "2", "3", "4"), search("   \t "))
    }

    @Test
    fun matchingIgnoresCaseAndWidth() {
        assertEquals(listOf("1"), search("SWORD art"))
        assertEquals(listOf("4"), search("re：ゼロ"))
        assertEquals(listOf("4"), search("Ｒｅ"))
    }

    @Test
    fun whitespaceIsIgnoredInsideTitlesAndSplitsTerms() {
        assertEquals(listOf("1"), search("swordart"))
        assertEquals(listOf("1"), search("  刀剑  神域 "))
        assertEquals(listOf("1"), search("神域\u3000online"))
    }

    @Test
    fun simplifiedAndTraditionalMatchEachOther() {
        assertEquals(listOf("1"), search("刀劍"))
        assertEquals(listOf("2"), search("恋爱"))
        assertEquals(listOf("2"), search("戀愛"))
        assertEquals(listOf("3"), search("輕小說"))
    }

    @Test
    fun authorMatchesAndTermsCombineAcrossFields() {
        assertEquals(listOf("2"), search("渡航"))
        assertEquals(listOf("1"), search("川原 刀剑"))
        assertEquals(emptyList<String>(), search("川原 青春"))
    }

    @Test
    fun noMatchReturnsEmptyResult() {
        assertEquals(emptyList<String>(), search("不存在的书"))
    }

    @Test
    fun rowsMissingFromTheIndexAreStillMatched() {
        val extra = entry("5", "新加入的書")
        val terms = BookshelfSearch.terms("新加入的书", fold)
        val result = BookshelfSearch.filter(books + extra, terms, BookshelfSearch.index(books, fold), fold)
        assertEquals(listOf("5"), result.map { it.bookKey })
    }

    @Test
    fun selectAllAppliesOnlyToShownResults() {
        val shown = linkedSetOf("1", "3")
        assertEquals(shown, bookshelfSelectAll(emptySet(), shown))
        assertEquals(shown, bookshelfSelectAll(setOf("1"), shown))
        assertTrue(bookshelfSelectAll(shown, shown).isEmpty())
        assertTrue(bookshelfSelectAll(emptySet(), emptySet()).isEmpty())
    }

    @Test
    fun defaultFoldWithoutIcuLeavesTextUsable() {
        assertEquals("刀剑神域", BookshelfSearch.normalize(" 刀剑 神域 "))
        assertEquals("abc", BookshelfSearch.normalize("ＡＢＣ"))
    }
}

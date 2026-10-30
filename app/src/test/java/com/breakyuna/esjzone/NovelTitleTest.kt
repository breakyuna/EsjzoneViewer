package com.breakyuna.esjzone

import com.breakyuna.esjzone.novellibrary.novel.preferredNovelTitle
import com.breakyuna.esjzone.novellibrary.novel.validNovelTitle
import org.junit.Assert.assertEquals
import org.junit.Test

class NovelTitleTest {
    @Test
    fun identityPlaceholdersCannotReplaceAKnownTitle() {
        val url = "https://www.esjzone.cc/detail/1791692704.html"
        assertEquals("Real title", preferredNovelTitle("1791692704", "Real title", novelUrl = url))
        assertEquals("Real title", preferredNovelTitle("", "Real title", novelUrl = url))
        assertEquals("Real title", preferredNovelTitle("https://www.esjzone.one/detail/1791692704.html", "Real title", novelUrl = url))
        assertEquals("", validNovelTitle("1791692704", novelUrl = url))
    }

    @Test
    fun numericAndMixedNumericTitlesRemainUsable() {
        val url = "/detail/1791692704.html"
        assertEquals("1984", validNovelTitle(" 1984 ", novelUrl = url))
        assertEquals("86—不存在的战区—", validNovelTitle("86—不存在的战区—", novelUrl = url))
    }

    @Test
    fun chapterFallbackIsNotPromotedToTheBookTitle() {
        assertEquals("", validNovelTitle("Chapter one", "123", chapterName = "Chapter one"))
        assertEquals("Real title", validNovelTitle("Real title", "123", chapterName = "Chapter one"))
    }
}

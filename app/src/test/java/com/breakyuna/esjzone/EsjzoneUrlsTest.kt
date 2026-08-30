package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.EsjzoneUrls
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EsjzoneUrlsTest {

    @Test
    fun coverOrEmpty_recognizesSitePlaceholderVariants() {
        val placeholders = listOf(
            "https://www.esjzone.cc/assets/img/empty_cover.jpg?t=123",
            "https://www.esjzone.cc/assets/img/nophoto.png",
            "/assets/img/no-image",
            "/assets/img/placeholder.webp"
        )

        placeholders.forEach { url ->
            assertEquals("", EsjzoneUrls.coverOrEmpty(url))
        }
    }

    @Test
    fun coverOrEmpty_keepsAndResolvesActualRelativeCover() {
        assertEquals(
            "https://www.esjzone.cc/uploads/covers/book.jpg",
            EsjzoneUrls.coverOrEmpty("/uploads/covers/book.jpg")
        )
    }

    @Test
    fun coverUrlFromNovelCard_readsLazyAttributesBeforePlaceholderSrc() {
        val card = Jsoup.parse(
            """
            <div class="novel-card">
              <a><img src="/assets/img/empty_cover.jpg" data-original="/uploads/covers/book.jpg"></a>
            </div>
            """.trimIndent(),
            "https://www.esjzone.cc/"
        ).selectFirst(".novel-card")

        assertEquals(
            "https://www.esjzone.cc/uploads/covers/book.jpg",
            EsjzoneUrls.coverUrlFromNovelCard(card)
        )
    }

    @Test
    fun coverUrlFromNovelCard_skipsPlaceholderCandidateAndUsesNextLazyAttribute() {
        val card = Jsoup.parse(
            """
            <div class="novel-card">
              <a><img data-src="/assets/img/nophoto.png" data-lazy-src="/uploads/covers/book.jpg"></a>
            </div>
            """.trimIndent(),
            "https://www.esjzone.cc/"
        ).selectFirst(".novel-card")

        assertEquals(
            "https://www.esjzone.cc/uploads/covers/book.jpg",
            EsjzoneUrls.coverUrlFromNovelCard(card)
        )
    }

    @Test
    fun coverUrlFromNovelCard_returnsEmptyForPlaceholderOnlyCard() {
        val card = Jsoup.parse(
            """
            <div><a><img src="/assets/img/empty_cover.jpg"></a></div>
            """.trimIndent(),
            "https://www.esjzone.cc/"
        ).selectFirst("div")

        assertTrue(EsjzoneUrls.coverUrlFromNovelCard(card).isEmpty())
    }
}

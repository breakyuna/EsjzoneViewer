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
            "/assets/img/placeholder.webp",
            "https://i.pinimg.com/564x/86/1e/51/861e5157abc25f92f6b49af0f1465927.jpg",
            "https://i.pinimg.com/236x/86/1e/51/861e5157abc25f92f6b49af0f1465927.webp?t=123",
            "https://images.example.com/covers/861e5157abc25f92f6b49af0f1465927.png"
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
    fun coverOrEmpty_keepsNormalPinterestCover() {
        val cover = "https://i.pinimg.com/564x/aa/bb/cc/aabbccddeeff00112233445566778899.jpg"

        assertEquals(cover, EsjzoneUrls.coverOrEmpty(cover))
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

    @Test
    fun coverUrlFromNovelCard_returnsEmptyForEsjGrayLazyBackground() {
        val card = Jsoup.parse(
            """
            <div class="card mb-30">
              <a class="card-img-tiles">
                <div class="inner"><div class="main-img">
                  <div class="lazyload" data-src="https://i.pinimg.com/564x/86/1e/51/861e5157abc25f92f6b49af0f1465927.jpg"></div>
                </div></div>
              </a>
            </div>
            """.trimIndent(),
            "https://www.esjzone.cc/"
        ).selectFirst(".card")

        assertTrue(EsjzoneUrls.coverUrlFromNovelCard(card).isEmpty())
    }
}

package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.features.isPasswordProtectedChapterHtml
import com.breakyuna.esjzone.network.features.isPasswordProtectedChapter
import com.breakyuna.esjzone.network.features.parseCommentPageSnapshot
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.network.PageResponsePolicy
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterPasswordProtectionTest {

    @Test
    fun commentsRemainReadableWhileChapterBodyIsPasswordProtected() {
        val url = "https://www.esjzone.cc/forum/9001/2.html"
        val html = """
            <html><head><title>ESJZone</title></head><body>
              <div class="forum-content mt-3">
                <div id="oops">请输入密码</div>
                <input id="pw" name="pw" type="text">
                <button class="btn-send-pw">送出</button>
              </div>
              <section class="comments-section">
                <div class="comment" id="comment-7">
                  <div class="comment-text">See the author's instructions.</div>
                </div>
              </section>
            </body></html>
        """.trimIndent()

        assertTrue(isPasswordProtectedChapterHtml(html, url))
        assertTrue(PageResponsePolicy.validate(200, html, url, kind = PageKind.COMMUNITY).trusted)
        val snapshot = parseCommentPageSnapshot(Jsoup.parse(html, url), url)
        assertEquals(listOf("7"), snapshot.comments.map { it.id })
        assertEquals("See the author's instructions.", snapshot.comments.single().contentText)
    }

    @Test
    fun recognizesEsjPasswordGateWithoutTreatingOrdinaryInputsAsProtectedChapters() {
        val protected = """
            <html><body><div class="forum-content mt-3">
              <div id="oops">请输入密码</div>
              <input id="pw" name="pw" type="text">
              <button class="btn-send-pw">送出</button>
            </div></body></html>
        """.trimIndent()
        val ordinary = """
            <html><body><div class="forum-content"><p>正文</p>
              <input id="pw" name="pw"><button class="btn-send-pw">送出</button>
            </div></body></html>
        """.trimIndent()

        assertTrue(isPasswordProtectedChapterHtml(protected))
        assertFalse(isPasswordProtectedChapterHtml(ordinary))
        val document = Jsoup.parse(protected)
        assertTrue(document.isPasswordProtectedChapter())
        document.selectFirst(".forum-content")!!.html("<p>解锁后的正文</p>")
        assertFalse(document.isPasswordProtectedChapter())
    }
}

package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.features.isPasswordProtectedChapterHtml
import com.breakyuna.esjzone.network.features.isPasswordProtectedChapter
import org.jsoup.Jsoup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterPasswordProtectionTest {

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

package com.breakyuna.esjzone.network.external

import com.breakyuna.esjzone.data.reader.withStructuredBody
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.novellibrary.component.analyseComponents
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.ChapterSource
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.novellibrary.novel.resolveChapterSource
import java.io.IOException
import java.nio.charset.Charset
import org.jsoup.Jsoup
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val MAX_BROWSER_CHAPTER_HTML_BYTES = 4 * 1024 * 1024

class ExternalChapterParseException : IOException("External chapter content could not be recognized")
class UnsupportedExternalChapterException : IOException("Unsupported external chapter")

internal object ExternalChapterHtml {
    private val charsetPattern = Regex("(?i)charset\\s*=\\s*['\"]?([a-z0-9_+.-]+)")

    fun decode(bytes: ByteArray, contentType: String?): String {
        val prefix = String(bytes, 0, minOf(bytes.size, 8192), Charsets.ISO_8859_1)
        val httpCharset = contentType?.let { charsetPattern.find(it)?.groupValues?.get(1) }
        val document = Jsoup.parse(prefix)
        val metaCharset = document.selectFirst("meta[charset]")?.attr("charset")
        val httpEquiv = document.selectFirst("meta[http-equiv=Content-Type]")?.attr("content")
            ?.let { charsetPattern.find(it)?.groupValues?.get(1) }
        val charset = sequenceOf(httpCharset, metaCharset, httpEquiv, "GBK")
            .filterNotNull()
            .mapNotNull { runCatching { Charset.forName(it.trim()) }.getOrNull() }
            .first()
        return String(bytes, charset)
    }

    fun parse(html: String, chapter: Chapter, url: String): DetailedChapter {
        val document = Jsoup.parse(html, url)
        val content = document.selectFirst("#content") ?: throw ExternalChapterParseException()
        fun nav(labels: String): Chapter? = document.select("a[href]")
            .lastOrNull { it.text().trim().matches(Regex(labels)) }
            ?.let { link ->
                val resolved = EsjzoneUrls.resolve(link.attr("href"), url)
                if (resolveChapterSource(resolved) == ChapterSource.WENKU8) {
                    Chapter(link.text().trim(), resolved, false)
                } else null
            }
        val previous = nav("上一页|上一章")
        val next = nav("下一页|下一章")
        content.select("script, style, iframe, form, button, nav, .ad, .ads, [id*=advert], [class*=advert]").remove()
        content.select("a[href]").forEach { link ->
            val label = link.text().trim()
            if (label.matches(Regex("(?i).*(上一页|下一页|上一章|下一章|返回书目|加入书签|添加书签).*"))) {
                link.remove()
            }
        }
        content.select("img").forEach { image ->
            val raw = image.attr("src")
            val resolved = EsjzoneUrls.resolve(raw, url)
            if (resolved.toHttpUrlOrNull()?.let { it.isHttps && it.host == "www.wenku8.net" &&
                    it.username.isEmpty() && it.password.isEmpty() } == true) image.attr("src", resolved)
            else image.remove()
        }
        if (content.text().trim().length < 20 && content.select("img").isEmpty()) {
            throw ExternalChapterParseException()
        }
        val title = document.selectFirst("#title")?.text()?.trim().orEmpty().ifBlank { chapter.name }
        return DetailedChapter(
            title, analyseComponents(content), previous, next,
            content.html(), url
        ).withStructuredBody()
    }

    fun cacheDocument(detail: DetailedChapter, url: String): String {
        val document = Jsoup.parse("<html><body><div id=title></div><div id=content></div></body></html>", url)
        document.selectFirst("#title")?.text(detail.name)
        document.selectFirst("#content")?.html(detail.contentHtml.orEmpty())
        detail.previous?.let { document.body().appendElement("a").attr("href", it.url).text("上一页") }
        detail.next?.let { document.body().appendElement("a").attr("href", it.url).text("下一页") }
        return document.outerHtml()
    }
}

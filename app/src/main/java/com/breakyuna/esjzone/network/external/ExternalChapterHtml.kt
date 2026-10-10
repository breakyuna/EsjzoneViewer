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
class UnsupportedExternalChapterException(
    stage: String = "chapter-source",
    kind: com.breakyuna.esjzone.network.wenku8.Wenku8PageKind? = null,
    requestedUrl: String? = null,
    actualUrl: String? = null
) : IOException("Unsupported external chapter: stage=$stage, kind=${kind?.name ?: "CHAPTER"}, " +
    "requested=${wenkuDiagnosticUrl(requestedUrl)}, actual=${wenkuDiagnosticUrl(actualUrl)}")

/** Query values, fragments and user information may contain session credentials. */
internal fun wenkuDiagnosticUrl(raw: String?): String {
    if (raw == null) return "unavailable"
    when (raw) {
        "" -> return "empty-url"
        "null" -> return "javascript-null"
        "undefined" -> return "javascript-undefined"
        "about:blank" -> return "about:blank"
    }
    val url = raw.toHttpUrlOrNull() ?: return "invalid-url"
    val address = url.newBuilder().username("").password("").query(null).fragment(null).build()
    return "$address queryNames=${url.queryParameterNames.sorted()}"
}

internal object ExternalChapterHtml {
    private val charsetPattern = Regex("(?i)charset\\s*=\\s*['\"]?([a-z0-9_+.-]+)")

    fun decode(bytes: ByteArray, contentType: String?): String {
        val prefix = String(bytes, 0, minOf(bytes.size, 8192), Charsets.ISO_8859_1)
        val httpCharset = contentType?.let { charsetPattern.find(it)?.groupValues?.get(1) }
        val document = Jsoup.parse(prefix)
        val metaCharset = document.selectFirst("meta[charset]")?.attr("charset")
        val httpEquiv = document.selectFirst("meta[http-equiv=Content-Type]")?.attr("content")
            ?.let { charsetPattern.find(it)?.groupValues?.get(1) }
        val charset = sequenceOf(httpCharset, metaCharset, httpEquiv, "GB18030")
            .filterNotNull()
            .mapNotNull {
                val name = it.trim()
                val compatible = if (name.lowercase() in setOf("gbk", "gb2312", "gb_2312-80", "x-gbk")) "GB18030" else name
                runCatching { Charset.forName(compatible) }.getOrNull()
            }
            .first()
        return String(bytes, charset)
    }

    fun parse(html: String, chapter: Chapter, url: String): DetailedChapter {
        val document = Jsoup.parse(html, url)
        if (document.text().contains("因版权问题")) {
            throw com.breakyuna.esjzone.network.wenku8.Wenku8RestrictedException()
        }
        val content = document.selectFirst("#content") ?: throw ExternalChapterParseException()
        fun nav(labels: String): Chapter? = document.select("a[href]")
            .lastOrNull { it.text().trim().matches(Regex(labels)) }
            ?.let { link ->
                val resolved = EsjzoneUrls.resolve(link.attr("href"), url)
                if (resolveChapterSource(resolved) == ChapterSource.WENKU8 &&
                    com.breakyuna.esjzone.network.wenku8.Wenku8Urls.chapterIdentity(resolved) ==
                    com.breakyuna.esjzone.network.wenku8.Wenku8Urls.chapterIdentity(url)) {
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
            if (resolved.toHttpUrlOrNull()?.let { it.isHttps && it.host == "www.wenku8.net" && it.port == 443 &&
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

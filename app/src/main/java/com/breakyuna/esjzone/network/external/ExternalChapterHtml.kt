package com.breakyuna.esjzone.network.external

import com.breakyuna.esjzone.data.reader.withStructuredBody
import com.breakyuna.esjzone.data.reader.ChapterBody
import com.breakyuna.esjzone.data.reader.analyseChapterContent
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.novellibrary.component.IMAGE_URL_ATTRIBUTES
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.ChapterSource
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.novellibrary.novel.resolveChapterSource
import java.io.IOException
import java.nio.charset.Charset
import org.jsoup.Jsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val MAX_BROWSER_CHAPTER_HTML_BYTES = 4 * 1024 * 1024

internal fun isWenkuImageHost(host: String): Boolean {
    val normalized = host.trim().lowercase().trimEnd('.')
    return normalized == "wenku8.net" ||
        (normalized.length > ".wenku8.net".length && normalized.endsWith(".wenku8.net")) ||
        normalized == "wenku8.com" ||
        (normalized.length > ".wenku8.com".length && normalized.endsWith(".wenku8.com"))
}

internal fun isAllowedWenkuImageUrl(url: HttpUrl): Boolean =
    url.isHttps &&
        url.username.isEmpty() &&
        url.password.isEmpty() &&
        isWenkuImageHost(url.host)

internal fun isAllowedWenkuImageUrl(rawUrl: String?): Boolean =
    rawUrl?.toHttpUrlOrNull()?.let(::isAllowedWenkuImageUrl) == true

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
            val resolved = IMAGE_URL_ATTRIBUTES.asSequence()
                .map { image.attr(it).trim() }
                .filterNot { it.startsWith('#') }
                .map { EsjzoneUrls.resolve(it, url) }
                .firstOrNull { isAllowedWenkuImageUrl(it) }
            if (resolved == null) {
                image.remove()
            } else {
                IMAGE_URL_ATTRIBUTES.forEach(image::removeAttr)
                image.attr("src", resolved)
            }
        }
        if (content.text().trim().length < 20 && content.select("img").isEmpty()) {
            throw ExternalChapterParseException()
        }
        val title = document.selectFirst("#title")?.text()?.trim().orEmpty().ifBlank { chapter.name }
        val parsed = analyseChapterContent(content)
        document.outputSettings().prettyPrint(false)
        return DetailedChapter(
            title, parsed.components, previous, next, content.html(), url,
            body = ChapterBody.fromBlocks(parsed.blocks).copy(formatting = parsed.formatting)
        ).withStructuredBody()
    }

    fun cacheDocument(detail: DetailedChapter, url: String): String {
        val document = Jsoup.parse("<html><body><div id=title></div><div id=content></div></body></html>", url)
        document.outputSettings().prettyPrint(false)
        document.selectFirst("#title")?.text(detail.name)
        document.selectFirst("#content")?.html(detail.contentHtml.orEmpty())
        detail.previous?.let { document.body().appendElement("a").attr("href", it.url).text("上一页") }
        detail.next?.let { document.body().appendElement("a").attr("href", it.url).text("下一页") }
        return document.outerHtml()
    }
}

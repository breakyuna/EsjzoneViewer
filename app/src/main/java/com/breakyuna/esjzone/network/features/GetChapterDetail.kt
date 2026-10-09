package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.data.reader.withStructuredBody
import com.breakyuna.esjzone.network.StructuredChapterCache
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.HtmlSelector
import com.breakyuna.esjzone.network.JsoupHtmlSelector
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.network.readTextBounded
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.novellibrary.component.analyseComponents
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.novellibrary.novel.ChapterSource
import com.breakyuna.esjzone.novellibrary.novel.resolveChapterSource
import com.breakyuna.esjzone.network.external.UnsupportedExternalChapterException
import com.breakyuna.esjzone.util.logSourceForUrl
import com.breakyuna.esjzone.util.AppLogger
import com.google.gson.JsonParser
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException

private val chapterSelector: HtmlSelector = JsoupHtmlSelector

/** A server-side ESJ chapter gate; this is not client-side encryption. */
class ChapterPasswordRequiredException : IOException("This chapter requires an ESJ password")

/** The password is accepted only as an argument to one request and is never persisted. */
class ChapterPasswordRejectedException(message: String? = null) : IOException(
    message?.takeIf { it.isNotBlank() } ?: "Incorrect chapter password"
)

fun EsjzoneClient.getChapterDetail(
    authorization: Authorization,
    chapter: Chapter,
    preferDownloaded: Boolean = true,
    forceRefresh: Boolean = false,
    baseUrl: String? = null,
    allowAutoSolve: Boolean = true,
    onSecurityCheck: (() -> Unit)? = null
): DetailedChapter {
    val targetUrl = baseUrl?.let { EsjzoneUrls.resolve(chapter.url, it) }
        ?: EsjzoneUrls.resolve(chapter.url)
    val source = resolveChapterSource(targetUrl)
    if (source == ChapterSource.UNSUPPORTED_EXTERNAL) throw UnsupportedExternalChapterException()

    if (preferDownloaded) {
        NovelDownloadStore.readChapter(targetUrl)
            ?.let { downloaded ->
            AppLogger.i("GetChapterDetail", "Using downloaded ${source.name} chapter", source = logSourceForUrl(targetUrl))
            return downloaded
        }
    }

    if (source == ChapterSource.WENKU8) {
        AppLogger.i("GetChapterDetail", "Fetching WENKU8 chapter", source = logSourceForUrl(targetUrl))
        return try {
            getWenkuChapter(chapter, targetUrl, forceRefresh, allowAutoSolve, onSecurityCheck)
        } catch (error: Exception) {
            NovelDownloadStore.readChapter(targetUrl)?.let { return it }
            throw error
        }
    }

    val cacheKey = novelDetailCacheKey(authorization, targetUrl)
    if (!forceRefresh) StructuredChapterCache.read(cacheKey, targetUrl)?.let { return it }
    AppLogger.i("GetChapterDetail", "Fetching ESJ chapter: ${chapter.name} at $targetUrl", source = logSourceForUrl(targetUrl))
    val responseBody = try {
        getPage(
            authorization,
            targetUrl,
            PageCacheTtl.CHAPTER,
            forceRefresh,
            PageKind.CHAPTER
        )
    } catch (error: Exception) {
        NovelDownloadStore.readChapter(targetUrl)
            ?.let { downloaded ->
            AppLogger.w(
                "GetChapterDetail",
                "Network unavailable; falling back to downloaded chapter: $targetUrl",
                error
            )
            return downloaded
        }
        throw error
    }

    val document = Jsoup.parse(responseBody, targetUrl)
    if (document.isPasswordProtectedChapter()) throw ChapterPasswordRequiredException()
    return document.toDetailedChapter(chapter, targetUrl).also {
        StructuredChapterCache.write(cacheKey, responseBody, it)
    }
}

/**
 * Exchanges a user-entered password for the chapter HTML in the current ESJ cookie session.
 * Neither this function nor its callers retain the password after the request completes.
 */
fun EsjzoneClient.unlockPasswordProtectedChapter(
    authorization: Authorization,
    chapter: Chapter,
    password: String,
    baseUrl: String? = null
): DetailedChapter {
    val targetUrl = baseUrl?.let { EsjzoneUrls.resolve(chapter.url, it) }
        ?: EsjzoneUrls.resolve(chapter.url)
    val page = getPage(
        authorization, targetUrl, PageCacheTtl.CHAPTER, forceRefresh = true, pageKind = PageKind.CHAPTER,
        allowStaleOnError = false
    )
    val document = Jsoup.parse(page, targetUrl)
    if (!document.isPasswordProtectedChapter()) return document.toDetailedChapter(chapter, targetUrl)

    val token = requestAuthToken(authorization, targetUrl)
        .takeIf { it.isNotBlank() }
        ?: throw IOException("Unable to request chapter authorization")
    val response = authenticatedClient(authorization).newCall(
        Request.Builder()
            .url(EsjzoneUrls.resolve("/inc/forum_pw.php", targetUrl))
            .post(FormBody.Builder().add("pw", password).build())
            .headers(headers.newBuilder()
                .add("Authorization", token)
                .add("X-Requested-With", "XMLHttpRequest")
                .build())
            .build()
    ).execute().use { reply ->
        if (!reply.isSuccessful) throw IOException("Chapter password request failed: HTTP ${reply.code}")
        reply.body?.readTextBounded().orEmpty()
    }
    val payload = runCatching { JsonParser.parseString(response).asJsonObject }.getOrNull()
        ?: throw IOException("Invalid chapter password response")
    when (payload.get("status")?.asInt) {
        206 -> throw ChapterPasswordRejectedException(payload.get("msg")?.asString)
        200 -> Unit
        else -> throw IOException("Chapter password request was rejected")
    }
    val unlockedHtml = payload.get("html")?.asString?.takeIf { it.isNotBlank() }
        ?: throw IOException("Unlocked chapter response was empty")
    val content = chapterSelector.first(document, ".forum-content.mt-3, .forum-content")
        ?: throw IOException("Chapter content container was missing")
    content.html(unlockedHtml)
    if (document.isPasswordProtectedChapter()) {
        throw IOException("Chapter remained password protected")
    }
    // A previously cached password gate must not keep masking the new ESJ cookie grant.
    invalidatePage(authorization, targetUrl)
    return document.toDetailedChapter(chapter, targetUrl)
}

internal fun isPasswordProtectedChapterHtml(html: String, baseUrl: String = ""): Boolean =
    Jsoup.parse(html, baseUrl).isPasswordProtectedChapter()

internal fun Document.isPasswordProtectedChapter(): Boolean {
    val content = chapterSelector.first(this, ".forum-content.mt-3, .forum-content") ?: return false
    return content.selectFirst("#oops") != null &&
        content.selectFirst("input#pw[name=pw]") != null &&
        content.selectFirst(".btn-send-pw") != null
}

private fun Document.toDetailedChapter(chapter: Chapter, targetUrl: String): DetailedChapter {
    val contentElement = chapterSelector.first(this, ".forum-content.mt-3, .forum-content")
    val components = if (contentElement != null) {
        analyseComponents(contentElement)
    } else {
        AppLogger.w("GetChapterDetail", "No content element found in chapter page: $targetUrl")
        listOf()
    }

    val previousChapter = chapterSelector.first(
        this,
        "a.btn-prev, a[rel='prev'], a[data-direction='previous']"
    )
    val nextChapter = chapterSelector.first(
        this,
        "a.btn-next, a[rel='next'], a[data-direction='next']"
    )

    val previous = parseChapterNav(previousChapter, targetUrl)
    val next = parseChapterNav(nextChapter, targetUrl)

    return DetailedChapter(
        chapter.name,
        components,
        previous,
        next,
        contentElement?.html(),
        targetUrl
    ).withStructuredBody()
}

private fun parseChapterNav(element: Element?, targetUrl: String): Chapter? {
    if (element == null || element.hasClass("disabled")) return null
    val rawHref = element.attr("href").trim()
    if (rawHref.isBlank() || rawHref == "#" || rawHref.startsWith("javascript:", ignoreCase = true)) {
        return null
    }
    val resolvedUrl = EsjzoneUrls.resolve(rawHref, targetUrl).takeIf { it.isNotBlank() } ?: return null
    val title = element.attr("data-title").ifBlank { element.text() }.trim()
    return Chapter(title, resolvedUrl, false)
}

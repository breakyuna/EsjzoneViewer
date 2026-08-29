package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.EsjzoneXPaths
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.novellibrary.community.ForumCategory
import com.breakyuna.esjzone.novellibrary.community.ForumThread
import com.breakyuna.esjzone.novellibrary.novel.Comment
import com.breakyuna.esjzone.novellibrary.novel.COMMENT_PAGE_SIZE
import com.breakyuna.esjzone.util.AppLogger
import java.io.IOException
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

private val CATEGORY_URL = Regex("(?:https?://[^/]+)?/forum/([0-9]+)/?$")
private val THREAD_URL = Regex("(?:https?://[^/]+)?/forum/([0-9]+)/([0-9]+)/?$")
private val POST_URL = Regex("(?:https?://[^/]+)?/forum/[0-9]+/([0-9]+)\\.html")
private val DETAIL_URL = Regex("(?:https?://[^/]+)?/detail/([0-9]+)\\.html")
private val PROFILE_UID = Regex("[?&]uid=([0-9]+)")
private val COMMENT_TIMESTAMP = Regex(
    "\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}(?:[T ]\\d{1,2}:\\d{2}(?::\\d{2})?)?"
)
private val CSS_IMAGE_URL = Regex(
    """url\(\s*['"]?([^'")]+)['"]?\s*\)""",
    RegexOption.IGNORE_CASE
)

data class CommentSubmission(
    val comments: List<Comment>,
    val createdComment: Comment
)

class CommentSubmissionNotVerifiedException(val comments: List<Comment>) : IOException(
    "The server accepted the request but the new comment could not be verified after refresh"
)

fun EsjzoneClient.getChapterComments(
    authorization: Authorization,
    chapterUrl: String
): List<Comment> = getPageComments(authorization, chapterUrl)

/** Loads comments from any page that uses the shared ESJ comment markup. */
fun EsjzoneClient.getPageComments(
    authorization: Authorization,
    pageUrl: String
): List<Comment> {
    val targetUrl = EsjzoneUrls.resolve(pageUrl).substringBefore('#')
    AppLogger.i("GetCommunity", "Fetching comments at $targetUrl")
    val document = Jsoup.parse(
        getPage(authorization, targetUrl, PageCacheTtl.COMMUNITY),
        targetUrl
    )
    return parseComments(document, commentParentId(targetUrl))
}

fun EsjzoneClient.submitForumComment(
    authorization: Authorization,
    pageUrl: String,
    content: String,
    replyToken: String? = null
): CommentSubmission {
    val submittedContent = content.trim()
    require(submittedContent.isNotEmpty()) { "Comment content cannot be empty" }

    val targetUrl = EsjzoneUrls.resolve(pageUrl).substringBefore('#')
    val initialDocument = Jsoup.parse(
        getPage(authorization, targetUrl, PageCacheTtl.COMMUNITY),
        targetUrl
    )
    val form = initialDocument.selectFirst("form.commentEditor, form.gbEditor")
        ?: throw IOException("Comment form was not found; the session may have expired")
    val formForumId = form.selectFirst("[name=forum_id]")
        ?.attr("value")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: POST_URL.find(targetUrl)?.groupValues?.getOrNull(1)
    // The detail page has an empty hidden forum_id but its page script uses
    // forum_id=0; chapter pages use the chapter post id.  The empty hidden
    // values must therefore be replaced with the route-specific values.
    val forumId = formForumId
        ?: DETAIL_URL.find(targetUrl)?.let { "0" }
    val formData = form.selectFirst("[name=data]")
        ?.attr("value")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    val data = formData ?: when {
        form.hasClass("gbEditor") -> null
        DETAIL_URL.containsMatchIn(targetUrl) -> "books"
        form.hasClass("commentEditor") -> "forum"
        else -> null
    }
    val actionUrl = form.absUrl("action")
        .ifBlank { targetUrl }
        .substringBefore('#')
    // forum_id is a submission routing field, not the stable identity used to
    // compare comments before and after the request (detail pages use 0 for it).
    val parentId = commentParentId(targetUrl)
    val previousComments = parseComments(initialDocument, parentId)
    val previousIds = previousComments.mapTo(mutableSetOf()) { it.id }

    val bodyBuilder = FormBody.Builder()
        .add("content", submittedContent)
    // Preserve hidden form fields (including any future anti-forgery or routing
    // fields) instead of rebuilding the payload from an assumed fixed schema.
    form.select("input[type=hidden][name]").forEach { input ->
        val name = input.attr("name").trim()
        if (name.isNotBlank() && name != "content" && name != "data" &&
            name != "forum_id" &&
            !(name == "reply" && !replyToken.isNullOrBlank())
        ) {
            bodyBuilder.add(name, input.attr("value"))
        }
    }
    data?.let { bodyBuilder.add("data", it) }
    forumId?.let { bodyBuilder.add("forum_id", it) }
    replyToken?.trim()?.takeIf { it.isNotBlank() }?.let {
        bodyBuilder.add("reply", it)
    }
    val body = bodyBuilder.build()
    val request = Request.Builder()
        .url(actionUrl)
        .post(body)
        .headers(headers)
        .build()

    AppLogger.i(
        "GetCommunity",
        if (replyToken == null) "Submitting forum comment" else "Submitting forum reply"
    )
    val response = try {
        authenticatedClient(authorization).newCall(request).execute()
    } catch (error: IOException) {
        invalidatePage(authorization, targetUrl)
        val recoveredComments = runCatching {
            val refreshed = getPage(authorization, targetUrl, PageCacheTtl.COMMUNITY)
            parseComments(Jsoup.parse(refreshed, targetUrl), parentId)
        }.getOrDefault(previousComments)
        findCreatedComment(recoveredComments, previousIds, submittedContent)?.let { created ->
            return CommentSubmission(recoveredComments, created)
        }
        throw CommentSubmissionNotVerifiedException(recoveredComments)
    }
    val responseCode = response.code
    val responseUrl = response.request.url.toString()
    val responseBody = try {
        response.use { it.body?.string().orEmpty() }
    } catch (error: IOException) {
        invalidatePage(authorization, targetUrl)
        val recoveredComments = runCatching {
            val refreshed = getPage(authorization, targetUrl, PageCacheTtl.COMMUNITY)
            parseComments(Jsoup.parse(refreshed, targetUrl), parentId)
        }.getOrDefault(previousComments)
        findCreatedComment(recoveredComments, previousIds, submittedContent)?.let { created ->
            return CommentSubmission(recoveredComments, created)
        }
        throw CommentSubmissionNotVerifiedException(recoveredComments)
    }
    if (responseCode !in 200..299) {
        throw IOException("Comment request failed with HTTP $responseCode")
    }
    if (responseUrl.contains("/my/login") || looksLikeLoginDocument(responseBody)) {
        throw IOException("Comment request was redirected to login")
    }

    invalidatePage(authorization, targetUrl)
    val refreshedDocument = try {
        Jsoup.parse(
            getPage(authorization, targetUrl, PageCacheTtl.COMMUNITY),
            targetUrl
        )
    } catch (error: IOException) {
        throw CommentSubmissionNotVerifiedException(previousComments)
    }
    val comments = parseComments(refreshedDocument, parentId)
    val createdComment = findCreatedComment(comments, previousIds, submittedContent)
        ?: throw CommentSubmissionNotVerifiedException(comments)

    return CommentSubmission(comments, createdComment)
}

fun EsjzoneClient.getGuestbookComments(authorization: Authorization): List<Comment> =
    getPageComments(authorization, EsjzoneUrls.Guestbook)

fun EsjzoneClient.getForumCategories(authorization: Authorization): List<ForumCategory> {
    AppLogger.i("GetCommunity", "Fetching forum categories")
    val document = Jsoup.parse(
        getPage(authorization, "${EsjzoneUrls.Forum}/", PageCacheTtl.COMMUNITY),
        "${EsjzoneUrls.Forum}/"
    )

    val categories = mutableListOf<ForumCategory>()
    for (table in document.select("table")) {
        val groupName = forumGroupName(table)
        for (anchor in table.select("a[href]")) {
            val rawUrl = anchor.attr("href").trim()
            val match = CATEGORY_URL.matchEntire(rawUrl) ?: continue
            val name = anchor.text().trim()
            if (name.isBlank()) continue
            val container = anchor.closest("td") ?: anchor.parent()
            val description = container?.selectFirst(".forum-desc")?.text()?.trim()
                ?.takeIf { it.isNotBlank() }
            categories += ForumCategory(
                id = match.groupValues[1],
                groupName = groupName,
                name = name,
                description = description,
                postCount = extractNumber(container?.text().orEmpty(), "(?:文章|貼文|帖子|主題)"),
                url = rawUrl
            )
        }
    }
    return categories.distinctBy { it.id }
}

fun EsjzoneClient.getForumThreads(
    authorization: Authorization,
    category: ForumCategory
): List<ForumThread> {
    val targetUrl = EsjzoneUrls.resolve(category.url)
    AppLogger.i("GetCommunity", "Fetching forum category ${category.id} at $targetUrl")
    val document = Jsoup.parse(
        getPage(authorization, targetUrl, PageCacheTtl.COMMUNITY),
        targetUrl
    )

    return document.select("table.forum-board-detail td").mapNotNull { cell ->
        val anchor = cell.selectFirst("a[href]") ?: return@mapNotNull null
        val rawUrl = anchor.attr("href").trim()
        val match = THREAD_URL.matchEntire(rawUrl) ?: return@mapNotNull null
        val descriptionText = cell.select(".forum-desc").joinToString(" ") { it.text() }
        ForumThread(
            categoryId = match.groupValues[1],
            id = match.groupValues[2],
            title = anchor.text().trim().ifBlank { return@mapNotNull null },
            topicCount = extractNumber(descriptionText, "主題"),
            replyCount = extractNumber(descriptionText, "回覆"),
            lastPostDate = Regex("最後發表[：:]?\\s*(.+)$")
                .find(descriptionText)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.takeIf { it.isNotBlank() },
            url = rawUrl
        )
    }.distinctBy { it.categoryId to it.id }
}

internal fun parseComments(document: Document, parentPostId: String): List<Comment> {
    val comments = mutableListOf<Comment>()
    val sections = document.select(".comments-section").ifEmpty {
        // A few templates omit the shared marker but keep the pager class.
        document.select("[class*=comments-page-]")
    }.ifEmpty {
        // The novel detail template has also been observed with a generic
        // section element but without either shared class.
        EsjzoneXPaths.Detail.Comment.Pages.evaluate(document).elements
    }
    val commentElements = sections
        .flatMap { it.select(".comment") }
        .ifEmpty { document.select(".comment") }
        .distinctBy { it.id().ifBlank { it.outerHtml().hashCode().toString() } }

    for (element in commentElements) {
        val content = element.selectFirst(".comment-text, .comment-content")
        val author = element.selectFirst(
            ".comment-header a[href*='/my/profile'], .comment-title a[href*='/my/profile']"
        ) ?: element.selectFirst(".comment-header a, .comment-title a")
        val authorUrl = author?.let { link ->
            link.absUrl("href").ifBlank { link.attr("href") }
        }?.trim()?.takeIf { it.isNotBlank() }
        val avatar = element.selectFirst(
            ".comment-author-ava .lazyload-author-ava, " +
                ".comment-header img, .comment-title img, .comment-author img, " +
                "img.avatar, [data-avatar], .lazyload-author-ava"
        )
        val authorAvatarUrl = avatar?.let(::resolveImageUrl)
        val replyToken = element.selectFirst(".forum_reply[data-comment]")
            ?.attr("data-comment")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val contentText = content?.text()?.trim().orEmpty()
        val rawId = element.id().removePrefix("comment-").trim()
        val pageGroup = comments.size / COMMENT_PAGE_SIZE + 1
        val stableId = rawId.ifBlank {
            "$parentPostId-$pageGroup-${comments.size}-${contentText.hashCode()}"
        }
        comments += Comment(
            id = stableId,
            parentPostId = parentPostId,
            authorId = authorUrl?.let { PROFILE_UID.find(it)?.groupValues?.getOrNull(1) },
            authorName = author?.text()?.trim()?.takeIf { it.isNotBlank() },
            authorUrl = authorUrl,
            authorAvatarUrl = authorAvatarUrl,
            floor = element.selectFirst(".comment-floor")?.text()?.trim()
                ?.takeIf { it.isNotBlank() },
            createdAt = resolveCommentTimestamp(element),
            contentHtml = content?.html().orEmpty(),
            contentText = contentText,
            pageGroup = pageGroup,
            replyToken = replyToken
        )
    }
    return comments.distinctBy { it.id }
}

private fun resolveImageUrl(image: Element): String? {
    // Lazy-loaded avatars commonly leave a placeholder in src and put the real
    // URL in one of the data-* attributes, so inspect those first.
    val attributeUrl = sequenceOf(
        "data-src",
        "data-original",
        "data-lazy-src",
        "data-original-src",
        "data-avatar",
        "data-url",
        "srcset",
        "src"
    )
        .mapNotNull { attribute ->
            val raw = image.attr(attribute)
                .trim()
                .substringBefore(',')
                .trim()
                .substringBefore(' ')
                .trim()
            if (raw.isBlank()) return@mapNotNull null
            val resolved = if (attribute == "srcset") {
                resolveRawImageUrl(image, raw)
            } else {
                image.absUrl(attribute).ifBlank { raw }
            }
                .trim()
            resolved.takeIf { it.isNotBlank() && isUsableImageUrl(it) }
        }
        .firstOrNull()
    if (attributeUrl != null) return attributeUrl

    // After the site's lazy-loader runs, avatars are moved from data-src to a
    // CSS background image on the same div.  Jsoup does not resolve URLs found
    // inside style attributes, so resolve that one explicitly against the
    // parsed page base URI.
    val backgroundUrl = CSS_IMAGE_URL.find(image.attr("style"))
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { raw ->
            val resolved = resolveRawImageUrl(image, raw)
            resolved.takeIf { isUsableImageUrl(it) }
        }
    return backgroundUrl
}

private fun resolveCommentTimestamp(element: Element): String? {
    // The first .comment-meta on ESJ is the floor number (#1), not a time.
    // Prefer explicit time attributes and exclude .comment-floor before using
    // the visual metadata span.
    val candidates = element.select(
        "time[datetime], [datetime], [data-time], .comment-date, " +
            ".comment-meta:not(.comment-floor)"
    )
    var firstNonBlank: String? = null
    for (candidate in candidates) {
        for (raw in sequenceOf(
            candidate.text(),
            candidate.attr("datetime"),
            candidate.attr("data-time")
        ).map(String::trim).filter { it.isNotBlank() }) {
            if (firstNonBlank == null) firstNonBlank = raw
            COMMENT_TIMESTAMP.find(raw)?.value?.let { return it }
        }
    }
    return firstNonBlank
}

private fun resolveRawImageUrl(image: Element, rawUrl: String): String {
    val raw = rawUrl.trim()
    if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
    if (raw.startsWith("//")) return "https:$raw"
    val baseUri = image.baseUri().trim()
    if (baseUri.isNotBlank()) {
        runCatching {
            java.net.URI(baseUri).resolve(raw).toString()
        }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return raw
}

private fun isUsableImageUrl(url: String): Boolean {
    val normalized = url.lowercase()
    return !normalized.startsWith("data:") &&
        !normalized.startsWith("javascript:") &&
        !normalized.startsWith("about:") &&
        normalized != "#" &&
        !Regex("(?:^|/)(?:blank|spacer|loading|avatar-placeholder)\\.(?:gif|png|jpg|jpeg|webp)$")
            .containsMatchIn(normalized)
}

internal fun commentParentId(rawUrl: String): String {
    val targetUrl = EsjzoneUrls.resolve(rawUrl).substringBefore('#')
    return POST_URL.find(targetUrl)?.groupValues?.getOrNull(1)
        ?: DETAIL_URL.find(targetUrl)?.groupValues?.getOrNull(1)?.let { "detail-$it" }
        ?: if (EsjzoneUrls.canonicalPageKey(targetUrl) == "/guestbook") {
            "guestbook"
        } else {
            EsjzoneUrls.canonicalPageKey(targetUrl)
        }
}

private fun forumGroupName(table: Element): String? {
    var level: Element? = table
    repeat(3) {
        var previous = level?.previousElementSibling()
        while (previous != null) {
            val headings = previous.select("h1, h2, h3, h4, h5, .card-title")
            headings.lastOrNull()?.text()?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
            previous = previous.previousElementSibling()
        }
        level = level?.parent()
    }
    return null
}

private fun extractNumber(text: String, label: String): Int? =
    Regex("$label[：:]?\\s*([0-9,]+)")
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.replace(",", "")
        ?.toIntOrNull()

private fun String.normalizedWhitespace(): String = trim().replace(Regex("\\s+"), " ")

private fun findCreatedComment(
    comments: List<Comment>,
    previousIds: Set<String>,
    submittedContent: String
): Comment? {
    val normalizedContent = submittedContent.normalizedWhitespace()
    return comments.firstOrNull { comment ->
        comment.id !in previousIds &&
            comment.contentText.normalizedWhitespace().let { rendered ->
                rendered == normalizedContent || rendered.endsWith(normalizedContent)
            }
    }
}

private fun looksLikeLoginDocument(body: String): Boolean {
    if (body.isBlank()) return false
    val document = Jsoup.parse(body)
    return document.selectFirst("input[name=pwd]") != null &&
        (document.selectFirst(".login-box") != null || body.contains("/my/login"))
}

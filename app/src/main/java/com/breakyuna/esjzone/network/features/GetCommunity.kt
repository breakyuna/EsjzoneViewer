package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.EsjzoneXPaths
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.network.NetworkRequestException
import com.breakyuna.esjzone.network.NetworkHttpException
import com.breakyuna.esjzone.novellibrary.community.ForumCategory
import com.breakyuna.esjzone.novellibrary.community.ForumPost
import com.breakyuna.esjzone.novellibrary.community.ForumTopic
import com.breakyuna.esjzone.novellibrary.community.ForumThread
import com.breakyuna.esjzone.novellibrary.novel.Comment
import com.breakyuna.esjzone.novellibrary.novel.COMMENT_PAGE_SIZE
import com.breakyuna.esjzone.util.AppLogger
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import okhttp3.Headers
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

private val CATEGORY_URL = Regex("(?:https?://[^/]+)?/forum/([0-9]+)/?(?:[?#].*)?$")
private val THREAD_URL = Regex("(?:https?://[^/]+)?/forum/([0-9]+)/([0-9]+)/?(?:[?#].*)?$")
private val POST_URL = Regex("(?:https?://[^/]+)?/forum/[0-9]+/([0-9]+)\\.html(?:[?#].*)?$")
private val TOPIC_URL = Regex(
    "(?:https?://[^/]+)?/forum/([0-9]+)/([0-9]+)\\.html(?:[?#].*)?$"
)
private val DETAIL_URL = Regex(
    "(?:https?://[^/]+)?/detail/([0-9]+)\\.html(?:[?#].*)?$"
)
private val FORUM_TOTAL_ROWS = Regex("(?:^|[?&])totalRows=([0-9]+)(?:[&#]|$)")
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
    chapterUrl: String,
    forceRefresh: Boolean = false
): List<Comment> = getPageComments(authorization, chapterUrl, forceRefresh)

/** Loads comments from any page that uses the shared ESJ comment markup. */
fun EsjzoneClient.getPageComments(
    authorization: Authorization,
    pageUrl: String,
    forceRefresh: Boolean = false
): List<Comment> {
    val targetUrl = EsjzoneUrls.resolve(pageUrl).substringBefore('#')
    AppLogger.i("GetCommunity", "Fetching comments at $targetUrl")
    val document = Jsoup.parse(
        getPage(
            authorization,
            targetUrl,
            PageCacheTtl.COMMUNITY,
            forceRefresh = forceRefresh,
            pageKind = PageKind.COMMUNITY
        ),
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
        getPage(
            authorization,
            targetUrl,
            PageCacheTtl.COMMUNITY,
            pageKind = PageKind.COMMUNITY
        ),
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
            val refreshed = getPage(
                authorization,
                targetUrl,
                PageCacheTtl.COMMUNITY,
                pageKind = PageKind.COMMUNITY
            )
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
            val refreshed = getPage(
                authorization,
                targetUrl,
                PageCacheTtl.COMMUNITY,
                pageKind = PageKind.COMMUNITY
            )
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
            getPage(
                authorization,
                targetUrl,
                PageCacheTtl.COMMUNITY,
                pageKind = PageKind.COMMUNITY
            ),
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
        getPage(
            authorization,
            "${EsjzoneUrls.Forum}/",
            PageCacheTtl.COMMUNITY,
            pageKind = PageKind.FORUM
        ),
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
        getPage(
            authorization,
            targetUrl,
            PageCacheTtl.COMMUNITY,
            pageKind = PageKind.COMMUNITY
        ),
        targetUrl
    )

    return parseForumThreads(document)
}

internal fun parseForumThreads(document: Document): List<ForumThread> =
    document.select("table.forum-board-detail td").mapNotNull { cell ->
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

internal class ForumBoardDataException(message: String) : IOException(message)

sealed class ForumBoardResult {
    data class Topics(
        val items: List<ForumTopic>,
        val totalCount: Int? = null
    ) : ForumBoardResult()

    /** An ESJ novel board may also expose its own forum topics. */
    data class Novel(
        val detailUrl: String,
        val items: List<ForumTopic> = emptyList(),
        val totalCount: Int? = null
    ) : ForumBoardResult()
}

/**
 * Loads either a dynamic forum topic board or an ESJ novel board. Both use the
 * same two-level URL shape; a detail link identifies a novel board, while the
 * bootstrap-table endpoint supplies its topics.
 */
fun EsjzoneClient.getForumBoard(
    authorization: Authorization,
    thread: ForumThread
): ForumBoardResult {
    val targetUrl = EsjzoneUrls.resolve(thread.url)
    AppLogger.i("GetCommunity", "Fetching forum board ${thread.id} at $targetUrl")
    val document = Jsoup.parse(
        // Refresh the board shell so the data-url and current table metadata
        // are read from the live page before requesting its JSON endpoint.
        getPage(
            authorization,
            targetUrl,
            PageCacheTtl.COMMUNITY,
            pageKind = PageKind.COMMUNITY,
            forceRefresh = true
        ),
        targetUrl
    )
    val novelDetailUrl = findForumNovelDetailUrl(document)
    val table = document.selectFirst("#dataTable[data-url]")
    if (table == null) {
        return novelDetailUrl?.let { ForumBoardResult.Novel(detailUrl = it) }
            ?: throw ForumBoardDataException("Forum board template was not recognized")
    }

    // Some deployments render the first page directly into the HTML. Keep
    // that path working before using the site's AJAX endpoint.
    val inlineTopics = parseForumTopicRows(table, thread.id)
    val dataUrl = table.attr("data-url").trim()
    val declaredTotal = FORUM_TOTAL_ROWS.find(dataUrl)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    if (inlineTopics.isNotEmpty()) {
        return forumBoardResult(novelDetailUrl, inlineTopics, declaredTotal)
    }
    if (declaredTotal == 0) {
        return forumBoardResult(novelDetailUrl, emptyList(), 0)
    }
    if (dataUrl.isBlank()) {
        throw ForumBoardDataException("Forum topic table has no data endpoint")
    }
    val endpoint = appendForumTableParams(EsjzoneUrls.resolve(dataUrl))
    var authToken = requireForumAuthToken(requestAuthToken(authorization, targetUrl))
    var body = getForumTableData(authorization, endpoint, targetUrl, authToken)
    if (forumApplicationStatus(body) == 301) {
        // Refresh the token once when the server reports the
        // application-level authentication failure (status 301).
        authToken = requireForumAuthToken(requestAuthToken(authorization, targetUrl))
        body = getForumTableData(authorization, endpoint, targetUrl, authToken)
    }
    validateForumTableResponse(body)
    val payload = parseForumTopicsPayload(body, thread.id)
    val totalCount = payload.totalCount ?: declaredTotal
    if (payload.items.isEmpty() && totalCount != null && totalCount > 0) {
        throw ForumBoardDataException("Forum topic response contained no readable topics")
    }
    return forumBoardResult(novelDetailUrl, payload.items, totalCount)
}

/**
 * The ESJ endpoint returns a JSON object even when it cannot serve the board.
 * In that case it uses a non-zero application status and an empty rows array.
 * Treating that response as a valid empty board hides the real loading failure
 * and produces the misleading "暂无主题" screen.
 */
internal fun validateForumTableResponse(body: String) {
    val status = forumApplicationStatus(body)
    if (status != null && status != 0 && status !in 200..299) {
        throw ForumBoardDataException("Forum topic request returned application status $status")
    }
}

private fun forumApplicationStatus(body: String): Int? = runCatching {
    val root = JsonParser.parseString(body)
    root.asJsonObject.get("status")?.asInt
}.getOrNull()

internal fun requireForumAuthToken(token: String): String = token.trim().takeIf { it.isNotEmpty() }
    ?: throw ForumBoardDataException("Forum topic request did not return an authorization token")

internal fun findForumNovelDetailUrl(document: Document): String? =
    document.select(".forum-detail a[href], h2 a[href]")
        .asSequence()
        .map { it.attr("href").trim() }
        .firstOrNull { rawUrl -> DETAIL_URL.matches(rawUrl) }
        ?.substringBefore('#')

private fun forumBoardResult(
    novelDetailUrl: String?,
    items: List<ForumTopic>,
    totalCount: Int?
): ForumBoardResult = novelDetailUrl?.let {
    ForumBoardResult.Novel(
        detailUrl = it,
        items = items,
        totalCount = totalCount
    )
} ?: ForumBoardResult.Topics(
    items = items,
    totalCount = totalCount
)

fun EsjzoneClient.getForumPost(
    authorization: Authorization,
    topic: ForumTopic
): ForumPost {
    val targetUrl = EsjzoneUrls.resolve(topic.url).substringBefore('#')
    AppLogger.i("GetCommunity", "Fetching forum topic ${topic.id} at $targetUrl")
    val document = Jsoup.parse(
        getPage(
            authorization,
            targetUrl,
            PageCacheTtl.COMMUNITY,
            pageKind = PageKind.COMMUNITY
        ),
        targetUrl
    )
    return parseForumPost(document, topic)
}

internal fun parseForumTopics(body: String, fallbackBoardId: String): List<ForumTopic> {
    return parseForumTopicsPayload(body, fallbackBoardId).items
}

internal data class ForumTopicsPayload(
    val totalCount: Int?,
    val items: List<ForumTopic>
)

internal fun parseForumTopicsPayload(
    body: String,
    fallbackBoardId: String
): ForumTopicsPayload {
    val root = runCatching { JsonParser.parseString(body) }.getOrNull()
        ?: throw ForumBoardDataException("Forum topic response was not valid JSON")
    var totalCount: Int? = null
    val rows = when {
        root.isJsonArray -> root.asJsonArray.toList().also { totalCount = it.size }
        root.isJsonObject -> {
            val objectRoot = root.asJsonObject
            totalCount = runCatching { objectRoot.get("total")?.asInt }.getOrNull()
            val array = sequenceOf("rows", "data", "items")
                .mapNotNull { key -> objectRoot.get(key) }
                .firstOrNull { it.isJsonArray }
                ?.asJsonArray
                ?.toList()
                ?: throw ForumBoardDataException("Forum topic response had no rows")
            if (totalCount == null) totalCount = array.size
            array
        }
        else -> throw ForumBoardDataException("Forum topic response had an unsupported shape")
    }
    val items = rows.mapNotNull { row ->
        row.takeIf { it.isJsonObject }?.asJsonObject?.let {
            parseForumTopicRow(it, fallbackBoardId)
        }
    }.distinctBy { it.url }
    if (rows.isNotEmpty() && items.isEmpty()) {
        throw ForumBoardDataException("Forum topic response rows had no readable links")
    }
    return ForumTopicsPayload(totalCount = totalCount, items = items)
}

internal fun parseForumTopicRows(table: Element, fallbackBoardId: String): List<ForumTopic> =
    table.select("tbody tr").mapNotNull { row ->
        val cells = row.select("td")
        if (cells.size < 4) return@mapNotNull null
        val subject = cells[0]
        val anchor = subject.selectFirst("a[href]") ?: return@mapNotNull null
        val rawUrl = anchor.attr("href").trim()
        val match = TOPIC_URL.matchEntire(rawUrl) ?: return@mapNotNull null
        ForumTopic(
            boardId = match.groupValues[1].ifBlank { fallbackBoardId },
            id = match.groupValues[2],
            title = anchor.text().trim().ifBlank { return@mapNotNull null },
            author = cells[1].mainCellText(),
            createdAt = cells[1].selectFirst(".forum-desc")?.text()?.trim()
                ?.takeIf { it.isNotBlank() },
            replyCount = cells[2].mainCellText()?.toIntOrNull(),
            viewCount = cells[2].selectFirst(".forum-desc")?.text()?.trim()?.toIntOrNull(),
            lastReplyAt = cells[3].text().trim().takeIf { it.isNotBlank() },
            url = rawUrl
        )
    }.distinctBy { it.url }

internal fun parseForumPost(document: Document, topic: ForumTopic): ForumPost {
    val content = document.selectFirst(".forum-content")
    val meta = document.selectFirst(".single-post-meta")
    val author = meta?.selectFirst("a[href*='/my/profile']")?.text()?.trim()
        ?.takeIf { it.isNotBlank() }
    val createdAt = meta?.let { COMMENT_TIMESTAMP.find(it.text())?.value }
    val title = document.selectFirst("h2")?.text()?.trim()
        ?.takeIf { it.isNotBlank() } ?: topic.title
    val contentText = forumContentText(content)
    return ForumPost(
        boardId = topic.boardId,
        id = topic.id,
        title = title,
        author = author,
        createdAt = createdAt,
        contentHtml = content?.html().orEmpty(),
        contentText = contentText,
        comments = parseComments(document, topic.id),
        url = EsjzoneUrls.resolve(topic.url).substringBefore('#')
    )
}

private fun forumContentText(content: Element?): String {
    if (content == null) return ""
    val blocks = content.select("p, li")
        .map { it.text().trim() }
        .filter { it.isNotBlank() }
    return if (blocks.isNotEmpty()) {
        blocks.joinToString("\n")
    } else {
        content.wholeText().trim()
    }
}

private fun parseForumTopicRow(row: JsonObject, fallbackBoardId: String): ForumTopic? {
    val subjectValue = row.stringValue("subject", "title") ?: return null
    val subjectDocument = Jsoup.parseBodyFragment(subjectValue)
    val anchor = subjectDocument.selectFirst("a[href]")
    val rawUrl = (anchor?.attr("href")?.trim()
        ?: row.stringValue("url", "link")?.trim()).orEmpty()
    val match = TOPIC_URL.matchEntire(rawUrl) ?: return null
    val subjectText = anchor?.text()?.trim().orEmpty().ifBlank {
        subjectDocument.body().text().trim()
    }
    if (subjectText.isBlank()) return null
    val authorCell = row.stringValue("cdate", "author")
    val statsCell = row.stringValue("vtimes", "stats")
    return ForumTopic(
        boardId = match.groupValues[1].ifBlank { fallbackBoardId },
        id = match.groupValues[2],
        title = subjectText,
        author = authorCell?.htmlCellMainText(),
        createdAt = authorCell?.htmlCellDescription(),
        replyCount = statsCell?.htmlCellMainText()?.toIntOrNull(),
        viewCount = statsCell?.htmlCellDescription()?.toIntOrNull(),
        lastReplyAt = row.stringValue("last_reply", "lastReply")
            ?.htmlCellMainText()
            ?.takeIf { it.isNotBlank() },
        url = rawUrl
    )
}

private fun JsonObject.stringValue(vararg keys: String): String? = keys.asSequence()
    .mapNotNull { key -> get(key)?.asSafeString() }
    .firstOrNull { it.isNotBlank() }

private fun JsonElement.asSafeString(): String? = runCatching {
    if (isJsonNull || !isJsonPrimitive) null else asString
}.getOrNull()

private fun String.htmlCellMainText(): String {
    val body = Jsoup.parseBodyFragment(this).body()
    body.select(".forum-desc").remove()
    return body.text().trim().takeIf { it.isNotBlank() }.orEmpty()
}

private fun String.htmlCellDescription(): String? = Jsoup.parseBodyFragment(this).body()
    .selectFirst(".forum-desc")
    ?.text()
    ?.trim()
    ?.takeIf { it.isNotBlank() }

private fun Element.mainCellText(): String? {
    return clone().apply { select(".forum-desc").remove() }
        .text()
        .trim()
        .takeIf { it.isNotBlank() }
}

private fun appendForumTableParams(url: String): String {
    val separator = if (url.contains('?')) '&' else '?'
    return "$url${separator}limit=20&offset=0&sort=last_reply&order=desc"
}

private fun EsjzoneClient.getForumTableData(
    authorization: Authorization,
    url: String,
    referer: String,
    authToken: String
): String {
    val requestHeaders: Headers = headers.newBuilder()
        .add("Accept", "application/json, text/javascript, */*; q=0.01")
        .add("Referer", referer)
        .add("X-Requested-With", "XMLHttpRequest")
        .add("Authorization", authToken)
        .build()
    val client = authenticatedClient(authorization)
    val response = try {
        client.newCall(
            Request.Builder()
                .url(url)
                .get()
                .headers(requestHeaders)
                .build()
        ).execute()
    } catch (error: IOException) {
        throw NetworkRequestException(url, error)
    }
    val responseCode = response.code
    val body = try {
        response.use { it.body?.string().orEmpty() }
    } catch (error: IOException) {
        throw NetworkRequestException(url, error)
    }
    if (responseCode !in 200..299) {
        throw NetworkHttpException(url, responseCode)
    }
    if (body.isBlank()) {
        throw ForumBoardDataException("Forum topic request returned an empty response")
    }
    return body
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
        val quotedContentText = element.selectFirst(".comment-body > blockquote")
            ?.wholeText()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
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
            quotedContentText = quotedContentText,
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

package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.NetworkHttpException
import com.breakyuna.esjzone.network.NetworkRequestException
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.network.PageResponsePolicy
import com.breakyuna.esjzone.network.PageValidation
import com.breakyuna.esjzone.network.UntrustedPageException
import com.breakyuna.esjzone.network.pageRequestCancellation
import com.breakyuna.esjzone.network.readTextBounded
import com.breakyuna.esjzone.novellibrary.user.ExperienceRecord
import com.breakyuna.esjzone.novellibrary.user.ExperienceRecordPage
import com.breakyuna.esjzone.novellibrary.user.ExperienceRecordType
import com.breakyuna.esjzone.novellibrary.user.MemberReply
import com.breakyuna.esjzone.novellibrary.user.SystemMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.io.InterruptedIOException
import java.util.Collections
import java.util.IdentityHashMap
import kotlinx.coroutines.CancellationException

/** The experience endpoint answered, but not with a usable `status: 200` payload. */
internal class ExperienceRecordException(message: String) : IOException(message)

const val EXPERIENCE_RECORD_PAGE_SIZE = 30

private fun Authorization.memberBase(): String =
    EsjzoneUrls.baseForDomain(domain.ifBlank { EsjzoneUrls.BaseWithoutProtocol })

/** Loads `/my/sys`; a read-only account page that never links to chapter pages. */
fun EsjzoneClient.getSystemMessages(
    authorization: Authorization,
    forceRefresh: Boolean = false
): List<SystemMessage> {
    val url = EsjzoneUrls.resolve("/my/sys", authorization.memberBase())
    val body = getPage(
        authorization,
        url,
        PageCacheTtl.ACCOUNT_LIST,
        forceRefresh = forceRefresh,
        pageKind = PageKind.ACCOUNT,
        allowStaleOnError = !forceRefresh
    )
    return parseSystemMessages(Jsoup.parse(body, url))
}

/**
 * Loads `/my/reply`. Every client-side page is present in the same HTML, so one
 * request returns the complete list. The reply links themselves are not followed.
 */
fun EsjzoneClient.getMemberReplies(
    authorization: Authorization,
    forceRefresh: Boolean = false
): List<MemberReply> {
    val url = EsjzoneUrls.resolve("/my/reply", authorization.memberBase())
    val body = getPage(
        authorization,
        url,
        PageCacheTtl.ACCOUNT_LIST,
        forceRefresh = forceRefresh,
        pageKind = PageKind.ACCOUNT,
        allowStaleOnError = !forceRefresh
    )
    return parseMemberReplies(Jsoup.parse(body, url))
}

/**
 * Loads one page of `/inc/mem_record_data.php`, the bootstrap-table data source
 * behind `/my/record`. As in the site's table script, each request first obtains
 * a fresh token from the member page and sends it in the Authorization header.
 *
 * The signed-in query can take tens of seconds before the server answers, so the
 * data request uses [EsjzoneClient.memberRecordClient] instead of the shared
 * budget. When called inside a cancellable page request, cancelling the caller
 * cancels the OkHttp call itself rather than leaving a slow query running.
 *
 * A JSON `status: 301` is the site's rejected-token answer. The token is fetched
 * again and the request repeated once, matching the forum table; a second 301
 * is a failure.
 */
fun EsjzoneClient.getExperienceRecords(
    authorization: Authorization,
    type: ExperienceRecordType,
    offset: Int,
    limit: Int = EXPERIENCE_RECORD_PAGE_SIZE
): ExperienceRecordPage {
    val base = authorization.memberBase()
    val referer = EsjzoneUrls.resolve("/my/record", base)
    val url = experienceRecordUrl(base, type, offset, limit)
    var body = requestExperienceRecords(authorization, url, referer, experienceRecordToken(authorization, referer))
    if (experienceRecordStatus(body) == "301") {
        checkRecordRequestActive()
        body = requestExperienceRecords(authorization, url, referer, experienceRecordToken(authorization, referer))
    }
    return parseExperienceRecordPage(body)
}

private fun EsjzoneClient.experienceRecordToken(authorization: Authorization, referer: String): String {
    val token = requestAuthToken(authorization, referer)
    checkRecordRequestActive()
    return token.takeIf { it.isNotBlank() }
        ?: throw ExperienceRecordException("Experience record authorization token unavailable")
}

private fun checkRecordRequestActive() {
    if (pageRequestCancellation.get()?.isCancelled() == true) {
        throw CancellationException("Experience record request cancelled")
    }
}

/** Performs one data request and returns its body after transport and session checks. */
private fun EsjzoneClient.requestExperienceRecords(
    authorization: Authorization,
    url: String,
    referer: String,
    token: String
): String {
    val request = Request.Builder()
        .url(url)
        .get()
        .headers(headers)
        .header("Accept", "application/json, text/javascript, */*; q=0.01")
        .header("Referer", referer)
        .header("X-Requested-With", "XMLHttpRequest")
        .header("Authorization", token)
        .build()
    val call = memberRecordClient(authorization).newCall(request)
    val cancellation = pageRequestCancellation.get()
    cancellation?.attach(call)
    val (code, body) = try {
        call.execute().use { it.code to it.body?.readTextBounded().orEmpty() }
    } catch (error: IOException) {
        if (cancellation?.isCancelled() == true) throw CancellationException("Experience record request cancelled")
        throw NetworkRequestException(url, error)
    } finally {
        cancellation?.detachCall()
    }
    checkRecordRequestActive()
    val looksLikeJson = body.trimStart().startsWith("{")
    if (code in 300..399 || (!looksLikeJson && PageResponsePolicy.looksLikeBlockedOrLoginPage(body))) {
        // A redirect from an XHR endpoint is the site's expired-session answer.
        throw UntrustedPageException(url, PageValidation(false, if (code in 300..399) "login page" else "login or block page"))
    }
    if (code !in 200..299) throw NetworkHttpException(url, code)
    return body
}

/** The application-level `status` of a table response, or null when it is not a JSON object. */
internal fun experienceRecordStatus(body: String): String? {
    val root = runCatching { recordJson.parseToJsonElement(body.trim()) }.getOrNull() as? JsonObject
        ?: return null
    return (root["status"] as? JsonPrimitive)?.content?.trim()
}

/** True when a load failed because the site did not answer within the request budget. */
internal fun Throwable.isRequestTimeout(): Boolean {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    var current: Throwable? = this
    while (current != null && seen.add(current)) {
        // SocketTimeoutException is a subclass; OkHttp's call timeout throws a plain
        // InterruptedIOException("timeout").
        if (current is InterruptedIOException) return true
        current = current.cause
    }
    return false
}

internal fun experienceRecordUrl(
    base: String,
    type: ExperienceRecordType,
    offset: Int,
    limit: Int = EXPERIENCE_RECORD_PAGE_SIZE
): String {
    val endpoint = EsjzoneUrls.resolve("/inc/mem_record_data.php", base).toHttpUrlOrNull()
        ?: throw IllegalArgumentException("Invalid ESJ base URL")
    return endpoint.newBuilder()
        .addQueryParameter("search", "type:${type.code}")
        .addQueryParameter("order", "desc")
        .addQueryParameter("offset", offset.coerceAtLeast(0).toString())
        .addQueryParameter("limit", limit.coerceIn(1, 100).toString())
        .build()
        .toString()
}

// ---------------------------------------------------------------------------
// Parsers
// ---------------------------------------------------------------------------

internal fun parseSystemMessages(document: Document): List<SystemMessage> {
    val rows = document.select("div.col-lg-8 div.table-responsive table.table > tbody > tr")
        .ifEmpty { document.select("table.table > tbody > tr") }
    return rows.mapNotNull { row ->
        val title = row.selectFirst("td > h5.product-title, h5.product-title")
            ?.text()?.trim()?.takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        val time = (row.selectFirst("div.book-ep > span") ?: row.selectFirst("div.book-ep"))
            ?.text()?.trim()?.takeIf(String::isNotBlank)
        SystemMessage(title = title, time = time)
    }
}

private val REPLY_SUBJECT_PREFIX = Regex("^主[旨题題]\\s*[：:]\\s*")
private val REPLY_DATE_PREFIX = Regex("^回[覆复]日期\\s*[：:]\\s*")
private val REPLIER_LABEL = Regex("回[覆复]人")
private val REPLY_DATE_LABEL = Regex("回[覆复]日期")

internal fun parseMemberReplies(document: Document): List<MemberReply> {
    val sections = document.select("section.reply-section")
    val items = if (sections.isNotEmpty()) {
        sections.flatMap { it.select("tbody > tr > td > div.product-item > div.product-info") }
            .ifEmpty { sections.flatMap { it.select("div.product-info") } }
    } else {
        document.select("div.product-item > div.product-info")
    }
    return items.mapNotNull(::parseMemberReply)
}

private fun parseMemberReply(info: Element): MemberReply? {
    val link = info.selectFirst("h5.product-title > a, h5.product-title a") ?: return null
    val content = link.text().trim()
    val href = link.attr("href").trim()
    val url = link.absUrl("href").ifBlank { EsjzoneUrls.resolve(href) }
    if (content.isBlank() && url.isBlank()) return null
    val subject = info.selectFirst("div.book-ep")?.text()?.trim()
        ?.replace(REPLY_SUBJECT_PREFIX, "")?.trim()?.takeIf(String::isNotBlank)
    val updates = info.select("div.book-update")
    val replierLink = updates.firstOrNull { REPLIER_LABEL.containsMatchIn(it.ownText()) }
        ?.selectFirst("a")
    val replierName = replierLink?.text()?.trim()?.takeIf(String::isNotBlank)
    val replierUrl = replierLink?.let { it.absUrl("href").ifBlank { it.attr("href").trim() } }
        ?.takeIf(String::isNotBlank)
    val date = updates.firstOrNull { REPLY_DATE_LABEL.containsMatchIn(it.ownText()) }
        ?.text()?.trim()?.replace(REPLY_DATE_PREFIX, "")?.trim()?.takeIf(String::isNotBlank)
    return MemberReply(
        content = content,
        url = url,
        subject = subject,
        replierName = replierName,
        replierUrl = replierUrl,
        date = date
    )
}

private val recordJson = Json { isLenient = true }
private val RECORD_TIME_KEYS = listOf("cdate", "date", "time", "created_at")
private val RECORD_EXP_KEYS = listOf("exp", "experience", "point", "points")
private val RECORD_NOTE_KEYS = listOf("non", "note", "memo", "description", "desc", "content", "title")

/**
 * Parses the bootstrap-table JSON. `total` has been seen both as a string and
 * a number. Any `status` other than 200 is a failure; an unauthorized request
 * can answer `status: 301` with an empty row list, which must not read as "empty".
 */
internal fun parseExperienceRecordPage(body: String): ExperienceRecordPage {
    val root = runCatching { recordJson.parseToJsonElement(body.trim()) }.getOrNull() as? JsonObject
        ?: throw ExperienceRecordException("Experience record response is not a JSON object")
    val status = (root["status"] as? JsonPrimitive)?.content?.trim()
    if (status != "200") throw ExperienceRecordException("Experience record status $status")
    val rows = when (val raw = root["rows"]) {
        is JsonArray -> raw.mapNotNull(::parseExperienceRecord)
        null, is JsonNull -> emptyList()
        else -> throw ExperienceRecordException("Experience record rows are not an array")
    }
    val total = (root["total"] as? JsonPrimitive)?.content?.trim()?.toIntOrNull()
        ?.coerceAtLeast(rows.size)
        ?: rows.size
    return ExperienceRecordPage(total = total, rows = rows)
}

private fun parseExperienceRecord(element: JsonElement): ExperienceRecord? {
    return when (element) {
        is JsonObject -> {
            val values = element.mapValues { (_, value) -> value.recordText() }
            fun pick(keys: List<String>): Pair<String, String>? = keys.firstNotNullOfOrNull { key ->
                values[key]?.takeIf(String::isNotBlank)?.let { key to it }
            }
            val time = pick(RECORD_TIME_KEYS)
            val exp = pick(RECORD_EXP_KEYS)
            val note = pick(RECORD_NOTE_KEYS)
            val used = setOfNotNull(time?.first, exp?.first, note?.first)
            val extras = values.filterKeys { it !in used }
                .filterValues(String::isNotBlank)
                .map { (key, value) -> "$key: $value" }
            if (time == null && exp == null && note == null && extras.isEmpty()) null
            else ExperienceRecord(time?.second, exp?.second, note?.second, extras)
        }
        is JsonArray -> {
            val cells = element.map { it.recordText() }
            if (cells.all(String::isBlank)) null
            else ExperienceRecord(
                time = cells.getOrNull(0)?.takeIf(String::isNotBlank),
                exp = cells.getOrNull(1)?.takeIf(String::isNotBlank),
                note = cells.getOrNull(2)?.takeIf(String::isNotBlank),
                extras = cells.drop(3).filter(String::isNotBlank)
            )
        }
        else -> null
    }
}

/** Table cells may carry small HTML fragments; show their text only. */
private fun JsonElement.recordText(): String = when (this) {
    is JsonPrimitive -> if (this is JsonNull) "" else {
        val raw = content.trim()
        if (raw.contains('<')) Jsoup.parseBodyFragment(raw).text().trim() else raw
    }
    else -> ""
}

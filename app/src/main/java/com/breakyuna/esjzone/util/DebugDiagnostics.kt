package com.breakyuna.esjzone.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

private val diagnosticPathNames = setOf(
    "", "_redacted_", "index.php", "login.php", "index.htm", "login", "logout", "profile",
    "my", "book", "novel", "detail", "forum", "inc", "modules", "article", "tags", "update",
    "articlelist.php", "toplist.php", "search.php", "mem_login.php", "forum_reply.php", "favorite",
    "view", "record", "list", "guestbook", "cdn-cgi", "challenge-platform", "images", "assets"
)

/** Keep route structure and query names, never query values, credentials, fragments or arbitrary slugs. */
internal fun diagnosticUrl(raw: String?): String {
    if (raw.isNullOrEmpty()) return "empty-url"
    if (raw == "about:blank") return raw
    val url = raw.toHttpUrlOrNull() ?: return "invalid-url"
    val path = url.encodedPath.split('/').joinToString("/") { segment ->
        if (segment in diagnosticPathNames || segment.matches(Regex("[0-9]+(?:\\.html?)?"))) segment else "_redacted_"
    }
    val names = url.queryParameterNames.map { name ->
        name.takeIf { it.matches(Regex("[A-Za-z][A-Za-z0-9_-]{0,47}")) } ?: "_redacted_"
    }.sorted()
    val port = if (url.port == if (url.isHttps) 443 else 80) "" else ":${url.port}"
    return "${url.scheme}://${url.host}$port$path" + if (names.isEmpty()) "" else " queryNames=$names"
}

/** Fixed selectors/counts only. No text, attribute values, input values or HTML snippets. */
internal fun diagnosticHtml(html: String): String {
    val document = Jsoup.parse(html)
    val selectors = linkedMapOf(
        "forms" to "form", "wenkuLogin" to "form[name=frmlogin]", "esjLogin" to "form.login-box",
        "userFields" to "input[name=username]", "passwordFields" to "input[type=password]",
        "loginDurations" to "select[name=usecookie] option", "submitters" to "input[type=submit],button[type=submit]",
        "challenge" to "#challenge-stage,#challenge-form,.cf-turnstile", "cards" to ".card-title",
        "wenkuBlocks" to "#centers .block,#right .block", "bookDetail" to ".book-detail",
        "content" to "#content,.forum-content", "tables" to "table", "links" to "a[href]",
        "images" to "img", "scripts" to "script", "frames" to "iframe"
    )
    return "chars=${html.length}, htmlOpen=${html.contains("<html", true)}, " +
        "bodyOpen=${html.contains("<body", true)}, textChars=${document.body().text().length}, " +
        selectors.entries.joinToString { (name, selector) -> "$name=${document.select(selector).size}" }
}

internal fun diagnosticSql(sql: String, argumentCount: Int): String {
    val operation = sql.trimStart().substringBefore(' ').uppercase().takeIf {
        it in setOf("SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "ALTER", "DROP", "PRAGMA", "BEGIN", "COMMIT", "END")
    } ?: "other"
    val tables = listOf("cache", "searchhistory", "bookmarks", "local_reading_history", "bookshelf",
        "bookshelf_groups", "bookshelf_group_members", "reading_stats")
        .filter { Regex("\\b$it\\b", RegexOption.IGNORE_CASE).containsMatchIn(sql) }
    return "operation=$operation, tables=$tables, argumentCount=$argumentCount"
}

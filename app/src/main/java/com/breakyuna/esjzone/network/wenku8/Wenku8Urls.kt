package com.breakyuna.esjzone.network.wenku8

import com.breakyuna.esjzone.network.EsjzoneUrls
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Source identities are local keys; only the numeric book id belongs in a site URL. */
object Wenku8Urls {
    const val BASE = "https://www.wenku8.net"
    private val numericId = Regex("[0-9]+")
    private val detailPath = Regex("/book/([0-9]+)\\.htm")
    private val catalogPath = Regex("/novel/[0-9]+/([0-9]+)/index\\.htm")
    private val chapterPath = Regex("/novel/[0-9]+/([0-9]+)/[0-9]+\\.htm")

    fun bookId(identity: String): String? = identity.trim().takeIf { it.startsWith("wenku8:") }
        ?.removePrefix("wenku8:")?.takeIf { numericId.matches(it) }

    fun identity(bookId: String): String {
        require(numericId.matches(bookId)) { "Invalid Wenku8 book id" }
        return "wenku8:$bookId"
    }

    fun detail(bookId: String): String {
        require(numericId.matches(bookId)) { "Invalid Wenku8 book id" }
        return "$BASE/book/$bookId.htm"
    }

    fun catalog(bookId: String): String {
        require(numericId.matches(bookId)) { "Invalid Wenku8 book id" }
        val group = bookId.toLongOrNull()?.div(1000) ?: error("Invalid Wenku8 book id")
        return "$BASE/novel/$group/$bookId/index.htm"
    }

    private fun siteUrl(raw: String): HttpUrl? {
        val value = raw.trim()
        if (value.contains('\\') || !value.startsWith("https://", ignoreCase = true)) return null
        return value.toHttpUrlOrNull()?.takeIf {
            it.isHttps && it.host == "www.wenku8.net" && it.port == 443 &&
                it.username.isEmpty() && it.password.isEmpty()
        }
    }

    fun detailIdentity(rawUrl: String): String? = siteUrl(rawUrl)?.let {
        detailPath.matchEntire(it.encodedPath)?.groupValues?.get(1)?.let(::identity)
    }

    fun chapterIdentity(rawUrl: String): String? = siteUrl(rawUrl)?.let {
        chapterPath.matchEntire(it.encodedPath)?.groupValues?.get(1)?.let(::identity)
    }

    fun catalogIdentity(rawUrl: String): String? = siteUrl(rawUrl)?.let {
        catalogPath.matchEntire(it.encodedPath)?.groupValues?.get(1)?.let(::identity)
    }
}

/** Keeps ESJ ids unchanged and refuses to send source-prefixed ids to ESJ. */
fun novelDetailUrlForId(novelId: String): String {
    val id = novelId.trim()
    Wenku8Urls.bookId(id)?.let { return Wenku8Urls.detail(it) }
    return if (id.matches(Regex("[0-9]+"))) EsjzoneUrls.resolve("/detail/$id.html") else ""
}

enum class Wenku8SearchType(val parameter: String) { TITLE("articlename"), AUTHOR("author") }
enum class Wenku8PageKind { DETAIL, CATALOG, SEARCH, CHAPTER }

/** Fixed-upstream GET parameters; the current website protocol still needs live verification. */
fun wenku8SearchUrl(keyword: String, type: Wenku8SearchType, page: Int): String {
    require(keyword.isNotBlank() && page > 0)
    val encoded = java.net.URLEncoder.encode(keyword, "GB2312")
    return "${Wenku8Urls.BASE}/modules/article/search.php".toHttpUrlOrNull()!!.newBuilder()
        .addQueryParameter("searchtype", type.parameter)
        .addEncodedQueryParameter("searchkey", encoded)
        .addQueryParameter("page", page.toString()).build().toString()
}

/** Preserve GB2312 query bytes; UTF-8 query decoding would collapse distinct Chinese keywords. */
internal fun wenku8PageCacheIdentity(rawUrl: String): String =
    rawUrl.toHttpUrlOrNull()?.newBuilder()?.fragment(null)?.build()?.toString().orEmpty()

internal fun wenku8PageAllowed(rawUrl: String, kind: Wenku8PageKind): Boolean {
    val url = rawUrl.toHttpUrlOrNull() ?: return false
    if (!url.isHttps || url.host != "www.wenku8.net" || url.port != 443 ||
        url.username.isNotEmpty() || url.password.isNotEmpty()) return false
    return when (kind) {
        Wenku8PageKind.DETAIL -> Wenku8Urls.detailIdentity(rawUrl) != null
        Wenku8PageKind.CATALOG -> Wenku8Urls.catalogIdentity(rawUrl) != null
        Wenku8PageKind.CHAPTER -> Wenku8Urls.chapterIdentity(rawUrl) != null
        Wenku8PageKind.SEARCH -> Wenku8Urls.detailIdentity(rawUrl) != null ||
            (url.encodedPath == "/modules/article/search.php" &&
                url.queryParameterNames == setOf("searchtype", "searchkey", "page") &&
                url.queryParameter("searchtype") in Wenku8SearchType.entries.map { it.parameter } &&
                url.queryParameter("page")?.toIntOrNull()?.let { it > 0 } == true)
    }
}

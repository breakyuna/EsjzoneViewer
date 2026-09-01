package com.breakyuna.esjzone.network

/** The broad shape expected from each server-rendered ESJ page family. */
internal enum class PageKind {
    GENERIC,
    HOME,
    FORUM,
    ACCOUNT,
    DETAIL,
    CHAPTER,
    LIST,
    SEARCH,
    COMMUNITY
}

internal data class PageValidation(
    val trusted: Boolean,
    val reason: String = ""
)

/**
 * Classifies HTML before it reaches a parser or the disk cache.
 *
 * This deliberately uses a small set of site-wide signals instead of one brittle XPath:
 * ESJ has several templates and deployments.  Page-family checks are only used to reject
 * obvious HTML shells which contain no ESJ structure at all.
 */
internal object PageResponsePolicy {
    private val blockedWords = listOf(
        "access denied",
        "request blocked",
        "forbidden",
        "ip has been blocked",
        "your ip has been",
        "temporarily blocked",
        "cloudflare",
        "just a moment",
        "verify you are human",
        "checking your browser",
        "enable javascript and cookies",
        "cf-chl-",
        "challenge-platform",
        "captcha"
    )

    private val loginPath = Regex("(?:^|/)my/login(?:[/?#.]|$)", RegexOption.IGNORE_CASE)

    fun validate(
        statusCode: Int,
        body: String,
        requestedUrl: String,
        finalUrl: String = requestedUrl,
        contentType: String? = null,
        kind: PageKind = PageKind.GENERIC
    ): PageValidation {
        if (statusCode !in 200..299) return PageValidation(false, "HTTP $statusCode")
        if (body.isBlank()) return PageValidation(false, "empty response body")

        val lower = body.lowercase()
        val lowerFinalUrl = finalUrl.lowercase()
        val lowerRequestedUrl = requestedUrl.lowercase()
        if (loginPath.containsMatchIn(lowerFinalUrl) || looksLikeLoginPage(lower)) {
            return PageValidation(false, "login page")
        }
        if (looksLikeBlockPage(lower)) {
            return PageValidation(false, "access challenge or block page")
        }
        if (contentType?.lowercase()?.let {
                !it.contains("text/html") && !it.contains("application/xhtml+xml")
            } == true) {
            return PageValidation(false, "unexpected content type")
        }
        if (!lower.contains("<html") || !lower.contains("<body")) {
            return PageValidation(false, "not a complete HTML document")
        }

        val hasEsjMarker = lower.contains("esjzone") ||
            lower.contains("/detail/") ||
            lower.contains("/forum/") ||
            lower.contains("/my/") ||
            lower.contains("id=\"integration\"") ||
            lower.contains("class=\"comments-section") ||
            (lower.contains("<nav") && lower.contains("<section"))
        if (!hasEsjMarker) return PageValidation(false, "missing ESJ page markers")

        val familyValid = when (kind) {
            PageKind.GENERIC -> true
            PageKind.HOME -> lower.contains("<section") &&
                (lower.contains("/detail/") || lower.contains("最新") || lower.contains("推荐"))
            PageKind.FORUM -> lower.contains("/forum/") &&
                lower.contains("<table")
            PageKind.COMMUNITY ->
                (lowerRequestedUrl.contains("/guestbook") || lower.contains("/forum/") ||
                    lower.contains("/detail/") || lower.contains("comment")) &&
                (lower.contains("<table") || lower.contains("<section") ||
                    lower.contains("comment") || lower.contains("forum"))
            PageKind.ACCOUNT -> (lower.contains("/my/") || lower.contains("<aside") ||
                lower.contains("profile")) &&
                (lower.contains("<table") || lower.contains("<aside") ||
                    lower.contains("收藏") || lower.contains("history") ||
                    lower.contains("view"))
            PageKind.DETAIL -> lower.contains("/detail/") ||
                lower.contains("id=\"integration\"") || lower.contains("book-detail")
            PageKind.CHAPTER -> lower.contains("/forum/") &&
                (lower.contains("<article") || lower.contains("<section") ||
                    lower.contains("comment") || lower.contains("chapter"))
            PageKind.LIST, PageKind.SEARCH ->
                lower.contains("/detail/") || lower.contains("<table") ||
                    lower.contains("<section")
        }
        return if (familyValid) PageValidation(true) else {
            PageValidation(false, "missing ${kind.name.lowercase()} page markers")
        }
    }

    /** Returns only a validated network body, otherwise the previously validated stale page. */
    fun selectTrustedBody(
        validation: PageValidation,
        networkBody: String,
        staleBody: String?
    ): String? = if (validation.trusted) networkBody else staleBody

    /** Exposed for authorization probes and unit tests without requiring an HTTP call. */
    fun looksLikeBlockedOrLoginPage(body: String, finalUrl: String = ""): Boolean {
        val lower = body.lowercase()
        return loginPath.containsMatchIn(finalUrl.lowercase()) ||
            looksLikeLoginPage(lower) || looksLikeBlockPage(lower)
    }

    private fun looksLikeLoginPage(lowerBody: String): Boolean {
        val hasPassword = Regex("name\\s*=\\s*['\"]pwd['\"]").containsMatchIn(lowerBody) ||
            lowerBody.contains("type=\"password\"")
        return hasPassword && (lowerBody.contains("login") || lowerBody.contains("登录"))
    }

    private fun looksLikeBlockPage(lowerBody: String): Boolean {
        // Keep user-authored forum text from tripping a detector merely because it
        // mentions a word such as "captcha". WAF pages put their message in the head
        // or at the beginning of the document, so inspect those bounded regions.
        val head = lowerBody.substringBefore("</head>")
        val leadingBody = lowerBody.take(2_000)
        return blockedWords.any { marker ->
            head.contains(marker) || leadingBody.contains(marker)
        }
    }
}

internal class UntrustedPageException(
    val url: String,
    val validation: PageValidation
) : java.io.IOException(
    "Untrusted ESJ response for $url: ${validation.reason}"
)

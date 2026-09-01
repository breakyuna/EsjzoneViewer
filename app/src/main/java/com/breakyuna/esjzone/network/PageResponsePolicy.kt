package com.breakyuna.esjzone.network

import org.jsoup.Jsoup

/** The broad shape expected from each server-rendered ESJ page family. */
/**
 * Page family used by the public [EsjzoneClient.getPage] API to validate responses.
 *
 * This type must be public because it appears in that public function's signature;
 * keeping it internal makes Kotlin reject the API as exposing an internal type.
 */
enum class PageKind {
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
    /**
     * These are messages a WAF puts in the document title or its error heading.
     * Vendor names and implementation URLs are intentionally absent: ESJ's normal
     * templates load cdnjs.cloudflare.com and may include Cloudflare's jsd script.
     */
    private val blockMessage = Regex(
        """\b(?:access denied|request blocked|forbidden|ip has been blocked|your ip has been|temporarily blocked|you have been blocked|sorry, you have been blocked|rate limited|you are being rate limited|error\s+(?:1015|1020)|just a moment|verify\s+(?:that\s+)?you\s+are\s+human|checking your browser|enable javascript and cookies|attention required|performing security verification|human verification|security check|captcha)\b""",
        RegexOption.IGNORE_CASE
    )
    private val challengeMessage = Regex(
        """\b(?:challenge|human verification|security check|captcha)\b""",
        RegexOption.IGNORE_CASE
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

        val hasEsjMarker = hasEsjPageMarker(lower)
        if (!hasEsjMarker) return PageValidation(false, "missing ESJ page markers")

        val familyValid = when (kind) {
            PageKind.GENERIC -> true
            // Route markers are more stable than a particular section/table nesting.
            // Some current ESJ pages are intentionally empty or use a different
            // template while retaining the same URL family.
            PageKind.HOME -> lowerRequestedUrl.isHomeRoute() &&
                (lower.contains("<section") || lower.contains("<main") || lower.contains("<div"))
            PageKind.FORUM -> (lowerRequestedUrl.contains("/forum") || lower.contains("/forum/")) &&
                (lower.contains("<table") || lower.contains("<article") ||
                    lower.contains("thread") || lower.contains("topic"))
            PageKind.COMMUNITY ->
                (lowerRequestedUrl.contains("/guestbook") || lower.contains("/forum/") ||
                    lower.contains("/detail/") || lower.contains("comment")) &&
                (lower.contains("<table") || lower.contains("<section") ||
                    lower.contains("<article") || lower.contains("comment") || lower.contains("forum"))
            PageKind.ACCOUNT -> (lowerRequestedUrl.contains("/my/") || lower.contains("/my/") ||
                lower.contains("<aside") ||
                lower.contains("profile")) &&
                (lower.contains("<table") || lower.contains("<section") || lower.contains("<div") ||
                    lower.contains("<aside") ||
                    lower.contains("收藏") || lower.contains("history") ||
                    lower.contains("view"))
            PageKind.DETAIL -> lowerRequestedUrl.contains("/detail/") || lower.contains("/detail/") ||
                lower.contains("id=\"integration\"") || lower.contains("book-detail")
            PageKind.CHAPTER -> (lowerRequestedUrl.contains("/forum/") || lower.contains("/forum/")) &&
                (lower.contains("<article") || lower.contains("<section") || lower.contains("<div") ||
                    lower.contains("comment") || lower.contains("chapter"))
            PageKind.LIST, PageKind.SEARCH ->
                lower.contains("/detail/") || lower.contains("<table") ||
                    lower.contains("<section") || lower.contains("<main") || lower.contains("<div")
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
        val document = Jsoup.parse(lowerBody)
        // Do not treat a reflected site hostname in a WAF error page as proof of
        // an ESJ document.  The WAF classifier needs actual page structure.
        val hasEsjMarker = hasEsjStructureMarker(lowerBody)
        val titleAndWafDetails = document.select(
            "title, #challenge-error-text, .cf-error-title, .cf-error-details, [data-testid=challenge-error]"
        ).text()
        val headings = document.select("h1, h2, [role=heading]").text()
        val bodyText = document.body()?.text().orEmpty()

        // A title or Cloudflare error element is a strong signal.  Headings/body
        // text are only considered when the document has no ESJ structure, so a
        // forum post discussing "captcha" or "forbidden" remains valid content.
        val hasWafDetailElement = document.select(
            "#challenge-error-text, .cf-error-title, .cf-error-details, [data-testid=challenge-error]"
        ).isNotEmpty()
        if (blockMessage.containsMatchIn(titleAndWafDetails) &&
            (!hasEsjMarker || hasWafDetailElement)
        ) {
            return true
        }
        if (!hasEsjMarker && blockMessage.containsMatchIn(headings + " " + bodyText)) {
            return true
        }

        // Challenge implementation markers are meaningful only together with a
        // challenge message and an otherwise non-ESJ document. In particular,
        // /cdn-cgi/challenge-platform/scripts/jsd/main.js is a normal ESJ footer.
        val hasChallengeStructure = lowerBody.contains("cf-chl-") ||
            lowerBody.contains("/cdn-cgi/challenge-platform/") ||
            lowerBody.contains("challenge-form") ||
            lowerBody.contains("challenge-stage")
        val securityText = headings + " " + bodyText + " " + titleAndWafDetails
        return !hasEsjMarker && hasChallengeStructure &&
            (challengeMessage.containsMatchIn(securityText) ||
                (lowerBody.contains("cloudflare") &&
                    blockMessage.containsMatchIn(titleAndWafDetails)))
    }

    private fun hasEsjPageMarker(lowerBody: String): Boolean =
        lowerBody.contains("esjzone") ||
            hasEsjStructureMarker(lowerBody)

    private fun hasEsjStructureMarker(lowerBody: String): Boolean =
        lowerBody.contains("/detail/") ||
            lowerBody.contains("/forum/") ||
            lowerBody.contains("/my/") ||
            lowerBody.contains("id=\"integration\"") ||
            lowerBody.contains("class=\"comments-section") ||
            (lowerBody.contains("<nav") && lowerBody.contains("<section"))

    private fun String.isHomeRoute(): Boolean =
        substringAfter("//", "").substringAfter('/', "").substringBefore('?').substringBefore('#')
            .trim('/')
            .isEmpty()
}

internal class UntrustedPageException(
    val url: String,
    val validation: PageValidation
) : java.io.IOException(
    "Untrusted ESJ response for $url: ${validation.reason}"
)

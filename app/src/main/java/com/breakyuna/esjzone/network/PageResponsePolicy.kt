package com.breakyuna.esjzone.network

import org.jsoup.Jsoup
import com.breakyuna.esjzone.novellibrary.novel.ChapterSource
import com.breakyuna.esjzone.novellibrary.novel.resolveChapterSource

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
    EXTERNAL_CHAPTER,
    LIST,
    WEEKLY_UPDATE,
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
 * This deliberately uses a small set of site-wide signals instead of one brittle positional selector:
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
    private val loginPasswordField = Regex("""name\s*=\s*['"]pwd['"]""", RegexOption.IGNORE_CASE)
    private val scriptLoginRedirect = Regex(
        """window\s*\.\s*location\s*\.\s*href\s*=\s*['\"]/?my/login(?:[/?'\"]|$)""",
        RegexOption.IGNORE_CASE
    )

    fun hasScriptLoginRedirect(body: String): Boolean =
        !body.contains("<html", ignoreCase = true) && scriptLoginRedirect.containsMatchIn(body)

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

        if (kind == PageKind.EXTERNAL_CHAPTER) {
            if (resolveChapterSource(finalUrl) != ChapterSource.WENKU8) {
                return PageValidation(false, "external chapter redirect outside allowed host")
            }
            if (com.breakyuna.esjzone.network.external.CloudflareChallenge.hasChallengeDocumentMarkers(body)) {
                return PageValidation(false, "access challenge or block page")
            }
            val content = Jsoup.parse(body, finalUrl).selectFirst("#content")
            return if (content != null &&
                (content.text().trim().length >= 20 || content.select("img").isNotEmpty())) PageValidation(true)
            else PageValidation(false, "missing external chapter content")
        }

        if (loginPath.containsMatchIn(finalUrl) || looksLikeLoginPage(body) ||
            hasScriptLoginRedirect(body)) {
            return PageValidation(false, "login page")
        }
        if (looksLikeBlockPage(body)) {
            return PageValidation(false, "access challenge or block page")
        }
        if (contentType?.let {
                !it.contains("text/html", ignoreCase = true) &&
                    !it.contains("application/xhtml+xml", ignoreCase = true)
            } == true) {
            return PageValidation(false, "unexpected content type")
        }
        if (!body.contains("<html", ignoreCase = true) || !body.contains("<body", ignoreCase = true) ||
            !body.contains("</body>", ignoreCase = true) || !body.contains("</html>", ignoreCase = true)) {
            return PageValidation(false, "not a complete HTML document")
        }

        val hasEsjMarker = hasEsjPageMarker(body)
        if (!hasEsjMarker) return PageValidation(false, "missing ESJ page markers")

        val familyValid = when (kind) {
            PageKind.GENERIC -> true
            PageKind.EXTERNAL_CHAPTER -> false
            // Route markers are more stable than a particular section/table nesting.
            // Some current ESJ pages are intentionally empty or use a different
            // template while retaining the same URL family.
            PageKind.HOME -> requestedUrl.isHomeRoute() &&
                (body.contains("<section", ignoreCase = true) ||
                    body.contains("<main", ignoreCase = true) ||
                    body.contains("<div", ignoreCase = true))
            PageKind.FORUM -> (requestedUrl.contains("/forum", ignoreCase = true) ||
                body.contains("/forum/", ignoreCase = true)) &&
                (body.contains("<table", ignoreCase = true) ||
                    body.contains("<article", ignoreCase = true) ||
                    body.contains("thread", ignoreCase = true) ||
                    body.contains("topic", ignoreCase = true))
            PageKind.COMMUNITY ->
                (requestedUrl.contains("/guestbook", ignoreCase = true) ||
                    body.contains("/forum/", ignoreCase = true) ||
                    body.contains("/detail/", ignoreCase = true) ||
                    body.contains("comment", ignoreCase = true)) &&
                (body.contains("<table", ignoreCase = true) ||
                    body.contains("<section", ignoreCase = true) ||
                    body.contains("<article", ignoreCase = true) ||
                    body.contains("comment", ignoreCase = true) ||
                    body.contains("forum", ignoreCase = true))
            PageKind.ACCOUNT -> (requestedUrl.contains("/my/", ignoreCase = true) ||
                body.contains("/my/", ignoreCase = true) ||
                body.contains("<aside", ignoreCase = true) ||
                body.contains("profile", ignoreCase = true)) &&
                (body.contains("<table", ignoreCase = true) ||
                    body.contains("<section", ignoreCase = true) ||
                    body.contains("<div", ignoreCase = true) ||
                    body.contains("<aside", ignoreCase = true) ||
                    body.contains("收藏") ||
                    body.contains("history", ignoreCase = true) ||
                    body.contains("view", ignoreCase = true))
            PageKind.DETAIL -> requestedUrl.contains("/detail/", ignoreCase = true) &&
                (body.contains("/detail/", ignoreCase = true) ||
                    body.contains("id=\"integration\"", ignoreCase = true) ||
                    body.contains("book-detail", ignoreCase = true))
            PageKind.CHAPTER -> (requestedUrl.contains("/forum/", ignoreCase = true) ||
                body.contains("/forum/", ignoreCase = true)) &&
                (body.contains("<article", ignoreCase = true) ||
                    body.contains("<section", ignoreCase = true) ||
                    body.contains("<div", ignoreCase = true) ||
                    body.contains("comment", ignoreCase = true) ||
                    body.contains("chapter", ignoreCase = true))
            PageKind.LIST, PageKind.WEEKLY_UPDATE, PageKind.SEARCH ->
                body.contains("/detail/", ignoreCase = true) ||
                    body.contains("<table", ignoreCase = true) ||
                    body.contains("<section", ignoreCase = true) ||
                    body.contains("<main", ignoreCase = true) ||
                    body.contains("<div", ignoreCase = true)
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
        return loginPath.containsMatchIn(finalUrl) ||
            looksLikeLoginPage(body) || hasScriptLoginRedirect(body) || looksLikeBlockPage(body)
    }

    private fun looksLikeLoginPage(body: String): Boolean {
        val hasPassword = loginPasswordField.containsMatchIn(body) ||
            body.contains("type=\"password\"", ignoreCase = true)
        return hasPassword && (body.contains("login", ignoreCase = true) || body.contains("登录"))
    }

    private fun looksLikeBlockPage(body: String): Boolean {
        val hasEsjMarker = hasEsjStructureMarker(body)
        val hasActiveChallengeMarker = body.contains("challenge-stage", ignoreCase = true) ||
            body.contains("challenge-form", ignoreCase = true) ||
            body.contains("cf-turnstile", ignoreCase = true) ||
            body.contains("challenge-error", ignoreCase = true) ||
            body.contains("cf-error-", ignoreCase = true)
        if (hasEsjMarker && !hasActiveChallengeMarker) {
            return false
        }

        val document = Jsoup.parse(body)
        // Active challenge elements rendered by Cloudflare Turnstile/Managed Challenges.
        val hasChallengeElement = document.select(
            "#challenge-stage, #challenge-form, .cf-turnstile, #challenge-error-text, .cf-error-title, .cf-error-details, [data-testid=challenge-error]"
        ).isNotEmpty()
        if (hasChallengeElement) {
            return true
        }

        val title = document.title().trim()
        val isWafExclusiveTitle = title.startsWith("Just a moment", ignoreCase = true) ||
            title.startsWith("Attention Required", ignoreCase = true) ||
            title.equals("403 Forbidden", ignoreCase = true) ||
            title.equals("Access Denied", ignoreCase = true)
        if (isWafExclusiveTitle && (!hasEsjMarker || hasChallengeElement)) {
            return true
        }

        val titleAndWafDetails = document.select(
            "title, #challenge-error-text, .cf-error-title, .cf-error-details, [data-testid=challenge-error]"
        ).text()
        val headings = document.select("h1, h2, [role=heading]").text()
        val bodyText = document.body()?.text().orEmpty()

        if (blockMessage.containsMatchIn(titleAndWafDetails) &&
            (!hasEsjMarker || hasChallengeElement)
        ) {
            return true
        }
        if (!hasEsjMarker && blockMessage.containsMatchIn(headings + " " + bodyText)) {
            return true
        }

        // Challenge implementation markers are meaningful only together with a
        // challenge message and an otherwise non-ESJ document. In particular,
        // /cdn-cgi/challenge-platform/scripts/jsd/main.js is a normal ESJ footer.
        val hasChallengeStructure = body.contains("cf-chl-", ignoreCase = true) ||
            body.contains("/cdn-cgi/challenge-platform/", ignoreCase = true) ||
            body.contains("challenge-form", ignoreCase = true) ||
            body.contains("challenge-stage", ignoreCase = true)
        val securityText = headings + " " + bodyText + " " + titleAndWafDetails
        return !hasEsjMarker && hasChallengeStructure &&
            (challengeMessage.containsMatchIn(securityText) ||
                (body.contains("cloudflare", ignoreCase = true) &&
                    blockMessage.containsMatchIn(titleAndWafDetails)))
    }

    private fun hasEsjPageMarker(body: String): Boolean =
        body.contains("esjzone", ignoreCase = true) ||
            hasEsjStructureMarker(body)

    private fun hasEsjStructureMarker(body: String): Boolean =
        body.contains("id=\"integration\"", ignoreCase = true) ||
            body.contains("class=\"comments-section", ignoreCase = true) ||
            body.contains("class=\"book-detail", ignoreCase = true) ||
            body.contains("class=\"product-item", ignoreCase = true) ||
            (body.contains("<nav", ignoreCase = true) && body.contains("<section", ignoreCase = true))

    private fun String.isHomeRoute(): Boolean =
        substringAfter("//", "").substringAfter('/', "").substringBefore('?').substringBefore('#')
            .trim('/')
            .isEmpty()
}

internal class UntrustedPageException(
    val url: String,
    val validation: PageValidation,
    responseDiagnostic: String? = null
) : java.io.IOException(
    "Untrusted ESJ response for $url: ${validation.reason}" +
        (responseDiagnostic?.let { " ($it)" } ?: "")
)

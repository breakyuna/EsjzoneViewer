package com.breakyuna.esjzone.network.external

import java.io.IOException
import org.jsoup.Jsoup

class CloudflareChallengeRequiredException(val chapterUrl: String? = null) :
    IOException("Site security verification is required")
class CloudflareWebViewUnavailableException(cause: Throwable? = null) :
    IOException("Android System WebView is unavailable", cause)
class WenkuCookieStoreUnavailableException : IOException("Wenku cookie storage is unavailable")

internal object CloudflareChallenge {
    private val challengeHeading = Regex(
        """\b(?:verify (?:that )?you are human|human verification|security check|captcha|challenge required|enable javascript and cookies)\b|人机验证|安全验证|安全检查|验证码""",
        RegexOption.IGNORE_CASE
    )

    /** For persisted HTML, whose response headers are no longer available. */
    fun hasChallengeDocumentMarkers(body: String): Boolean {
        val lower = body.lowercase()
        if (!lower.contains("/cdn-cgi/challenge-platform/") &&
            !lower.contains("just a moment") &&
            !lower.contains("attention required") &&
            !lower.contains("enable javascript and cookies") &&
            !lower.contains("turnstile")) return false

        val document = Jsoup.parse(body)
        val title = document.title().trim()
        if (title.startsWith("Just a moment", ignoreCase = true) ||
            title.startsWith("Attention Required", ignoreCase = true) ||
            document.select("#challenge-stage, #challenge-form, .cf-turnstile, " +
                "#challenge-error-text, [data-testid=challenge-error]").isNotEmpty()) return true

        val pageHeadings = document.select("h1, h2, [role=heading]")
            .filter { heading -> heading.parents().none { it.id() == "content" } }
            .joinToString(" ") { it.text() }
        val securityHeadings = pageHeadings + " " + document.select(
            "#challenge-error-text, .cf-error-title, .cf-error-details"
        ).text() + " " + title
        if (challengeHeading.containsMatchIn(securityHeadings)) return true

        // Cloudflare's JavaScript detection script can also appear on a real chapter page.
        val content = document.selectFirst("#content")
        if (content != null &&
            (content.text().trim().length >= 20 || content.select("img").isNotEmpty())) {
            val hasNormalJsDetectionScript = document.select("script[src]").any { script ->
                script.attr("src").substringBefore('?').substringBefore('#')
                    .endsWith("/cdn-cgi/challenge-platform/scripts/jsd/main.js", ignoreCase = true)
            }
            val hasOtherChallengeSignal = lower.contains("cf-chl-") ||
                lower.contains("enable javascript and cookies") || lower.contains("turnstile")
            return hasOtherChallengeSignal ||
                (lower.contains("/cdn-cgi/challenge-platform/") && !hasNormalJsDetectionScript)
        }

        return lower.contains("/cdn-cgi/challenge-platform/") ||
            lower.contains("enable javascript and cookies") ||
            lower.contains("turnstile")
    }

    fun isChallenge(status: Int, mitigated: String?, server: String?, body: String): Boolean {
        val cfServer = server?.contains("cloudflare", ignoreCase = true) == true
        if (mitigated?.equals("challenge", ignoreCase = true) == true) return true
        if (status !in listOf(403, 503) && status !in 200..299) return false
        return cfServer && hasChallengeDocumentMarkers(body)
    }
}

package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.EsjzoneXPaths
import com.breakyuna.esjzone.network.PageResponsePolicy
import com.breakyuna.esjzone.network.hasCredentials
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import okhttp3.Request
import org.jsoup.Jsoup

enum class AuthorizationCheckResult {
    AUTHORIZED,
    UNAUTHORIZED,
    UNKNOWN
}

private const val AUTHORIZATION_VERIFICATION_TTL = 30L * 60L * 1000L
private const val AUTHORIZATION_CHECK_TIMEOUT_MILLIS = 8_000L

/**
 * Checks a cached session without turning temporary network failures into a
 * local logout. UNKNOWN means that the cached credentials should be kept.
 */
fun EsjzoneClient.checkAuthorization(
    authorization: Authorization,
    totalTimeoutMillis: Long = AUTHORIZATION_CHECK_TIMEOUT_MILLIS
): AuthorizationCheckResult {
    if (!authorization.hasCredentials()) {
        AppLogger.i("IsAuthorized", "No stored authorization credentials found")
        return AuthorizationCheckResult.UNAUTHORIZED
    }

    if (wasAuthorizationVerifiedRecently(authorization, AUTHORIZATION_VERIFICATION_TTL)) {
        AppLogger.i("IsAuthorized", "Using recently verified local session")
        return AuthorizationCheckResult.AUTHORIZED
    }

    val deadlineNanos = System.nanoTime() + totalTimeoutMillis.coerceAtLeast(1L) * 1_000_000L
    val firstTimeout = remainingTimeoutMillis(deadlineNanos)
    val first = if (firstTimeout > 0L) {
        checkAuthorizationOnce(authorization, firstTimeout)
    } else {
        AuthorizationProbe(AuthorizationCheckResult.UNKNOWN)
    }
    if (!first.retryableStatus) {
        if (first.result == AuthorizationCheckResult.AUTHORIZED) {
            markAuthorizationVerified(authorization)
        }
        return first.result
    }

    // A retryable 403 can be produced by a proxy, rate limiter, or a transient
    // server edge. Confirm it before treating the persisted session as expired.
    val secondTimeout = remainingTimeoutMillis(deadlineNanos)
    val second = if (secondTimeout > 0L) {
        checkAuthorizationOnce(authorization, secondTimeout)
    } else {
        AuthorizationProbe(AuthorizationCheckResult.UNKNOWN)
    }
    // A repeated WAF/proxy response is still not proof that the session expired.
    // Only an explicit unauthorized result may surface the relogin prompt.
    val result = if (second.retryableStatus) AuthorizationCheckResult.UNKNOWN else second.result
    if (result == AuthorizationCheckResult.AUTHORIZED) {
        markAuthorizationVerified(authorization)
    }
    return result
}

private data class AuthorizationProbe(
    val result: AuthorizationCheckResult,
    val retryableStatus: Boolean = false
)

private fun EsjzoneClient.checkAuthorizationOnce(
    authorization: Authorization,
    timeoutMillis: Long
): AuthorizationProbe {
    return try {
        val sessionBase = EsjzoneUrls.baseForDomain(
            authorization.domain.ifBlank { EsjzoneUrls.BaseWithoutProtocol }
        )
        val profileUrl = EsjzoneUrls.resolve("/my/profile", sessionBase)
        AppLogger.i("IsAuthorized", "Checking authorization with server at $profileUrl")
        val client = authorizationCheckClient(authorization, timeoutMillis)
        val response = client.newCall(
            Request.Builder()
                .url(profileUrl)
                .get()
                .headers(this.headers)
                .build()
        ).execute()

        val responseCode = response.code
        val isSuccessful = response.isSuccessful
        val finalPath = response.request.url.encodedPath.lowercase()
        val responseBody = response.bodyStringOrEmpty()

        val document = Jsoup.parse(responseBody)
        val hasProfileMarker = EsjzoneXPaths.Profile.Username.evaluate(document)
            .get()
            ?.trim()
            ?.isNotEmpty() == true
        val redirectedToLogin = finalPath.contains("/my/login") ||
            LOGIN_REDIRECT_PATTERN.containsMatchIn(responseBody)
        val hasLoginForm = document.select("form.login-box").isNotEmpty() ||
            (document.select("input[name=pwd]").isNotEmpty() &&
                responseBody.contains("/my/login", ignoreCase = true))
        val explicitLoginPage = finalPath.contains("/my/login") ||
            (redirectedToLogin && hasLoginForm)

        val result = when {
            explicitLoginPage ->
                AuthorizationProbe(AuthorizationCheckResult.UNAUTHORIZED)
            responseCode == 401 ->
                AuthorizationProbe(AuthorizationCheckResult.UNAUTHORIZED)
            responseCode == 403 &&
                !PageResponsePolicy.looksLikeBlockedOrLoginPage(responseBody, finalPath) ->
                // A plain 403 can be a genuine authorization rejection, but a WAF/IP
                // response must never erase a usable local session. Keep it unknown and
                // let the next foreground/retry request decide.
                AuthorizationProbe(AuthorizationCheckResult.UNKNOWN, retryableStatus = true)
            responseCode == 403 ->
                AuthorizationProbe(AuthorizationCheckResult.UNKNOWN)
            !isSuccessful ->
                AuthorizationProbe(AuthorizationCheckResult.UNKNOWN)
            responseBody.isBlank() ||
                PageResponsePolicy.looksLikeBlockedOrLoginPage(responseBody, finalPath) ->
                AuthorizationProbe(AuthorizationCheckResult.UNKNOWN)
            hasProfileMarker ->
                AuthorizationProbe(AuthorizationCheckResult.AUTHORIZED)
            else ->
                AuthorizationProbe(AuthorizationCheckResult.UNKNOWN)
        }

        AppLogger.i("IsAuthorized", "Authorization check result: ${result.result}")
        result
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.e("IsAuthorized", "Failed to check authorization due to network/parsing exception", e)
        AuthorizationProbe(AuthorizationCheckResult.UNKNOWN)
    }
}

private fun remainingTimeoutMillis(deadlineNanos: Long): Long {
    return ((deadlineNanos - System.nanoTime()) / 1_000_000L).coerceAtLeast(0L)
}

/**
 * Compatibility helper for callers that only need a strict boolean result.
 */
fun EsjzoneClient.isAuthorized(authorization: Authorization): Boolean =
    checkAuthorization(authorization) == AuthorizationCheckResult.AUTHORIZED

private val LOGIN_REDIRECT_PATTERN = Regex(
    """(?i)window\s*\.\s*location\s*\.\s*href\s*=\s*['\"]/?my/login(?:[/?'\"]|$)"""
)

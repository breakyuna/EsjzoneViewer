package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
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

    val timeoutMillis = totalTimeoutMillis.coerceAtLeast(1L)
    val result = checkAuthorizationOnce(authorization, timeoutMillis)
    if (result == AuthorizationCheckResult.AUTHORIZED) {
        markAuthorizationVerified(authorization)
    }
    return result
}

private fun EsjzoneClient.checkAuthorizationOnce(
    authorization: Authorization,
    timeoutMillis: Long
): AuthorizationCheckResult {
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

        val redirectedToLogin = finalPath.contains("/my/login") ||
            PageResponsePolicy.hasScriptLoginRedirect(responseBody)

        val result = when {
            redirectedToLogin -> AuthorizationCheckResult.UNAUTHORIZED
            responseCode == 401 -> AuthorizationCheckResult.UNAUTHORIZED
            responseCode == 403 || !isSuccessful || responseBody.isBlank() ->
                // A 403/non-2xx/blank response may come from a WAF, proxy, or transient
                // edge failure and must never erase a usable local session.
                AuthorizationCheckResult.UNKNOWN
            else -> {
                val document = Jsoup.parse(responseBody)
                val profileMarkerPresent = hasProfileMarker(document)
                val hasLoginForm = document.select("form.login-box").isNotEmpty() ||
                    (document.select("input[name=pwd]").isNotEmpty() &&
                        responseBody.contains("/my/login", ignoreCase = true))
                when {
                    hasLoginForm && !profileMarkerPresent -> AuthorizationCheckResult.UNAUTHORIZED
                    PageResponsePolicy.looksLikeBlockedOrLoginPage(responseBody, finalPath) ->
                        AuthorizationCheckResult.UNKNOWN
                    profileMarkerPresent -> AuthorizationCheckResult.AUTHORIZED
                    else -> AuthorizationCheckResult.UNKNOWN
                }
            }
        }

        AppLogger.i("IsAuthorized", "Authorization check result: $result")
        result
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.e("IsAuthorized", "Failed to check authorization due to network/parsing exception", e)
        AuthorizationCheckResult.UNKNOWN
    }
}

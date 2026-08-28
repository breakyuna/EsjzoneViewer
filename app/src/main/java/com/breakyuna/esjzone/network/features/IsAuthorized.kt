package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.EsjzoneXPaths
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

/**
 * Checks a cached session without turning temporary network failures into a
 * local logout. UNKNOWN means that the cached credentials should be kept.
 */
fun EsjzoneClient.checkAuthorization(authorization: Authorization): AuthorizationCheckResult {
    if (!authorization.hasCredentials()) {
        AppLogger.i("IsAuthorized", "No stored authorization credentials found")
        return AuthorizationCheckResult.UNAUTHORIZED
    }

    return try {
        AppLogger.i("IsAuthorized", "Checking authorization with server at ${EsjzoneUrls.My.Profile}")
        val response = authenticatedClient(authorization).newCall(
            Request.Builder()
                .url(EsjzoneUrls.My.Profile)
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
            .list()
            .isNotEmpty()
        val redirectedToLogin = finalPath.contains("/my/login") ||
            LOGIN_REDIRECT_PATTERN.containsMatchIn(responseBody)
        val hasLoginForm = document.select("form.login-box, input[name=pwd]").isNotEmpty()

        val result = when {
            responseCode == 401 || responseCode == 403 ->
                AuthorizationCheckResult.UNAUTHORIZED
            redirectedToLogin || hasLoginForm ->
                AuthorizationCheckResult.UNAUTHORIZED
            !isSuccessful ->
                AuthorizationCheckResult.UNKNOWN
            hasProfileMarker || finalPath.contains("/my/profile") ->
                AuthorizationCheckResult.AUTHORIZED
            else ->
                AuthorizationCheckResult.UNKNOWN
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

/**
 * Compatibility helper for callers that only need a strict boolean result.
 */
fun EsjzoneClient.isAuthorized(authorization: Authorization): Boolean =
    checkAuthorization(authorization) == AuthorizationCheckResult.AUTHORIZED

private val LOGIN_REDIRECT_PATTERN = Regex(
    """(?i)window\s*\.\s*location\s*\.\s*href\s*=\s*['\"]/?my/login(?:[/?'\"]|$)"""
)

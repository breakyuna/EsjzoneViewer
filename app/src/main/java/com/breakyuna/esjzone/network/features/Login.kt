package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.readTextBounded
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.NetworkHttpException
import com.breakyuna.esjzone.network.NetworkRequestException
import com.breakyuna.esjzone.data.settings.SettingsDefaults
import com.breakyuna.esjzone.network.pageRequestCancellation
import com.breakyuna.esjzone.util.AppLogger
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Request
import java.io.IOException

internal sealed interface LoginAttemptResult {
    data class Success(val authorization: Authorization) : LoginAttemptResult
    data class NonSuccessStatus(val status: Int) : LoginAttemptResult
    data object InvalidResponse : LoginAttemptResult
    data class IoFailure(val error: IOException) : LoginAttemptResult
}

fun EsjzoneClient.login(
    email: String,
    password: String,
    domain: String = EsjzoneUrls.BaseWithoutProtocol
): Authorization? = when (val result = loginWithOutcome(email, password, domain)) {
    is LoginAttemptResult.Success -> result.authorization
    is LoginAttemptResult.IoFailure -> throw result.error
    is LoginAttemptResult.NonSuccessStatus,
    LoginAttemptResult.InvalidResponse -> null
}

internal fun EsjzoneClient.loginWithOutcome(
    email: String,
    password: String,
    domain: String = EsjzoneUrls.BaseWithoutProtocol,
    sessionBatch: LoginSessionBatch? = null
): LoginAttemptResult {
    require(domain in SettingsDefaults.DOMAINS)
    return try {
        val baseUrl = EsjzoneUrls.baseForDomain(domain)
        var loginResponseUrl: HttpUrl? = null
        val cookieJar = LoginCookieJar()
        val httpClient = loginClient(cookieJar)

        val tokenUrl = EsjzoneUrls.resolve("/my/login", baseUrl)
        val tokenCall = httpClient.newCall(
            Request.Builder()
                .url(tokenUrl)
                .post(
                    FormBody.Builder()
                        .add("plxf", "getAuthToken")
                        .build()
                )
                .headers(this.headers)
                .build()
        )
        val cancellation = pageRequestCancellation.get()
        cancellation?.attach(tokenCall)
        val authorizationToken = try { tokenCall.execute().use { response ->
            if (!response.isSuccessful) {
                throw NetworkHttpException(tokenUrl, response.code)
            }
            if (!response.request.url.host.equals(domain, ignoreCase = true)) {
                throw NetworkRequestException(
                    tokenUrl,
                    IOException("Unexpected login token redirect host: ${response.request.url.host}")
                )
            }
            parseAuthorizationToken(response.body?.readTextBounded().orEmpty())
        } } finally { cancellation?.detachCall() }

        if (authorizationToken.isNullOrBlank()) {
            AppLogger.w("Login", "Login token response was empty or malformed")
            return LoginAttemptResult.InvalidResponse
        }

        val loginUrl = EsjzoneUrls.resolve("/inc/mem_login.php", baseUrl)
        val loginCall = httpClient.newCall(
            Request.Builder()
                .url(loginUrl)
                .post(
                    FormBody.Builder()
                        .add("email", email)
                        .add("pwd", password)
                        .add("remember_me", "on")
                        .build()
                )
                .headers(this.headers)
                .header("Authorization", authorizationToken)
                .build()
        )
        cancellation?.attach(loginCall)
        val status = try { loginCall.execute().use { response ->
            loginResponseUrl = response.request.url
            if (!response.isSuccessful) {
                throw NetworkHttpException(loginUrl, response.code)
            }
            if (!response.request.url.host.equals(domain, ignoreCase = true)) {
                throw NetworkRequestException(
                    loginUrl,
                    IOException("Unexpected login redirect host: ${response.request.url.host}")
                )
            }
            parseLoginStatus(response.body?.readTextBounded().orEmpty())
        } } finally { cancellation?.detachCall() }

        if (cancellation?.isCancelled() == true) throw CancellationException("Login cancelled")

        if (status == null) return LoginAttemptResult.InvalidResponse
        if (status != 200) return LoginAttemptResult.NonSuccessStatus(status)

        val responseUrl = loginResponseUrl ?: return LoginAttemptResult.InvalidResponse
        val cookies = cookieJar.allCookies().filter { it.matches(responseUrl) }
        val key = cookies.firstOrNull { it.name == "ews_key" }?.value
        val token = cookies.firstOrNull { it.name == "ews_token" }?.value
        if (key.isNullOrBlank() || token.isNullOrBlank()) {
            AppLogger.w("Login", "Login succeeded without the required session cookies")
            return LoginAttemptResult.InvalidResponse
        }

        val authorization = Authorization(key, token, domain)
        val committed = if (sessionBatch != null) {
            sessionBatch.commit(responseUrl, email, cookies, authorization)
        } else {
            commitLoginSession(responseUrl, email, cookies, authorization) != null
        }
        if (!committed) throw CancellationException("Login session changed")
        LoginAttemptResult.Success(authorization)
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        if (pageRequestCancellation.get()?.isCancelled() == true) throw CancellationException("Login cancelled")
        AppLogger.e("Login", "Login network request failed", e)
        LoginAttemptResult.IoFailure(e)
    } catch (e: Exception) {
        AppLogger.e("Login", "Login request or response parsing failed", e)
        LoginAttemptResult.InvalidResponse
    }
}

/** Collects the complete login cookie exchange before it is committed to persistent storage. */
private class LoginCookieJar : CookieJar {

    private val lock = Any()
    private val cookies = mutableListOf<Cookie>()

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
        cookies.filter { it.matches(url) }
    }

    override fun saveFromResponse(url: HttpUrl, responseCookies: List<Cookie>) {
        synchronized(lock) {
            for (cookie in responseCookies) {
                val index = cookies.indexOfFirst { it.sameIdentity(cookie) }
                if (cookie.expiresAt <= System.currentTimeMillis()) {
                    if (index >= 0) cookies.removeAt(index)
                } else if (index >= 0) {
                    cookies[index] = cookie
                } else {
                    cookies += cookie
                }
            }
        }
    }

    fun allCookies(): List<Cookie> = synchronized(lock) { cookies.toList() }

    private fun Cookie.sameIdentity(other: Cookie): Boolean =
        name == other.name &&
            domain.equals(other.domain, ignoreCase = true) &&
            path == other.path
}

private fun parseLoginStatus(body: String): Int? {
    return try {
        val root = JsonParser.parseString(body)
        if (!root.isJsonObject) {
            null
        } else {
            val status = root.asJsonObject.get("status")
            if (status == null || !status.isJsonPrimitive) {
                null
            } else {
                status.asJsonPrimitive.asString.toIntOrNull()
            }
        }
    } catch (e: Exception) {
        AppLogger.w("Login", "Login response was not valid JSON", e)
        null
    }
}

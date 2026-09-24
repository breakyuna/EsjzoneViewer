package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.readTextBounded
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.data.settings.SettingsDefaults
import com.breakyuna.esjzone.network.pageRequestCancellation
import com.breakyuna.esjzone.util.AppLogger
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.io.IOException

fun EsjzoneClient.login(
    email: String,
    password: String,
    domain: String = EsjzoneUrls.BaseWithoutProtocol
): Authorization? {
    require(domain in SettingsDefaults.DOMAINS)
    return try {
        val baseUrl = EsjzoneUrls.baseForDomain(domain)
        var loginResponseUrl: HttpUrl? = null
        val cookieJar = LoginCookieJar()
        val httpClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()

        val tokenCall = httpClient.newCall(
            Request.Builder()
                .url(EsjzoneUrls.resolve("/my/login", baseUrl))
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
            if (!response.isSuccessful || response.request.url.host != domain) {
                null
            } else {
                parseAuthorizationToken(response.body?.readTextBounded().orEmpty())
            }
        } } finally { cancellation?.detachCall() }

        if (authorizationToken.isNullOrBlank()) {
            AppLogger.w("Login", "Login token response was empty or malformed")
            return null
        }

        val loginCall = httpClient.newCall(
            Request.Builder()
                .url(EsjzoneUrls.resolve("/inc/mem_login.php", baseUrl))
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
            if (!response.isSuccessful || response.request.url.host != domain) {
                null
            } else {
                parseLoginStatus(response.body?.readTextBounded().orEmpty())
            }
        } } finally { cancellation?.detachCall() }

        if (cancellation?.isCancelled() == true) throw CancellationException("Login cancelled")

        if (status != 200) {
            return null
        }

        val responseUrl = loginResponseUrl ?: return null
        val cookies = cookieJar.allCookies().filter { it.matches(responseUrl) }
        val key = cookies.firstOrNull { it.name == "ews_key" }?.value
        val token = cookies.firstOrNull { it.name == "ews_token" }?.value
        if (key.isNullOrBlank() || token.isNullOrBlank()) {
            AppLogger.w("Login", "Login succeeded without the required session cookies")
            return null
        }

        activateAccountScope(responseUrl.host, email, key)
        rotatePageCacheScope(responseUrl.host)
        persistCookies(responseUrl, cookies)
        return Authorization(key, token, domain).also {
            markAuthorizationVerified(it)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        if (pageRequestCancellation.get()?.isCancelled() == true) throw CancellationException("Login cancelled")
        AppLogger.e("Login", "Login network request failed", e)
        throw e
    } catch (e: Exception) {
        AppLogger.e("Login", "Login request or response parsing failed", e)
        null
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

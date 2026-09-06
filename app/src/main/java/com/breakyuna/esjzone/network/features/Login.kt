package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
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

fun EsjzoneClient.login(email: String, password: String): Authorization? {
    return try {
        var loginResponseUrl: HttpUrl? = null
        val cookieJar = LoginCookieJar()
        val httpClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()

        val authorizationToken = httpClient.newCall(
            Request.Builder()
                .url(EsjzoneUrls.My.Login)
                .post(
                    FormBody.Builder()
                        .add("plxf", "getAuthToken")
                        .build()
                )
                .headers(this.headers)
                .build()
        ).execute().use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                parseAuthorizationToken(response.body?.string().orEmpty())
            }
        }

        if (authorizationToken.isNullOrBlank()) {
            AppLogger.w("Login", "Login token response was empty or malformed")
            return null
        }

        val status = httpClient.newCall(
            Request.Builder()
                .url(EsjzoneUrls.Inc.MemLogin)
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
        ).execute().use { response ->
            loginResponseUrl = response.request.url
            if (!response.isSuccessful) {
                null
            } else {
                parseLoginStatus(response.body?.string().orEmpty())
            }
        }

        if (status != 200) {
            return null
        }

        val cookies = cookieJar.allCookies()
        val key = cookies.firstOrNull { it.name == "ews_key" }?.value
        val token = cookies.firstOrNull { it.name == "ews_token" }?.value
        if (key.isNullOrBlank() || token.isNullOrBlank()) {
            AppLogger.w("Login", "Login succeeded without the required session cookies")
            return null
        }

        loginResponseUrl?.let { responseUrl ->
            rotatePageCacheScope(responseUrl.host)
            persistCookies(responseUrl, cookies)
        }
        return Authorization(key, token, EsjzoneUrls.BaseWithoutProtocol).also {
            markAuthorizationVerified(it)
        }
    } catch (e: CancellationException) {
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

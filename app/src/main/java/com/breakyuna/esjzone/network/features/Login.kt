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

fun EsjzoneClient.login(email: String, password: String): Authorization? {
    return try {
        var authorization: Authorization? = null
        val httpClient = OkHttpClient.Builder()
            .cookieJar(object : CookieJar {
                override fun loadForRequest(url: HttpUrl): List<Cookie> = emptyList()

                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    var ewsKey: String? = null
                    var ewsToken: String? = null

                    for (cookie in cookies) {
                        when (cookie.name) {
                            "ews_key" -> ewsKey = cookie.value
                            "ews_token" -> ewsToken = cookie.value
                        }
                    }

                    val key = ewsKey?.takeIf { it.isNotBlank() }
                    val token = ewsToken?.takeIf { it.isNotBlank() }
                    if (key != null && token != null) {
                        authorization = Authorization(key, token)
                    }
                }
            })
            .build()

        val authorizationToken = EMPTY_HTTP_CLIENT.newCall(
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
            if (!response.isSuccessful) {
                null
            } else {
                parseLoginStatus(response.body?.string().orEmpty())
            }
        }

        if (status != 200) {
            return null
        }

        authorization
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.e("Login", "Login request or response parsing failed", e)
        null
    }
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
